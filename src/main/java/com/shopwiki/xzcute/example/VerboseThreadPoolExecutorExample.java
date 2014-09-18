package com.shopwiki.xzcute.example;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.shopwiki.xzcute.VerboseThreadPoolExecutor;

public class VerboseThreadPoolExecutorExample {

    public static void main(String[] args) throws InterruptedException {
        System.out.println();

        int numTasks = 100;

        VerboseThreadPoolExecutor executor = VerboseThreadPoolExecutor.builder()
                .setPoolSize(2)
                .setVerbosePrint(true)
                .setExpectedNumTasks(numTasks)
                .build();

        for (int i = 1; i < numTasks; i++) {
            final int n = i;

            Callable<String> task = new Callable<String>() {
                @Override
                public String toString() {
                    return "Foo" + n;
                }

                @Override
                public String call() throws Exception {
                    Thread.sleep(777);
                    return "Bar" + n; 
                }
            };

            executor.submit(task);
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        System.out.println();
    }
}
