package me.heng.tool.support;

import com.google.common.util.concurrent.Uninterruptibles;
import com.sun.xml.internal.rngom.parse.host.Base;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static me.heng.tool.support.BaseSupport.isEmpty;
import static me.heng.tool.support.BaseSupport.println;

/**
 * AUTHOR: wangdi
 * DATE: 15/06/2018
 * TIME: 4:00 PM
 */
public class FutureSupport {


    private static Logger logger = LoggerFactory.getLogger(FutureSupport.class);
    /**
     * 包装future的ex
     * @param ex
     * @param <T>
     * @return
     */
    public static <T> CompletableFuture<T> completeExceptionally(Exception ex) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

    /**
     * deferredResult是spring-web提供的数据结构
     */
//    public static <T> DeferredResult<T> future2Deferred(CompletableFuture<T> future) {
//        DeferredResult<T> result = new DeferredResult<>();
//        future.whenComplete((t, ex) -> {
//            if (ex == null) {
//                result.setResult(t);
//            } else {
//                result.setErrorResult(ex);
//            }
//        });
//        return result;
//    }

    /**
     * 监视 future列表,全部完成/抛出异常,则完成
     *
     * @param futures
     * @param <V>
     * @return
     */
    public static <V> CompletableFuture<List<Pair<V, Throwable>>> watch(
            Collection<CompletableFuture<V>> futures) {
        AtomicInteger latch = new AtomicInteger(futures.size());
        CompletableFuture<List<Pair<V, Throwable>>> future = new CompletableFuture<>();
        /**
         * notice 并发
         */
        List<Pair<V, Throwable>> list =
                Collections.synchronizedList(new ArrayList<>(futures.size()));
        futures.forEach(f -> {
            f.whenComplete((v, ex) -> {
                list.add(Pair.of(v, ex));
                if (latch.decrementAndGet() <= 0) {
                    future.complete(list);
                }
            });
        });
        return future;
    }

