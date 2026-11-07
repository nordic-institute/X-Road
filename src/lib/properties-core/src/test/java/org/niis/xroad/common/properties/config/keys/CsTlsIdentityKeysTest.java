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
import org.niis.xroad.common.properties.EnvProperties;
import org.niis.xroad.common.properties.HostIdentity;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Source;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class CsTlsIdentityKeysTest {

    private final HostIdentity host = HostIdentity.local();

    @Test
    void adminAndManagementIdentityDefaultToTheHostIdentityWhenNoRowExists() {
        assertDefaultsToTheHostIdentity(CsAdminServiceConfigKeys.instance(),
                CsAdminServiceConfigKeys.TLS_CERT_PROVISIONING_COMMON_NAME,
                CsAdminServiceConfigKeys.TLS_CERT_PROVISIONING_ALT_NAMES,
                CsAdminServiceConfigKeys.TLS_CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES);
        assertDefaultsToTheHostIdentity(CsManagementServiceConfigKeys.instance(),
                CsManagementServiceConfigKeys.TLS_CERT_PROVISIONING_COMMON_NAME,
                CsManagementServiceConfigKeys.TLS_CERT_PROVISIONING_ALT_NAMES,
                CsManagementServiceConfigKeys.TLS_CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES);
    }

    private void assertDefaultsToTheHostIdentity(ConfigKeyProvider provider, ConfigKey<String> commonNameKey,
                                                 ConfigKey<String[]> altNamesKey, ConfigKey<String[]> ipNamesKey) {
        var config = XRoadConfigBuilder.create().register(provider).build();

        var commonName = config.get(commonNameKey);
        assertThat(commonName.source()).isEqualTo(Source.DEFAULT);
        assertThat(commonName.value()).isEqualTo(EnvProperties.xroadHost(host.commonName()));
        assertThat(config.get(altNamesKey).value()).containsExactlyElementsOf(host.dnsNames());
        assertThat(config.get(ipNamesKey).value()).containsExactlyElementsOf(host.ipAddresses());
    }
}
