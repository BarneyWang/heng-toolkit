package me.heng.tool.support;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.*;

/**
 * AUTHOR: wangdi
 * DATE: 15/06/2018
 * TIME: 4:09 PM
 */
public class ThreadSupport {

    private static final ThreadFactory DEFAULT_FACTORY = (new ThreadFactoryBuilder()).setNameFormat("default-%d").build();
    private static final ScheduledExecutorService DEFAULT_SCHEDULER;

    public ThreadSupport() {
    }

    public static ExecutorService finiteIO(ThreadFactory threadFactory, int timeout, int max) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(max, max, (long)timeout, TimeUnit.SECONDS, new LinkedBlockingQueue(), threadFactory);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public static ExecutorService finiteIO(String group, int max) {
        ThreadFactory factory = (new ThreadFactoryBuilder()).setNameFormat(group + "-%d").build();
        return finiteIO((ThreadFactory)factory, 60, max);
    }

    public static ExecutorService infiniteIO(String group) {
        ThreadFactory factory = (new ThreadFactoryBuilder()).setNameFormat(group + "-%d").build();
        return new ThreadPoolExecutor(0, 2147483647, 60L, TimeUnit.SECONDS, new SynchronousQueue(), factory);
    }

    public static ExecutorService finiteIO(String group, int timeout, int max) {
        ThreadFactory factory = (new ThreadFactoryBuilder()).setNameFormat(group + "-%d").build();
        return finiteIO(factory, timeout, max);
    }

    public static ThreadFactory newThreadFactory(String group, boolean daemon, Integer priority) {
        ThreadFactoryBuilder builder = (new ThreadFactoryBuilder()).setNameFormat(group + "-%d");
        if (daemon) {
            builder.setDaemon(true);
        }

        if (priority != null) {
            builder.setPriority(priority);
        }

        return builder.build();
    }

    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue(), DEFAULT_FACTORY);
    }

    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue(), threadFactory);
    }

    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize, ThreadFactory threadFactory) {
        return new ScheduledThreadPoolExecutor(corePoolSize, threadFactory);
    }

    public static ScheduledExecutorService newScheduledThreadPool(String name, int corePoolSize) {
        ThreadFactory factory = newThreadFactory(name, true, (Integer)null);
        return newScheduledThreadPool(corePoolSize, factory);
    }

    public static void watchFutureTimeout(int millis, CompletableFuture<?> future, String errorMsg) {
        watchFutureTimeout(DEFAULT_SCHEDULER, millis, future, () -> {
            future.completeExceptionally(new TimeoutException(errorMsg));
        });
    }

    public static void watchFutureTimeout(ScheduledExecutorService scheduler, int millis, CompletableFuture<?> future, Runnable timeoutHandler) {
        if (millis > 0 && !future.isDone()) {
            scheduler.schedule(() -> {
                if (!future.isDone()) {
                    timeoutHandler.run();
                }

            }, (long)millis, TimeUnit.MILLISECONDS);
        }

    }

    static {
        DEFAULT_SCHEDULER = new ScheduledThreadPoolExecutor(1, DEFAULT_FACTORY);
    }
}
