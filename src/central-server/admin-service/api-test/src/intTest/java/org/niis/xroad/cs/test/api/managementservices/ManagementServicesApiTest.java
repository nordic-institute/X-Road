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
package org.niis.xroad.cs.test.api.managementservices;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.ResourceLocks;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.ManagementServicesAdminClient;

import java.io.IOException;

import static org.hamcrest.Matchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for Central Server management services configuration API.
 */
@SuppressWarnings("checkstyle:magicnumber")
class ManagementServicesApiTest extends CsApiTest {

    private static final String VAULT_ISSUE_MOCK_TEMPLATE = """
            {
              "httpRequest": {"method": "POST", "path": "/v1/xrd-pki/issue/xrd-internal"},
              "httpResponse": {
                "statusCode": 200,
                "headers": {"Content-Type": ["application/json"]},
                "body": {"type": "JSON", "json": %s}
              }
            }
            """;

    private static final String VAULT_SECRET_POST_MOCK = """
            {
              "httpRequest": {"method": "POST", "path": "/v1/xrd-secret/tls/management-service"},
              "httpResponse": {"statusCode": 200, "headers": {"Content-Type": ["application/json"]}}
            }
            """;

    private static final String VAULT_SECRET_GET_MOCK_TEMPLATE = """
            {
              "httpRequest": {"method": "GET", "path": "/v1/xrd-secret/tls/management-service"},
              "httpResponse": {
                "statusCode": 200,
                "headers": {"Content-Type": ["application/json"]},
                "body": {"type": "JSON", "json": %s}
              }
            }
            """;

    @Test
    @ResourceLocks({
            @ResourceLock(value = "management-services-config", mode = ResourceAccessMode.READ_WRITE),
            @ResourceLock(value = "server-address", mode = ResourceAccessMode.READ)
    })
    void updateManagementServicesConfigurationSuccessful(CsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var soSession = seeder.newSecurityOfficerSession();
        var mgmt = new ManagementServicesAdminClient(soSession);
        var ns = "mgmtsvc01";

        var memberClass = given("member class seeded", () -> seeder.seedMemberClass(session, ns));
        var memberId = given("member seeded", () -> seeder.seedMember(session, ns, memberClass));
        given("subsystem seeded", () -> seeder.seedSubsystem(session, ns, memberId));
        given("security server seeded and approved", () -> seeder.seedSecurityServer(session, ns, memberId));

        var subsystemId = "%s:%s".formatted(memberId, "subsys" + ns);

        when("management services provider id is set to the subsystem", () ->
                mgmt.updateConfiguration(subsystemId)
                        .statusCode(200)
                        .body("security_server_owners_global_group_code", equalTo("security-server-owners"))
                        .body("service_provider_id", equalTo("SUBSYSTEM:" + subsystemId))
                        .body("service_provider_name", equalTo("Seeded member " + ns))
                        .body("services_address", equalTo("https://cs:4002/managementservice/manage/"))
                        .body("wsdl_address", equalTo("http://cs/managementservices.wsdl")));

        then("configuration can be retrieved and reflects the update", () ->
                mgmt.getConfiguration()
                        .statusCode(200)
                        .body("security_server_owners_global_group_code", equalTo("security-server-owners"))
                        .body("service_provider_id", equalTo("SUBSYSTEM:" + subsystemId))
                        .body("service_provider_name", equalTo("Seeded member " + ns))
                        .body("services_address", equalTo("https://cs:4002/managementservice/manage/"))
                        .body("wsdl_address", equalTo("http://cs/managementservices.wsdl")));
    }

