package me.heng.tool.spring;

import me.heng.tool.support.ExceptionSupport;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by chuanbao on 9/23/2016 AD.
 */

public class SpringContextHelper implements ApplicationContextAware {
    private static ApplicationContext ctx;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ctx = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        return ctx;
    }

    public static ApplicationContext getContext() {
        return ctx;
    }

    public static <T> T getBean(Class<? extends T> clz) {
        return ctx.getBean(clz);
    }

    public static <T> void autowireBean(T bean) {
        AutowireCapableBeanFactory beanFactory =
            ctx.getAutowireCapableBeanFactory();
        beanFactory.autowireBean(bean);
        try {
            beanFactory.initializeBean(bean, "");
        } catch (BeansException e) {
            Throwable ex = e.getRootCause();
            throw ExceptionSupport.wrapThrowable(ex, true);
        }
    }

    public <T> T autowireBean(Class<T> beanClass) {
        AutowireCapableBeanFactory beanFactory =
            getApplicationContext().getAutowireCapableBeanFactory();
        try {
            T bean = beanClass.newInstance();
            autowireBean(bean);
            return bean;
        } catch (Exception e) {
            return ExceptionSupport.softThrow(e);
        }
    }

    public static <T> List<T> getBeanOfType(Class<T> clz) {
        String[] names = ctx.getBeanNamesForType(clz);
        if (names == null || names.length == 0) {
            return Collections.emptyList();
        }
        List<T> beans = new ArrayList<>(names.length);
        for (String name : names) {
            Object bean = ctx.getBean(name);
            beans.add((T)bean);
        }
        return beans;
    }

    public <T> T getBeanByName(String name) {
        Object bean = ctx.getBean(name);
        if (bean != null) {
            return ((T)bean);
        }
        return null;
    }
}
