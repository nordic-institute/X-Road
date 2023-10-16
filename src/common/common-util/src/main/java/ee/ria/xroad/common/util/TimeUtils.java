/*
 * The MIT License
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
package ee.ria.xroad.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.MICROS;

/**
 * This class contains various time related utility methods.
 */
public final class TimeUtils {

    private TimeUtils() {
    }

    /**
     * Gets the number of seconds from the Java epoch of 1970-01-01T00:00:00Z.
     *
     * @return the seconds from the epoch of 1970-01-01T00:00:00Z
     */
    public static long getEpochSecond() {
        return Instant.now().getEpochSecond();
    }

    /**
     * Gets the number of milliseconds from the Java epoch of
     * 1970-01-01T00:00:00Z.
     *
     * @return the milliseconds from the epoch of 1970-01-01T00:00:00Z
     */
    public static long getEpochMillisecond() {
        return Instant.now().toEpochMilli();
    }

    /**
     * Converts given seconds to milliseconds.
     *
     * @param seconds given seconds
     * @return the converted milliseconds
     */
    public static int secondsToMillis(int seconds) {
        return (int) TimeUnit.SECONDS.toMillis(seconds);
    }

    /**
     * Converts given date to offsetdatetime using the system default timezone
     */
    public static OffsetDateTime toOffsetDateTime(Date date) {
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    /**
     * Current time truncated to microseconds. Some OS/JDK might use nanoseconds precision.
     *
     * @return Instant wil microseconds precision.
     */
    public static Instant now() {
        return Instant.now().truncatedTo(MICROS);
    }

    /**
     * Current time truncated to microseconds. Some OS/JDK might use nanoseconds precision.
     *
     * @return OffsetDateTime wil microseconds precision.
     */
    public static OffsetDateTime offsetDateTimeNow() {
        return OffsetDateTime.now().truncatedTo(MICROS);
    }

    /**
     * Current time truncated to microseconds. Some OS/JDK might use nanoseconds precision.
     *
     * @return OffsetDateTime wil microseconds precision.
     */
    public static OffsetDateTime offsetDateTimeNow(ZoneId zoneId) {
        return OffsetDateTime.now(zoneId).truncatedTo(MICROS);
    }

    /**
     * Current time truncated to microseconds. Some OS/JDK might use nanoseconds precision.
     *
     * @return LocalDateTime wil microseconds precision.
     */
    public static LocalDateTime localDateTimeNow() {
        return LocalDateTime.now().truncatedTo(MICROS);
    }

    /**
     * Current time truncated to microseconds. Some OS/JDK might use nanoseconds precision.
     *
     * @return ZonedDateTime wil microseconds precision.
     */
    public static ZonedDateTime zonedDateTimeNow(ZoneId zoneId) {
        return ZonedDateTime.now(zoneId).truncatedTo(MICROS);
    }

}
