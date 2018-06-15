package me.heng.tool.map;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * 服务方法, 需要注意 value必须是相应的返回类型,否则会抛出ClassCastException
 */
public interface MapTypeEnhancer<K> extends Oneself<Map<K, Object>> {

    /**
     * 自动转换类型
     *
     * @param key
     * @param <T>
     * @return
     */
    default <T> T getObject(K key) {
        return (T) self().get(key);
    }

    default String getString(K key) {
        return getObject(key);
    }

    default Integer getInteger(K key) {
        return getObject(key);
    }

    default Boolean getBool(K key) {
        Object val = getObject(key);
        return val == null ? false : (Boolean) val;
    }

    /**
     * 可以返回默认值
     *
     * @param key
     * @param val
     * @param <T>
     * @return
     */
    default <T> T get(K key, T val) {
        return (T) self().getOrDefault(key, val);
    }

    class EnhancedMap<K> extends HashMap<K,Object> implements MapTypeEnhancer<K> {
        public EnhancedMap() {
        }

        public EnhancedMap(Map<? extends K, ?> m) {
            super(m);
        }
    }

    static <K> EnhancedMap<K> newEnhancedMap(Map<K, Object> map){
        return new EnhancedMap<>(map);
    }
}
