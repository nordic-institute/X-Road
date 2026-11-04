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
import org.niis.xroad.common.properties.HostIdentity;
import org.niis.xroad.common.properties.config.Source;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CsTlsIdentityKeysTest {

    private static final String ADMIN = "xroad.admin-service.tls.certificate-provisioning.";
    private static final String MANAGEMENT = "xroad.management-service.tls.certificate-provisioning.";

    private final HostIdentity host = HostIdentity.local();

    @Test
    void adminServiceIdentityDefaultsToTheHostIdentityWhenNoRowExists() {
        var config = XRoadConfigBuilder.create().register(CsAdminServiceConfigKeys.instance()).build();

        var commonName = config.get(CsAdminServiceConfigKeys.TLS_CERT_PROVISIONING_COMMON_NAME);
        assertThat(commonName.source()).isEqualTo(Source.DEFAULT);
        assertThat(commonName.value()).isEqualTo(expectedCommonName());
        assertThat(config.get(CsAdminServiceConfigKeys.TLS_CERT_PROVISIONING_ALT_NAMES).value())
                .containsExactlyElementsOf(host.dnsNames());
        assertThat(config.get(CsAdminServiceConfigKeys.TLS_CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES).value())
                .containsExactlyElementsOf(host.ipAddresses());
    }

    @Test
    void managementServiceIdentityDefaultsToTheHostIdentityWhenNoRowExists() {
        var config = XRoadConfigBuilder.create().register(CsManagementServiceConfigKeys.instance()).build();

        assertThat(config.get(CsManagementServiceConfigKeys.TLS_CERT_PROVISIONING_COMMON_NAME).value())
                .isEqualTo(expectedCommonName());
        assertThat(config.get(CsManagementServiceConfigKeys.TLS_CERT_PROVISIONING_ALT_NAMES).value())
                .containsExactlyElementsOf(host.dnsNames());
        assertThat(config.get(CsManagementServiceConfigKeys.TLS_CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES).value())
                .containsExactlyElementsOf(host.ipAddresses());
    }

    @Test
    void adminServiceRowsOverrideTheHostDefaults() {
        var config = XRoadConfigBuilder.create().register(CsAdminServiceConfigKeys.instance())
                .overrides(Map.of(
                        ADMIN + "common-name", "cs.example.org",
                        ADMIN + "alt-names", "cs.example.org,cs-lb",
                        ADMIN + "ip-subject-alt-names", "192.0.2.10"))
                .build();

        var commonName = config.get(CsAdminServiceConfigKeys.TLS_CERT_PROVISIONING_COMMON_NAME);
        assertThat(commonName.source()).isEqualTo(Source.DB);
        assertThat(commonName.value()).isEqualTo("cs.example.org");
        assertThat(config.get(CsAdminServiceConfigKeys.TLS_CERT_PROVISIONING_ALT_NAMES).value())
                .containsExactly("cs.example.org", "cs-lb");
        assertThat(config.get(CsAdminServiceConfigKeys.TLS_CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES).value())
                .containsExactly("192.0.2.10");
    }

    @Test
    void managementServiceRowsOverrideTheHostDefaults() {
        var config = XRoadConfigBuilder.create().register(CsManagementServiceConfigKeys.instance())
                .overrides(Map.of(
                        MANAGEMENT + "common-name", "cs.example.org",
                        MANAGEMENT + "alt-names", "cs.example.org,cs-lb",
                        MANAGEMENT + "ip-subject-alt-names", "192.0.2.10"))
                .build();

        assertThat(config.get(CsManagementServiceConfigKeys.TLS_CERT_PROVISIONING_COMMON_NAME).value())
                .isEqualTo("cs.example.org");
        assertThat(config.get(CsManagementServiceConfigKeys.TLS_CERT_PROVISIONING_ALT_NAMES).value())
                .containsExactly("cs.example.org", "cs-lb");
        assertThat(config.get(CsManagementServiceConfigKeys.TLS_CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES).value())
                .containsExactly("192.0.2.10");
    }

    private String expectedCommonName() {
        var xroadHost = System.getenv("XROAD_HOST");
        return xroadHost == null || xroadHost.isBlank() ? host.commonName() : xroadHost;
    }
}
