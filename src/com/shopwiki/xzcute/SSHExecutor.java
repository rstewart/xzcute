package com.shopwiki.xzcute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import sun.security.jca.GetInstance.Instance;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.shopwiki.xzcute.VerboseThreadPoolExecutor.TaskPrinter;
import com.shopwiki.xzcute.sshexec.Args;
import com.shopwiki.xzcute.sshexec.LineIterator;
import com.shopwiki.xzcute.sshexec.PasswordField;
import com.shopwiki.xzcute.sshexec.SSH;
import com.shopwiki.xzcute.sshexec.SSH.SSHException;
import com.sun.tools.javac.util.Pair;

/**
 * @owner rstewart
 */
public class SSHExecutor {

    public final String _username;
    public final String _sshKeyFile;
    private final String _sudoPassword;
    public final List<Worker> _workers;
    public final boolean _verbose; // TODO: Get rid of this and make the methods that use it taken an extra param ???

    public static class Worker {
        public final int w;
        public final String host;

        private Worker(int w, String host) {
            this.w    = w;
            this.host = host;
        }

        @Override
        public String toString() {
            String s = String.format("%d2", w);
            return "Worker # " + s + ": " + host;
        }
    }

    public SSHExecutor(Args args) throws Exception {
        this(args, getWorkers(args));
    }

    public SSHExecutor(Args args, Iterable<String> workers) throws Exception {
        this(args, getWorkers(workers));
    }

    public SSHExecutor(Args args, Collection<Worker> workers) throws Exception {
        if (args == null) {
            args = new Args(null);
        }

        String username = args.get("user");
        String sshKeyFile = args.get("key");

        if (username == null) {
            if (args.hasFlag("ec2")) {
                username = "ec2-user";
            } else {
                username = "localadmin";
            }
        }
        _username = username;

        if (sshKeyFile == null && args.hasFlag("ec2")) {
            sshKeyFile = "/data/shopwiki-aws/account-info/id_rsa-kp1";
        }
        _sshKeyFile = sshKeyFile;

        System.out.println();
        System.out.println("USERNAME: " + username);
        System.out.println("SSH-KEY: " + sshKeyFile);

        if (args.hasFlag("sudo")) {
            _sudoPassword = PasswordField.readPassword("Enter sudo password: ");
        } else {
            _sudoPassword = null;
        }

        _workers = ImmutableList.copyOf(workers);
        _verbose = ! args.hasFlag("quiet");
    }

    public static List<Worker> getWorkers(Args args) {
        List<Worker> workers = null;
        if (args.hasFlag("hosts")) {
            String str = args.get("hosts");
            workers = getWorkersFromString(str);
        } else if (args.hasFlag("file")) {
            String filename = args.get("file");
            workers = getWorkersFromFile(filename);
        }

        if (workers == null || workers.isEmpty()) {
            throw new RuntimeException("Don't have any workers!");
        }

        Set<Integer> ws = getWorkerNums(args);
        if (ws == null) {
            return workers;
        }

        System.out.println("Only using " + ws.size() + " workers: " + ws);
        List<Worker> workersToUse = new ArrayList<Worker>();
        for (Worker worker : workers) {
            if (ws.contains(worker.w)) {
                workersToUse.add(worker);
            }
        }
        return workersToUse;
    }

    public static Set<Integer> getWorkerNums(Args args) {
        String arg = args.get("w", "");
        if (arg.isEmpty()) {
            return null;
        }

        Set<Integer> ws = new TreeSet<Integer>();
        for (String piece : StringUtil.split(arg, ",")) {
            ws.add(Integer.parseInt(piece));
        }
        return ws;
    }

    public static List<Worker> getWorkers(Iterable<String> it) {
        List<Worker> workers = new ArrayList<Worker>();
        int w = 0;
        for (String s : it) {
            String host = s.trim();
            if (host.isEmpty()) {
                continue;
            }
            if (host.startsWith("#")) { // skip over lines that are commented-out
                continue;
            }
            w++;
            Worker worker = new Worker(w, host);
            workers.add(worker);
            System.out.println("# " + w + "\t" + host);
        }
        return workers;
    }

    public static List<Worker> getWorkersFromString(String str) {
        System.out.println("Getting workers from string: " + str);
        return getWorkers(StringUtil.split(str, ","));
    }

    public static List<Worker> getWorkersFromFile(String filename) {
        System.out.println("Getting workers from file: " + filename);
        return getWorkers(new LineIterator(filename));
    }

