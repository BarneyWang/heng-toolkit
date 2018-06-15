package me.heng.tool.function;

/**
 * AUTHOR: wangdi
 * DATE: 15/06/2018
 * TIME: 4:25 PM
 */
@FunctionalInterface
public interface ThrowableFunction<I, O, E extends Throwable> {
    O apply(I input) throws E;
}
