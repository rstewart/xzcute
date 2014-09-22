package com.shopwiki.xzcute;

import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Supplier;
import com.shopwiki.xzcute.VerboseThreadPoolExecutor.TaskPrinter;
import com.shopwiki.xzcute.util.UTF8;

/**
 * @owner jdickinson
 */
public final class VerboseThreadPoolExecutorBuilder {

    public VerboseThreadPoolExecutorBuilder() {
        // Use the static factory method in VerboseThreadPoolExecutor.
    }

    private int corePoolSize = 0;
    private int maximumPoolSize = 1;

    private long keepAliveTime = 1L;
    private TimeUnit unit = TimeUnit.MINUTES;

    private Supplier<BlockingQueue<Runnable>> workQueueSupplier = newDefaultQueueSupplier(1000);
    private ThreadFactory threadFactory = Executors.defaultThreadFactory();
    private RejectedExecutionHandler handler = new AbortPolicy();

    private boolean print = true;
    private PrintStream printStream = UTF8.out;
    private int tasksPerPrint = 1;
    private long millisPerPrint = 0L;
    private boolean verbosePrint = false;
    private boolean printExceptions = true;
    private int expectedNumTasks = 0;

    private TaskPrinter<?> taskPrinter = new TaskPrinter<Object>();

    private static Supplier<BlockingQueue<Runnable>> newDefaultQueueSupplier(final int capacity) {
        return new Supplier<BlockingQueue<Runnable>>() {
            @Override
            public BlockingQueue<Runnable> get() {
                return new EnhancedLinkedBlockingQueue<Runnable>(capacity);
            }
        };
    }

    public VerboseThreadPoolExecutor build() {
        return new VerboseThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime, unit,
                workQueueSupplier.get(),
                threadFactory,
                handler,
                print,
                printStream,
                tasksPerPrint,
                millisPerPrint,
                verbosePrint,
                printExceptions,
                expectedNumTasks,
                taskPrinter
                );
    }

    public VerboseThreadPoolExecutorBuilder setCorePoolSize(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        corePoolSize = size;
        return this;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public VerboseThreadPoolExecutorBuilder setMaximumPoolSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException();
        }
        maximumPoolSize = size;
        return this;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public VerboseThreadPoolExecutorBuilder setPoolSize(int size) {
        setCorePoolSize(size);
        setMaximumPoolSize(size);
        return this;
    }

    public VerboseThreadPoolExecutorBuilder setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
        return this;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public VerboseThreadPoolExecutorBuilder setUnit(TimeUnit unit) {
        this.unit = unit;
        return this;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public VerboseThreadPoolExecutorBuilder setWorkQueueSupplier(Supplier<BlockingQueue<Runnable>> workQueueSupplier) {
        this.workQueueSupplier = workQueueSupplier;
        return this;
    }

    public Supplier<BlockingQueue<Runnable>> getWorkQueueSupplier() {
        return workQueueSupplier;
    }

    public VerboseThreadPoolExecutorBuilder setWorkQueueCapacity(int capacity) {
        this.workQueueSupplier = newDefaultQueueSupplier(capacity);
        return this;
    }

    public VerboseThreadPoolExecutorBuilder setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        return this;
    }

    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    public VerboseThreadPoolExecutorBuilder setHandler(RejectedExecutionHandler handler) {
        this.handler = handler;
        return this;
    }

    public RejectedExecutionHandler getHandler() {
        return handler;
    }

    public VerboseThreadPoolExecutorBuilder setPrint(boolean print) {
        this.print = print;
        return this;
    }

    public boolean isPrint() {
        return print;
    }

    public VerboseThreadPoolExecutorBuilder setPrintStream(PrintStream printStream) {
        this.printStream = printStream;
        return this;
    }

    public PrintStream getPrintStream() {
        return printStream;
    }

    public VerboseThreadPoolExecutorBuilder setTasksPerPrint(int tasksPerPrint) {
        this.tasksPerPrint = tasksPerPrint;
        return this;
    }

    public int getTasksPerPrint() {
        return tasksPerPrint;
    }

    public VerboseThreadPoolExecutorBuilder setMillisPerPrint(long millisPerPrint) {
        this.millisPerPrint = millisPerPrint;
        return this;
    }

    public long getMillisPerPrint() {
        return millisPerPrint;
    }

    public VerboseThreadPoolExecutorBuilder setVerbosePrint(boolean verbosePrint) {
        this.verbosePrint = verbosePrint;
        return this;
    }

    public boolean isVerbosePrint() {
        return verbosePrint;
    }

    public VerboseThreadPoolExecutorBuilder setPrintExceptions(boolean printExceptions) {
        this.printExceptions = printExceptions;
        return this;
    }

    public boolean isPrintExceptions() {
        return printExceptions;
    }

    public VerboseThreadPoolExecutorBuilder setExpectedNumTasks(int expectedNumTasks) {
        this.expectedNumTasks = expectedNumTasks;
        return this;
    }

    public int getExpectedNumTasks() {
        return expectedNumTasks;
    }

    public VerboseThreadPoolExecutorBuilder setTaskPrinter(TaskPrinter<?> taskPrinter) {
        this.taskPrinter = taskPrinter;
        return this;
    }

    public TaskPrinter<?> getTaskPrinter() {
        return taskPrinter;
    }
}
