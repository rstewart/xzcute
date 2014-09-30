package com.shopwiki.xzcute.ssh;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

/**
 * @author rstewart
 */
public class Worker {

    public final int w;
    public final String host;

    private Worker(int w, String host) {
        this.w = w;
        this.host = host;
    }

    @Override
    public String toString() {
        String s = String.format("%d2", w);
        return "Worker # " + s + ": " + host;
    }

    static List<Worker> get(Args args) {
        List<Worker> workers = null;

        if (args.hasFlag("hosts")) {
            String hostStr = args.get("hosts");
            workers = getFromString(hostStr);
        } else if (args.hasFlag("file")) {
            String filename = args.get("file");
            workers = getFromFile(filename);
        } else {
            // TODO: USAGE message
        }

        String filterStr = args.get("w");
        return filterWorkers(workers, filterStr);
    }

    public static List<Worker> getFromString(String str) {
        System.out.println("Getting workers from string: " + str);
        return get(COMMA_SPLITTER.split(str));
    }

    public static List<Worker> getFromFile(String filename) {
        System.out.println("Getting workers from file: " + filename);
        File file = new File(filename);
        try {
            List<String> lines = Files.readLines(file, Charsets.UTF_8);
            return get(lines);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Worker> get(Iterable<String> it) {
        List<Worker> workers = Lists.newArrayList();
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

    // TODO: Make this public ???
    private static List<Worker> filterWorkers(List<Worker> workers, String filterStr) {
        Set<Integer> ws = getNums(filterStr);
        if (ws == null) {
            return workers;
        }

        System.out.println("Only using " + ws.size() + " workers: " + ws);
        List<Worker> workersToUse = Lists.newArrayList();
        for (Worker worker : workers) {
            if (ws.contains(worker.w)) {
                workersToUse.add(worker);
            }
        }
        return workersToUse;
    }

    private static final Splitter COMMA_SPLITTER = Splitter.on(',');

    private static Set<Integer> getNums(String arg) {

        if (Strings.isNullOrEmpty(arg)) {
            return null;
        }

        Set<Integer> ws = Sets.newTreeSet();
        for (String piece : COMMA_SPLITTER.split(arg)) {
            ws.add(Integer.parseInt(piece));
        }
        return ws;
    }
}
