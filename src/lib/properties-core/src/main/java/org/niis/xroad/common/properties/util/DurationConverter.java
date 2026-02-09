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
package org.niis.xroad.common.properties.util;

import org.eclipse.microprofile.config.spi.Converter;

import java.io.Serializable;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * A converter for a {@link Duration} interface.
 * A carbon copy of the same class from the Quarkus.
 */
public class DurationConverter implements Converter<Duration>, Serializable {
    private static final long serialVersionUID = 7499347081928776532L;
    private static final String PERIOD = "P";
    private static final String PERIOD_OF_TIME = "PT";
    public static final Pattern DIGITS = Pattern.compile("^[-+]?\\d+$");
    private static final Pattern DIGITS_AND_UNIT = Pattern.compile("^(?:[-+]?\\d+(?:\\.\\d+)?(?i)[hms])+$");
    private static final Pattern DAYS = Pattern.compile("^[-+]?\\d+(?i)d$");
    private static final Pattern MILLIS = Pattern.compile("^[-+]?\\d+(?i)ms$");

    /**
     * If the {@code value} starts with a number, then:
     * <ul>
     * <li>If the value is only a number, it is treated as a number of seconds.</li>
     * <li>If the value is a number followed by {@code ms}, it is treated as a number of milliseconds.</li>
     * <li>If the value is a number followed by {@code h}, {@code m}, or {@code s}, it is prefixed with {@code PT}
     * and {@link Duration#parse(CharSequence)} is called.</li>
     * <li>If the value is a number followed by {@code d}, it is prefixed with {@code P}
     * and {@link Duration#parse(CharSequence)} is called.</li>
     * </ul>
     * <p>
     * Otherwise, {@link Duration#parse(CharSequence)} is called.
     *
     * @param value a string duration
     * @return the parsed {@link Duration}
     * @throws IllegalArgumentException in case of parse failure
     */
    @Override
    public Duration convert(String value) {
        return parseDuration(value);
    }

    /**
     * If the {@code value} starts with a number, then:
     * <ul>
     * <li>If the value is only a number, it is treated as a number of seconds.</li>
     * <li>If the value is a number followed by {@code ms}, it is treated as a number of milliseconds.</li>
     * <li>If the value is a number followed by {@code h}, {@code m}, or {@code s}, it is prefixed with {@code PT}
     * and {@link Duration#parse(CharSequence)} is called.</li>
     * <li>If the value is a number followed by {@code d}, it is prefixed with {@code P}
     * and {@link Duration#parse(CharSequence)} is called.</li>
     * </ul>
     * <p>
     * Otherwise, {@link Duration#parse(CharSequence)} is called.
     *
     * @param value a string duration
     * @return the parsed {@link Duration}
     * @throws IllegalArgumentException in case of parse failure
     */
    public static Duration parseDuration(String value) {
        value = value.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (DIGITS.asPredicate().test(value)) {
            return Duration.ofSeconds(Long.parseLong(value));
        } else if (MILLIS.asPredicate().test(value)) {
            return Duration.ofMillis(Long.parseLong(value.substring(0, value.length() - 2)));
        }

        try {
            if (DIGITS_AND_UNIT.asPredicate().test(value)) {
                return Duration.parse(PERIOD_OF_TIME + value);
            } else if (DAYS.asPredicate().test(value)) {
                return Duration.parse(PERIOD + value);
            }

            return Duration.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
