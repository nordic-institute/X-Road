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

/**
 * Typed handle to a single configuration property; built via {@link Builder}.
 *
 * @param key          effective dotted key, e.g. {@code xroad.signer.key-length}
 * @param scope        scope segment, e.g. {@code signer}
 * @param shortKey     scope-relative short name
 * @param type         value type
 * @param defaultValue packaged default ({@code null} if undeclared)
 * @param validator    value validator
 * @param <T>          value type
 */
public record ConfigKey<T>(
        String key,
        String scope,
        String shortKey,
        Class<T> type,
        T defaultValue,
        Validator<T> validator) {

    /**
     * Fluent builder for {@link ConfigKey}, obtained from {@link Scope}.
     *
     * @param <T> value type
     */
    public static final class Builder<T> {

        private final Scope scope;
        private final String shortKey;
        private final Class<T> type;

        private Validator<T> validator = Validator.none();
        private T defaultValue;

        Builder(Scope scope, String shortKey, Class<T> type) {
            this.scope = scope;
            this.shortKey = shortKey;
            this.type = type;
        }

        /**
         * @param validatorToApply validator to apply
         * @return this builder
         */
        public Builder<T> validation(Validator<T> validatorToApply) {
            this.validator = validatorToApply;
            return this;
        }

        /**
         * @param value packaged default ({@code null} means "no default")
         * @return this builder
         */
        public Builder<T> defaultValue(T value) {
            this.defaultValue = value;
            return this;
        }

        /**
         * Validates the default (if any) and registers the key with its scope.
         *
         * @return the built key
         */
        public ConfigKey<T> build() {
            var effectiveKey = "xroad." + scope.name() + "." + shortKey;

            if (defaultValue != null) {
                var result = validator.validate(defaultValue);
                if (!result.valid()) {
                    throw new IllegalStateException(
                            "Invalid default for %s: %s".formatted(effectiveKey, result.message()));
                }
            }

            return scope.track(new ConfigKey<>(effectiveKey, scope.name(), shortKey, type,
                    defaultValue, validator));
        }
    }
}
