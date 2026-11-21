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
import java.util.Set;

import static org.niis.xroad.common.properties.config.Validator.durationAtLeast;
import static org.niis.xroad.common.properties.config.Validator.durationBetween;
import static org.niis.xroad.common.properties.config.Validator.nonEmpty;
import static org.niis.xroad.common.properties.config.Validator.positive;
import static org.niis.xroad.common.properties.config.Validator.positiveInts;
import static org.niis.xroad.common.properties.config.Validator.positiveLong;
import static org.niis.xroad.common.properties.config.Validator.range;

/**
 * The {@code xroad.dsp.*} keys shared by the dataspace components.
 *
 * <p>A key is consumed in one of three ways. An EDC extension reads a key through
 * {@code ServiceExtensionContext.getSetting}: the control-plane extensions read the catalog cache keys, the
 * built-in services keys, the issuer-trust refresh interval, the task-poll worker count, the datasource pool
 * size and the outbound per-host request limit, and the Issuer Service's DID resolution extension reads the
 * DID resolution fetch timeout. An X-Road process reads a key
 * through {@code XRoadConfig}: the admin
 * service reads both port keys through {@code AdminServiceProperties.Dataspace.getDidPort()} and
 * {@code getDspPort()}, and the issuer data extensions of the control plane and the Identity Hub read the
 * {@code issuer-data} keys. An application configuration interpolates a
 * key with {@code ${...}}: the identity hub's DID web context port uses {@code ${xroad.dsp.did-port}}.
 *
 * <p>{@code publishedToFramework()} is set on every key so that the packaged default and a stored row both
 * reach the framework configuration, which serves the first and the third way.
 */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class DspConfigKeys implements ConfigKeyProvider {

    private static final Prefix DSP = Prefix.of(Category.COMMON, "xroad.dsp");
    private static final Prefix CATALOG_CACHE = DSP.subPrefix("catalog").subPrefix("cache");
    private static final Prefix BUILTIN_SERVICES = DSP.subPrefix("builtin-services");
    private static final Prefix ISSUER_TRUST = DSP.subPrefix("issuer-trust");
    private static final Prefix DID_RESOLUTION = DSP.subPrefix("did-resolution");
    private static final Prefix TASK_POLL = DSP.subPrefix("task-poll");
    private static final Prefix OUTBOUND_HTTP = DSP.subPrefix("outbound-http");
    private static final Prefix CONTROL_PLANE_POOL = DSP.subPrefix("control-plane").subPrefix("pool");
    private static final Prefix ISSUER_DATA = DSP.subPrefix("issuer-data");
    private static final Prefix ISSUER_DATA_DOWNLOAD_RETRY = ISSUER_DATA.subPrefix("download-retry");
    private static final Prefix ISSUER_DATA_CACHE = ISSUER_DATA.subPrefix("cache");
    private static final Prefix ISSUER_DATA_CACHE_STATUSLIST = ISSUER_DATA_CACHE.subPrefix("statuslist");
    private static final Prefix ISSUER_DATA_CLEANUP = ISSUER_DATA.subPrefix("cleanup");

    private static final DspConfigKeys INSTANCE = new DspConfigKeys();

    /** {@code xroad.dsp.did-port}. */
    public static final ConfigKey<Integer> DID_PORT = DSP
            .integer("did-port")
            .withDefaultValue(7183)
            .withValidator(range(1, 65535))
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.dsp-port}. */
    public static final ConfigKey<Integer> DSP_PORT = DSP
            .integer("dsp-port")
            .withDefaultValue(8183)
            .withValidator(range(1, 65535))
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

    /** {@code xroad.dsp.issuer-trust.refresh-interval-seconds}. */
    public static final ConfigKey<Long> ISSUER_TRUST_REFRESH_INTERVAL_SECONDS = ISSUER_TRUST
            .longValue("refresh-interval-seconds")
            .withDefaultValue(60L)
            .withValidator(positiveLong())
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.did-resolution.fetch-timeout-ms}. */
    public static final ConfigKey<Long> DID_RESOLUTION_FETCH_TIMEOUT_MS = DID_RESOLUTION
            .longValue("fetch-timeout-ms")
            .withDefaultValue(3000L)
            .withValidator(positiveLong())
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.task-poll.workers}. */
    public static final ConfigKey<Integer> TASK_POLL_WORKERS = TASK_POLL
            .integer("workers")
            .withDefaultValue(4)
            .withValidator(range(1, Integer.MAX_VALUE))
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.outbound-http.max-requests-per-host}. */
    public static final ConfigKey<Integer> OUTBOUND_HTTP_MAX_REQUESTS_PER_HOST = OUTBOUND_HTTP
            .integer("max-requests-per-host")
            .withDefaultValue(8)
            .withValidator(range(1, Integer.MAX_VALUE))
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.control-plane.pool.max-total}. */
    public static final ConfigKey<Integer> CONTROL_PLANE_POOL_MAX_TOTAL = CONTROL_PLANE_POOL
            .integer("max-total")
            .withDefaultValue(16)
            .withValidator(range(1, Integer.MAX_VALUE))
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.control-plane.pool.max-idle}. */
    public static final ConfigKey<Integer> CONTROL_PLANE_POOL_MAX_IDLE = CONTROL_PLANE_POOL
            .integer("max-idle")
            .withDefaultValue(16)
            .withValidator(range(1, Integer.MAX_VALUE))
            .publishedToFramework()
            .build();

    /**
     * {@code xroad.dsp.issuer-data.download-wait}: the longest wait of a check for a fresh download while the
     * issuer answers.
     */
    public static final ConfigKey<Duration> ISSUER_DATA_DOWNLOAD_WAIT = ISSUER_DATA
            .keyDuration("download-wait")
            .withDefaultValue(Duration.ofSeconds(2))
            .withValidator(durationBetween(Duration.ZERO, Duration.ofSeconds(10)))
            .exposedInUi()
            .publishedToFramework()
            .build();

    /**
     * {@code xroad.dsp.issuer-data.download-retry.delays-seconds}: the wait in seconds before the next download after
     * a failed one. Entry n is the wait after failure n, and the last entry repeats.
     */
    public static final ConfigKey<String[]> ISSUER_DATA_DOWNLOAD_RETRY_DELAYS_SECONDS = ISSUER_DATA_DOWNLOAD_RETRY
            .stringArray("delays-seconds")
            .withDefaultValue("5,10,15,20,30,60")
            .withValidator(positiveInts())
            .exposedInUi()
            .publishedToFramework()
            .build();

    /**
     * {@code xroad.dsp.issuer-data.download-retry.reset-after}: after this time a failed or unanswered download is
     * forgotten, and the retry delay starts again from the first entry.
     */
    public static final ConfigKey<Duration> ISSUER_DATA_DOWNLOAD_RETRY_RESET_AFTER = ISSUER_DATA_DOWNLOAD_RETRY
            .keyDuration("reset-after")
            .withDefaultValue(Duration.ofMinutes(10))
            .withValidator(positive())
            .exposedInUi()
            .publishedToFramework()
            .build();

    /**
     * {@code xroad.dsp.issuer-data.cache.statuslist.size}: the most statuslists kept in memory. Value 0 keeps no
     * copy in memory; the data then comes from the shared download, or from the stored copy during an outage.
     */
    public static final ConfigKey<Integer> ISSUER_DATA_CACHE_STATUSLIST_SIZE = ISSUER_DATA_CACHE_STATUSLIST
            .integer("size")
            .withDefaultValue(1000)
            .withValidator(range(0, Integer.MAX_VALUE))
            .exposedInUi()
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.issuer-data.cleanup.interval}: the time between runs of the expired issuer data cleanup. */
    public static final ConfigKey<Duration> ISSUER_DATA_CLEANUP_INTERVAL = ISSUER_DATA_CLEANUP
            .keyDuration("interval")
            .withDefaultValue(Duration.ofMinutes(10))
            .withValidator(durationAtLeast(Duration.ofMinutes(5)))
            .exposedInUi()
            .publishedToFramework()
            .build();

    /** {@code xroad.dsp.issuer-data.cleanup.enabled}: whether the expired issuer data cleanup job runs. */
    public static final ConfigKey<Boolean> ISSUER_DATA_CLEANUP_ENABLED = ISSUER_DATA_CLEANUP
            .bool("enabled")
            .withDefaultValue(true)
            .exposedInUi()
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
