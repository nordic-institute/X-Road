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

package org.niis.xroad.common.properties.config;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates a typed property value. Run on import, PATCH, and against the declared
 * default at build time.
 *
 * @param <T> value type
 */
@FunctionalInterface
public interface Validator<T> {

    /**
     * @param value value to check (may be {@code null})
     * @return outcome
     */
    Result validate(T value);

    /**
     * Validation outcome.
     *
     * @param valid   whether the value passed
     * @param message failure reason, {@code null} when valid
     */
    record Result(boolean valid, String message) {
        /** @return passing result */
        public static Result ok() {
            return new Result(true, null);
        }

        /**
         * @param message failure reason
         * @return failing result
         */
        public static Result error(String message) {
            return new Result(false, message);
        }
    }

    /**
     * @param <T> value type
     * @return validator that accepts everything
     */
    static <T> Validator<T> none() {
        return value -> Result.ok();
    }

    /**
     * @param allowed permitted values
     * @return validator accepting only {@code allowed}
     */
    static Validator<Integer> oneOf(Integer... allowed) {
        var permitted = Set.of(allowed);
        return value -> permitted.contains(value)
                ? Result.ok()
                : Result.error("must be one of " + permitted);
    }

    /**
     * @param min inclusive lower bound
     * @param max inclusive upper bound
     * @return validator accepting integers within {@code [min, max]}
     */
    static Validator<Integer> range(int min, int max) {
        return value -> value != null && value >= min && value <= max
                ? Result.ok()
                : Result.error("must be within [%d, %d]".formatted(min, max));
    }

    /**
     * @param regex regex the value must fully match
     * @return validator enforcing {@code regex}
     */
    static Validator<String> pattern(String regex) {
        var compiled = Pattern.compile(regex);
        return value -> value != null && compiled.matcher(value).matches()
                ? Result.ok()
                : Result.error("must match " + regex);
    }

    /** @return validator rejecting null/blank strings */
    static Validator<String> nonEmpty() {
        return value -> value != null && !value.isBlank()
                ? Result.ok()
                : Result.error("must not be empty");
    }

    /**
     * @param first  first validator
     * @param second second validator
     * @param <T>    value type
     * @return validator passing when both pass
     */
    static <T> Validator<T> and(Validator<T> first, Validator<T> second) {
        return value -> {
            var result = first.validate(value);
            return result.valid() ? second.validate(value) : result;
        };
    }

    /**
     * @param first  first validator
     * @param second second validator
     * @param <T>    value type
     * @return validator passing when either passes
     */
    static <T> Validator<T> or(Validator<T> first, Validator<T> second) {
        return value -> first.validate(value).valid() ? Result.ok() : second.validate(value);
    }
}
