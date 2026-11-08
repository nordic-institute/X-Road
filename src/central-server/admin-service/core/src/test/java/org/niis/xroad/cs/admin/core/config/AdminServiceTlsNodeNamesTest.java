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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.properties.EnvProperties;
import org.niis.xroad.common.properties.HostIdentity;
import org.niis.xroad.common.properties.config.XRoadConfigOverrides;
import org.niis.xroad.common.properties.dbsource.NodeConfigTestTable;
import org.niis.xroad.common.properties.dbsource.NodeConfigTestTable.Row;
import org.niis.xroad.common.vault.config.CertificateProvisioningProperties;
import org.springframework.mock.env.MockEnvironment;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The admin and management TLS certificate names are read through the application {@code XRoadConfig} bean, so
 * each node can store its own values in {@code configuration_properties}: a row of the node, else a shared row,
 * else the host default.
 */
@ExtendWith(SystemStubsExtension.class)
class AdminServiceTlsNodeNamesTest {

    private static final String ADMIN = "xroad.admin-service.tls.certificate-provisioning.";
    private static final String MANAGEMENT = "xroad.management-service.tls.certificate-provisioning.";
    private static final String NODE_1 = "node_1";
    private static final String NODE_2 = "node_2";

    private static Map<String, String> databaseEnvironment;

    @SystemStub
    private final EnvironmentVariables variables = new EnvironmentVariables();

    @BeforeAll
    static void createTable() throws SQLException {
        databaseEnvironment = NodeConfigTestTable.create("admintlsnodenames", List.of(
                new Row(ADMIN + "common-name", "ui.shared.example.org", null),
                new Row(ADMIN + "common-name", "ui-1.example.org", NODE_1),
                new Row(ADMIN + "alt-names", "ui.shared.example.org,ui-lb", null),
                new Row(ADMIN + "ip-subject-alt-names", "192.0.2.22", NODE_2),
                new Row(MANAGEMENT + "common-name", "mgmt-1.example.org", NODE_1),
                new Row(MANAGEMENT + "alt-names", "mgmt.shared.example.org,mgmt-lb", null),
                new Row(MANAGEMENT + "ip-subject-alt-names", "192.0.2.32", NODE_2),
                new Row(ADMIN + "ttl", "PT48H", null),
                new Row(ADMIN + "issuance-role-name", "admin-role", null),
                new Row(ADMIN + "secret-store-pki-path", "admin-pki", null),
                new Row(MANAGEMENT + "ttl", "PT72H", null),
                new Row(MANAGEMENT + "issuance-role-name", "management-role", null),
                new Row(MANAGEMENT + "secret-store-pki-path", "management-pki", null)));
    }

    @BeforeEach
    void pointAtTheDatabase() {
        databaseEnvironment.forEach(variables::set);
    }

    @Test
    void node1ResolvesItsOwnRowThenTheSharedRowThenTheHostDefault() {
        var properties = tlsProperties(NODE_1);

        assertNames(properties.getCertificateProvisioning(),
                "ui-1.example.org", List.of("ui.shared.example.org", "ui-lb"), defaultIpAddresses());
        assertNames(properties.getManagementServiceCertificateProvisioning(),
                "mgmt-1.example.org", List.of("mgmt.shared.example.org", "mgmt-lb"), defaultIpAddresses());
    }

    @Test
    void node2ResolvesTheSharedRowOrTheHostDefaultWhereOnlyNode1HasARow() {
        var properties = tlsProperties(NODE_2);

        assertNames(properties.getCertificateProvisioning(),
                "ui.shared.example.org", List.of("ui.shared.example.org", "ui-lb"), List.of("192.0.2.22"));
        assertNames(properties.getManagementServiceCertificateProvisioning(),
                defaultCommonName(), List.of("mgmt.shared.example.org", "mgmt-lb"), List.of("192.0.2.32"));
    }

    @Test
    void nodeWithoutRowsResolvesTheSharedRowsAndTheHostDefaults() {
        var properties = tlsProperties("node_3");

        assertNames(properties.getCertificateProvisioning(),
                "ui.shared.example.org", List.of("ui.shared.example.org", "ui-lb"), defaultIpAddresses());
        assertNames(properties.getManagementServiceCertificateProvisioning(),
                defaultCommonName(), List.of("mgmt.shared.example.org", "mgmt-lb"), defaultIpAddresses());
        assertSettings(properties.getCertificateProvisioning(), Duration.ofHours(48), "admin-role", "admin-pki");
        assertSettings(properties.getManagementServiceCertificateProvisioning(),
                Duration.ofHours(72), "management-role", "management-pki");
    }

    private static AdminServiceTlsProperties tlsProperties(String nodeName) {
        var environment = new MockEnvironment()
                .withProperty("spring.application.name", "admin-service")
                .withProperty("xroad.admin-service.ha-node-name", nodeName);
        var configuration = new XRoadConfigBeanConfiguration();
        return configuration.adminServiceTlsProperties(
                configuration.xRoadConfig("admin-service", environment, XRoadConfigOverrides.none()));
    }

    private static void assertNames(CertificateProvisioningProperties provisioning, String commonName,
                                    List<String> altNames, List<String> ipSubjectAltNames) {
        assertThat(provisioning.commonName()).isEqualTo(commonName);
        assertThat(provisioning.altNames()).containsExactlyElementsOf(altNames);
        assertThat(provisioning.ipSubjectAltNames()).containsExactlyElementsOf(ipSubjectAltNames);
    }

    private static void assertSettings(CertificateProvisioningProperties provisioning, Duration ttl,
                                       String issuanceRoleName, String secretStorePkiPath) {
        assertThat(provisioning.ttl()).isEqualTo(ttl);
        assertThat(provisioning.issuanceRoleName()).isEqualTo(issuanceRoleName);
        assertThat(provisioning.secretStorePkiPath()).isEqualTo(secretStorePkiPath);
    }

    private static String defaultCommonName() {
        return EnvProperties.xroadHost(HostIdentity.local().commonName());
    }

    private static List<String> defaultIpAddresses() {
        return HostIdentity.local().ipAddresses().stream().filter(value -> !value.isBlank()).toList();
    }
}
