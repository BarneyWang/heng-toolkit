package me.heng.tool.map;

/**
 * 参考scala的trait用法
 */
public interface Oneself<T> {

    default T self() {
        return (T) this;
    }
}