    public void commandWorkers(String command, Args args) throws SSHException, InterruptedException {
        boolean serial = args.hasFlag("serial");
        boolean noWait = args.hasFlag("noWait");
        commandWorkers(command, serial, noWait);
    }

    public void commandWorkers(String command, boolean serial, boolean noWait) throws SSHException, InterruptedException {
        System.out.println("Commanding workers:\n" + command + "\n");

        if (_sudoPassword != null) {
            command = "echo " + _sudoPassword + " | sudo -S " + command;
        }

        if (serial) {
            _commandWorkersSerial(command);
        } else if (noWait) {
            _commandWorkersAsyncNoWait(command);
        } else {
            _commandWorkersAsyncOrdered(command);
        }
    }

    private void _commandWorkersSerial(String command) throws SSHException {
        for (Worker worker : _workers) {
            System.out.println(worker + "\n");
            String result = SSH.sendCommand(_username, _sshKeyFile, worker.host, command);
            if (_verbose) {
                UTF8.out.println(result);
            }
        }
    }

    private class Task implements Callable<String> {
        public final Worker worker;
        public final String command;

        public Task(Worker worker, String command) {
            this.worker = worker;
            this.command = command;
        }

        @Override
        public String call() throws SSHException {
            return SSH.sendCommand(_username, _sshKeyFile, worker.host, command);
        }

        @Override
        public String toString() {
            return worker.toString();
        }
    }

    private void _commandWorkersAsyncNoWait(String command) throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(_workers.size());
        ExecutorCompletionService<String> ecs = new ExecutorCompletionService<String>(executor);

        Map<Future<String>,Worker> futureToWorker = new HashMap<Future<String>,Worker>();

        for (final Worker worker : _workers) {
            Task task = new Task(worker, command);
            Future<String> future = ecs.submit(task);
            futureToWorker.put(future, worker);
        }
        executor.shutdown();

        if (! _verbose) {
            System.out.println();
            return;
        }

        for (int i = 0; i < futureToWorker.size(); i++) {
            Future<String> future = ecs.take();
            Worker worker = futureToWorker.get(future);
            printFuture(worker, future);
        }

        executor.awaitTermination(1, TimeUnit.DAYS); // TODO: awaitTermnation even if ! _verbose ???
    }

    private void _commandWorkersAsyncOrdered(String command) throws InterruptedException {

        VerboseThreadPoolExecutorFactory factory = new VerboseThreadPoolExecutorFactory();
        factory.setPoolSize(_workers.size());
        factory.setVerbosePrint(true);
        factory.setPrintExceptions(false);
        factory.setExpectedNumTasks(_workers.size());
        factory.setTaskPrinter(new TaskPrinter<String>() {
            @Override
            public String resultToString(String result) { return ""; }
        });
        VerboseThreadPoolExecutor executor = factory.getTheThreadPoolExecutor();

        Map<Worker,Future<String>> workerToFuture = new LinkedHashMap<Worker,Future<String>>();

        for (Worker worker : _workers) {
            Task task = new Task(worker, command);
            Future<String> future = executor.submit(task);
            workerToFuture.put(worker, future);
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.DAYS);
        System.out.println();

        if (! _verbose) {
            return;
        }

        for (Worker worker : workerToFuture.keySet()) {
            Future<String> future = workerToFuture.get(worker);
            printFuture(worker, future);
        }
    }

    private static void printFuture(Worker worker, Future<String> future) throws InterruptedException {
        System.out.println(worker + "\n");
        try {
            UTF8.out.println(future.get());
        } catch (ExecutionException e) {
            e.getCause().printStackTrace();
            System.out.println();
        }
    }

    public void psGrep(String pattern, Args args) throws SSHException, InterruptedException {
        commandWorkers("ps aux | grep " + pattern + " | grep -v grep", args);
    }

    public void doStuff(Args args) throws Exception {

        if (args.hasFlag("cmd")) {
            String cmd = args.get("cmd");
            commandWorkers(cmd, args);
            return;
        }

        if (args.hasFlag("ps")) {
            String pattern = args.get("ps");
            psGrep(pattern, args);
            return;
        }
    }

    public static void main(String[] jargs) throws Exception {
        Args args = new Args(jargs);
        System.out.println();
        SSHExecutor clusterSSH = new SSHExecutor(args);
        System.out.println();
        clusterSSH.doStuff(args);
    }
}
