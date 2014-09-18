package com.shopwiki.xzcute;

import java.io.PrintStream;
import java.util.Formatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * @owner jdickinson
 * @owner rstewart
 */
public class VerboseThreadPoolExecutor extends ThreadPoolExecutor {

    public static VerboseThreadPoolExecutorBuilder builder() {
        return new VerboseThreadPoolExecutorBuilder();
    }

    private final AtomicInteger completedCounter = new AtomicInteger(0);
    private final AtomicInteger exceptionCounter = new AtomicInteger(0);
    private final AtomicLong lastPrintTime = new AtomicLong(0L);
    private final AtomicLong startTime = new AtomicLong(0L);

    public final boolean print;
    public final PrintStream printStream;
    public final int tasksPerPrint;
    public final long millisPerPrint;
    public final boolean verbosePrint;
    public final boolean printExceptions;
    public final int expectedNumTasks;

    private final String printWidth;
    private final String countsFormat;

    public static class TaskPrinter<P> {
        public String taskToString(Object task) {
            return String.valueOf(task);
        }

        public String resultToString(P result) {
            return String.valueOf(result);
        }
    }

    private final TaskPrinter taskPrinter; // Can't get generics working here :(

    public VerboseThreadPoolExecutor(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit unit,
            BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory,
            RejectedExecutionHandler handler,
            boolean print,
            PrintStream printStream,
            int tasksPerPrint,
            long millisPerPrint,
            boolean verbosePrint,
            boolean printExceptions,
            int expectedNumTasks,
            TaskPrinter<?> taskPrinter
            ) {

        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        this.print = print;
        this.printStream = printStream;
        this.tasksPerPrint = tasksPerPrint;
        this.millisPerPrint = millisPerPrint;
        this.verbosePrint = verbosePrint;
        this.printExceptions = printExceptions;
        this.expectedNumTasks = expectedNumTasks;
        int numTasksDigits = expectedNumTasks > 0 ? String.valueOf(expectedNumTasks).length() : 10;
        this.printWidth = numTasksDigits + "d";
        this.countsFormat = "%1$tF %1$tT.%1$tL %2$" + printWidth + " tasks complete" + SEP + "%3$" + printWidth + " exceptions";
        this.taskPrinter = taskPrinter;
    }

    private static final String SEP = "; ";

    private class FutureTaskWithCallable<V> extends FutureTask<V> {

        private Object callable = null;

        public FutureTaskWithCallable(Callable<V> callable) {
            super(callable);
            this.callable = callable;
        }

        public FutureTaskWithCallable(Runnable runnable, V result) {
            super(runnable, result);
            this.callable = runnable;
        }

        @Override
        public String toString() {
            String message;
            try {
                message = taskPrinter.resultToString(this.get());
            } catch (InterruptedException e) {
                message = e.toString();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                message = cause != null ? cause.toString() : e.toString();
            }
            return taskPrinter.taskToString(callable) + SEP + message;
        }
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        startTime.compareAndSet(0L, System.currentTimeMillis());
        return new FutureTaskWithCallable<T>(callable);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        startTime.compareAndSet(0L, System.currentTimeMillis());
        return new FutureTaskWithCallable<T>(runnable, value);
    }

    @Override
    protected void afterExecute(Runnable runnable, Throwable thrown) {

        if (print == false) {
            return;
        }

        if (thrown == null) {
            FutureTaskWithCallable<?> ftwc = (FutureTaskWithCallable<?>) runnable;
            try {
                ftwc.get();
            } catch (Throwable e) {
                if (e.getCause() != null) {
                    e = e.getCause();
                }
                thrown = e;
            }
        }

        int completedCount;
        int exceptionCount;

        if (thrown == null) {
            completedCount = completedCounter.incrementAndGet();
            exceptionCount = exceptionCounter.get();
        } else {
            exceptionCount = exceptionCounter.incrementAndGet();
            completedCount = completedCounter.get();
            if (printExceptions) {
                thrown.printStackTrace();
            }
        }

        int done = completedCount + exceptionCount;
        int left = expectedNumTasks - done;

        if (done % tasksPerPrint != 0) {
            return;
        }

        long time = System.currentTimeMillis();

        if (millisPerPrint != 0) {
            if (lastPrintTime.get() + millisPerPrint >= time) {
                return;
            }
            lastPrintTime.set(time);
        }

        String outputLine = getLogString(exceptionCount, done, left, time);
        if (verbosePrint) {
            outputLine += SEP + runnable.toString();
        }

        printStream.println(outputLine);
    }

    private String getLogString(int except, int done, int left, long time) {
        Formatter formatter = new Formatter();
        formatter.format(countsFormat, time, done, except);
        long millisTaken = System.currentTimeMillis() - startTime.get();
        double tasksPerHour = (double)(TimeUnit.HOURS.toMillis(1) * done) / millisTaken;
        //formatter.format(sep + "taken %s (%.3g per hour)", Pretty.time(millisTaken), tasksPerHour);
        formatter.format(SEP + "taken %-12s (%6s per hour)", Pretty.time(millisTaken), Pretty.metric(tasksPerHour));
        if (expectedNumTasks > 0) {
            long millisToComplete = millisToComplete(startTime.get(), done, left);
            //sb.append(String.format(" %" + printWidth + " left", left));
            formatter.format(SEP + "ETC %-12s", Pretty.time(millisToComplete));
        }
        return formatter.toString();
    }

    // Also see ThreadPoolExecutor.getTaskCount() = The # of tasks submitted ???
    // Also see ThreadPoolExecutor.getCompletedTaskCount() = completedCount + exceptionCount ???

    public int getCompletedCount() {
        return completedCounter.get();
    }

    public int getExceptionCount() {
        return exceptionCounter.get();
    }

    public long getLastPrintTime() {
        return lastPrintTime.get();
    }

    public long getStartTime() {
        return startTime.get();
    }

    public String getStatus() {
        int except = exceptionCounter.get();
        int done = completedCounter.get() + except;
        int left = expectedNumTasks - done;
        return getLogString(except, done, left, System.currentTimeMillis());
    }

    public static long millisToComplete(long startMillis, long numDone, long numLeft) {
        if (numDone == 0) {
            return 0;
        }

        double totalMillis = System.currentTimeMillis() - startMillis;
        double millisPer = totalMillis / numDone;
        return (long)(millisPer * numLeft);
    }
}
