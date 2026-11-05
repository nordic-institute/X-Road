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
package org.niis.xroad.cs.admin.core.config;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CsAdminServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.CsManagementServiceConfigKeys;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminServiceTlsPropertiesTest {

    private static final String ADMIN = "xroad.admin-service.tls.certificate-provisioning.";
    private static final String MANAGEMENT = "xroad.management-service.tls.certificate-provisioning.";

    private final AdminServiceTlsProperties properties = new AdminServiceTlsProperties(XRoadConfigBuilder.create()
            .register(CsAdminServiceConfigKeys.instance())
            .register(CsManagementServiceConfigKeys.instance())
            .overrides(Map.ofEntries(
                    Map.entry(ADMIN + "common-name", "ui.example.org"),
                    Map.entry(ADMIN + "alt-names", "ui.example.org,ui-lb"),
                    Map.entry(ADMIN + "ip-subject-alt-names", "192.0.2.10"),
                    Map.entry(ADMIN + "ttl", "PT48H"),
                    Map.entry(ADMIN + "issuance-role-name", "admin-role"),
                    Map.entry(ADMIN + "secret-store-pki-path", "admin-pki"),
                    Map.entry(MANAGEMENT + "common-name", "mgmt.example.org"),
                    Map.entry(MANAGEMENT + "alt-names", "mgmt.example.org,mgmt-lb"),
                    Map.entry(MANAGEMENT + "ip-subject-alt-names", "192.0.2.20"),
                    Map.entry(MANAGEMENT + "ttl", "PT72H"),
                    Map.entry(MANAGEMENT + "issuance-role-name", "management-role"),
                    Map.entry(MANAGEMENT + "secret-store-pki-path", "management-pki")))
            .build());

    @Test
    void managementServiceProvisioningUsesTheManagementServiceRows() {
        var provisioning = properties.getManagementServiceCertificateProvisioning();

        assertThat(provisioning.commonName()).isEqualTo("mgmt.example.org");
        assertThat(provisioning.altNames()).containsExactly("mgmt.example.org", "mgmt-lb");
        assertThat(provisioning.ipSubjectAltNames()).containsExactly("192.0.2.20");
        assertThat(provisioning.ttl()).isEqualTo(Duration.ofHours(72));
        assertThat(provisioning.issuanceRoleName()).isEqualTo("management-role");
        assertThat(provisioning.secretStorePkiPath()).isEqualTo("management-pki");
    }

    @Test
    void adminServiceProvisioningKeepsUsingTheAdminServiceRows() {
        var provisioning = properties.getCertificateProvisioning();

        assertThat(provisioning.commonName()).isEqualTo("ui.example.org");
        assertThat(provisioning.altNames()).containsExactly("ui.example.org", "ui-lb");
        assertThat(provisioning.ipSubjectAltNames()).containsExactly("192.0.2.10");
        assertThat(provisioning.ttl()).isEqualTo(Duration.ofHours(48));
        assertThat(provisioning.issuanceRoleName()).isEqualTo("admin-role");
        assertThat(provisioning.secretStorePkiPath()).isEqualTo("admin-pki");
    }
}
