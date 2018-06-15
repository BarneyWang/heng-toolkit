package me.heng.tool.support;

import java.util.concurrent.CompletionException;

/**
 * AUTHOR: wangdi
 * DATE: 15/06/2018
 * TIME: 4:56 PM
 */
public class ExceptionSupport {

    /**
     * CompletableFuture 很多操作符会包装 Throwable eg. future.completeExceptionally
     *
     * @param ex
     * @return
     */
    public static Throwable unwrap(Throwable ex) {
        if (ex == null) {
            return null;
        }
        /**
         * 循环引用问题
         */
        int max = 5;
        while (ex.getCause() != null && (ex instanceof CompletionException || ex.getClass() == RuntimeException.class) && max > 0) {
            ex = ex.getCause();
            max--;
        }
        return ex;
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



    public static <T> T softThrow(Throwable ex, String msg) {
        if (ex != null) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            } else {
                throw new RuntimeException(msg, ex);
            }
        }
        return null;
    }

    public static <T> T softThrow(Throwable ex) {
        if (ex != null) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            } else {
                throw new RuntimeException("", ex);
            }
        }
        return null;
    }

}
