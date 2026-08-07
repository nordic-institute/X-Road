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

package org.niis.xroad.common.properties.config.quarkus;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.ConfigKeyProviders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XRoadDefaultsConfigSourceFactoryTest {

    private final List<ConfigKeyProvider> all = ConfigKeyProviders.allProviders();

    @Test
    void publishesEveryProviderWhenScopesUnset() {
        assertThat(XRoadDefaultsConfigSourceFactory.selectProviders(all, null)).isEqualTo(all);
        assertThat(XRoadDefaultsConfigSourceFactory.selectProviders(all, "   ")).isEqualTo(all);
    }

    @Test
    void publishesOnlyListedScopes() {
        var selected = XRoadDefaultsConfigSourceFactory.selectProviders(all, "xroad.common");

        assertThat(selected).containsExactly(CommonConfigKeys.instance());
    }

    @Test
    void ignoresBlankAndUnknownScopes() {
        var selected = XRoadDefaultsConfigSourceFactory.selectProviders(all, " xroad.common , , xroad.unknown ");

        assertThat(selected).containsExactly(CommonConfigKeys.instance());
    }

    @Test
    void selectsBareKeysOfProvidersSpanningSeveralRoots() {
        var selected = XRoadDefaultsConfigSourceFactory.selectProviders(all, "xroad.acme");

        assertThat(selected).hasSize(1);
        assertThat(selected.getFirst().keys())
                .extracting(ConfigKey::key)
                .containsExactly("xroad.acme");
    }
}
