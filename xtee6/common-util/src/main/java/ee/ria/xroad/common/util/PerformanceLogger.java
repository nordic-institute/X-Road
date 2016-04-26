/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
        long now = 0;

        if (logger.isTraceEnabled()) {
            now = System.currentTimeMillis();
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
