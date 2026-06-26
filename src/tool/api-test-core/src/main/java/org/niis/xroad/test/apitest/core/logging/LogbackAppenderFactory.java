/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.test.apitest.core.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import lombok.experimental.UtilityClass;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Utility for programmatically creating and registering Logback appenders during test execution.
 */
@UtilityClass
public class LogbackAppenderFactory {

    private static final String DEFAULT_PATTERN = "%d{HH:mm:ss.SS} %-5level %logger{36} - %msg%n";
    private static final String REPORT_APPENDER_NAME = "REPORT";
    private static final String REPORT_LOG_FILENAME = "test-automation-exec.log";

    /**
     * Creates and registers the main REPORT file appender to the root logger.
     *
     * @param workingDir the base directory for log files
     */
    public static void registerReportAppender(String workingDir) {
        LoggerContext context = getLoggerContext();
        if (isAppenderRegistered(REPORT_APPENDER_NAME)) {
            return;
        }
        String logFilePath = workingDir + "/" + REPORT_LOG_FILENAME;
        FileAppender<ILoggingEvent> appender = createFileAppender(context, REPORT_APPENDER_NAME, logFilePath, DEFAULT_PATTERN, false);
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    /**
     * Creates a dedicated file logger for a Docker container.
     *
     * @param loggerName  logger name (used as file prefix)
     * @param workingDir  base directory
     * @param subdirectory optional subdirectory under workingDir
     * @param filename    log filename
     * @param pattern     log pattern (null for default)
     * @return configured logger
     */
    public static Logger createFileLogger(String loggerName, String workingDir, String subdirectory, String filename, String pattern) {
        LoggerContext context = getLoggerContext();
        String logFilePath = buildLogPath(workingDir, subdirectory, filename);
        String logPattern = pattern != null ? pattern : DEFAULT_PATTERN;

        Logger logger = context.getLogger(loggerName);
        if (logger.iteratorForAppenders().hasNext()) {
            return logger;
        }
        FileAppender<ILoggingEvent> appender = createFileAppender(context, loggerName + "-APPENDER", logFilePath, logPattern, true);
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        return logger;
    }

    private static FileAppender<ILoggingEvent> createFileAppender(LoggerContext context, String appenderName,
                                                                   String filePath, String pattern, boolean append) {
        FileAppender<ILoggingEvent> appender = new FileAppender<>();
        appender.setContext(context);
        appender.setName(appenderName);
        appender.setFile(filePath);
        appender.setAppend(append);
        appender.setEncoder(createEncoder(context, pattern));
        appender.start();
        return appender;
    }

    private static PatternLayoutEncoder createEncoder(LoggerContext context, String pattern) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(pattern);
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();
        return encoder;
    }

    private static LoggerContext getLoggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    private static boolean isAppenderRegistered(String appenderName) {
        Logger rootLogger = getLoggerContext().getLogger(Logger.ROOT_LOGGER_NAME);
        Appender<ILoggingEvent> existing = rootLogger.getAppender(appenderName);
        return existing != null;
    }

    private static String buildLogPath(String workingDir, String subdirectory, String filename) {
        var path = new StringBuilder(workingDir);
        if (!workingDir.endsWith("/")) {
            path.append("/");
        }
        if (subdirectory != null && !subdirectory.isEmpty()) {
            path.append(subdirectory);
            if (!subdirectory.endsWith("/")) {
                path.append("/");
            }
        }
        path.append(filename);
        return path.toString();
    }
}
