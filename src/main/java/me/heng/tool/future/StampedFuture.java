package me.heng.tool.future;

import java.util.concurrent.CompletableFuture;

/**
 * AUTHOR: wangdi
 * DATE: 15/06/2018
 * TIME: 4:33 PM
 * 带时间戳的completableFuture
 */
public class StampedFuture<T> extends CompletableFuture<T> {

    private long innerStart;
    private long innerFinish = -1L;

    public StampedFuture() {
        super();
        innerStart = now();
    }

    /**
     * 返回开始时间
     */
    public long start() {
        return innerStart;
    }

    /**
     * 返回完成时间
     *
     * @return
     */
    public long finished() {
        return innerFinish;
    }

    public long elapsed() {
        if (innerFinish > 0) {
            return innerFinish - innerStart;
        }
        return now() - innerStart;
    }

    @Override
    public boolean complete(T value) {
        innerFinish = now();
        return super.complete(value);
    }

    @Override
    public boolean completeExceptionally(Throwable ex) {
        innerFinish = now();
        return super.completeExceptionally(ex);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        innerFinish = now();
        return super.cancel(mayInterruptIfRunning);
    }

    /**
     * 重设时间
     *
     * @return 返回之前的 innerStart
     */
    public long reset() {
        long t = innerStart;
        innerStart = now();
        return t;
    }

    public static long now() {
        return System.currentTimeMillis();
    }
}