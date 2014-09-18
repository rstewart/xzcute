package com.shopwiki.xzcute;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Throwables;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

/**
 * @owner rstewart
 */
public class BackgroundExecutor {

    public static class Job implements Comparable<Job> {

        public final String name;
        public final long period;
        public final TimeUnit unit;

        final AtomicBoolean running = new AtomicBoolean(false);
        final AtomicInteger numRuns = new AtomicInteger(0);
        final AtomicInteger numErrors = new AtomicInteger(0);
        final AtomicLong lastEndTime = new AtomicLong(0);
        final AtomicLong lastTimeTaken = new AtomicLong(-1);
        final AtomicLong totalTimeTaken = new AtomicLong(0);

        Job(String name, long period, TimeUnit unit) {
            this.name = name;
            this.period = period;
            this.unit = unit;
        }

        void ran(long startTime) {
            long endTime = System.currentTimeMillis();
            long timeTaken = endTime - startTime;
            running.compareAndSet(true, false);
            numRuns.getAndIncrement();
            lastEndTime.set(endTime);
            lastTimeTaken.set(timeTaken);
            totalTimeTaken.getAndAdd(timeTaken);
        }

        void error() {
            numErrors.getAndIncrement();
        }

        public boolean isRunning() {
            return running.get();
        }

        public int getNumRuns() {
            return numRuns.get();
        }

        public int getNumErrors() {
            return numErrors.get();
        }

        public long getLastEndTime() {
            return lastEndTime.get();
        }

        public long getLastTimeTaken() {
            return lastTimeTaken.get();
        }

        public long getTotalTimeTaken() {
            return totalTimeTaken.get();
        }

        @Override
        public int compareTo(Job o) {
            return String.CASE_INSENSITIVE_ORDER.compare(name, o.name);
        }
    }

    public interface BackgroundCallable extends Callable<String> { // Doesn't have any checked Exceptions!
        @Override
        String call();
    }

    public interface Logger {
        void error(String jobName, Throwable t);
        void success(String jobName, long timeTaken, String result); // TODO: Should result just be an Object ???
        void manual(String jobName, int numJobs);
    }

    private static Logger NOOP_LOGGER = new Logger() {
        @Override
        public void error(String jobName, Throwable t) {
            // do nothing
        }

        @Override
        public void success(String jobName, long timeTaken, String result) {
            // do nothing
        }

        @Override
        public void manual(String jobName, int numJobs) {
            // do nothing
        }
    };

    private final Set<Job> jobs = Collections.newSetFromMap(new ConcurrentHashMap<Job,Boolean>());
    private final Multimap<String, BackgroundCallable> nameToCallable = HashMultimap.create();
    private final ScheduledExecutorService scheduler;
    private final Logger logger;

    public BackgroundExecutor(int numThreads) {
        this(numThreads, NOOP_LOGGER);
    }

    public BackgroundExecutor(int numThreads, Logger logger) {
        scheduler = new DaemonScheduledExecutor(numThreads, "BackgroundExecutor");
        this.logger = logger;
    }

    public SortedSet<Job> getJobs() {
        return new TreeSet<Job>(jobs);
    }

    public List<BackgroundCallable> getCallables(String name) {
        return Lists.newArrayList(nameToCallable.get(name));
    }

    public List<String> runJobs(String name) {
        Collection<BackgroundCallable> callables = nameToCallable.get(name);
        logger.manual(name, callables.size());
        List<String> results = Lists.newArrayList();
        for (BackgroundCallable callable : callables) {
            String result = callable.call();
            results.add(result);
        }
        return results;
    }

    private BackgroundCallable addJob(final String name, final Callable<?> callable, long period, TimeUnit unit) {
        final Job job = new Job(name, period, unit);
        jobs.add(job); // TODO: Enforce that 2 jobs not have the same name (and/or Runnable) ???

        BackgroundCallable myCallable = new BackgroundCallable() {
            @Override
            public String call() {
                if (! job.running.compareAndSet(false, true)) {
                    return "Already running!";
                }

                Object result = null;
                long startTime = System.currentTimeMillis();
                try {
                    result = callable.call();
                } catch (Throwable t) {
                    logger.error(name, t);
                    job.error();
                    return Throwables.getStackTraceAsString(t);
                } finally {
                    job.ran(startTime);
                }

                String s = String.valueOf(result);
                if (result != null) {
                    long timeTaken = job.lastTimeTaken.get();
                    logger.success(name, timeTaken, s);
                }
                return s;
            }
        };

        nameToCallable.put(name, myCallable);

        return myCallable;
    }

    public ScheduledFuture<?> runAndSchedule(String name, Runnable runnable, long period, TimeUnit unit) {
        return runAndSchedule(name, Executors.callable(runnable), period, unit);
    }

    public ScheduledFuture<?> schedule(String name, Runnable runnable, long period, TimeUnit unit, boolean runImmediately) {
        return schedule(name, Executors.callable(runnable), period, unit, runImmediately);
    }

    /**
     * Runs the job once synchronously and then schedules it.
     */
    public ScheduledFuture<?> runAndSchedule(String name, Callable<?> callable, long period, TimeUnit unit) {
        BackgroundCallable myCallable = addJob(name, callable, period, unit);
        myCallable.call();
        return schedule(myCallable, period, period, unit);
    }

    public ScheduledFuture<?> schedule(String name, Callable<?> callable, long period, TimeUnit unit, boolean runImmediately) {
        BackgroundCallable myCallable = addJob(name, callable, period, unit);
        long initialDelay = runImmediately ? 0 : period;
        return schedule(myCallable, initialDelay, period, unit);
    }

    private ScheduledFuture<?> schedule(final BackgroundCallable callable, long initialDelay, long period, TimeUnit unit) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                callable.call();
            }
        };
        return scheduler.scheduleAtFixedRate(runnable, initialDelay, period, unit);
    }
}
