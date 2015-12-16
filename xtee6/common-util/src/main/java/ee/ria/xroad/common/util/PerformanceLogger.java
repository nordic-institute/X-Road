package ee.ria.xroad.common.util;

import org.slf4j.Logger;

/**
 * Contains utility methods for logging performance statistics.
 */
public final class PerformanceLogger {

    private PerformanceLogger() {
    }

    /**
     * Log given message and the current time, then return it.
     * @param logger the logger to use
     * @param message the message that should be logged
     * @return current time in milliseconds
     */
    public static long log(Logger logger, String message) {
        long now = System.currentTimeMillis();

        if (logger.isTraceEnabled()) {
            logger.trace("PERFORMANCE: {}: {}", now, message);
        }

        return now;
    }

    /**
     * Log given message and the current time and the time that passed since the
     * provided timestamp.
     * @param logger the logger to use
     * @param startMillis timestamp to compare with the current time
     * @param message the message that should be logged
     */
    public static void log(Logger logger, long startMillis, String message) {
        if (logger.isTraceEnabled()) {
            long now = System.currentTimeMillis();

            logger.trace("PERFORMANCE: {}: {}: {}",
                    new Object[] {now, now - startMillis, message});
        }
    }
}
