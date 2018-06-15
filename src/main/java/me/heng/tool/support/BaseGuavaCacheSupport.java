package me.heng.tool.support;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import me.heng.tool.function.ThrowableFunction;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static me.heng.tool.support.BaseSupport.now;

/**
 * AUTHOR: wangdi
 * DATE: 15/06/2018
 * TIME: 4:16 PM
 */
public class BaseGuavaCacheSupport {


    public static <K, V> LoadingCache<K, V> guavaCache(int size, int timeout,
                                                       ThrowableFunction<K, V, ? extends Exception> getter) {
        return CacheBuilder.newBuilder().maximumSize(size)
                .expireAfterWrite(timeout, TimeUnit.SECONDS).build(new CacheLoader<K, V>() {
                    @Override
                    public V load(K key) throws Exception {
                        return getter.apply(key);
                    }
                });
    }


    public static <V> Supplier<V> cachedSupplier(int timeoutMillis, boolean throwable, Supplier<V> supplier) {
        final ArrayList<Object> list = new ArrayList<>(2);
        list.add(0, 0L);
        list.add(1, null);
        return () -> {
            Long last = (Long)list.get(0);
            long now = now();
            if (last == 0L || now > last + timeoutMillis) {
                // 首次调用，或过期
                synchronized (list) {
                    last = (Long)list.get(0);
                    if (last == null || now > last + timeoutMillis) {
                        try {
                            V val = supplier.get();

                            list.add(0, now);
                            list.add(1, val);
                        } catch (Exception e) {
                            if (throwable) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
            V cached = (V)list.get(1);
            return cached;
        };
    }

    public static <K, V> Function<K, V> cachedGetter(int timeoutMillis, boolean throwable, ThrowableFunction<K, V, Exception> getter) {
        final ConcurrentMap<K, Pair<Long, V>> cacheMap = new ConcurrentHashMap<>();

        return key -> {
            long now = BaseSupport.now();
            Pair<Long, V> p = cacheMap.get(key);
            if (p == null || p.getLeft() == null || now > p.getLeft() + timeoutMillis) {
                synchronized (cacheMap) {
                    p = cacheMap.get(key);
                    if (p == null || p.getLeft() == null || now > p.getLeft() + timeoutMillis) {
                        // 首次调用，或过期
                        try {
                            V val = getter.apply(key);
                            p = Pair.of(now, val);
                            cacheMap.put(key, p);
                        } catch (Exception e) {
                            if (throwable) {
                                throw new RuntimeException(e);
                            }
                            // 否则继续执行， 返回过期的值
                        }
                    }
                }
            }
            return p != null ? p.getRight() : null;
        };
    }



    public static <K, V> ConcurrentMap<K, V> simpleCache(int size, int timeout) {
        return expiringCache(null, size, 0, timeout, null);
    }

    public static <K, V> Function<K, V> simpleCacheWrapper(int size, int timeout, ThrowableFunction<K, V, ?> func) {
        LoadingCache<K, V> cache = guavaCache(size, timeout, (K key) -> {
            try {
                return func.apply(key);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
        return (K k) -> {
            try {
                return cache.get(k);
            } catch (ExecutionException e) {
                Throwable ex = Throwables.getRootCause(e);
                throw new RuntimeException(ex);
            }
        };
    }

    /**
     * 基于guava cache的可过期、可设置cache
     *
     * @param name
     * @param maxSize
     * @param seconds
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> ConcurrentMap<K, V> expiringCacheByWrite(String name, int maxSize,
                                                                  int seconds) {
        return expiringCache(name, maxSize, 0, seconds, null);
    }

    /**
     * 基于访问超时的cache
     *
     * @param name
     * @param maxSize
     * @param seconds 超过seconds未访问之后过期
     * @param daemon
     * @return
     */
    public static <K, V> ConcurrentMap<K, V> expiringCacheByRead(String name, int maxSize,
                                                                 int seconds, RemovalListener<K, V> listener, boolean daemon) {
        ConcurrentMap<K, V> cache = expiringCache(name, maxSize, seconds, 0, listener);
        if (daemon) {
            int delay = seconds > 10 ? 10 : seconds;
            SCHEDULER.scheduleWithFixedDelay(() -> {
                try {
                    cache.put((K)NOT_EXIST_KEY, (V)NOT_EXIST_KEY);
                } catch (Exception e) {
                    // not happen
                }
            }, delay, delay, TimeUnit.SECONDS);
        }
        return cache;
    }

    public static <K, V> ConcurrentMap<K, V> expiringCache(String name, int maxSize,
                                                           int readSeconds, int writeSeconds, RemovalListener<K, V> listener) {
        CacheBuilder<K, V> builder =
                cacheBuilder(name, maxSize, readSeconds, writeSeconds, listener);
        return ((CacheBuilder)builder).build().asMap();
    }

    /**
     * http://stackoverflow.com/questions/10144194/how-does-guava-expire-entries-in-its-cachebuilder
     *
     * @param name
     * @param maxSize
     * @param readSeconds
     * @param writeSeconds
     * @param listener
     * @param <K>
     * @param <V>
     * @return
     */
    protected static <K, V> CacheBuilder<K, V> cacheBuilder(String name, int maxSize,
                                                            int readSeconds, int writeSeconds, RemovalListener<K, V> listener) {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
        if (!Strings.isNullOrEmpty(name)) {
            // builder.refreshAfterWrite();
        }
        if (readSeconds > 0) {
            builder.expireAfterAccess(readSeconds, TimeUnit.SECONDS);
        }
        if (writeSeconds > 0) {
            builder.expireAfterWrite(writeSeconds, TimeUnit.SECONDS);
        }
        if (maxSize > 0) {
            builder.maximumSize(maxSize);
        }
        if (listener != null) {
            builder.removalListener(listener);
        }
        return (CacheBuilder)builder;
    }

    private static final Object NOT_EXIST_KEY = new Object();

    private static final ScheduledExecutorService SCHEDULER = ThreadSupport.newScheduledThreadPool("cacheEvict", 1);
}
