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
package org.niis.xroad.test.framework.core.logging;

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
 * Utility class for programmatically creating and registering Logback
 * appenders.
 *
 * <p>
 * This class provides methods to create file appenders with specific patterns
 * and register them with the root logger or specific loggers. It centralizes
 * logger configuration that was previously split between logback.xml and code.
 */
@UtilityClass
public class LogbackAppenderFactory {

    private static final String DEFAULT_PATTERN = "%d{HH:mm:ss.SS} %-5level %logger{36} - %msg%n";
    private static final String REPORT_APPENDER_NAME = "REPORT";
    private static final String REPORT_LOG_FILENAME = "test-automation-exec.log";

    /**
     * Creates and registers the main REPORT file appender to the root logger.
     *
     * <p>
     * This appender writes all test execution logs to a single file.
     * It should be called early during test initialization, typically from
     * a LauncherSessionListener.
     *
     * @param workingDir the base directory for log files
     */
    public static void registerReportAppender(String workingDir) {
        LoggerContext context = getLoggerContext();

        // Check if already registered to avoid duplicates
        if (isAppenderRegistered(REPORT_APPENDER_NAME)) {
            return;
        }

        String logFilePath = workingDir + "/" + REPORT_LOG_FILENAME;
        FileAppender<ILoggingEvent> appender = createFileAppender(
                context,
                REPORT_APPENDER_NAME,
                logFilePath,
                DEFAULT_PATTERN,
                false
        );

        // Register to root logger
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(appender);
    }

    /**
     * Creates a file appender for a specific logger (e.g., container logs).
     *
     * <p>
     * This method creates a dedicated logger with its own file appender,
     * useful for separating logs by source (e.g., different Docker containers).
     *
     * @param loggerName   the name of the logger to create
     * @param workingDir   the base directory for log files
     * @param subdirectory optional subdirectory under workingDir (can be null)
     * @param filename     the log filename
     * @param pattern      the log pattern (null to use default)
     * @return the configured logger
     */
    public static Logger createFileLogger(String loggerName,
                                          String workingDir,
                                          String subdirectory,
                                          String filename,
                                          String pattern) {
        LoggerContext context = getLoggerContext();

        String logFilePath = buildLogPath(workingDir, subdirectory, filename);
        String logPattern = pattern != null ? pattern : DEFAULT_PATTERN;

        FileAppender<ILoggingEvent> appender = createFileAppender(
                context,
                loggerName + "-APPENDER",
                logFilePath,
                logPattern,
                false);

        Logger logger = context.getLogger(loggerName);
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false); // Don't propagate to root logger

        return logger;
    }

    /**
     * Creates a file appender with the specified configuration.
     *
     * @param context      the logger context
     * @param appenderName the name of the appender
     * @param filePath     the full path to the log file
     * @param pattern      the log pattern
     * @param append       whether to append to existing file or overwrite
     * @return the configured and started appender
     */
    private static FileAppender<ILoggingEvent> createFileAppender(LoggerContext context,
                                                                  String appenderName,
                                                                  String filePath,
                                                                  String pattern,
                                                                  boolean append) {
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setName(appenderName);
        fileAppender.setFile(filePath);
        fileAppender.setAppend(append);
        fileAppender.setEncoder(createEncoder(context, pattern));
        fileAppender.start();

        return fileAppender;
    }

    /**
     * Creates a pattern layout encoder with the specified pattern.
     *
     * @param context the logger context
     * @param pattern the log pattern
     * @return the configured and started encoder
     */
    private static PatternLayoutEncoder createEncoder(LoggerContext context, String pattern) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(pattern);
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();

        return encoder;
    }

    /**
     * Gets the Logback logger context.
     *
     * @return the logger context
     */
    private static LoggerContext getLoggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    /**
     * Checks if an appender with the given name is already registered on the root
     * logger.
     *
     * @param appenderName the appender name to check
     * @return true if the appender is registered, false otherwise
     */
    private static boolean isAppenderRegistered(String appenderName) {
        Logger rootLogger = getLoggerContext().getLogger(Logger.ROOT_LOGGER_NAME);
        Appender<ILoggingEvent> existingAppender = rootLogger.getAppender(appenderName);
        return existingAppender != null;
    }

    /**
     * Builds a log file path from components.
     *
     * @param workingDir   the base working directory
     * @param subdirectory optional subdirectory (can be null)
     * @param filename     the log filename
     * @return the full log file path
     */
    private static String buildLogPath(String workingDir, String subdirectory, String filename) {
        StringBuilder path = new StringBuilder(workingDir);

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
