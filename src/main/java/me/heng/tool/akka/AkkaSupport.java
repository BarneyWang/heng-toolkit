package me.heng.tool.akka;


import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.japi.function.Procedure;
import akka.stream.*;
import akka.stream.javadsl.*;
import akka.stream.stage.*;
import me.heng.tool.support.BaseSupport;
import scala.Tuple2;
import scala.concurrent.duration.FiniteDuration;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Created by wangdi
 *
 * reactive stream 辅助函数
 */
public class AkkaSupport {

    public static <V, T> akka.japi.function.Function<V, T> toAkka(Function<V, T> func) {
        return t -> func.apply(t);
    }

    public static FiniteDuration millis(long millis) {
        return FiniteDuration.create(millis, TimeUnit.MILLISECONDS);
    }

    public static FiniteDuration seconds(int s) {
        return FiniteDuration.create(s, TimeUnit.SECONDS);
    }

    public static FiniteDuration minutes(int s) {
        return FiniteDuration.create(s, TimeUnit.MINUTES);
    }

    public static <E> Graph<FlowShape<E, E>, AuditBoard> auditor(int bundle) {
        Auditor<E> auditor = new Auditor<>(bundle);
        return auditor;
    }

    /**
     * 滞留 window * waittings时间, 在window内收集的数据, 批量向下游传递
     * 
     * @param window window 毫秒内的数据为一簇, 等到 window * waittings 后批量向下游发送
     * @param waittings 比如retain(100, 100, 10) 0.1s的数据为一簇, 超时10s(100*100ms)后就才向下游发送一簇数据
     * @param initSize
     * @param <T>
     * @return
     */
    public static <T> GraphStage<FlowShape<T, List<T>>> retain(int window, int waittings,
            int initSize) {
        Retention<T> retention = new Retention<>(window, waittings, initSize);
        return retention;
    }

    public static <T> Graph<FlowShape<T, List<T>>, NotUsed> batch(final int size) {
        Flow<T, T, NotUsed> flow = Flow.create();
        Flow<T, List, NotUsed> f = flow.batch(size, t -> {
            List<T> list = new ArrayList<>(size);
            list.add(t);
            return list;
        }, (List l, T t) -> {
            l.add(t);
            return (List<T>) l;
        });
        return (Flow) f;
    }

    /**
     * 审计员, 总计总数与速率
     */
    public static class Auditor<T>
            extends GraphStageWithMaterializedValue<FlowShape<T, T>, AuditBoard> {

        private final Inlet<T> inlet;
        private final Outlet<T> outlet;
        private final FlowShape<T, T> shape;
        private final AuditBoard board;

        public Auditor(int bundle) {
            inlet = Inlet.create("Auditor.in");
            outlet = Outlet.create("Auditor.out");
            shape = FlowShape.of(inlet, outlet);
            this.board = new AuditBoard(bundle);
        }

        @Override
        public FlowShape<T, T> shape() {
            return shape;
        }

        @Override
        public Tuple2<GraphStageLogic, AuditBoard> createLogicAndMaterializedValue(
                Attributes inheritedAttributes) throws Exception {
            GraphStageLogic logic = new GraphStageLogic(shape) {
                {
                    setHandler(inlet, new AbstractInHandler() {
                        @Override
                        public void onPush() throws Exception {
                            T t = grab(inlet);
                            board.audit(1L);
                            push(outlet, t);
                        }
                    });
                    setHandler(outlet, new AbstractOutHandler() {
                        @Override
                        public void onPull() throws Exception {
                            pull(inlet);
                        }
                    });
                }
            };
            return Tuple2.apply(logic, board);
        }
    }

    private static final String TEMPLATE = "%1$d %2$.1f/s";

    public static class AuditBoard {
        private final AtomicLong counter;
        private long count;
        private long last;
        private long lastFlag;
        private final long mask; // 掩码

        public AuditBoard(int bit) {
            this.counter = new AtomicLong(0);
            this.last = now();
            mask = Long.MAX_VALUE >> (63 - bit) ^ Long.MAX_VALUE;
            count = 0;
        }

        /**
         * 总数
         */
        public long count() {
            return counter.get();
        }

        /**
         * 当前的速度, 1s计, 在下一个bundle时归零
         */
        public float speed() {
            long delta = counter.get() - count;
            long current = now();
            return (current == last) || delta == 0 ? 0f : delta * 1000f / (current - last);
        }

        public void audit(long count) {
            /**
             * 使用位操作, 整除性能比较差
             */
            if ((counter.addAndGet(count) & mask) != lastFlag) {
                last = now();
                this.count = counter.get();
                lastFlag = counter.get() & mask;
            }
        }

        @Override
        public String toString() {
            return String.format(TEMPLATE, count(), speed());
        }
    }

