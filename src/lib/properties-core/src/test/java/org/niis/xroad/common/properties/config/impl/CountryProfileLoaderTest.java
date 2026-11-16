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

package org.niis.xroad.common.properties.config.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountryProfileLoaderTest {

    @Test
    void listsRenderAsCommaSeparatedStrings() {
        var values = CountryProfileLoader.load("FI", null);

        assertThat(values).containsEntry("xroad.proxy.client-proxy.client-tls-protocols", "TLSv1.2");
        assertThat(values.get("xroad.proxy.client-proxy.client-tls-ciphers"))
                .startsWith("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,")
                .endsWith(",TLS_DHE_RSA_WITH_AES_256_CBC_SHA256")
                .doesNotContain("[", " ");
    }

    @Test
    void nestedKeysFlattenToDottedDslKeys() {
        var values = CountryProfileLoader.load("FI", null);

        assertThat(values)
                .containsEntry("xroad.signer.key-length", "3072")
                .containsEntry("xroad.proxy.server.min-supported-client-version", "7.6.0")
                .containsEntry("xroad.proxy.message-log.timestamper.acceptable-timestamp-failure-period", "172800")
                .containsEntry("xroad.proxy-ui-api.acme-challenge-port-enabled", "true")
                .doesNotContainKey("xroad")
                .allSatisfy((key, _) -> assertThat(key).startsWith("xroad."));
    }

    @Test
    void metadataBlockIsNotLoaded() {
        assertThat(CountryProfileLoader.load("FI", null).keySet()).noneMatch(key -> key.startsWith("metadata"));
    }
    @Test
    void excludeListRemovesNamedKeysAndTrimsEntries() {
        var all = CountryProfileLoader.load("FI", null);
        var values = CountryProfileLoader.load("FI",
                " xroad.signer.key-length , xroad.proxy-ui-api.acme-challenge-port-enabled,, not.a.key ");
        assertThat(values).hasSize(all.size() - 2)
                .doesNotContainKeys("xroad.signer.key-length", "xroad.proxy-ui-api.acme-challenge-port-enabled");
    }
    @Test
    void unknownProfileNamesValueAndAvailableProfiles() {
        assertThatThrownBy(() -> CountryProfileLoader.load("XX", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'XX'")
                .hasMessageContaining("available profiles: FI");
    }
}
