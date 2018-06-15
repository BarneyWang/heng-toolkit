package me.heng.tool.support;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * 延迟 iterator
 * @param <T>
 */
public interface LazyIterable<T> extends Iterable<T> {

    @Override
    LazyIterator<T> iterator();

    interface LazyIterator<T> extends Iterator<T> {
    }

    /**
     * 基于慢请求的Iterable, 比如网络请求,数据库请求等满操作
     */
    interface CachedIterable<T> extends LazyIterable<T> {
        @Override
        LazyIterator<T> iterator();
    }

    abstract class AbstractCachedIterator<T> implements LazyIterator<T> {

        Iterator<T> cache;

        @Override
        public boolean hasNext() {
            if (cache == null || cache.hasNext() == false) {
                cache = query();
            }
            return cache != null && cache.hasNext();
        }

        @Override
        public T next() {
            if (hasNext() == false) {
                throw new NoSuchElementException("CachedIterator");
            }
            return cache.next();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException(
                    "CachedIterable cannot support remove operation");
        }

        public abstract Iterator<T> query();

    }

    class ProxiedIterator<T> implements LazyIterator<T> {
        Iterator<T> p;

        private ProxiedIterator(Iterator<T> p) {
            this.p = p;
        }

        @Override
        public boolean hasNext() {
            return p.hasNext();
        }

        @Override
        public T next() {
            return p.next();
        }
    }

    LazyIterator<Object> EMPTY_ITERATOR = new LazyIterator<Object>() {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Object next() {
            throw new NoSuchElementException();
        }
    };

    static <K> LazyIterator<K> emptyIterator() {
        return (LazyIterator) EMPTY_ITERATOR;
    }

    static <K> LazyIterable<K> emptyIterable() {
        return () -> emptyIterator();
    }

    static <K> LazyIterable<K> iterable(Collection<K> collection) {
        return () -> new ProxiedIterator<>(collection.iterator());
    }

    static <K, T> LazyIterable<T> andThen(LazyIterable<K> iterable, Function<K, T> fn) {
        return () -> new LazyIterator<T>() {
            LazyIterator<K> IT = iterable.iterator();

            @Override
            public boolean hasNext() {
                return IT.hasNext();
            }

            @Override
            public T next() {
                K next = IT.next();
                return fn.apply(next);
            }
        };
    }
}
