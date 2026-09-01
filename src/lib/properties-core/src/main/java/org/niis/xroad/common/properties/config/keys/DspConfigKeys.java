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

import java.util.Set;

import static org.niis.xroad.common.properties.config.Validator.nonEmpty;
import static org.niis.xroad.common.properties.config.Validator.positiveLong;
import static org.niis.xroad.common.properties.config.Validator.range;

/**
 * X-Road owned inputs to ds-control-plane's own {@code ServiceExtensionContext} settings
 * ({@code xroad.dsp.*}).
 *
 * <p>Unlike a packaged-yaml-interpolated EDC setting ({@link EdcConfigKeys}), these keys are read
 * directly by ds-control-plane extensions via {@code ServiceExtensionContext.getSetting(key, default)} —
 * the key declared here already is the setting name the extension asks for, so no yaml interpolation
 * hop is needed. {@code publishedToFramework()} still has to be set on every key: it is what copies a
 * stored override into the Quarkus config tree ds-control-plane's {@code QuarkusConfigBridge} exposes to
 * {@code getSetting()}; without it a DB-seeded row would never reach the extension.
 */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class DspConfigKeys implements ConfigKeyProvider {

    private static final Prefix DSP = Prefix.of(Category.COMMON, "xroad.dsp");
    private static final Prefix CATALOG_CACHE = DSP.subPrefix("catalog").subPrefix("cache");
    private static final Prefix BUILTIN_SERVICES = DSP.subPrefix("builtin-services");

    private static final DspConfigKeys INSTANCE = new DspConfigKeys();

    /**
     * {@code xroad.dsp.participant-context-id} — no default; the extension falls back to
     * {@code edc.hostname} itself when unset.
     */
    public static final ConfigKey<String> PARTICIPANT_CONTEXT_ID = DSP
            .string("participant-context-id")
            .publishedToFramework()
            .build();

    /**
     * {@code xroad.dsp.management-participant-context-id} — no default; the extension falls back to
     * {@code <participant-context-id>-mgmt} itself when unset.
     */
    public static final ConfigKey<String> MANAGEMENT_PARTICIPANT_CONTEXT_ID = DSP
            .string("management-participant-context-id")
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.catalog.cache.enabled}. */
    public static final ConfigKey<Boolean> CATALOG_CACHE_ENABLED = CATALOG_CACHE
            .bool("enabled")
            .withDefaultValue(true)
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.catalog.cache.ttl-seconds}. */
    public static final ConfigKey<Long> CATALOG_CACHE_TTL_SECONDS = CATALOG_CACHE
            .longValue("ttl-seconds")
            .withDefaultValue(60L)
            .withValidator(positiveLong())
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.catalog.cache.find-by-id-max-size}. */
    public static final ConfigKey<Integer> CATALOG_CACHE_FIND_BY_ID_MAX_SIZE = CATALOG_CACHE
            .integer("find-by-id-max-size")
            .withDefaultValue(10000)
            .withValidator(range(1, Integer.MAX_VALUE))
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.builtin-services.proxyMonitor.enabled}. */
    public static final ConfigKey<Boolean> BUILTIN_SERVICES_PROXY_MONITOR_ENABLED = BUILTIN_SERVICES
            .bool("proxyMonitor.enabled")
            .withDefaultValue(true)
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.builtin-services.opMonitor.enabled}. */
    public static final ConfigKey<Boolean> BUILTIN_SERVICES_OP_MONITOR_ENABLED = BUILTIN_SERVICES
            .bool("opMonitor.enabled")
            .withDefaultValue(true)
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.builtin-services.metaservices.enabled}. */
    public static final ConfigKey<Boolean> BUILTIN_SERVICES_METASERVICES_ENABLED = BUILTIN_SERVICES
            .bool("metaservices.enabled")
            .withDefaultValue(true)
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.builtin-services.server-proxy-url}. */
    public static final ConfigKey<String> BUILTIN_SERVICES_SERVER_PROXY_URL = BUILTIN_SERVICES
            .string("server-proxy-url")
            .withDefaultValue("http://localhost:5500/")
            .withValidator(nonEmpty())
            .publishedToFramework()
            .build();

    private DspConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static DspConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public String rootPath() {
        return DSP.rootPath();
    }

    @Override
    public Set<ConfigKey<?>> keys() {
        return DSP.keys();
    }
}