    /**
     * 滞留, 将元素滞留一段时间,批量下发下去
     *
     * TODO 实现得不正确,无法使用
     */
    public static class Retention<T> extends GraphStage<FlowShape<T, List<T>>> {
        private final int window;
        private final long delay;
        private final Inlet<T> inlet;
        private final Outlet<List<T>> outlet;
        private final FlowShape<T, List<T>> shape;
        private final Deque<Pair<Long, List<T>>> deque;
        private final int initSize;

        public Retention(int window, int waittings, int initSize) {
            this.window = window;
            this.delay = window * waittings;
            this.initSize = initSize;
            inlet = Inlet.create("Retention.in");
            outlet = Outlet.create("Retention.out");
            shape = FlowShape.of(inlet, outlet);
            deque = new LinkedBlockingDeque<>(waittings);
        }

        /**
         * 当下游pull/上游push空白一段时间,这时候会造成时间窗口(delay)不准确<br/>
         * 但保证至少delay之后才向下游发送
         */
        @Override
        public GraphStageLogic createLogic(Attributes inheritedAttributes) throws Exception {
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            return new TimerGraphStageLogicWithLogging(shape()) {
                {
                    setHandler(inlet, new AbstractInHandler() {
                        long lastTime = 0;

                        @Override
                        public void onPush() throws Exception {
                            long currentNow = now();
                            T ele = grab(inlet);
                            if (deque.isEmpty() || lastTime + window < currentNow) {
                                lastTime = currentNow;
                                deque.offerLast(Pair.create(lastTime, new ArrayList<>(initSize)));
                            }
                            deque.peekLast().second().add(ele);
                            pull(inlet);
                        }

                        @Override
                        public void onUpstreamFinish() {
                            done.complete(true);
                        }

                        @Override
                        public void onUpstreamFailure(Throwable ex) throws Exception {
                            super.onUpstreamFailure(ex);
                            List<List<T>> overdues = removeOverdueList(now() - delay); // 立刻置为过期
                            if (overdues != null) {
                                emitMultiple(outlet, overdues.iterator());
                            }
                            done.completeExceptionally(ex);
                        }
                    });
                    setHandler(outlet, new AbstractOutHandler() {
                        @Override
                        public void onPull() throws Exception {
                            if (!hasBeenPulled(inlet)) {
                                pull(inlet);
                            }
                        }
                    });
                }

                private final String Tick = "tick";

                @Override
                public void preStart() throws Exception {
                    schedulePeriodicallyWithInitialDelay(Tick, millis(delay), millis(window));
                }

                @Override
                public void onTimer(Object timerKey) throws Exception {
                    if (Tick.equals(timerKey)) {
                        log().debug("Retention.timer triggered");
                        if (!done.isDone()) {
                            List<List<T>> overdues = removeOverdueList(now());
                            if (overdues != null) {
                                List<T> ts = new ArrayList<>(overdues.size());
                                for (List<T> overdue : overdues) {
                                    ts.addAll(overdue);
                                }
                                emit(outlet, ts);
                            }
                        } else if (done.isCompletedExceptionally()) {
                            log().warning("Retention.timer exception");
                        } else {
                            completeStage();
                        }
                    }
                }

                private List<List<T>> removeOverdueList(long now) {
                    List<List<T>> overdueList = null;
                    while (!deque.isEmpty() && deque.peekFirst().first() + delay <= now) {
                        if (overdueList == null) {
                            overdueList = new ArrayList<>(1);
                        }
                        overdueList.add(deque.removeFirst().second());
                    }
                    return overdueList;
                }
            };
        }

        @Override
        public FlowShape<T, List<T>> shape() {
            return this.shape;
        }
    }

    private static final long now() {
        return System.currentTimeMillis();
    }


    /**
     * 类似 map/reduce一样，将数据量map/reduce减少下游处理压力
     *
     * @param materializer
     * @param filter
     * @param maxSubstreams 预估最大的子流数量（不可超过该值）
     * @param mapper
     * @param reducer
     * @param flipTimeout
     * @param consumer
     * @return
     */
    public static <T> Consumer<T> mapReduceStream(Materializer materializer, Predicate<T> filter,
            int maxSubstreams, Function<T, String> mapper, BiFunction<T, T, T> reducer,
            int flipTimeout, Consumer<T> consumer) {

        SourceQueueWithComplete<T> sourceQueueWithComplete = mapReduceStreamWithoutAutoFlush(materializer, filter,
                maxSubstreams, mapper, reducer, consumer);

        FiniteDuration delay = seconds(flipTimeout);
        final SourceQueueWithComplete[] holders = new SourceQueueWithComplete[1];
        holders[0] = sourceQueueWithComplete;
        /**
         * 定时反转，刷新流
         */
        Source.tick(delay, delay, 1).to(Sink.foreach(i -> {
            SourceQueueWithComplete q = holders[0];
            holders[0] = mapReduceStreamWithoutAutoFlush(materializer, filter, maxSubstreams,
                    mapper, reducer, consumer);
            q.complete(); // 将之前的queue置为完成
        })).run(materializer);
        return t -> {
            SourceQueueWithComplete q = holders[0];
            q.offer(t);
        };
    }

