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
package org.niis.xroad.common.properties.config.keys;

import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Prefix;

import java.time.Duration;

/**
 * Shared global configuration keys ({@code xroad.common-global-conf.*}), consumed by both Quarkus and Spring
 * products. The {@code source} enum is modelled as a String key (parsed by the consuming properties class) to
 * keep this module domain-neutral.
 */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class GlobalConfConfigKeys implements ConfigKeyProvider {

    private static final Prefix GLOBAL_CONF = Prefix.of(Category.COMMON, "xroad.common-global-conf");

    private static final GlobalConfConfigKeys INSTANCE = new GlobalConfConfigKeys();

    /** {@code xroad.common-global-conf.source} — {@code FILESYSTEM} natively, {@code REMOTE} in containers. */
    public static final ConfigKey<String> SOURCE = GLOBAL_CONF
            .string("source")
            .withDefaultValue("FILESYSTEM")
            .withContainerDefaultValue("REMOTE")
            .build();
    /** {@code xroad.common-global-conf.refresh-rate}. */
    public static final ConfigKey<Duration> REFRESH_RATE = GLOBAL_CONF
            .keyDuration("refresh-rate")
            .withDefaultValue(Duration.ofSeconds(60))
            .build();
    /** {@code xroad.common-global-conf.configuration-path}. */
    public static final ConfigKey<String> CONFIGURATION_PATH = GLOBAL_CONF
            .string("configuration-path")
            .withDefaultValue("/etc/xroad/globalconf")
            .build();

    private GlobalConfConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static GlobalConfConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Prefix scope() {
        return GLOBAL_CONF;
    }
}
