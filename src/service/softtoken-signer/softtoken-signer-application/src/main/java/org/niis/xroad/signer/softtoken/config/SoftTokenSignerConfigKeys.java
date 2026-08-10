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
package org.niis.xroad.signer.softtoken.config;

import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Prefix;

import java.time.Duration;
import java.util.Set;

/** DSL config keys for the {@code xroad.softtoken-signer} namespace. */
@SuppressWarnings("checkstyle:MagicNumber")
final class SoftTokenSignerConfigKeys implements ConfigKeyProvider {

    private static final Prefix SOFTTOKEN_SIGNER = Prefix.of(Category.SOFTTOKEN_SIGNER, "xroad.softtoken-signer");
    private static final Prefix HEALTH_CHECK = SOFTTOKEN_SIGNER.subPrefix("health-check");
    private static final Prefix KEY_SYNC = HEALTH_CHECK.subPrefix("key-sync");
    private static final Prefix KEYS = SOFTTOKEN_SIGNER.subPrefix("keys");
    private static final Prefix RPC = SOFTTOKEN_SIGNER.subPrefix("rpc");

    private static final SoftTokenSignerConfigKeys INSTANCE = new SoftTokenSignerConfigKeys();

    // --- xroad.softtoken-signer.health-check.key-sync ---------------------------

    /** {@code xroad.softtoken-signer.health-check.key-sync.max-consecutive-failures} */
    static final ConfigKey<Integer> KEY_SYNC_MAX_CONSECUTIVE_FAILURES = KEY_SYNC
            .integer("max-consecutive-failures")
            .withDefaultValue(3)
            .build();

    /** {@code xroad.softtoken-signer.health-check.key-sync.max-sync-age} */
    static final ConfigKey<Duration> KEY_SYNC_MAX_SYNC_AGE = KEY_SYNC
            .keyDuration("max-sync-age")
            .withDefaultValue(Duration.ofMinutes(5))
            .build();

    // --- xroad.softtoken-signer.keys --------------------------------------------

    /** {@code xroad.softtoken-signer.keys.sync-rate} */
    static final ConfigKey<Duration> KEYS_SYNC_RATE = KEYS
            .keyDuration("sync-rate")
            .withDefaultValue(Duration.ofSeconds(30))
            .build();

    // --- xroad.softtoken-signer.rpc ---------------------------------------------

    /** {@code xroad.softtoken-signer.rpc.enabled} */
    static final ConfigKey<Boolean> RPC_ENABLED = RPC
            .bool("enabled")
            .withDefaultValue(true)
            .build();

    /** {@code xroad.softtoken-signer.rpc.listen-address} */
    static final ConfigKey<String> RPC_LISTEN_ADDRESS = RPC
            .string("listen-address")
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue("0.0.0.0")
            .build();

    /** {@code xroad.softtoken-signer.rpc.port} */
    static final ConfigKey<Integer> RPC_PORT = RPC
            .integer("port")
            .withDefaultValue(5561)
            .build();

    private SoftTokenSignerConfigKeys() {
    }

    /** @return the provider singleton. */
    static SoftTokenSignerConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public String rootPath() {
        return SOFTTOKEN_SIGNER.rootPath();
    }

    @Override
    public Set<ConfigKey<?>> keys() {
        return SOFTTOKEN_SIGNER.keys();
    }
}
