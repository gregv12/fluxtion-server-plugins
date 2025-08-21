package com.fluxtion.server.plugin.trading.component.quickfixj;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.LoggerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Simple in-memory Log4j2 appender for capturing log messages during tests.
 *
 * This implementation uses LoggerContext/Configuration to attach and detach the appender,
 * which is compatible across Log4j2 versions.
 */
class LogCaptureUtil {

    static class InMemoryAppender extends AbstractAppender {
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final Class<?> targetClass;
        private Level previousLevel;
        private String loggerName;

        protected InMemoryAppender(String name, Class<?> targetClass) {
            super(name, null, PatternLayout.newBuilder().withPattern("%m").build(), true, null);
            this.targetClass = targetClass;
            this.loggerName = targetClass.getName();
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }

        public List<String> getMessages() {
            return new ArrayList<>(messages);
        }
    }

    static InMemoryAppender attachToLogger(Class<?> clazz) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        InMemoryAppender appender = new InMemoryAppender("InMemoryAppender-" + clazz.getSimpleName(), clazz);
        appender.start();

        // Register appender and attach to the effective LoggerConfig for this logger
        config.addAppender(appender);
        LoggerConfig loggerConfig = config.getLoggerConfig(clazz.getName());
        appender.previousLevel = loggerConfig.getLevel();
        loggerConfig.addAppender(appender, Level.INFO, null);
        // Ensure INFO level so info messages are captured by default
        loggerConfig.setLevel(Level.INFO);
        ctx.updateLoggers();
        return appender;
    }

    static void detachFromLogger(Class<?> clazz, Appender appender) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(clazz.getName());

        loggerConfig.removeAppender(appender.getName());
        if (appender instanceof InMemoryAppender im) {
            // restore previous level
            loggerConfig.setLevel(im.previousLevel);
        }
        ctx.updateLoggers();
        appender.stop();
    }

    static boolean anyMessageMatches(InMemoryAppender appender, Predicate<String> predicate) {
        return appender.getMessages().stream().anyMatch(predicate);
    }
}
