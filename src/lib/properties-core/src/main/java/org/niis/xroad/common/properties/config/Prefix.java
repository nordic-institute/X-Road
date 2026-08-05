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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.util.DurationConverter;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A dotted key-path prefix (e.g. {@code xroad.signer}) that keys are declared under, bound to a
 * {@link Category} for UI grouping. Nested segments are created with {@link #subPrefix(String)} and inherit
 * the parent's category. Declare a prefix with {@link #of(Category, String)}, or {@link #of(String)} which
 * defaults to {@link Category#COMMON}.
 */
public abstract sealed class Prefix {

    private final String rootPath;

    private Prefix(String rootPath) {
        this.rootPath = rootPath;
    }

    /** @return UI grouping this prefix (and its keys) belong to; {@link Category#COMMON} when unscoped */
    public abstract Category category();

    /** @return keys declared under the root of this prefix tree, in declaration order */
    public abstract Set<ConfigKey<?>> keys();

    /** @return dotted key prefix, e.g. {@code xroad.signer} */
    public String rootPath() {
        return rootPath;
    }

    /**
     * @param pathSegment nested segment appended to this prefix's path
     * @return a nested prefix inheriting this prefix's {@link Category}
     */
    public Prefix subPrefix(String pathSegment) {
        return new ChildPrefix(this, rootPath() + "." + pathSegment);
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

    /**
     * Declare a prefix in the {@link Category#COMMON} UI grouping (shared keys not owned by a single service).
     *
     * @param rootPath dotted key prefix, e.g. {@code xroad.common-rpc}
     * @return the prefix
     */
    public static Prefix of(String rootPath) {
        Objects.requireNonNull(rootPath, "rootPath is null");
        return new RootPrefix(rootPath, Category.COMMON);
    }

    /**
     * Declare a prefix bound to an explicit UI {@link Category}.
     *
     * @param category UI grouping for every key under this prefix
     * @param rootPath dotted key prefix, e.g. {@code xroad.signer}
     * @return the prefix
     */
    public static Prefix of(Category category, String rootPath) {
        Objects.requireNonNull(category, "category is null");
        Objects.requireNonNull(rootPath, "rootPath is null");
        return new RootPrefix(rootPath, category);
    }

    private static final class RootPrefix extends Prefix {
        private final Category category;
        private final Set<ConfigKey<?>> keys = new LinkedHashSet<>();

        private RootPrefix(String rootPath, Category category) {
            super(rootPath);
            this.category = category;
        }

        @Override
        public Category category() {
            return category;
        }

        @Override
        public Set<ConfigKey<?>> keys() {
            return keys;
        }
    }

    private static final class ChildPrefix extends Prefix {

        private final Prefix parent;

        private ChildPrefix(Prefix parent, String rootPath) {
            super(rootPath);
            this.parent = parent;
        }

        @Override
        public Category category() {
            return parent.category();
        }

        @Override
        public Set<ConfigKey<?>> keys() {
            return Set.of();
        }
    }

    public static final class DefaultConfigKey<T> implements ConfigKey<T> {

        private final Category category;
        private final boolean exposedInUi;
        private final boolean publishedToFramework;
        private final String key;
        private final Class<T> type;
        private final String defaultValue;
        private final String containerDefaultValue;
        private final Function<String, T> converter;
        private final Validator<T> validator;

        private DefaultConfigKey(Category category,
                                 boolean exposedInUi,
                                 boolean publishedToFramework,
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

            this.category = category;
            this.exposedInUi = exposedInUi;
            this.publishedToFramework = publishedToFramework;
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
        public Category category() {
            return category;
        }

        @Override
        public boolean exposedInUi() {
            return exposedInUi;
        }

        @Override
        public boolean publishedToFramework() {
            return publishedToFramework;
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
        protected final Prefix prefix;
        protected final String shortKey;
        protected final Class<T> type;

        protected Validator<T> validator = Validator.none();
        protected Function<String, T> converter;
        protected String defaultValue;
        protected String containerDefaultValue;
        protected boolean exposedInUi;
        protected boolean publishedToFramework;


        @SuppressWarnings("unchecked")
        public KB withValidator(Validator<T> val) {
            this.validator = val;
            return (KB) this;
        }

        /** Marks the key as shown/editable in the system-parameters UI (internal keys stay hidden by default). */
        @SuppressWarnings("unchecked")
        public KB exposedInUi() {
            this.exposedInUi = true;
            return (KB) this;
        }

        /**
         * Publishes the resolved value into the framework's own config (Spring {@code Environment} /
         * SmallRye) as well. Only for keys a packaged {@code application.yaml} interpolates into a
         * framework setting — see {@link ConfigKey#publishedToFramework()}.
         */
        @SuppressWarnings("unchecked")
        public KB publishedToFramework() {
            this.publishedToFramework = true;
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
            var key = prefix.rootPath() + "." + shortKey;

            var configKey = new DefaultConfigKey<>(prefix.category(), exposedInUi, publishedToFramework, key, type,
                    defaultValue, containerDefaultValue, converter, validator);

            if (defaultValue != null) {
                var result = validator.validate(converter.apply(defaultValue));
                if (!result.valid()) {
                    throw new IllegalStateException(
                            "Invalid default for %s: %s".formatted(key, result.message()));
                }
            }

            // every key (root or nested) is tracked on the root prefix, so provider.keys() returns the whole tree
            var root = prefix;
            while (root instanceof ChildPrefix child) {
                root = child.parent;
            }
            if (!root.keys().add(configKey)) {
                throw new IllegalStateException("Duplicate config key declaration: " + key);
            }

            return configKey;
        }
    }

    public static final class StringKeyBuilder extends Builder<String, StringKeyBuilder> {

        private StringKeyBuilder(Prefix prefix, String shortKey) {
            super(prefix, shortKey, String.class);
            converter = Function.identity();
        }

    }

    public static final class StringArrayKeyBuilder extends Builder<String[], StringArrayKeyBuilder> {

        private StringArrayKeyBuilder(Prefix prefix, String shortKey) {
            super(prefix, shortKey, String[].class);
            converter = raw -> Stream.of(raw.split(","))
                    .map(String::trim)
                    .toArray(String[]::new);
        }

    }

    public static final class IntKeyBuilder extends Builder<Integer, IntKeyBuilder> {

        private IntKeyBuilder(Prefix prefix, String shortKey) {
            super(prefix, shortKey, Integer.class);
            converter = Integer::parseInt;
        }


        public IntKeyBuilder withDefaultValue(int defaultValue) {
            this.defaultValue = String.valueOf(defaultValue);
            return this;
        }
    }

    public static final class LongKeyBuilder extends Builder<Long, LongKeyBuilder> {

        private LongKeyBuilder(Prefix prefix, String shortKey) {
            super(prefix, shortKey, Long.class);
            converter = Long::parseLong;
        }

        public LongKeyBuilder withDefaultValue(long defaultValue) {
            this.defaultValue = String.valueOf(defaultValue);
            return this;
        }
    }

    public static final class EnumKeyBuilder<E extends Enum<E>> extends Builder<E, EnumKeyBuilder<E>> {

        private EnumKeyBuilder(Prefix prefix, String shortKey, Class<E> type) {
            super(prefix, shortKey, type);
            converter = raw -> Enum.valueOf(type, raw);
        }

        public EnumKeyBuilder<E> defaultValue(E defaultValue) {
            this.defaultValue = defaultValue == null ? null : defaultValue.name();
            return this;
        }
    }

    public static final class DurationKeyBuilder extends Builder<Duration, DurationKeyBuilder> {

        private DurationKeyBuilder(Prefix prefix, String shortKey) {
            super(prefix, shortKey, Duration.class);
            converter = DurationConverter::parseDuration;
        }

        public DurationKeyBuilder withDefaultValue(Duration defaultValue) {
            this.defaultValue = defaultValue == null ? null : defaultValue.toString();
            return this;
        }
    }

    public static final class BooleanKeyBuilder extends Builder<Boolean, BooleanKeyBuilder> {

        private BooleanKeyBuilder(Prefix prefix, String shortKey) {
            super(prefix, shortKey, Boolean.class);
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

        private GeneralKeyBuilder(Prefix prefix, String shortKey, Class<T> type) {
            super(prefix, shortKey, type);
        }

        public GeneralKeyBuilder<T> withConverter(Function<String, T> converter) {
            this.converter = converter;
            return this;
        }
    }

}