    /**
     * 基本功能同 tickBusDecorator，但是没有自动刷新
     *
     * @param materializer
     * @param filter
     * @param maxSubstreams
     * @param mapper
     * @param reducer
     * @param consumer
     * @param <T>
     * @return
     */
    public static <T> SourceQueueWithComplete<T> mapReduceStreamWithoutAutoFlush(
            Materializer materializer, Predicate<T> filter, int maxSubstreams,
            Function<T, String> mapper, BiFunction<T, T, T> reducer, Consumer<T> consumer) {

        Source<T, SourceQueueWithComplete<T>> queueSrc =
                Source.queue(10000, OverflowStrategy.backpressure());

        Source<T, SourceQueueWithComplete<T>> flow = queueSrc;
        if (filter != null) {
            flow = flow.filter(t -> filter.test(t));
        }
        Source<T, SourceQueueWithComplete<T>> mergedStream =
                flow.groupBy(maxSubstreams, t -> mapper.apply(t))
                        .reduce((t1, t2) -> reducer.apply(t1, t2)).mergeSubstreams();
        RunnableGraph<SourceQueueWithComplete<T>> graph =
                mergedStream.to(Sink.foreach(t -> consumer.accept(t)));
        return graph.run(materializer);
    }

    public static <T> Consumer<T> tickBus(Materializer materializer, @Nullable Predicate<T> filter, int size,
                                          int timeout, Consumer<Collection<T>> bus) {
        Source<T, SourceQueueWithComplete<T>> queueSrc =
                Source.queue(size, OverflowStrategy.backpressure());
        if (filter != null) {
            queueSrc = queueSrc.filter(t -> filter.test(t));
        }
        Source<List<T>, SourceQueueWithComplete<T>> flow =
                queueSrc.groupedWithin(size, millis(timeout));
        Procedure<List<T>> p = l -> bus.accept(l);
        SourceQueueWithComplete<T> q = flow.to(Sink.foreach(p)).run(materializer);
        return t -> q.offer(t);
    }

    /**
     * 简单的基于 akka-stream queue
     * @param bufSize
     * @param mat
     * @param consumer
     * @param handler
     * @param <T>
     * @return
     */
    public static <T> SourceQueueWithComplete<T> queue(int bufSize, Materializer mat, Consumer<? super T> consumer,
        Function<Throwable, Boolean> handler) {
        Procedure<T> p = consumer::accept;
        Source<T, SourceQueueWithComplete<T>> source = Source.queue(bufSize, OverflowStrategy.backpressure());
        SourceQueueWithComplete<T> q = source.to(Sink.foreach(p)).withAttributes(
            ActorAttributes.withSupervisionStrategy(ex -> {
                if (handler.apply(ex)) {
                    return Supervision.resume();
                } else {
                    return Supervision.stop();
                }
            })).run(mat);
        return q;
    }

    public static void main(String... args) throws Exception {
        // testTicBus();
        testMapReduceStream();
    }

    private static void testTicBus() throws Exception {
        ActorSystem system = ActorSystem.create();
        ActorMaterializerSettings settings = ActorMaterializerSettings.create(system);
        Materializer materializer = ActorMaterializer.create(settings, system);

        Consumer<Collection<Integer>> bus = ints -> {
            BaseSupport.println("ints size:%d", ints.size());
        };

        Consumer<Integer> consumer = tickBus(materializer, null, 10, 2000, bus);

        for (int i = 0; i < 100; i++) {
            consumer.accept(i);
        }

        Thread.sleep(10 * 1000);
        system.terminate();
    }

    private static void testMapReduceStream() throws Exception {
        ActorSystem system = ActorSystem.create();
        ActorMaterializerSettings settings = ActorMaterializerSettings.create(system);
        Materializer materializer = ActorMaterializer.create(settings, system);

        int up = 1000 * 1000;
        CountDownLatch latch = new CountDownLatch(up);
        Consumer<Pair<String, Integer>> pc = p -> BaseSupport.println("%s:%d", p.first(), p.second());
        Function<Pair<String, Integer>, String> mapper = p -> p.first();
        BiFunction<Pair<String, Integer>, Pair<String, Integer>, Pair<String, Integer>> reducer =
                (p1, p2) -> {
                    latch.countDown();
                    return Pair.create(p1.first(), p1.second() + p2.second());
                };

        Consumer<Pair<String, Integer>> consumer =
                mapReduceStream(materializer, null, 10, mapper, reducer, 4, pc);

        int ii = 10;
        for (int i = 0; i < ii; i++) {
            for (int j = 0; j < 10000; j++) {
                int key = j % 10;
                consumer.accept(Pair.create(String.valueOf(key), j));
            }

            Thread.sleep(4 * 1000);
        }
        Thread.sleep(2 * 1000);
        BaseSupport.println("countdown:%s", up - latch.getCount());

        system.terminate();
    }
}
