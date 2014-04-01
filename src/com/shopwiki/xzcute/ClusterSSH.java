package com.shopwiki.xzcute;

import java.util.*;
import java.util.concurrent.*;

import com.amazonaws.services.ec2.model.*;
import com.google.common.collect.*;
import com.google.common.collect.ImmutableList;
import com.shopwiki.io.LineIterator;
import com.shopwiki.net.Page;
import com.shopwiki.prodsys.SSH.SSHException;
import com.shopwiki.util.*;
import com.shopwiki.util.concurrent.*;
import com.shopwiki.util.concurrent.EnhancedThreadPoolExecutor.TaskPrinter;
import com.shopwiki.util.webservices.aws.AWSUtil;
import com.shopwiki.wiki.*;

/**
 * @owner rstewart
 */
public class ClusterSSH {

    public final String _username;
    public final String _sshKeyFile;
    private final String _sudoPassword;
    public final List<Worker> _workers;
    public final boolean _verbose; // TODO: Get rid of this and make the methods that use it taken an extra param ???

    public static class Worker { // TODO: Extend from com.shopwiki.wiki.Machine ???
        public final int w;
        public final String host;

        private Worker(int w, String host) {
            this.w    = w;
            this.host = host;
        }

        @Override
        public String toString() {
            String s = TextUtil.pad("" + w, 2, true, ' ');
            return "Worker # " + s + ": " + host;
        }
    }

    public ClusterSSH(Args args) throws Exception {
        this(args, getWorkers(args));
    }

    public ClusterSSH(Args args, Iterable<String> workers) throws Exception {
        this(args, getWorkers(workers));
    }

    public ClusterSSH(Args args, Collection<Worker> workers) throws Exception {
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
        } else if (args.hasFlag("ec2")) {
            String securityGroup = args.get("ec2");
            workers = getWorkersFromEC2(securityGroup);
        } else if (args.hasFlag("use")) {
            String use = args.get("use");
            workers = getWorkersByUse(use, args);
        } else if (args.hasFlag("all")) {
            workers = getAllWorkers();
        } else {
            workers = getWorkersByProperty(args);
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

    public static List<Worker> getWorkersFromEC2(String securityGroup) {
        System.out.println("Getting workers from EC2 security group: " + securityGroup);
        List<Worker> workers = new ArrayList<Worker>();
        List<String> instanceIds = new ArrayList<String>();
        int w = 0;
        for (Reservation reservation : AWSUtil.getEC2().describeInstances().getReservations()) {
            if (! reservation.getGroupNames().contains(securityGroup)) {
                continue;
            }

            for (Instance instance : reservation.getInstances()) {
                String host = instance.getPublicDnsName();
                if (host == null || host.isEmpty()) {
                    continue;
                }
                w++;
                Worker worker = new Worker(w, host);
                workers.add(worker);
                instanceIds.add(instance.getInstanceId());
                System.out.println("# " + w + "\t" + worker);
            }
        }
        System.out.println(StringUtil.join(" ", instanceIds));
        return workers;
    }

    public static List<Worker> getWorkersByUse(String use, Args args) {
        StringBuilder url = new StringBuilder("http://dev.shopwiki.com/admin/prodsys/machineSearch.jsp?f_use=" + use);

        for (Pair<String, String> filter : getFilters(args)) {
            url.append("&" + filter.first + "=" + filter.second);
        }

        Page page = new Page(-1, url.toString());
        page.setUseCache(false);
        page.setWriteToCache(false);
        System.out.println("Getting workers from " + page);
        String text = page.getRawHtml().trim();
        if (text.isEmpty()) {
            return Collections.emptyList();
        }
        return getWorkers(StringUtil.split(text, "\n"));
    }

    public static List<Worker> getWorkersByProperty(Args args) {
        List<Pair<String, String>> filters = getFilters(args);
        List<String> hosts = new ArrayList<String>();

        for (Machine machine : Machine.getAllProductionActive()) {
            boolean add = true;

            for (Pair<String, String> filter : filters) {
                String property = filter.first.substring(2);
                String value = machine.getProperty(property);
                if (value == null || StringUtil.containsIgnoreCase(value, filter.second) == false) {
                    add = false;
                    break;
                }
            }

            if (add) {
                hosts.add(machine.getHost());
            }
        }

        return getWorkers(hosts);
    }

    private static List<Pair<String, String>> getFilters(Args args) {
        List<Pair<String, String>> filters = Lists.newArrayList();

        for (String flag : args.getFlags(false)) {
            if (! flag.startsWith("f_")) {
                continue;
            }
            String value = args.get(flag);
            if (value == null || value.isEmpty()) {
                continue;
            }
            System.out.println("FILTER - " + flag + ": " + value);
            filters.add(Pair.create(flag, value));
        }

        return filters;
    }

    public static List<Worker> getAllWorkers() {
        List<Worker> workers = Lists.newArrayList();
        int w = 0;
        for (int i = 1; i <= 255; i++) {
            w++;
            Worker worker = new Worker(w, "ny" + i + ".shopwiki.com");
            workers.add(worker);
            System.out.println("# " + w + "\t" + worker);
        }
        return workers;
    }

    public void commandWorkers(String command, Args args) throws SSHException, InterruptedException {
        boolean serial = args.hasFlag("serial");
        boolean noWait = args.hasFlag("noWait", false);
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

        EnhancedThreadPoolExecutorFactory factory = new EnhancedThreadPoolExecutorFactory();
        factory.setPoolSize(_workers.size());
        factory.setVerbosePrint(true);
        factory.setPrintExceptions(false);
        factory.setExpectedNumTasks(_workers.size());
        factory.setTaskPrinter(new TaskPrinter<String>() {
            @Override
            public String resultToString(String result) { return ""; }
        });
        EnhancedThreadPoolExecutor executor = factory.getTheThreadPoolExecutor();

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

    public void supervisorControl(String command, Args args) throws SSHException, InterruptedException {
        command = "supervisorctl " + command;
        if (_username.equals("ec2-user")) {
            command = "sudo " + command;
        }
        commandWorkers(command, args);
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

        if (args.hasFlag("supervisor")) {
            String cmd = args.get("supervisor");
            supervisorControl(cmd, args);
            return;
        }
    }

    public static void main(String[] jargs) throws Exception {
        Args args = new Args(jargs);
        System.out.println();
        ClusterSSH clusterSSH = new ClusterSSH(args);
        System.out.println();
        clusterSSH.doStuff(args);
    }
}
