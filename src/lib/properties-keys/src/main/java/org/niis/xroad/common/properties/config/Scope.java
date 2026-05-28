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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Property namespace forming effective keys {@code xroad.<scope>.<shortKey>}; obtained
 * via {@link XRoadConfig#scope(String)}. Every key built through it registers back here,
 * so providers never maintain a parallel key list.
 */
public final class Scope {

    private final String name;
    private final List<ConfigKey<?>> keys = new ArrayList<>();

    Scope(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("scope name is required");
        }
        this.name = name;
    }

    /** @return scope segment, e.g. {@code "signer"} */
    public String name() {
        return name;
    }

    /** @return keys built through this scope, in declaration order (immutable copy) */
    public List<ConfigKey<?>> keys() {
        return List.copyOf(keys);
    }

    /**
     * @param shortKey scope-relative key name
     * @return builder for an {@code Integer} property
     */
    public ConfigKey.Builder<Integer> integer(String shortKey) {
        return new ConfigKey.Builder<>(this, shortKey, Integer.class);
    }

    /**
     * @param shortKey scope-relative key name
     * @return builder for a {@code Boolean} property
     */
    public ConfigKey.Builder<Boolean> bool(String shortKey) {
        return new ConfigKey.Builder<>(this, shortKey, Boolean.class);
    }

    /**
     * @param shortKey scope-relative key name
     * @return builder for a {@code String} property
     */
    public ConfigKey.Builder<String> string(String shortKey) {
        return new ConfigKey.Builder<>(this, shortKey, String.class);
    }

    /**
     * @param shortKey scope-relative key name
     * @return builder for a {@code Duration} property
     */
    public ConfigKey.Builder<Duration> duration(String shortKey) {
        return new ConfigKey.Builder<>(this, shortKey, Duration.class);
    }

    /**
     * @param shortKey scope-relative key name
     * @param type     enum type
     * @param <E>      enum type
     * @return builder for an enum property
     */
    public <E extends Enum<E>> ConfigKey.Builder<E> enumOf(String shortKey, Class<E> type) {
        return new ConfigKey.Builder<>(this, shortKey, type);
    }

    /**
     * Records a key built by {@link ConfigKey.Builder#build()}.
     *
     * @param key the built key
     * @param <T> value type
     * @return same key, for fluent return
     */
    <T> ConfigKey<T> track(ConfigKey<T> key) {
        keys.add(key);
        return key;
    }
}
