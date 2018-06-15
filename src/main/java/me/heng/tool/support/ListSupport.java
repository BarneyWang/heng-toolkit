package me.heng.tool.support;

import com.google.common.collect.Lists;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * AUTHOR: wangdi
 * DATE: 15/06/2018
 * TIME: 4:18 PM
 */
public class ListSupport {

    public static <K, V> List<V> getAll(Map<K, V> map, Collection<K> keys) {
        if (map != null && map.size() > 0 && keys != null && keys.size() > 0) {
            List<V> vals = new ArrayList<>(keys.size());
            for (K key : keys) {
                vals.add(map.get(key));
            }
            return vals;
        }
        return Collections.emptyList();
    }

    /**
     * 集合操作相关
     */
    public static <T> T firstOrNull(Collection<T> col) {
        if (col == null || col.size() == 0) {
            return null;
        }
        return col.iterator().next();
    }


    /**
     * 简单断言
     */
    public static boolean isEmpty(Collection<?> col) {
        if (col == null || col.size() == 0) {
            return true;
        }
        return false;
    }

    public static boolean isNotEmpty(Collection<?> col) {
        if (col != null && col.size() > 0) {
            return true;
        }
        return false;
    }


    public static <V> List<V> filter(Collection<V> vals, Predicate<V> p) {
        if (vals != null) {
            return vals.stream().filter(p).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public static <V> Set<V> filterToSet(Collection<V> vals, Predicate<V> p) {
        if (vals != null) {
            return vals.stream().filter(p).collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    public static <V> List<V> arrayFilter(V[] inputs, Predicate<V> p) {
        if (inputs == null || inputs.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(inputs).filter(p).collect(Collectors.toList());
    }

    public static <V, T> List<V> filterMap(Collection<T> from, Predicate<T> predicate,
                                           Function<T, V> mapper) {
        if (from != null && from.size() > 0) {
            return from.stream().filter(predicate).map(mapper).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public static <V> List<V> filterNotNull(Collection<V> vals) {
        return filter(vals, v -> v != null);
    }

    /**
     * notice: 返回的是 immutable list
     */
    public static <T> List<T> list(T... vals) {
        return Arrays.asList(vals);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> concat(Collection<T> c1, Collection<T>... cs) {
        List<T> list = new ArrayList<>();
        if (c1 != null) {
            list.addAll(c1);
        }
        if (cs != null && cs.length > 0) {
            for (Collection<T> c : cs) {
                if (c != null) {
                    list.addAll(c);
                }
            }
        }
        return list;
    }

    public static <T> List<T> list(Iterable<T> vals) {
        if (vals == null) {
            return Collections.emptyList();
        } else if (vals instanceof List) {
            return ((List)vals);
        }
        return Lists.newArrayList(vals.iterator());
    }

    public static <V> V[] list2vector(Collection<? extends V> list, Class<V> clz) {
        if (list == null || list.size() == 0) {
            return (V[])new Object[0];
        }
        V[] vs = (V[]) Array.newInstance(clz, list.size());
        list.toArray(vs);
        return vs;
    }

    public static List<Long> ints2longs(Collection<Integer> ints, Predicate<Integer> filter) {
        if (filter != null) {
            return ints.stream().filter(filter).map(Long::valueOf).collect(Collectors.toList());
        } else {
            return ints.stream().map(Long::valueOf).collect(Collectors.toList());
        }
    }

    public static List<Integer> longs2ints(Collection<Long> longs, Predicate<Long> filter) {
        if (filter != null) {
            return longs.stream().filter(filter).map(Long::intValue).collect(Collectors.toList());
        } else {
            return longs.stream().map(Long::intValue).collect(Collectors.toList());
        }
    }

    public static List<Long> ints2longs(Collection<Integer> ints) {
        return ints2longs(ints, Objects::nonNull);
    }

    public static List<Integer> longs2ints(Collection<Long> longs) {
        return longs2ints(longs, Objects::nonNull);
    }

}
