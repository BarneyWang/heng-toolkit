package me.heng.tool.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AUTHOR: wangdi
 * DATE: 15/06/2018
 * TIME: 3:01 PM
 */
public interface Loggable {


    /**
     * Logger
     *
     * @return
     */
    default Logger getLogger() {
        return LoggerFactory.getLogger(getClass());
    }

    /**
     * Logger
     *
     * @return
     */
    default Logger getLogger(String name) {
        return LoggerFactory.getLogger( name);
    }

}
