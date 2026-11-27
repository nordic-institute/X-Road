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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DspConfigKeysTest {
    private static final List<String> ISSUER_DATA_KEYS = List.of(
            "xroad.dsp.issuer-data.download-wait",
            "xroad.dsp.issuer-data.download-retry-seconds",
            "xroad.dsp.issuer-data.download-expire-after",
            "xroad.dsp.issuer-data.download-timeout",
            "xroad.dsp.issuer-data.cache.statuslist.size",
            "xroad.dsp.issuer-data.cache.statuslist-download.size",
            "xroad.dsp.issuer-data.cache.did-download.size",
            "xroad.dsp.issuer-data.cleanup.interval",
            "xroad.dsp.issuer-data.cleanup.enabled");
    private static final List<String> OTHER_KEYS = List.of(
            "xroad.dsp.did-port",
            "xroad.dsp.dsp-port",
            "xroad.dsp.catalog.cache.enabled",
            "xroad.dsp.catalog.cache.ttl-seconds",
            "xroad.dsp.catalog.cache.find-by-id-max-size",
            "xroad.dsp.builtin-services.proxyMonitor.enabled",
            "xroad.dsp.builtin-services.opMonitor.enabled",
            "xroad.dsp.builtin-services.metaservices.enabled",
            "xroad.dsp.builtin-services.server-proxy-url",
            "xroad.dsp.issuer-trust.refresh-interval-seconds",
            "xroad.dsp.did-resolution.fetch-timeout-ms",
            "xroad.dsp.task-poll.workers",
            "xroad.dsp.outbound-http.max-requests-per-host",
            "xroad.dsp.control-plane.pool.max-total",
            "xroad.dsp.control-plane.pool.max-idle");

    @Test
    void didPortRejectsOutOfRangeAndAcceptsInRange() {
        assertThat(DspConfigKeys.DID_PORT.validate(0).valid()).isFalse();
        assertThat(DspConfigKeys.DID_PORT.validate(65536).valid()).isFalse();
        assertThat(DspConfigKeys.DID_PORT.validate(7183).valid()).isTrue();
    }

    @Test
    void dspPortRejectsOutOfRangeAndAcceptsInRange() {
        assertThat(DspConfigKeys.DSP_PORT.validate(0).valid()).isFalse();
        assertThat(DspConfigKeys.DSP_PORT.validate(65536).valid()).isFalse();
        assertThat(DspConfigKeys.DSP_PORT.validate(8183).valid()).isTrue();
    }

    @Test
    void ttlSecondsRejectsZeroNegativeAndNull() {
        assertThat(DspConfigKeys.CATALOG_CACHE_TTL_SECONDS.validate(0L).valid()).isFalse();
        assertThat(DspConfigKeys.CATALOG_CACHE_TTL_SECONDS.validate(-5L).valid()).isFalse();
        assertThat(DspConfigKeys.CATALOG_CACHE_TTL_SECONDS.validate(null).valid()).isFalse();
    }

    @Test
    void ttlSecondsAcceptsPositiveValueAndDefault() {
        assertThat(DspConfigKeys.CATALOG_CACHE_TTL_SECONDS.validate(5L).valid()).isTrue();
        assertThat(DspConfigKeys.CATALOG_CACHE_TTL_SECONDS.convertedDefaultValue()).isEqualTo(60L);
    }

    @Test
    void findByIdMaxSizeRejectsZeroAndNegative() {
        assertThat(DspConfigKeys.CATALOG_CACHE_FIND_BY_ID_MAX_SIZE.validate(0).valid()).isFalse();
        assertThat(DspConfigKeys.CATALOG_CACHE_FIND_BY_ID_MAX_SIZE.validate(-1).valid()).isFalse();
        assertThat(DspConfigKeys.CATALOG_CACHE_FIND_BY_ID_MAX_SIZE.validate(1).valid()).isTrue();
    }

    @Test
    void issuerTrustRefreshIntervalSecondsRejectsZeroAndNegative() {
        assertThat(DspConfigKeys.ISSUER_TRUST_REFRESH_INTERVAL_SECONDS.validate(0L).valid()).isFalse();
        assertThat(DspConfigKeys.ISSUER_TRUST_REFRESH_INTERVAL_SECONDS.validate(-1L).valid()).isFalse();
        assertThat(DspConfigKeys.ISSUER_TRUST_REFRESH_INTERVAL_SECONDS.validate(1L).valid()).isTrue();
    }

    @Test
    void issuerTrustRefreshIntervalSecondsAcceptsPositiveValueAndDefault() {
        assertThat(DspConfigKeys.ISSUER_TRUST_REFRESH_INTERVAL_SECONDS.validate(5L).valid()).isTrue();
        assertThat(DspConfigKeys.ISSUER_TRUST_REFRESH_INTERVAL_SECONDS.convertedDefaultValue()).isEqualTo(60L);
    }

    @Test
    void didResolutionFetchTimeoutRejectsZeroAndNegativeAndDefaultsToThreeSeconds() {
        assertThat(DspConfigKeys.DID_RESOLUTION_FETCH_TIMEOUT_MS.validate(0L).valid()).isFalse();
        assertThat(DspConfigKeys.DID_RESOLUTION_FETCH_TIMEOUT_MS.validate(-1L).valid()).isFalse();
        assertThat(DspConfigKeys.DID_RESOLUTION_FETCH_TIMEOUT_MS.validate(1L).valid()).isTrue();
        assertThat(DspConfigKeys.DID_RESOLUTION_FETCH_TIMEOUT_MS.convertedDefaultValue()).isEqualTo(3000L);
    }

    @Test
    void taskPollWorkersRejectsLessThanOneAndAcceptsDefault() {
        assertThat(DspConfigKeys.TASK_POLL_WORKERS.validate(0).valid()).isFalse();
        assertThat(DspConfigKeys.TASK_POLL_WORKERS.validate(-1).valid()).isFalse();
        assertThat(DspConfigKeys.TASK_POLL_WORKERS.validate(1).valid()).isTrue();
        assertThat(DspConfigKeys.TASK_POLL_WORKERS.convertedDefaultValue()).isEqualTo(4);
    }

    @Test
    void outboundHttpMaxRequestsPerHostRejectsLessThanOneAndAcceptsDefault() {
        assertThat(DspConfigKeys.OUTBOUND_HTTP_MAX_REQUESTS_PER_HOST.validate(0).valid()).isFalse();
        assertThat(DspConfigKeys.OUTBOUND_HTTP_MAX_REQUESTS_PER_HOST.validate(-1).valid()).isFalse();
        assertThat(DspConfigKeys.OUTBOUND_HTTP_MAX_REQUESTS_PER_HOST.validate(1).valid()).isTrue();
        assertThat(DspConfigKeys.OUTBOUND_HTTP_MAX_REQUESTS_PER_HOST.convertedDefaultValue()).isEqualTo(8);
    }

    @Test
    void controlPlanePoolMaxTotalRejectsLessThanOneAndAcceptsDefault() {
        assertThat(DspConfigKeys.CONTROL_PLANE_POOL_MAX_TOTAL.validate(0).valid()).isFalse();
        assertThat(DspConfigKeys.CONTROL_PLANE_POOL_MAX_TOTAL.validate(-1).valid()).isFalse();
        assertThat(DspConfigKeys.CONTROL_PLANE_POOL_MAX_TOTAL.validate(1).valid()).isTrue();
        assertThat(DspConfigKeys.CONTROL_PLANE_POOL_MAX_TOTAL.convertedDefaultValue()).isEqualTo(16);
    }

    @Test
    void controlPlanePoolMaxIdleRejectsLessThanOneAndAcceptsDefault() {
        assertThat(DspConfigKeys.CONTROL_PLANE_POOL_MAX_IDLE.validate(0).valid()).isFalse();
        assertThat(DspConfigKeys.CONTROL_PLANE_POOL_MAX_IDLE.validate(-1).valid()).isFalse();
        assertThat(DspConfigKeys.CONTROL_PLANE_POOL_MAX_IDLE.validate(1).valid()).isTrue();
        assertThat(DspConfigKeys.CONTROL_PLANE_POOL_MAX_IDLE.convertedDefaultValue()).isEqualTo(16);
    }

    @Test
    void serverProxyUrlRejectsBlank() {
        assertThat(DspConfigKeys.BUILTIN_SERVICES_SERVER_PROXY_URL.validate("").valid()).isFalse();
        assertThat(DspConfigKeys.BUILTIN_SERVICES_SERVER_PROXY_URL.validate(null).valid()).isFalse();
        assertThat(DspConfigKeys.BUILTIN_SERVICES_SERVER_PROXY_URL.validate("http://localhost:5500/").valid()).isTrue();
    }

    @Test
    void everyKeyIsPublishedToFramework() {
        assertThat(DspConfigKeys.instance().keys())
                .allSatisfy(key -> assertThat(key.publishedToFramework())
                        .as("%s must be published to framework", key.key()).isTrue());
    }

    @Test
    void onlyTheIssuerDataKeysAreExposedInTheUi() {
        assertThat(DspConfigKeys.instance().keys())
                .filteredOn(ConfigKey::exposedInUi)
                .extracting(ConfigKey::key)
                .containsExactlyInAnyOrderElementsOf(ISSUER_DATA_KEYS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("issuerDataKeysAndTaskPollWorkers")
    void aBlankOrUnparsableStoredRowStopsTheConfigBuildNamingTheKey(String key) {
        for (var stored : new String[] {"", "not-a-value"}) {
            assertThatThrownBy(() -> config(key, stored))
                    .as("%s=%s", key, stored)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(key);
        }
    }

    @Test
    void aTaskPollWorkersRowOfZeroStopsTheConfigBuild() {
        assertThatThrownBy(() -> config("xroad.dsp.task-poll.workers", "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xroad.dsp.task-poll.workers");
    }

    @Test
    void statuslistCacheSizeAcceptsZeroAndRefusesNegative() {
        assertThat(DspConfigKeys.ISSUER_DATA_CACHE_STATUSLIST_SIZE.validate(0).valid()).isTrue();
        assertThat(DspConfigKeys.ISSUER_DATA_CACHE_STATUSLIST_SIZE.validate(-1).valid()).isFalse();
    }

    @Test
    void statuslistDownloadSizeDefaultsTo10000AndRefusesZero() {
        assertThat(DspConfigKeys.ISSUER_DATA_CACHE_STATUSLIST_DOWNLOAD_SIZE.defaultValue()).isEqualTo("10000");
        assertThat(DspConfigKeys.ISSUER_DATA_CACHE_STATUSLIST_DOWNLOAD_SIZE.validate(1).valid()).isTrue();
        assertThat(DspConfigKeys.ISSUER_DATA_CACHE_STATUSLIST_DOWNLOAD_SIZE.validate(0).valid()).isFalse();
        assertThat(DspConfigKeys.ISSUER_DATA_CACHE_STATUSLIST_DOWNLOAD_SIZE.validate(-1).valid()).isFalse();
    }

    @Test
    void didDownloadSizeDefaultsTo10000AndRefusesZero() {
        assertThat(DspConfigKeys.ISSUER_DATA_CACHE_DID_DOWNLOAD_SIZE.defaultValue()).isEqualTo("10000");
        assertThat(DspConfigKeys.ISSUER_DATA_CACHE_DID_DOWNLOAD_SIZE.validate(1).valid()).isTrue();
        assertThat(DspConfigKeys.ISSUER_DATA_CACHE_DID_DOWNLOAD_SIZE.validate(0).valid()).isFalse();
        assertThat(DspConfigKeys.ISSUER_DATA_CACHE_DID_DOWNLOAD_SIZE.validate(-1).valid()).isFalse();
    }

    @Test
    void downloadWaitAcceptsPositiveAndRefusesZeroAndNegative() {
        assertThat(DspConfigKeys.ISSUER_DATA_DOWNLOAD_WAIT.validate(Duration.ofMillis(1)).valid()).isTrue();
        assertThat(DspConfigKeys.ISSUER_DATA_DOWNLOAD_WAIT.validate(Duration.ofSeconds(30)).valid()).isTrue();
        assertThat(DspConfigKeys.ISSUER_DATA_DOWNLOAD_WAIT.validate(Duration.ZERO).valid()).isFalse();
        assertThat(DspConfigKeys.ISSUER_DATA_DOWNLOAD_WAIT.validate(Duration.ofMillis(-1)).valid()).isFalse();
    }

    @Test
    void downloadRetryDelaysAcceptPositiveWholeNumbersAndRefuseTheRest() {
        var delays = DspConfigKeys.ISSUER_DATA_DOWNLOAD_RETRY_SECONDS;
        assertThat(delays.validate(new String[] {"5", "5", "10"}).valid()).isTrue();
        assertThat(delays.validate(new String[] {"0"}).valid()).isFalse();
        assertThat(delays.validate(new String[] {"-5"}).valid()).isFalse();
        assertThat(delays.validate(new String[] {"3000000000"}).valid()).isFalse();
        assertThat(delays.validate(new String[] {"5", "0", "10"}).valid()).isFalse();
        assertThat(delays.validate(new String[] {"10", "-5"}).valid()).isFalse();
    }

    @Test
    void downloadExpireAfterRefusesZero() {
        assertThat(DspConfigKeys.ISSUER_DATA_DOWNLOAD_EXPIRE_AFTER.validate(Duration.ofMinutes(10)).valid()).isTrue();
        assertThat(DspConfigKeys.ISSUER_DATA_DOWNLOAD_EXPIRE_AFTER.validate(Duration.ZERO).valid()).isFalse();
    }

    @Test
    void downloadTimeoutRefusesZeroAndNegativeAndAcceptsPositive() {
        var timeout = DspConfigKeys.ISSUER_DATA_DOWNLOAD_TIMEOUT;
        assertThat(timeout.validate(Duration.ofSeconds(30)).valid()).isTrue();
        assertThat(timeout.validate(Duration.ZERO).valid()).isFalse();
        assertThat(timeout.validate(Duration.ofSeconds(-1)).valid()).isFalse();
    }

    @Test
    void cleanupIntervalAcceptsFiveMinutesAndMoreAndRefusesLess() {
        assertThat(DspConfigKeys.ISSUER_DATA_CLEANUP_INTERVAL.validate(Duration.ofMinutes(5)).valid()).isTrue();
        assertThat(DspConfigKeys.ISSUER_DATA_CLEANUP_INTERVAL.validate(Duration.ofMinutes(10)).valid()).isTrue();
        assertThat(DspConfigKeys.ISSUER_DATA_CLEANUP_INTERVAL.validate(Duration.parse("PT4M59S")).valid()).isFalse();
        assertThat(DspConfigKeys.ISSUER_DATA_CLEANUP_INTERVAL.validate(Duration.ZERO).valid()).isFalse();
        assertThat(DspConfigKeys.ISSUER_DATA_CLEANUP_INTERVAL.validate(Duration.ofSeconds(-1)).valid()).isFalse();
    }

    @Test
    void keysCarryTheExpectedDottedPaths() {
        assertThat(DspConfigKeys.instance().keys())
                .extracting(ConfigKey::key)
                .containsExactlyInAnyOrderElementsOf(Stream.concat(OTHER_KEYS.stream(), ISSUER_DATA_KEYS.stream()).toList());
    }

    @Test
    void rootPathIsTheDspScope() {
        assertThat(DspConfigKeys.instance().rootPath()).isEqualTo("xroad.dsp");
    }

    private static Stream<String> issuerDataKeysAndTaskPollWorkers() {
        return Stream.concat(ISSUER_DATA_KEYS.stream(), Stream.of("xroad.dsp.task-poll.workers"));
    }

    private static XRoadConfig config(String key, String stored) {
        return XRoadConfigBuilder.create()
                .register(DspConfigKeys.instance())
                .overrides(Map.of(key, stored))
                .build();
    }
}
