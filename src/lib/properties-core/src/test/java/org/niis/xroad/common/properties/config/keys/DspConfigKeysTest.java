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

import static org.assertj.core.api.Assertions.assertThat;

class DspConfigKeysTest {

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
    void serverProxyUrlRejectsBlank() {
        assertThat(DspConfigKeys.BUILTIN_SERVICES_SERVER_PROXY_URL.validate("").valid()).isFalse();
        assertThat(DspConfigKeys.BUILTIN_SERVICES_SERVER_PROXY_URL.validate(null).valid()).isFalse();
        assertThat(DspConfigKeys.BUILTIN_SERVICES_SERVER_PROXY_URL.validate("http://localhost:5500/").valid()).isTrue();
    }

    @Test
    void everyKeyIsPublishedToFrameworkAndHiddenFromUi() {
        assertThat(DspConfigKeys.instance().keys())
                .allSatisfy(key -> {
                    assertThat(key.publishedToFramework()).as("%s must be published to framework", key.key()).isTrue();
                    assertThat(key.exposedInUi()).as("%s must not be exposed in UI", key.key()).isFalse();
                });
    }

    @Test
    void keysCarryTheExpectedDottedPaths() {
        assertThat(DspConfigKeys.instance().keys())
                .extracting(key -> key.key())
                .containsExactlyInAnyOrder(
                        "xroad.dsp.participant-context-id",
                        "xroad.dsp.management-participant-context-id",
                        "xroad.dsp.catalog.cache.enabled",
                        "xroad.dsp.catalog.cache.ttl-seconds",
                        "xroad.dsp.catalog.cache.find-by-id-max-size",
                        "xroad.dsp.builtin-services.proxyMonitor.enabled",
                        "xroad.dsp.builtin-services.opMonitor.enabled",
                        "xroad.dsp.builtin-services.metaservices.enabled",
                        "xroad.dsp.builtin-services.server-proxy-url");
    }

    @Test
    void rootPathIsTheDspScope() {
        assertThat(DspConfigKeys.instance().rootPath()).isEqualTo("xroad.dsp");
    }
}
