package ee.cyber.sdsb.common.util;

import org.slf4j.Logger;

/**
 */
public class PerformanceLogger {
    public static long log(Logger logger, String message) {
        long now = 0;

        if (logger.isTraceEnabled()) {
            now = System.currentTimeMillis();
            logger.trace("PERFORMANCE: {}: {}", now, message);
        }

        return now;
    }

    public static void log(Logger logger, long startMillis, String message) {
        if (logger.isTraceEnabled()) {
            long now = System.currentTimeMillis();

            logger.trace("PERFORMANCE: {}: {}: {}",
                    new Object[] {now , now - startMillis, message});
        }
    }
}
