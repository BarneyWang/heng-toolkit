package me.heng.tool.support;

import com.google.common.util.concurrent.Uninterruptibles;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AUTHOR: wangdi
 * DATE: 15/06/2018
 * TIME: 4:02 PM
 */
public class BaseSupport {

    public static final PrintStream STDOUT = System.out;

    /**
     * 返回当前时间
     * @return
     */
    public static long now() {
        return System.currentTimeMillis();
    }

    /**
     * 控制台打印，格式化字符串
     */
    public static void println(String fmt, Object... objs) {
        STDOUT.println(format(fmt, objs));
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


    public static void sleep(int millis) {
        Uninterruptibles.sleepUninterruptibly(millis, TimeUnit.MILLISECONDS);
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




}
