package me.heng.tool.support;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.*;

/**
 * Created by chuanbao on 29/11/2016.
 *
 * 线程\线程池\并发相关的辅助方法
 */
public class ThreadSupport2 {

    public static ExecutorService finiteIO(ThreadFactory threadFactory, int timeout, int core, int max) {
        ThreadPoolExecutor executor =
            new ThreadPoolExecutor(core, max, timeout, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), threadFactory);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * 返回适用于IO密集型的有限线程池
     *
     * @param group
     * @param max
     * @return
     */
    public static ExecutorService finiteIO(String group, int max) {
        final ThreadFactory factory =
            new ThreadFactoryBuilder().setNameFormat(group + "-%d").build();
        return finiteIO(factory, 60, max, max);
    }

    public static ExecutorService infiniteIO(String group) {
        final ThreadFactory factory =
            new ThreadFactoryBuilder().setNameFormat(group + "-%d").build();
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
            factory);
    }

    public static ExecutorService finiteIO(String group, int timeout, int max) {
        final ThreadFactory factory =
            new ThreadFactoryBuilder().setNameFormat(group + "-%d").build();
        return finiteIO(factory, timeout, max, max);
    }

    public static ExecutorService finiteIO(String group, int timeout, int core, int max) {
        final ThreadFactory factory =
            new ThreadFactoryBuilder().setNameFormat(group + "-%d").build();
        return finiteIO(factory, timeout, core, max);
    }

    public static ThreadFactory newThreadFactory(String group, boolean daemon, Integer priority) {
        ThreadFactoryBuilder builder = new ThreadFactoryBuilder().setNameFormat(group + "-%d");
        if (daemon) {
            builder.setDaemon(true);
        }
        if (priority != null) {
            builder.setPriority(priority);
        }
        return builder.build();
    }

    private static final ThreadFactory DEFAULT_FACTORY = new ThreadFactoryBuilder().setNameFormat("default-%d").build();

    /**
     * @param nThreads
     * @return
     */
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(), DEFAULT_FACTORY);
    }

    public static ExecutorService newFixedThreadPool(int nThreads, String group) {
        final ThreadFactory factory =
            new ThreadFactoryBuilder().setNameFormat(group + "-%d").build();
        return newFixedThreadPool(nThreads, factory);
    }

    /**
     * @param nThreads
     * @param threadFactory
     * @return
     */
    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(nThreads, nThreads,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            threadFactory);
    }

    /**
     * @param corePoolSize
     * @param threadFactory
     * @return
     */
    public static ScheduledExecutorService newScheduledThreadPool(
        int corePoolSize, ThreadFactory threadFactory) {
        return new ScheduledThreadPoolExecutor(corePoolSize, threadFactory);
    }

    public static ScheduledExecutorService newScheduledThreadPool(String name, int corePoolSize) {
        ThreadFactory factory = ThreadSupport.newThreadFactory(name, true, null);
        return newScheduledThreadPool(corePoolSize, factory);
    }

    public static void watchFutureTimeout(int millis, CompletableFuture<?> future, String errorMsg) {
        watchFutureTimeout(DEFAULT_SCHEDULER, millis, future, () -> {
            future.completeExceptionally(new TimeoutException(errorMsg));
        });
    }

    /**
     * 监视future是否超时
     *
     * @param scheduler
     * @param millis
     * @param future
     * @param timeoutHandler
     */
    public static void watchFutureTimeout(ScheduledExecutorService scheduler, int millis, CompletableFuture<?> future,
        Runnable timeoutHandler) {
        if (millis > 0 && !future.isDone()) {
            scheduler.schedule(() -> {
                if (!future.isDone()) {
                    timeoutHandler.run();
                }
            }, millis, TimeUnit.MILLISECONDS);
        }
    }

    private final static ScheduledExecutorService DEFAULT_SCHEDULER = new ScheduledThreadPoolExecutor(1,
        DEFAULT_FACTORY);
}