    /**
     * 等待 future完成, 不抛出超时异常, 如果超时,直接返回
     *
     * @param future
     * @param timeout  毫秒
     * @param interval
     */
    public static void awaitFuture(Future<?> future, long timeout, long interval) {
        long start = BaseSupport.now();
        while (!future.isDone()) {
            try {
                if (start + timeout <= BaseSupport.now()) {
                    break;
                }
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void awaitFuture(Collection<? extends Future> futures, long timeout, long interval) {
        if (isEmpty(futures)) {
            return;
        }
        long start = BaseSupport.now();
        boolean done;
        while (true) {
            done = true;
            for (Future f : futures) {
                if (!f.isDone()) {
                    done = false;
                    break;
                }
            }
            if (done) {
                break;
            }
            try {
                if (timeout > 0 && start + timeout <= BaseSupport.now()) {
                    break;
                }
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 阻塞调用
     *
     * @param <T>
     * @param timeout  毫秒
     * @param interval
     * @param partial  标示在 futures没有全部完成时，是否执行操作（true表示接受且执行）
     * @param futures
     * @return
     */
    public static <T> List<T> awaitJoinFuture(long timeout, long interval,
                                              boolean partial, Boolean throwException, CompletableFuture<List<T>>... futures) {
        CompletableFuture<Void> f = CompletableFuture.allOf(futures);
        awaitFuture(f, timeout, interval);
        if (f.isDone() || partial) {
            ArrayList<T> list = new ArrayList<>(futures.length);
            for (CompletableFuture<List<T>> future : futures) {
                if (!future.isDone()) {
                    continue;
                }
                try {
                    Collection<? extends T> ts = future.get();
                    list.addAll(ts);
                } catch (Exception e) {
                    if (throwException) {
                        //TODO ee 这里处理得不合理
                        throw new RuntimeException(e.getMessage());
                    }
                }
            }
            return list;
        }
        return Collections.emptyList();
    }

    public static <T> List<T> awaitJoinFuture(long timeout, long interval,
                                              boolean partial, CompletableFuture<List<T>>... futures) {
        return awaitJoinFuture(timeout, interval, partial, false, futures);
    }


    private static ScheduledExecutorService scheduler = ThreadSupport.newScheduledThreadPool("timeout-keeper", 1);

    public static <T> CompletableFuture<T> timeout(CompletableFuture<T> future, long millis) {
        if (future == null || future.isDone() || millis <= 0) {
            return future;
        }
        CompletableFuture<T> f = new CompletableFuture<>();
        future.whenComplete((r, ex) -> {
            if (!f.isDone()) {
                synchronized (f) {
                    if (!f.isDone()) {
                        if (ex == null) {
                            f.complete(r);
                        } else {
                            f.completeExceptionally(ex);
                        }
                    }
                }
            }
        });
        scheduler.schedule(() -> {
            if (!f.isDone()) {
                synchronized (f) {
                    if (!f.isDone()) {
                        f.completeExceptionally(new TimeoutException(millis + " millis"));
                    }
                }
            }
        }, millis, TimeUnit.MILLISECONDS);
        return f;
    }

    /**
     * @param executorService
     * @param runnables
     * @return 任务完成数量
     */
    public static CompletableFuture<Integer> submitAsyncTask(ExecutorService executorService,
                                                             Collection<Runnable> runnables) {
        if (isEmpty(runnables)) {
            return CompletableFuture.completedFuture(0);
        }
        final int size = runnables.size();
        CountDownLatch latch = new CountDownLatch(size);
        CompletableFuture<Integer> future = new CompletableFuture<>();

        for (Runnable runnable : runnables) {
            executorService.submit(() -> {
                try {
                    runnable.run();
                } finally {
                    latch.countDown();
                    if (latch.getCount() == 0) {
                        future.complete(size);
                    }
                }
            });
        }
        return future;
    }

    public static int awaitAsyncTask(ExecutorService executorService,
                                     Collection<Runnable> runnables, int timeoutMills, int checkInterval) {
        if (isEmpty(runnables)) {
            return 0;
        }
        final int size = runnables.size();
        CountDownLatch latch = new CountDownLatch(size);
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        for (Runnable runnable : runnables) {
            executorService.submit(() -> {
                try {
                    runnable.run();
                } finally {
                    latch.countDown();
                    if (latch.getCount() == 0) {
                        f.complete(true);
                    }
                }
            });
        }
        awaitFuture(f, timeoutMills, checkInterval);
        // 阻塞一直到完成，或者超时
        return size - (int)latch.getCount();
    }

    /**
     * @param callable
     * @param <R>
     * @return
     */
    public static <R> CompletableFuture<R> submitAsyncTask(ExecutorService executorService, Callable<R> callable) {
        CompletableFuture<R> future = new CompletableFuture<>();
        executorService.submit(() -> {
            try {
                R result = callable.call();
                future.complete(result);
            } catch (Exception e) {
                logger.error("submitAsyncTask fail", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public static <R> CompletableFuture<R> submitAsyncFutureTask(ExecutorService executorService, Callable<CompletableFuture<R>> callable) {
        CompletableFuture<R> future = new CompletableFuture<>();
        executorService.submit(() -> {
            try {
                CompletableFuture<R> f = callable.call();
                f.whenComplete((r, ex) -> {
                    if (ex != null) {
                        future.completeExceptionally(ex);
                    } else {
                        future.complete(r);
                    }
                });
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * 重试
     *
     * 递归调用，注意调用栈深度
     *
     * @param callable
     * @param retry    重试次数，包括一次正常调用
     * @param p        判断异常情况是否重试
     * @param <T>
     * @return
     */
    public static <T> CompletableFuture<T> retry(Callable<CompletableFuture<T>> callable, int retry,
                                                 Predicate<Throwable> p) {
        return retry(callable, retry, 0, p);
    }

    public static <T> CompletableFuture<T> retry(Callable<CompletableFuture<T>> callable, int retry, int interval,
                                                 Predicate<Throwable> p) {
        if (retry > 1) {
            CompletableFuture<T> callQuietly = callQuietly(callable);
            final CompletableFuture<T> rf = new CompletableFuture<>();
            CountDownLatch latch = new CountDownLatch(retry - 1); // 这是第一次重试
            final BiConsumer<T, Throwable>[] holder = new BiConsumer[1];
            holder[0] = (r, ex) -> {
                if (ex == null) {
                    rf.complete(r);
                } else {
                    if ((p == null || p.test(ex)) && latch.getCount() > 0) {
                        // 这两种异常做重试
                        latch.countDown();
                        if (interval > 0) {
                            // 中间停顿 interval ms
                            Uninterruptibles.sleepUninterruptibly(interval, TimeUnit.MILLISECONDS);
                        }
                        CompletableFuture<T> f2 = callQuietly(callable);
                        f2.whenComplete((rx, ex2) -> {
                            holder[0].accept(rx, ex2);
                        });
                    } else {
                        rf.completeExceptionally(ex);
                    }
                }
            };
            callQuietly.whenComplete(holder[0]);
            return rf;
        } else {
            return callQuietly(callable);
        }
    }

    public static <T> CompletableFuture<T> callQuietly(Callable<CompletableFuture<T>> callable) {
        CompletableFuture<T> f = new CompletableFuture<>();
        try {
            CompletableFuture<T> f1 = callable.call();
            f1.whenComplete((r, ex) -> {
                if (ex != null) {
                    f.completeExceptionally(ex);
                } else {
                    f.complete(r);
                }
            });
        } catch (Exception e) {
            f.completeExceptionally(e);
        }
        return f;
    }

    @FunctionalInterface
    public interface TriFunction<A, B, C, R> {

        R apply(A a, B b, C c);

        default <V> TriFunction<A, B, C, V> andThen(Function<? super R, ? extends V> after) {
            Objects.requireNonNull(after);
            return (A a, B b, C c) -> after.apply(apply(a, b, c));
        }
    }

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {

        void accept(A a, B b, C c);

        default TriConsumer<A, B, C> andThen(TriConsumer<? super A, ? super B, ? super C> after) {
            Objects.requireNonNull(after);

            return (a, b, c) -> {
                accept(a, b, c);
                after.accept(a, b, c);
            };
        }
    }

    public static void main(String... args) throws Exception {
        //        testRetry();
        for (int i = 0; i < 100; i++) {
            testSubmitAsyncTask();
        }
        Thread.sleep(10 * 1000);
    }

    static void testSubmitAsyncTask() throws Exception {
        ExecutorService executorService = ThreadSupport.finiteIO("xxx", 10);
        HashMap<Integer, Integer> map = new HashMap<>();
        Random random = new Random();
        ArrayList<Runnable> list = new ArrayList<>();
        int testCount = 4;
        for (int i = 0; i < testCount; i++) {
            int s = i;
            Runnable runnable = () -> {
                int i1 = random.nextInt(s * 1000);
                try {
                    Thread.sleep(i1);
                } catch (InterruptedException e) {

                }
                map.put(i1, i1);
            };
            list.add(runnable);
        }
        CompletableFuture<Integer> future = submitAsyncTask(executorService, list);
        future.get();
        if (list.size() != testCount) {
            System.out.println("xxxxxxxx");
        }
    }

    static void testRetry() {
        int up = 100;
        CountDownLatch latch = new CountDownLatch(up);
        Callable<CompletableFuture<Object>> cThrowable = () -> {
            latch.countDown();
            println("retry: %d", up - latch.getCount());
            throw new RuntimeException();
        };
        retry(cThrowable, 10, null).whenComplete((r, ex) -> {
            if (ex == null) {
                println(r.toString());
            } else {
                println(ex.getMessage());
            }
        });

        AtomicInteger counter = new AtomicInteger(0);
        Callable<CompletableFuture<Object>> cOk = () -> {
            if (counter.incrementAndGet() > 3) {
                return CompletableFuture.completedFuture("OK");
            }
            println("retry: %d", counter.get());
            return completeExceptionally(new RuntimeException("error"));
        };
        retry(cOk, 10, null).whenComplete((r, ex) -> {
            if (ex == null) {
                println(r.toString());
            } else {
                println(ex.getMessage());
            }
        });
    }
}
