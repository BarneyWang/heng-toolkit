package me.heng.tool.akka;

import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.Scheduler;
import me.heng.tool.support.ThreadSupport;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static me.heng.tool.support.BaseSupport.println;


/**
 * Created by chuanbao on 14/11/2016.
 *
 * 信号转换器, 切换状态
 *
 * 基于两个时间片的简单断路器, 不过与断路器相比,接口的意义是相反的, 只有两个时间片,精度不高
 *
 * 与Hystrix, akka的 CircuitBreaker不同,没有Half状态, 执行open, close状态,且状态意义相反
 */
public class SignalSwitcher<T> implements Function<T, Boolean> {
    private Function<T, Integer> factor;
    private long innerStamp;
    private long stamp;
    private long innerCount;
    private long count;
    private final long window;
    private final long half;
    private final long throttle;
    private final Callable<Boolean> innerOnOpen;
    private final Callable<Boolean> innerOnClose;
    private volatile Boolean innerOpen = true;
    private Scheduler scheduler;
    private FiniteDuration timeout;
    private ExecutionContext executor;
    Cancellable innerCancellable;

    /**
     * 当时间片内的引子积累到某个阈值throttle时, 执行onClose动作
     *
     * @param actorSystem
     * @param window
     * @param resetTimeout 当switcher为closed时,超时resetTimeout执行恢复onOpen
     * @param throttle
     * @param factor 对每一个输入返回一个权重引子
     * @param onOpen 开启时的动作, 返回状态, true表示执行成功, 状态为开启
     * @param onClose 关闭的动作, 返回状态, true表示执行成功, 状态为关闭
     */
    public SignalSwitcher(ActorSystem actorSystem, long window, long resetTimeout, long throttle,
                          Function<T, Integer> factor, Callable<Boolean> onOpen, Callable<Boolean> onClose) {
        this.window = window;
        this.throttle = throttle;
        this.half = window / 2;
        this.factor = (Function) factor;
        this.innerOnOpen = onOpen;
        this.innerOnClose = onClose;
        scheduler = actorSystem.scheduler();
        timeout = Duration.apply(resetTimeout, TimeUnit.MILLISECONDS);
        executor = actorSystem.dispatcher();
        reset(0L);
    }

    /**
     * 非线程安全接口
     */
    @Override
    public Boolean apply(T ex) {
        Integer ff = this.factor.apply(ex);
        return accumulate(ff);
    }

    public void reset(long val) {
        stamp = System.currentTimeMillis() + window;
        innerStamp = stamp - half;
        innerCount = val;
        count = val;
    }

    public synchronized boolean accumulate(int fp) {
        long now = System.currentTimeMillis();
        if (now < innerStamp) {
            count += fp;
            innerCount += fp;
        } else if (now >= stamp) {
            reset(fp);
        } else {
            innerStamp += half;
            stamp += half;
            innerCount = count + fp;
            count = fp;
        }
        if (innerCount >= throttle) {
            try {
                if (innerOpen) {
                    innerOpen = innerOnClose.call() ? !innerOpen : innerOpen;
                    if (!innerOpen && innerCancellable == null) {
                        innerCancellable = scheduler.scheduleOnce(timeout, () -> {
                            insideOpen();
                        } , executor);
                        reset(0L);
                    }
                }
            } catch (Exception e1) {
                throw new RuntimeException(e1);
            }
        }
        return true;
    }

    public synchronized void open() {
        insideOpen();
    }

    private void insideOpen() {
        if (!innerOpen) {
            try {
                innerOpen = innerOnOpen.call() ? !innerOpen : innerOpen;
                if (innerCancellable != null && !innerCancellable.isCancelled()) {
                    innerCancellable.cancel(); // 取消定时任务
                }
                innerCancellable = null;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public synchronized void close() {
        if (innerOpen) {
            try {
                innerOpen = innerOnClose.call() ? !innerOpen : innerOpen;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static void main(String... args) {
        ActorSystem actorSystem = ActorSystem.create();
        Function<Throwable, Integer> handler = ex -> {
            if (ex instanceof Exception) {
                return 100;
            }
            return 1;
        };

        List<Integer> list = new ArrayList<>(1);
        SignalSwitcher<Throwable> switcher =
                new SignalSwitcher(actorSystem, 10 * 1000, 5 * 1000, 1000, handler, () -> {
                    println("open at %tr", new Date());
                    list.clear();
                    return true;
                } , () -> {
                    println("close at %tr", new Date());
                    list.add(1);
                    return true;
                });

        int p = 10;
        ExecutorService es = ThreadSupport.newFixedThreadPool(p);
        Random random = new Random();

        for (int i = 0; i < p; i++) {
            es.submit(() -> {
                while (true) {
                    int r = random.nextInt(4);
                    if (list.isEmpty()) {
                        if (r % 4 == 0) {
                            switcher.apply(new RuntimeException());
                        } else {
                            switcher.apply(null);
                        }
                    }
                    Thread.sleep(r);
                }
            });
        }
    }
}
