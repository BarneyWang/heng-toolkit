package me.heng.tool.support;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.*;
import com.google.common.collect.Sets.SetView;
import com.google.common.util.concurrent.Uninterruptibles;
import me.heng.tool.function.ThrowableFunction;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by chuanbao on 9/11/2016 AD.
 */
public class Supports {

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
            long now = Supports.now();
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

    public static Throwable getOriginCause(Throwable ex) {
        if (ex == null) {
            return null;
        }
        /**
         * 避免循环问题
         */
        int max = 5;
        while (ex.getCause() != null && max > 0) {
            ex = ex.getCause();
            max--;
        }
        return ex;
    }

    /**
     * 函数式编程部分
     */
    public static <I, O> List<O> map(Collection<I> inputs, Function<I, O> mapper) {
        if (isEmpty(inputs)) {
            return Collections.emptyList();
        }
        return inputs.stream().map(mapper).collect(Collectors.toList());
    }

    public static <I, O> List<O> mapAsync(Collection<I> inputs, Function<I, O> mapper) {
        if (isEmpty(inputs)) {
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
        Map<K, V> singleMap = Maps.transformValues(multimap.asMap(), Supports::firstOrNull);
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
        V[] vs = (V[])Array.newInstance(clz, list.size());
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

    /**
     * @see String#trim 实现
     *
     * @param str
     * @return
     */
    public static String trimRight(String str) {
        if (str == null) {
            return null;
        }
        int len = str.length();
        int st = 0;
        char[] val = str.toCharArray();
        while ((st < len) && (val[len - 1] <= ' ')) {
            len--;
        }
        return str.substring(0, len);
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

    public static boolean isNotEmpty(Map<?, ?> col) {
        return col != null && !col.isEmpty();
    }

    public static boolean isEmpty(Map<?, ?> col) {
        return col == null || col.isEmpty();
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
     * 字符串join, split
     */
    private static Splitter splitter = Splitter.on(",").omitEmptyStrings();

    public static List<String> split(String str) {
        if (Strings.isNullOrEmpty(str)) {
            return Collections.emptyList();
        }
        return list(splitter.split(str));
    }

    public static String join(Collection<String> list) {
        if (list == null || list.size() == 0) {
            return "";
        }
        return joiner.join(list);
    }

    private static Joiner joiner = Joiner.on(",").skipNulls();

    /**
     * 按照正则分隔
     * @param str
     * @param pattern
     * @return
     */
    public static List<String> splitByPattern(String str, String pattern) {
        if (Strings.isNullOrEmpty(str)) {
            return Collections.emptyList();
        }
        return Splitter.onPattern(pattern).trimResults().omitEmptyStrings().splitToList(str);
    }

    private static Splitter insideSplitter = Splitter.on(",").trimResults().omitEmptyStrings();

    public static List<String> split(String str, String comma) {
        if (Strings.isNullOrEmpty(str)) {
            return Collections.emptyList();
        }
        if (",".equals(comma)) {
            return insideSplitter.splitToList(str);
        } else {
            return Splitter.on(comma).trimResults().omitEmptyStrings().splitToList(str);
        }
    }

    /**
     * 将line按照comma切分为limit个部分<br/>
     * 比如 splitWithLimit("a:b:c:de", ":", 3) => ["a", "b", "c:de"]
     *
     * @param line
     * @param comma
     * @param limit
     * @return
     */
    public static List<String> splitWithLimit(String line, String comma, int limit) {
        if (Strings.isNullOrEmpty(line) || Strings.isNullOrEmpty(comma) || limit < 1) {
            return Collections.emptyList();
        }
        int s = 0;
        int e = 0;
        List<String> vs = new ArrayList<>(limit);
        String v;
        for (int i = 1; i <= limit; i++) {
            if (i == limit) {
                v = line.substring(s);
            } else {
                e = line.indexOf(comma, s);
                if (e < 0) {
                    break;
                }
                v = line.substring(s, e);
            }
            vs.add(v);
            s = e + 1;
        }
        return vs;
    }

    public static String quote(String s) {
        return s == null ? null : "'" + s + "'";
    }

    public static String checkOrDoubleQuote(String s) {
        if (s != null && s.matches("\\s+")) {
            // 包含非空白符
            return doubleQuote(s);
        }
        return s;
    }

    public static String doubleQuote(String s) {
        return s == null ? null : "\"" + s + "\"";
    }

    public static String commonPrefix(Collection<String> items) {
        if (isEmpty(items)) {
            return "";
        }
        String[] vs = items.toArray(new String[items.size()]);
        String prefix = StringUtils.getCommonPrefix(vs);
        return prefix;
    }

    public static <T,K> Set<T> commonElements(Function<T,K> keyer, Collection<T> colA, Collection<T> colB) {
        if (colA == null || colB == null || colA.isEmpty() || colB.isEmpty()) {
            return Collections.emptySet();
        }
        Set<K> setA = set(colA, keyer);
        Set<K> setB = set(colB, keyer);
        SetView<K> commons = Sets.intersection(setA, setB);
        if (commons.isEmpty()) {
            return Collections.emptySet();
        }
        List<T> set = filter(colA, a -> commons.contains(keyer.apply(a)));
        return set(set);
    }

    public static <T,K> Set<T> commonElements(Function<T,K> keyer, Collection<Collection<T>> collections) {
        if (isEmpty(collections)) {
            return Collections.emptySet();
        }
        Iterator<Collection<T>> it = collections.iterator();
        Set<T> commons = new HashSet<>(it.next());
        while (it.hasNext()) {
            commons = commonElements(keyer, commons, it.next());
        }
        return commons;
    }

    /**
     * 分割\排序\去重
     */
    public static Set<String> splitToSortedSet(String line, String comma) {
        List<String> list = splitToList(line, comma);
        return list.stream().sorted().collect(Collectors.toSet());
    }

    public static List<String> splitToList(String line, String comma) {
        if (Strings.isNullOrEmpty(line)) {
            return Collections.emptyList();
        }
        return Splitter.on(comma).trimResults().omitEmptyStrings().splitToList(line);
    }

    public static Integer[] parseInt(String s, String comma, int len) {
        if (!Strings.isNullOrEmpty(s)) {
            String[] vs = s.split(comma);
            if (vs.length >= len) {
                Integer[] ints = new Integer[len];
                for (int i = 0; i < len; i++) {
                    ints[i] = Integer.valueOf(vs[i]);
                }
                return ints;
            }
        }
        return new Integer[0];
    }

    /**
     * 比较两个集合, 返回差异Pair, pair.first表示 first比second多的部分, pair.second表示 first比second少的部分
     *
     * @param first
     * @param second
     * @param comparator
     * @return
     */
    public static <T> Pair<List<T>, List<T>> diff(Collection<T> first, Collection<T> second,
        Comparator<T> comparator) {
        if (isEmpty(first) && isNotEmpty(second)) {
            return Pair.of(Collections.emptyList(), new ArrayList<>(second));
        }
        if (isNotEmpty(first) && isEmpty(second)) {
            return Pair.of(new ArrayList<>(first), Collections.emptyList());
        }
        if (isEmpty(first) && isEmpty(second)) {
            return Pair.of(Collections.emptyList(), Collections.emptyList());
        }
        ArrayList<T> fl = new ArrayList<>(first); // first list
        ArrayList<T> sl = new ArrayList<>(second); // second list
        ArrayList<T> fd = new ArrayList<>(first.size()); // first difference
        ArrayList<T> sd = new ArrayList<>(second.size()); // second difference
        for (T f : first) {
            for (T s : second) {
                if (comparator.compare(f, s) == 0) {
                    fd.add(f);
                    sd.add(s);
                }
            }
        }
        fl.removeAll(fd);
        sl.removeAll(sd);
        return Pair.of(fl, sl);
    }

    /**
     * @param first
     * @param second
     * @param keyGetter
     * @param <T>
     * @param <K>
     * @return Pair.of((first - second), (second - first))
     */
    public static <T, K> Pair<List<T>, List<T>> diff(Collection<T> first, Collection<T> second,
        Function<T, K> keyGetter) {
        if (isEmpty(first) && isNotEmpty(second)) {
            return Pair.of(Collections.emptyList(), new ArrayList<>(second));
        }
        if (isNotEmpty(first) && isEmpty(second)) {
            return Pair.of(new ArrayList<>(first), Collections.emptyList());
        }
        if (isEmpty(first) && isEmpty(second)) {
            return Pair.of(Collections.emptyList(), Collections.emptyList());
        }
        // Map<K, T> map1 = first.stream().collect(Collectors.toMap(keyGetter,
        // Function.identity())); //不能处理键重复问题
        Map<K, T> map1 = first.stream()
            .collect(Collectors.toMap(keyGetter, Function.identity(), (f, s) -> f));
        Map<K, T> map2 = second.stream()
            .collect(Collectors.toMap(keyGetter, Function.identity(), (f, s) -> f));
        SetView<K> ks = Sets.intersection(map1.keySet(), map2.keySet());
        List<T> t1 = new ArrayList<>(), t2 = new ArrayList<>();
        for (K k : ks) {
            t1.add(map1.get(k));
            t2.add(map2.get(k));
        }
        List<T> l1 = new ArrayList<>(first);
        List<T> l2 = new ArrayList<>(second);
        l1.removeAll(t1);
        l2.removeAll(t2);
        return Pair.of(l1, l2);
    }

    public static <T> Set<T> unique(Collection<T> vals, Comparator<T> comparator) {
        Set<T> set = new TreeSet<>(comparator);
        set.addAll(vals);
        return set;
    }

    /**
     *
     */
    public static <T, K> Set<T> unique(Function<T, K> idGetter, Collection<T> c1, Collection<T>... cs) {
        List<Collection<T>> list = new ArrayList<>();
        list.add(c1);
        if (cs != null && cs.length > 0) {
            list.addAll(Arrays.asList(cs));
        }
        Set<K> idSet = new HashSet<>();
        Set<T> valueSet = new HashSet<>();
        for (Collection<T> ts : list) {
            for (T t : ts) {
                if (!idSet.contains(idGetter.apply(t))) {
                    idSet.add(idGetter.apply(t));
                    valueSet.add(t);
                }
            }
        }
        return valueSet;
    }

    /**
     * 一些辅助函数
     */

    static Splitter CommaSplitter = Splitter.on(",").trimResults().omitEmptyStrings();
    static final String COMMA = ",";

    /**
     * 自动切分字符串,变为整型
     */
    public static List<Integer> splitToInteger(String ints, String split) {
        return splitToNumber(ints, split, Integer::valueOf);
    }

    /**
     * 自动切分字符串,变为数字
     *
     * @param numbers
     * @param split
     * @return
     */
    public static <N> List<N> splitToNumber(String numbers, String split, Function<String, N> toNum) {
        if (numbers != null && numbers.length() > 0) {
            List<String> list;
            if (split == null || COMMA.equals(split)) {
                list = CommaSplitter.splitToList(numbers);
            } else {
                list = Splitter.on(split).trimResults().omitEmptyStrings().splitToList(numbers);
            }
            return Supports.map(list, toNum);
        }
        return Collections.emptyList();
    }

    /**
     * 字符串分割\排序, 比较 split, sort, unique, diff
     */
    public static Pair<List<String>, List<String>> splitSortUniqueDiff(String origin, String target,
        String comma) {
        Set<String> cmds1 = splitToSortedSet(origin, comma);
        Set<String> cmds2 = splitToSortedSet(target, comma);
        Pair<List<String>, List<String>> pair = diff(cmds1, cmds2, Function.identity());
        return pair;
    }

    public static boolean isInteger(String input) {
        try {
            Integer.valueOf(input);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static boolean isLong(String input) {
        try {
            Long.valueOf(input);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 字符串辅助相关的函数
     */

    public static String format(String fmt, Object... objs) {
        String line = fmt;
        if (objs != null && objs.length > 0) {
            line = String.format(fmt, objs);
        }
        return line;
    }

    public static final PrintStream STDOUT = System.out;

    public static long now() {
        return System.currentTimeMillis();
    }

    /**
     * 随机long
     *
     * @return
     */
    public static Long randomLong() {
        return NONCE_RANDOM.nextLong();
    }

    static final Random NONCE_RANDOM = new Random(now());

    /**
     * 控制台打印，格式化字符串
     */
    public static void println(String fmt, Object... objs) {
        STDOUT.println(format(fmt, objs));
    }


    public static void sleep(int millis) {
        Uninterruptibles.sleepUninterruptibly(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * 数字相关
     */
    public static int long2int(Long l) {
        return Math.toIntExact(l);
    }

    public static void main(String... args) {
        Map<String, Integer> m = new HashMap<>();
        m.put("hello", 5);
        m.put("world", 5);
        m.put("xxxxx", null);
        m.put("yyyyy", null);

        BiFunction<String, Integer, String> mapper = (k, v) -> {
            if ("yyyyy".equals(k)) {
                return null;
            }
            return k + String.valueOf(v);
        };
        map(m, mapper, false).forEach(Supports::println);
        map(m, mapper, true).forEach(Supports::println);

        List<String> vs = splitWithLimit("::pwd:xx", ":", 3);
        println("%s", vs);
    }

    /**
     * 性能更好的removeAll
     *
     * @param source
     * @param destination
     * @param <T>
     * @return
     */
    public static <T> List<T> removeAll(Collection<T> source, Collection<T> destination) {
        List<T> result = Lists.newArrayList();
        Set<T> destinationSet = new HashSet<T>(destination);
        for (T t : source) {
            if (!destinationSet.contains(t)) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * 获取子列
     *
     * @param list
     * @param start
     * @param size
     * @param <T>
     * @return
     */
    public static <T> List<T> getSubList(List<T> list, Integer start, Integer size) {
        List<T> subList;
        Integer total = list.size();
        if (start > total) {
            subList = new ArrayList<>();
        } else if (start + size > total) {
            subList = list.subList(start, total);
        } else {
            subList = list.subList(start, start + size);
        }
        return subList;
    }

    /**
     * 获取子set
     * @author jiangcai.zjc
     * @param set
     * @param start
     * @param size
     * @param <T>
     * @return
     */
    public static <T> Set<T> getSubSet(Set<T> set, Integer start, Integer size) {
        return set.stream().skip(start).limit(size).collect(Collectors.toSet());
    }

    /**
     * 组合为set
     * @param set1
     * @param set2
     * @param <T>
     * @return
     */
    public static <T> Set<T> concatSet(Collection<T> set1, Collection<T> set2) {
        Set ret = new HashSet();
        set1.forEach(ret::add);
        set2.forEach(ret::add);
        return ret;
    }

    /**
     * 交集,不影响入参集合
     * @param col1
     * @param col2
     * @param <T>
     * @return
     */
    public static <T> List<T> retainAll(Collection<T> col1, Collection<T> col2) {
        if (ListSupport.isEmpty(col1) || ListSupport.isEmpty(col2)) {
            return Collections.emptyList();
        } else {
            List<T> list1 = Lists.newArrayList(col1);
            list1.retainAll(col2);
            return list1;
        }
    }

    /**
     * 相除取上界
     * @param divisor
     * @param dividend
     * @return
     */
    public static Integer ceil(Integer divisor, Integer dividend) {
        Integer num = divisor/dividend;
        if (!Objects.equals(num * dividend, divisor)) {
            num++;
            return num;
        } else {
            return num;
        }
    }

    public static Throwable unwrap(Throwable ex) {
        if (ex == null) {
            return null;
        }
        /**
         * 循环引用问题
         */
        int max = 5;
        while (ex.getCause() != null && max > 0) {
            ex = ex.getCause();
            max--;
        }
        return ex;
    }

    public static String stringifyException(Throwable ex) {
        Throwable error = unwrap(ex);
        return error == null ? "null" : error.getClass().getName() + ":" + error.getMessage();
    }

    /**
     * 包装 Throwable成 RuntimeException
     * @param e
     * @param unwrap
     * @return
     */
    public static RuntimeException wrapThrowable(Throwable e, boolean unwrap) {
        if (e != null) {
            if (e instanceof RuntimeException) {
                return ((RuntimeException)e);
            } else {
                Throwable ex = unwrap ? unwrap(e): e;
                return new RuntimeException(ex.getMessage(), ex);
            }
        }
        return null;
    }

    /**
     * 生产uid
     * @return
     */
    public static String generateUid() {
        return UUID.randomUUID().toString();
    }

}
