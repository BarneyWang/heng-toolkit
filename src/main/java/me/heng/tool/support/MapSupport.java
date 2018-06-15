package me.heng.tool.support;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * AUTHOR: wangdi
 * DATE: 15/06/2018
 * TIME: 4:18 PM
 */
public class MapSupport {


    public static boolean isNotEmpty(Map<?, ?> col) {
        return col != null && !col.isEmpty();
    }

    public static boolean isEmpty(Map<?, ?> col) {
        return col == null || col.isEmpty();
    }


    /**
     * 函数式编程部分
     */
    public static <I, O> List<O> map(Collection<I> inputs, Function<I, O> mapper) {
        if (ListSupport.isEmpty(inputs)) {
            return Collections.emptyList();
        }
        return inputs.stream().map(mapper).collect(Collectors.toList());
    }

    public static <I, O> List<O> mapAsync(Collection<I> inputs, Function<I, O> mapper) {
        if (ListSupport.isEmpty(inputs)) {
            return Collections.emptyList();
        }
        return inputs.parallelStream().map(mapper).collect(Collectors.toList());
    }

    public static <K, V, O> List<O> map(Map<K, V> inputs,
                                        BiFunction<? super K, ? super V, ? extends O> mapper, boolean nullable) {
        if (isEmpty(inputs)) {
            return Collections.emptyList();
        }
        Stream<Map.Entry<K, V>> stream = inputs.entrySet().stream();
        if (!nullable) {
            stream = stream.filter(entry -> entry.getKey() != null && entry.getValue() != null);
        }
        Stream<O> stream2 = stream.map(entry -> mapper.apply(entry.getKey(), entry.getValue()));
        if (!nullable) {
            stream2 = stream2.filter(Objects::nonNull);
        }
        return stream2.collect(Collectors.toList());
    }

    public static <K, V> HashMap<K, V> newMap(Map<K, V> map, K k, V v) {
        HashMap<K, V> newMap = new HashMap<>();
        newMap.putAll(map);
        newMap.put(k, v);
        return newMap;
    }

    public static <K, V> HashMap<K, V> newMap(K k, V v) {
        HashMap<K, V> map = new HashMap<>();
        map.put(k, v);
        return map;
    }

    public static <K, V> HashMap<K, V> newMap(K k1, V v1, K k2, V v2) {
        HashMap<K, V> map = new HashMap<>(2);
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    @Deprecated
    public static <K, V> Map<K, V> asSingleValueMapByKey(Collection<V> vals,
                                                         Function<V, K> keyGetter) {
        return asMapByKey(vals, keyGetter);
    }

    public static <T, K, V> Map<K, V> asMap(Collection<T> vals, Function<T, K> keyGetter,
                                            Function<T, V> valGetter) {
        if (vals != null && !vals.isEmpty()) {
            Map<K, V> map = new HashMap<>();
            for (T t : vals) {
                K key = keyGetter.apply(t);
                if (key != null) {
                    V val = valGetter.apply(t);
                    map.put(key, val);
                }
            }
            return map;
        }
        return Collections.emptyMap();
    }

    public static <T, K> Map<K, T> asMapByKey(Collection<T> vals, Function<T, K> keyGetter) {
        return asMap(vals, keyGetter, Function.identity());
    }

    public static <T, V> Map<T, V> asMapByValue2(Collection<T> vals, Function<T, V> valGetter) {
        return asMap(vals, Function.identity(), valGetter);
    }

    public static <T, V> Map<T, V> asMapByValue(Collection<T> vals, V val) {
        return asMap(vals, Function.identity(), key -> val);
    }

    public static <T, K> Multimap<K, T> asMultiMap(Collection<T> vals, Function<T, K> keyGetter) {
        Multimap<K, T> map = ArrayListMultimap.create();
        for (T val : vals) {
            K key = keyGetter.apply(val);
            map.put(key, val);
        }
        return map;
    }

    public static <T, K, C extends Collection> Multimap<K, T> asMultiMap(Map<K, C> map) {
        Multimap<K, T> to = ArrayListMultimap.create();
        map.forEach((k, vs) -> {
            to.putAll(k, vs);
        });
        return to;
    }

    public static <T, K, V> Multimap<K, V> asMultiMap(Collection<T> vals, Function<T, K> keyGetter,
                                                      Function<T, V> valGetter) {
        Multimap<K, V> map = ArrayListMultimap.create();
        for (T val : vals) {
            K key = keyGetter.apply(val);
            map.put(key, valGetter.apply(val));
        }
        return map;
    }

    public static <K, V> Map<K, V> multiMap2SingleMap(Multimap<K, V> multimap) {
        Map<K, V> singleMap = Maps.transformValues(multimap.asMap(), ListSupport::firstOrNull);
        return singleMap;
    }

    public static <V> void each(Collection<V> collection, Consumer<V> consumer) {
        if (collection != null && collection.size() > 0) {
            collection.forEach(consumer);
        }
    }

    public static <I, O> Set<O> set(Collection<I> inputs, Function<I, O> mapper) {
        if (inputs == null || inputs.isEmpty()) {
            return Collections.emptySet();
        }
        return inputs.stream().map(mapper).collect(Collectors.toSet());
    }

    public static <V> Set<V> set(Iterable<V> inputs) {
        if (inputs == null) {
            return Collections.emptySet();
        }
        return Sets.newHashSet(inputs);
    }

    public static <K, V> Map<V, K> flipMap(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }
        HashMap<V, K> flipped = new HashMap<>();
        map.forEach((k, v) -> {
            flipped.put(v, k);
        });
        return flipped;
    }

    public static <T, V> List<V> flatMap(Collection<T> cols, Function<T, Collection<V>> getter) {
        if (cols != null && cols.size() > 0) {
            List<V> list = cols.stream().flatMap(t -> {
                Collection<V> vs = getter.apply(t);
                if (vs != null) {
                    return vs.stream();
                } else {
                    return Stream.empty();
                }
            }).collect(Collectors.toList());
            return list;
        }
        return Collections.emptyList();
    }
}