    @Test
    @ResourceLock(value = "management-services-config", mode = ResourceAccessMode.READ_WRITE)
    void updateManagementServicesConfigurationViaDedicatedEndpoint(CsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var soSession = seeder.newSecurityOfficerSession();
        var mgmt = new ManagementServicesAdminClient(soSession);
        var ns = "mgmtsvc02";

        var memberClass = given("member class seeded", () -> seeder.seedMemberClass(session, ns));
        var memberId = given("member seeded", () -> seeder.seedMember(session, ns, memberClass));
        given("subsystem seeded", () -> seeder.seedSubsystem(session, ns, memberId));
        var serverId = given("security server seeded and approved", () -> seeder.seedSecurityServer(session, ns, memberId));

        var subsystemId = "%s:%s".formatted(memberId, "subsys" + ns);
        var serverRef = "SERVER:" + serverId;

        given("management services provider id is set to the subsystem", () ->
                mgmt.updateConfiguration(subsystemId)
                        .statusCode(200));

        when("security server is registered as management service provider", () ->
                mgmt.registerProvider(serverId)
                        .statusCode(200)
                        .body("security_server_id", equalTo(serverRef))
                        .body("service_provider_id", equalTo("SUBSYSTEM:" + subsystemId)));
    }

    @Test
    void updateManagementServicesConfigurationForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) {
        var mgmt = new ManagementServicesAdminClient(seeder.newRegistrationOfficerSession());

        when("REGISTRATION_OFFICER attempts to update config returns 403", () ->
                mgmt.updateConfiguration("CS:E2E:member-for-management:Management")
                        .statusCode(403));
    }

    @Test
    void updateManagementServicesConfigurationFailsMissingSubsystem(CsBaselineSeeder seeder) {
        var mgmt = new ManagementServicesAdminClient(seeder.newSecurityOfficerSession());

        when("setting a non-existent subsystem returns 404 subsystem_not_found", () ->
                mgmt.updateConfiguration("CS:E2E:member-for-management:Random")
                        .statusCode(404)
                        .body("error.code", equalTo("subsystem_not_found")));
    }

    @Test
    void updateManagementServicesConfigurationFailsNotASubsystem(CsBaselineSeeder seeder) {
        var mgmt = new ManagementServicesAdminClient(seeder.newSecurityOfficerSession());

        when("setting a member id (not subsystem) returns 400 invalid_service_provider_id", () ->
                mgmt.updateConfiguration("CS:E2E:member-for-management")
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_service_provider_id")));
    }

    @Test
    @ResourceLocks({
            @ResourceLock(value = "management-services-config", mode = ResourceAccessMode.READ),
            @ResourceLock(value = "server-address", mode = ResourceAccessMode.READ)
    })
    void getManagementServicesConfigurationSuccessful(CsBaselineSeeder seeder) {
        var mgmt = new ManagementServicesAdminClient(seeder.newSecurityOfficerSession());

        when("management services configuration is retrieved", () ->
                mgmt.getConfiguration()
                        .statusCode(200)
                        .body("security_server_owners_global_group_code", equalTo("security-server-owners"))
                        .body("services_address", equalTo("https://cs:4002/managementservice/manage/"))
                        .body("wsdl_address", equalTo("http://cs/managementservices.wsdl")));
    }

    @Test
    void managementServiceTlsKeyAndCertificateCreatedForPrivilegedUser(CsBaselineSeeder seeder) {
        var mgmt = new ManagementServicesAdminClient(seeder.newSecurityOfficerSession());

        given("vault mocks registered for TLS credential issuance", () -> {
            seeder.mockExpectation(buildVaultIssueMock());
            seeder.mockExpectation(VAULT_SECRET_POST_MOCK);
            seeder.mockExpectation(buildVaultGetMock());
        });

        try {
            then("generating TLS key and certificate returns 201", () ->
                    mgmt.generateKeyAndCertificate()
                            .statusCode(201));
        } finally {
            seeder.clearMockExpectations("/v1/xrd-pki/issue/xrd-internal");
            seeder.clearMockExpectations("/v1/xrd-secret/tls/management-service");
        }
    }

    @Test
    void getManagementServicesConfigurationForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) {
        var mgmt = new ManagementServicesAdminClient(seeder.newRegistrationOfficerSession());

        when("REGISTRATION_OFFICER attempts to GET config returns 403", () ->
                mgmt.getConfiguration()
                        .statusCode(403));
    }

    @Test
    void managementServiceTlsCertificateRetrievedForPrivilegedUser(CsBaselineSeeder seeder) {
        var mgmt = new ManagementServicesAdminClient(seeder.newSecurityOfficerSession());

        given("vault mock registered for TLS credentials retrieval", () ->
                seeder.mockExpectation(buildVaultGetMock()));

        try {
            then("GET /management-services-configuration/certificate returns 200", () ->
                    mgmt.getCertificate()
                            .statusCode(200));
        } finally {
            seeder.clearMockExpectations("/v1/xrd-secret/tls/management-service");
        }
    }

    @Test
    void managementServiceTlsCertificateDownloadedForPrivilegedUser(CsBaselineSeeder seeder) {
        var mgmt = new ManagementServicesAdminClient(seeder.newSecurityOfficerSession());

        given("vault mock registered for TLS credentials retrieval", () ->
                seeder.mockExpectation(buildVaultGetMock()));

        try {
            then("GET /management-services-configuration/download-certificate returns 200", () ->
                    mgmt.downloadCertificate()
                            .statusCode(200));
        } finally {
            seeder.clearMockExpectations("/v1/xrd-secret/tls/management-service");
        }
    }

    @Test
    void managementServiceCertificateSignRequestCreatedForPrivilegedUser(CsBaselineSeeder seeder) {
        var mgmt = new ManagementServicesAdminClient(seeder.newSecurityOfficerSession());

        given("vault mock registered for TLS credentials retrieval", () ->
                seeder.mockExpectation(buildVaultGetMock()));

        try {
            then("POST /management-services-configuration/generate-csr returns 200", () ->
                    mgmt.generateCsr("CN=cs")
                            .statusCode(200));
        } finally {
            seeder.clearMockExpectations("/v1/xrd-secret/tls/management-service");
        }
    }

    @Test
    void managementServiceTlsCertificateUploadedForPrivilegedUser(CsBaselineSeeder seeder) throws IOException {
        var mgmt = new ManagementServicesAdminClient(seeder.newSecurityOfficerSession());
        var certBytes = loadFile("test-data/management-service-new.crt");

        given("vault mocks registered for TLS credential addition and retrieval", () -> {
            seeder.mockExpectation(buildVaultIssueMock());
            seeder.mockExpectation(VAULT_SECRET_POST_MOCK);
            seeder.mockExpectation(buildVaultGetMock());
        });

        try {
            then("POST /management-services-configuration/upload-certificate returns 200", () ->
                    mgmt.uploadCertificate(certBytes)
                            .statusCode(200));
        } finally {
            seeder.clearMockExpectations("/v1/xrd-pki/issue/xrd-internal");
            seeder.clearMockExpectations("/v1/xrd-secret/tls/management-service");
        }
    }

    @Test
    void managementServiceTlsCertificateUploadRejectedWhenKeyMismatch(CsBaselineSeeder seeder) throws IOException {
        var mgmt = new ManagementServicesAdminClient(seeder.newSecurityOfficerSession());
        var certBytes = loadFile("test-data/management-service-mismatched.crt");

        given("vault GET mock registered so backend can read the stored public key", () ->
                seeder.mockExpectation(buildVaultGetMock()));

        try {
            then("POST /management-services-configuration/upload-certificate returns 400 key_not_found", () ->
                    mgmt.uploadCertificate(certBytes)
                            .statusCode(400)
                            .body("error.code", equalTo("key_not_found")));
        } finally {
            seeder.clearMockExpectations("/v1/xrd-secret/tls/management-service");
        }
    }

    private String buildVaultIssueMock() {
        return VAULT_ISSUE_MOCK_TEMPLATE.formatted(loadResourceAsString("test-data/vault-issue-tls-creds-response.json"));
    }

    private String buildVaultGetMock() {
        return VAULT_SECRET_GET_MOCK_TEMPLATE.formatted(loadResourceAsString("test-data/vault-get-tls-creds.response.json"));
    }

    private String loadResourceAsString(String resourcePath) {
        try (var stream = ClassLoader.getSystemResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(stream.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resource: " + resourcePath, e);
        }
    }

    private static byte[] loadFile(String resourcePath) throws IOException {
        try (var stream = ClassLoader.getSystemResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return stream.readAllBytes();
        }
    }
}
