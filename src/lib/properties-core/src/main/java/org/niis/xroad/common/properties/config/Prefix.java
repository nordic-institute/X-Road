/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.common.properties.config;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.util.DurationConverter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public abstract sealed class Prefix {

    private final String rootPath;

    private Prefix(String rootPath) {
        this.rootPath = rootPath;
    }

    public abstract Optional<String> name();

    /** @return target scope this prefix (and its keys) belong to; {@link Category#COMMON} when unscoped */
    public abstract Category category();

    public abstract List<ConfigKey<?>> keys();

    public String rootPath() {
        return rootPath;
    }

    /** @param pathSegment nested segment appended to this prefix's path
     *  @return a nested prefix inheriting this prefix's {@link Category} */
    public Prefix subPrefix(String pathSegment) {
        return new ChildScope(this, rootPath() + "." + pathSegment);
    }

    public StringKeyBuilder string(String shortKey) {
        return new StringKeyBuilder(this, shortKey);
    }

    public StringArrayKeyBuilder stringArray(String shortKey) {
        return new StringArrayKeyBuilder(this, shortKey);
    }

    public IntKeyBuilder integer(String shortKey) {
        return new IntKeyBuilder(this, shortKey);
    }

    public LongKeyBuilder longValue(String shortKey) {
        return new LongKeyBuilder(this, shortKey);
    }

    public DurationKeyBuilder keyDuration(String shortKey) {
        return new DurationKeyBuilder(this, shortKey);
    }

    public BooleanKeyBuilder bool(String shortKey) {
        return new BooleanKeyBuilder(this, shortKey);
    }

    public <E extends Enum<E>> EnumKeyBuilder<E> keyEnum(String shortKey, Class<E> type) {
        return new EnumKeyBuilder<E>(this, shortKey, type);
    }

    public <T> GeneralKeyBuilder<T> key(String shortKey, Class<T> type) {
        return new GeneralKeyBuilder<T>(this, shortKey, type);
    }

    public static Prefix of(String rootPath) {
        Objects.requireNonNull(rootPath, "scope rootPath is null");
        return new RootScope(rootPath, null, Category.COMMON);
    }

    public static Prefix of(String rootPath, String name) {
        Objects.requireNonNull(rootPath, "scope rootPath is null");
        Objects.requireNonNull(name, "scope name is null");
        return new RootScope(rootPath, name, Category.COMMON);
    }

    /**
     * The design's {@code Prefix.of(scope, path)}: declare a prefix bound to an explicit target scope.
     * The single-arg {@link #of(String)} defaults the scope to {@link Category#COMMON}.
     *
     * @param scope    target scope for every key under this prefix
     * @param rootPath dotted key prefix, e.g. {@code xroad.signer}
     * @return the prefix
     */
    public static Prefix of(Category scope, String rootPath) {
        Objects.requireNonNull(scope, "scope is null");
        Objects.requireNonNull(rootPath, "scope rootPath is null");
        return new RootScope(rootPath, null, scope);
    }

    private static final class RootScope extends Prefix {
        private final String name;
        private final Category category;
        private final List<ConfigKey<?>> keys = new ArrayList<>();

        private RootScope(String rootPath, String name, Category category) {
            super(rootPath);
            this.name = name;
            this.category = category;
        }

        @Override
        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        @Override
        public Category category() {
            return category;
        }

        @Override
        public List<ConfigKey<?>> keys() {
            return keys;
        }
    }

    private static final class ChildScope extends Prefix {

        private final Prefix parent;

        private ChildScope(Prefix parent, String rootPath) {
            super(rootPath);
            this.parent = parent;
        }

        @Override
        public Optional<String> name() {
            return parent.name();
        }

        @Override
        public Category category() {
            return parent.category();
        }

        @Override
        public List<ConfigKey<?>> keys() {
            return List.of();
        }
    }

    public static final class DefaultConfigKey<T> implements ConfigKey<T> {

        private final String scopeName;
        private final Category category;
        private final String key;
        private final Class<T> type;
        private final String defaultValue;
        private final String containerDefaultValue;
        private final Function<String, T> converter;
        private final Validator<T> validator;

        private DefaultConfigKey(String scopeName,
                                 Category category,
                                 String key, Class<T> type,
                                 String defaultValue,
                                 String containerDefaultValue,
                                 Function<String, T> converter,
                                 Validator<T> validator) {
            if (key == null || key.isEmpty()) {
                throw new IllegalArgumentException("key cannot be empty");
            }

            Objects.requireNonNull(type, "type is required for %s".formatted(key));
            Objects.requireNonNull(converter, "converter is required for %s".formatted(key));
            Objects.requireNonNull(validator, "validator is required for %s".formatted(key));

            validateDefault(key, defaultValue, converter, validator);
            validateDefault(key, containerDefaultValue, converter, validator);

            this.scopeName = scopeName;
            this.category = category;
            this.key = key;
            this.type = type;
            this.defaultValue = defaultValue;
            this.containerDefaultValue = containerDefaultValue;
            this.converter = converter;
            this.validator = validator;
        }

        private static <T> void validateDefault(String key, String rawDefault,
                                                Function<String, T> converter, Validator<T> validator) {
            if (rawDefault != null) {
                var result = validator.validate(converter.apply(rawDefault));
                if (!result.valid()) {
                    throw new IllegalArgumentException(
                            "Invalid default for %s: %s".formatted(key, result.message()));
                }
            }
        }

        @Override
        public Optional<String> scopeName() {
            return Optional.ofNullable(scopeName);
        }

        @Override
        public Category category() {
            return category;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String defaultValue() {
            return defaultValue;
        }

        @Override
        public T convertedDefaultValue() {
            return defaultValue == null ? null : convert(defaultValue);
        }

        @Override
        public String containerDefaultValue() {
            return containerDefaultValue;
        }

        @Override
        public T convertedContainerDefaultValue() {
            return containerDefaultValue == null ? null : convert(containerDefaultValue);
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public T convert(String rawValue) {
            return rawValue == null ? null : converter.apply(rawValue);
        }

        @Override
        public Validator.Result validate(T value) {
            return validator.validate(value);
        }

        @Override
        public Optional<String> validationSummary() {
            return validator.describe();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DefaultConfigKey<?> that)) return false;
            return Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(key);
        }
    }

    @RequiredArgsConstructor
    private abstract static sealed class Builder<T, KB extends Builder<T, KB>> {
        protected final Prefix scope;
        protected final String shortKey;
        protected final Class<T> type;

        protected Validator<T> validator = Validator.none();
        protected Function<String, T> converter;
        protected String defaultValue;
        protected String containerDefaultValue;


        @SuppressWarnings("unchecked")
        public KB withValidator(Validator<T> val) {
            this.validator = val;
            return (KB) this;
        }

        @SuppressWarnings("unchecked")
        public KB withDefaultValue(String defValue) {
            this.defaultValue = defValue;
            return (KB) this;
        }

        /** Container-mode default; resolvers in {@code CONTAINERIZED} mode prefer this over the regular default. */
        @SuppressWarnings("unchecked")
        public KB withContainerDefaultValue(String defValue) {
            this.containerDefaultValue = defValue;
            return (KB) this;
        }

        public ConfigKey<T> build() {
            var key = scope.rootPath() + "." + shortKey;

            var configKey = new DefaultConfigKey<>(scope.name().orElse(null), scope.category(), key, type, defaultValue,
                    containerDefaultValue, converter, validator);

            if (defaultValue != null) {
                var result = validator.validate(converter.apply(defaultValue));
                if (!result.valid()) {
                    throw new IllegalStateException(
                            "Invalid default for %s: %s".formatted(key, result.message()));
                }
            }

            // every key (root or nested) is tracked on the root scope, so provider.keys() returns the whole tree
            var root = scope;
            while (root instanceof ChildScope child) {
                root = child.parent;
            }
            root.keys().add(configKey);

            return configKey;
        }
    }

    public static final class StringKeyBuilder extends Builder<String, StringKeyBuilder> {

        private StringKeyBuilder(Prefix scope, String shortKey) {
            super(scope, shortKey, String.class);
            converter = Function.identity();
        }

    }

    public static final class StringArrayKeyBuilder extends Builder<String[], StringArrayKeyBuilder> {

        private StringArrayKeyBuilder(Prefix scope, String shortKey) {
            super(scope, shortKey, String[].class);
            converter = raw -> Stream.of(raw.split(","))
                    .map(String::trim)
                    .toArray(String[]::new);
        }

    }

    public static final class IntKeyBuilder extends Builder<Integer, IntKeyBuilder> {

        private IntKeyBuilder(Prefix scope, String shortKey) {
            super(scope, shortKey, Integer.class);
            converter = Integer::parseInt;
        }


        public IntKeyBuilder withDefaultValue(int defaultValue) {
            this.defaultValue = String.valueOf(defaultValue);
            return this;
        }
    }

    public static final class LongKeyBuilder extends Builder<Long, LongKeyBuilder> {

        private LongKeyBuilder(Prefix scope, String shortKey) {
            super(scope, shortKey, Long.class);
            converter = Long::parseLong;
        }

        public LongKeyBuilder withDefaultValue(long defaultValue) {
            this.defaultValue = String.valueOf(defaultValue);
            return this;
        }
    }

    public static final class EnumKeyBuilder<E extends Enum<E>> extends Builder<E, EnumKeyBuilder<E>> {

        private EnumKeyBuilder(Prefix scope, String shortKey, Class<E> type) {
            super(scope, shortKey, type);
            converter = raw -> Enum.valueOf(type, raw);
        }

        public EnumKeyBuilder<E> defaultValue(E defaultValue) {
            this.defaultValue = defaultValue == null ? null : defaultValue.name();
            return this;
        }
    }

    public static final class DurationKeyBuilder extends Builder<Duration, DurationKeyBuilder> {

        private DurationKeyBuilder(Prefix scope, String shortKey) {
            super(scope, shortKey, Duration.class);
            converter = DurationConverter::parseDuration;
        }

        public DurationKeyBuilder withDefaultValue(Duration defaultValue) {
            this.defaultValue = defaultValue == null ? null : defaultValue.toString();
            return this;
        }
    }

    public static final class BooleanKeyBuilder extends Builder<Boolean, BooleanKeyBuilder> {

        private BooleanKeyBuilder(Prefix scope, String shortKey) {
            super(scope, shortKey, Boolean.class);
            converter = Boolean::parseBoolean;
        }

        public BooleanKeyBuilder withDefaultValue(Boolean defaultValue) {
            this.defaultValue = defaultValue == null ? null : String.valueOf(defaultValue);
            return this;
        }

        public BooleanKeyBuilder withContainerDefaultValue(Boolean defaultValue) {
            this.containerDefaultValue = defaultValue == null ? null : String.valueOf(defaultValue);
            return this;
        }
    }

    public static final class GeneralKeyBuilder<T> extends Builder<T, GeneralKeyBuilder<T>> {

        private GeneralKeyBuilder(Prefix scope, String shortKey, Class<T> type) {
            super(scope, shortKey, type);
        }

        public GeneralKeyBuilder<T> withConverter(Function<String, T> converter) {
            this.converter = converter;
            return this;
        }
    }

}
