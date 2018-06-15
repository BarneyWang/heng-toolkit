package me.heng.tool.function;

import java.util.function.BiFunction;

/**
 * Created by  heyong on 6/20/17.
 *
 */
public interface RuntimeConfigGetter extends BiFunction<String, String, String> {

    @Override
    String apply(String category, String name);
}
