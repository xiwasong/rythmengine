/**
 * Copyright (C) 2013-2016 The Rythm Engine project
 * for LICENSE and other details see:
 * https://github.com/rythmengine/rythmengine
 */
package org.rythmengine.logger;

import org.rythmengine.Rythm;
import org.rythmengine.extension.ILoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: luog
 * Date: 19/01/12
 * Time: 3:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class Logger {

    private Logger() {
    }

    private static class Proxy implements ILogger {
        private Class<?> c_;
        private ILogger l_;

        Proxy(Class<?> clazz) {
            c_ = clazz;
        }

        ILogger impl() {
            if (null == l_) {
                l_ = fact.getLogger(c_);
            }
            return l_;
        }

        @Override
        public boolean isTraceEnabled() {
            return impl().isTraceEnabled();
        }

        @Override
        public void trace(String format, Object... args) {
            impl().trace(format, args);
        }

        @Override
        public void trace(Throwable t, String format, Object... args) {
            impl().trace(t, format, args);
        }

        @Override
        public boolean isDebugEnabled() {
            return impl().isDebugEnabled();
        }

        @Override
        public void debug(String format, Object... args) {
            impl().debug(format, args);
        }

        @Override
        public void debug(Throwable t, String format, Object... args) {
            impl().debug(t, format, args);
        }

        @Override
        public boolean isInfoEnabled() {
            return impl().isInfoEnabled();
        }

        @Override
        public void info(String format, Object... arg) {
            impl().info(format, arg);
        }

        @Override
        public void info(Throwable t, String format, Object... args) {
            impl().info(t, format, args);
        }

        @Override
        public boolean isWarnEnabled() {
            return impl().isWarnEnabled();
        }

        @Override
        public void warn(String format, Object... args) {
            impl().warn(format, args);
        }

        @Override
        public void warn(Throwable t, String format, Object... args) {
            impl().warn(t, format, args);
        }

        @Override
        public boolean isErrorEnabled() {
            return impl().isErrorEnabled();
        }

        @Override
        public void error(String format, Object... arg) {
            impl().error(format, arg);
        }

        @Override
        public void error(Throwable t, String format, Object... args) {
            impl().error(t, format, args);
        }
    }

    private static Map<Class<?>, ILogger> loggers = new HashMap<Class<?>, ILogger>();

    private static ILoggerFactory userFact = null;
    private static final ILoggerFactory fact = new ILoggerFactory() {
        private ILoggerFactory defFact = new JDKLogger.Factory();

        @Override
        public ILogger getLogger(Class<?> clazz) {
            return null == userFact ? defFact.getLogger(clazz) : userFact.getLogger(clazz);
        }
    };

    public static ILogger get(Class<?> clazz) {
        ILogger logger = loggers.get(clazz);
        if (null == logger) {
            logger = new Proxy(clazz);
            loggers.put(clazz, logger);
        }
        return logger;
    }

    private static ILogger def = null;

    private static ILogger def() {
        if (null == def) def = get(Rythm.class);
        return def;
    }

    public static void registerLoggerFactory(ILoggerFactory fact) {
        reset();
        userFact = fact;
    }

    public static void reset() {
        userFact = null;
        def = null;
        loggers.clear();
    }

    public static boolean isTraceEnabled() {
        return def().isTraceEnabled();
    }

    public static void trace(String format, Object... args) {
        def().trace(format, args);
    }

    public static void trace(Throwable t, String format, Object... args) {
        def().trace(t, format, args);
    }

    public static boolean isDebugEnabled() {
        return def().isDebugEnabled();
    }

    public static void debug(String format, Object... args) {
        def().debug(format, args);
    }

    public static void debug(Throwable t, String format, Object... args) {
        def().debug(t, format, args);
    }

    public static boolean isInfoEnabled() {
        return def().isInfoEnabled();
    }

    public static void info(String format, Object... arg) {
        def().info(format, arg);
    }

    public static void info(Throwable t, String format, Object... args) {
        def().info(t, format, args);
    }

    public static boolean isWarnEnabled() {
        return def().isWarnEnabled();
    }

    public static void warn(String format, Object... arg) {
        def().warn(format, arg);
    }

    public static void warn(Throwable t, String format, Object... args) {
        def().warn(t, format, args);
    }

    public static boolean isErrorEnabled() {
        return def().isErrorEnabled();
    }

    public static void error(String format, Object... arg) {
        def().error(format, arg);
    }

    public static void error(Throwable t, String format, Object... args) {
        def().error(t, format, args);
    }
}
