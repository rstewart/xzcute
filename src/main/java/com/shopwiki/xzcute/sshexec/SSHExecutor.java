package com.shopwiki.xzcute.sshexec;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.shopwiki.xzcute.VerboseThreadPoolExecutor;
import com.shopwiki.xzcute.VerboseThreadPoolExecutor.TaskPrinter;
import com.shopwiki.xzcute.sshexec.SSH.SSHException;
import com.shopwiki.xzcute.util.UTF8;

/**
 * @owner rstewart
 */
public class SSHExecutor {

    private final String _username;
    private final String _sshKeyFile;
    private final String _sudoPassword;
    private final List<Worker> _workers;
    private final boolean _verbose; // TODO: Get rid of this and make the methods that use it taken an extra param ???

    private SSHExecutor(Args args) throws Exception {
        this(Worker.get(args), args);
    }

    private SSHExecutor(Collection<Worker> workers, Args args) throws Exception {
        this(workers, args.get("user"), args.get("key"), args.hasFlag("sudo"), args.hasFlag("quiet"));
    }

    public SSHExecutor(Collection<Worker> workers, String username, String sshKeyFile, boolean sudo, boolean quiet) throws Exception {

        if (workers == null || workers.isEmpty()) {
            throw new RuntimeException("Don't have any workers!");
        }

        _workers = ImmutableList.copyOf(workers);

        _username = username;
        _sshKeyFile = sshKeyFile;

        System.out.println();
        System.out.println("USERNAME: " + _username);
        System.out.println("SSH-KEY: " + _sshKeyFile);

        if (sudo) {
            _sudoPassword = PasswordField.readPassword("Enter sudo password: ");
        } else {
            _sudoPassword = null;
        }

        _verbose = ! quiet;
    }

    public String getUsername() {
        return _username;
    }

    public String getSSHKeyFile() {
        return _sshKeyFile;
    }

    public List<Worker> getWorkers() {
        return _workers;
    }

    public boolean isVerbose() {
        return _verbose;
    }

    private void commandWorkers(String command, Args args) throws SSHException, InterruptedException {
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

        Map<Future<String>, Worker> futureToWorker = Maps.newHashMap();

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

        TaskPrinter<String> taskPrinter = new TaskPrinter<String>() {
            @Override
            public String resultToString(String result) {
                return "";
            }
        };

        VerboseThreadPoolExecutor executor = VerboseThreadPoolExecutor.builder()
                .setPoolSize(_workers.size())
                .setVerbosePrint(true)
                .setPrintExceptions(false)
                .setExpectedNumTasks(_workers.size())
                .setTaskPrinter(taskPrinter)
                .build();

        Map<Worker, Future<String>> workerToFuture = Maps.newLinkedHashMap();

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

    private void psGrep(String pattern, Args args) throws SSHException, InterruptedException {
        commandWorkers("ps aux | grep " + pattern + " | grep -v grep", args);
    }

    private void doStuff(Args args) throws Exception {

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
        Args args = new Args(jargs, true);
        System.out.println();
        SSHExecutor clusterSSH = new SSHExecutor(args);
        System.out.println();
        clusterSSH.doStuff(args);
    }
}
