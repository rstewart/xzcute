package com.shopwiki.xzcute;

import java.util.concurrent.*;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.atomic.*;

import com.shopwiki.xzcute.VerboseThreadPoolExecutor.TaskPrinter;

/**
 * @owner jdickinson
 */
public final class VerboseThreadPoolExecutorFactory {

    public VerboseThreadPoolExecutorFactory() { }

    private int corePoolSize = 0;
    private int maximumPoolSize = 1;
    private long keepAliveTime = 1L;
    private TimeUnit unit = TimeUnit.MINUTES;
    private BlockingQueue<Runnable> workQueue = new EnhancedLinkedBlockingQueue<Runnable>(1000);
    private ThreadFactory threadFactory = Executors.defaultThreadFactory();
    private RejectedExecutionHandler handler = new AbortPolicy();

    private boolean print = true;
    private int tasksPerPrint = 1;
    private long millisPerPrint = 0L;
    private boolean verbosePrint = false;
    private boolean printExceptions = true;
    private int expectedNumTasks = 0;

    private TaskPrinter<?> taskPrinter = new TaskPrinter<Object>();

    public VerboseThreadPoolExecutorFactory setCorePoolSize(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        corePoolSize = size;
        return this;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public VerboseThreadPoolExecutorFactory setMaximumPoolSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException();
        }
        maximumPoolSize = size;
        return this;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public VerboseThreadPoolExecutorFactory setPoolSize(int size) {
        setCorePoolSize(size);
        setMaximumPoolSize(size);
        return this;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public BlockingQueue<Runnable> getWorkQueue() {
        return workQueue;
    }

    public VerboseThreadPoolExecutorFactory setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
        return this;
    }

    public VerboseThreadPoolExecutorFactory setUnit(TimeUnit unit) {
        this.unit = unit;
        return this;
    }

    public VerboseThreadPoolExecutorFactory setWorkQueue(BlockingQueue<Runnable> workQueue) {
        this.workQueue = workQueue;
        return this;
    }

    public VerboseThreadPoolExecutorFactory setWorkQueueCapacity(int capacity) {
        this.workQueue = new EnhancedLinkedBlockingQueue<Runnable>(capacity);
        return this;
    }

    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    public RejectedExecutionHandler getHandler() {
        return handler;
    }

    public VerboseThreadPoolExecutorFactory setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        return this;
    }

    public VerboseThreadPoolExecutorFactory setHandler(RejectedExecutionHandler handler) {
        this.handler = handler;
        return this;
    }

    private AtomicBoolean gotTheExecutorOnce = new AtomicBoolean(false);

    public VerboseThreadPoolExecutor getTheThreadPoolExecutor() {

        if (gotTheExecutorOnce.compareAndSet(false, true) == false) {
            throw new RuntimeException("Don't get the ThreadPoolExecutor from this object more than once!");
        }

        return new VerboseThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime, unit,
                workQueue,
                threadFactory,
                handler,
                print,
                tasksPerPrint,
                millisPerPrint,
                verbosePrint,
                printExceptions,
                expectedNumTasks,
                taskPrinter
                );
    }

    public int getTasksPerPrint() {
        return tasksPerPrint;
    }

    public boolean isPrinting() {
        return print;
    }

    public long getMillisPerPrint() {
        return millisPerPrint;
    }

    public VerboseThreadPoolExecutorFactory setTasksPerPrint(int tasksPerPrint) {
        this.tasksPerPrint = tasksPerPrint;
        return this;
    }

    public VerboseThreadPoolExecutorFactory setPrinting(boolean printing) {
        this.print = printing;
        return this;
    }

    public VerboseThreadPoolExecutorFactory setMillisPerPrint(long millisPerPrint) {
        this.millisPerPrint = millisPerPrint;
        return this;
    }

    public boolean getVerbosePrint() {
        return verbosePrint;
    }

    public VerboseThreadPoolExecutorFactory setVerbosePrint(boolean verbosePrint) {
        this.verbosePrint = verbosePrint;
        return this;
    }

    public int getExpectedNumTasks() {
        return expectedNumTasks;
    }

    public VerboseThreadPoolExecutorFactory setExpectedNumTasks(int expectedNumTasks) {
        this.expectedNumTasks = expectedNumTasks;
        return this;
    }

    public TaskPrinter<?> getTaskPrinter() {
        return taskPrinter;
    }

    public VerboseThreadPoolExecutorFactory setTaskPrinter(TaskPrinter<?> taskPrinter) {
        this.taskPrinter = taskPrinter;
        return this;
    }

    public boolean isPrintExceptions() {
        return printExceptions;
    }

    public VerboseThreadPoolExecutorFactory setPrintExceptions(boolean printExceptions) {
        this.printExceptions = printExceptions;
        return this;
    }
}
