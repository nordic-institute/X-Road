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
package org.niis.xroad.ss.test.api.seeding;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrFormatDto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitializationStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenInitStatusDto;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.ss.test.api.admin.AdminApiSession;
import org.niis.xroad.ss.test.api.admin.ClientsAdminClient;
import org.niis.xroad.ss.test.api.admin.TokensAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

/**
 * Idempotent seeding facade for the Security Server API test warm substrate.
 *
 * <p>Two layers:
 * <ol>
 *   <li><b>Baseline</b> (get-or-create) — ensures the SS is initialized: admin user bootstrapped,
 *       configuration anchor uploaded, SS owner/code set, software token logged in. Called once before
 *       the suite; re-entrant (skips steps already done on a warm stack). Baseline HTTP is routed through
 *       a non-reporting RestAssured spec so it never attaches to any individual test report.</li>
 *   <li><b>Per-test preconditions</b> (seeded on demand) — adds a namespaced subsystem client and a
 *       REST service description under that client. Uses the test-specific namespace token so concurrent
 *       scenarios never collide and leftover state from previous runs is inert. Each seeding method
 *       wraps its work in a labelled {@link Step#given} group so the Allure report shows what was seeded.</li>
 * </ol>
 */
@Slf4j
@SuppressWarnings("checkstyle:magicnumber")
public class SsBaselineSeeder {

    public static final String SS_OWNER_CLASS = "COM";
    public static final String SS_OWNER_CODE = "1234";
    public static final String SS_SERVER_CODE = "SS0";
    public static final String SS_TOKEN_PIN = "Secret1234";

    private static final String SUBSYSTEM_SEED_NS = "seed";
    private static final String SOFT_TOKEN = "0";
    private static final String TEST_CA = "Test CA";
    private static final String OWNER_MEMBER_ID = "DEV:%s:%s".formatted(SS_OWNER_CLASS, SS_OWNER_CODE);
    private static final String OWNER_CLIENT_ID = OWNER_MEMBER_ID;
    private static final String DS_TLS_SAN =
            "DNS:ui,DNS:ds-identity-hub,DNS:ds-control-plane,DNS:ds-issuer-service,DNS:localhost";

    private final String uiBaseUrl;
    private final String testCaBaseUrl;
    private volatile AdminApiSession session;

    public SsBaselineSeeder(SsApiTestContainerSetup stack) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        this.uiBaseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());
        var caMapping = stack.getContainerMapping(SsApiTestContainerSetup.TESTCA, Port.TEST_CA);
        this.testCaBaseUrl = "http://%s:%d/testca".formatted(caMapping.host(), caMapping.port());
    }

    public SsBaselineSeeder(String uiBaseUrl) {
        this.uiBaseUrl = uiBaseUrl;
        this.testCaBaseUrl = null;
    }

    /**
     * Opens a new authenticated admin API session. Each test should call this to get its own
     * session (owns its session cookies and XSRF token).
     */
    public AdminApiSession newSession() {
        return Step.given("test environment session opened", () -> new AdminApiSession(uiBaseUrl));
    }

    /**
     * Ensures the shared baseline is in place (idempotent).
     * Called once per JVM before any test starts. All HTTP here is silent (no Allure attachment).
     */
    public synchronized void ensureBaseline() {
        log.info("Checking Security Server baseline state");

        bootstrapAdminUser(uiBaseUrl);

        session = AdminApiSession.silent(uiBaseUrl);

        var status = fetchInitializationStatus();
        log.info("Initialization status: anchorImported={}, serverCodeInit={}, tokenStatus={}",
                status.getIsAnchorImported(), status.getIsServerCodeInitialized(),
                status.getSoftwareTokenInitStatus());

        if (!Boolean.TRUE.equals(status.getIsAnchorImported())) {
            uploadAnchor();
            status = fetchInitializationStatus();
        }

        if (!Boolean.TRUE.equals(status.getIsServerCodeInitialized())) {
            initializeServer();
            // initialization can outlive the UI session timeout; re-authenticate
            session = AdminApiSession.silent(uiBaseUrl);
        }

        if (status.getSoftwareTokenInitStatus() != TokenInitStatusDto.INITIALIZED) {
            loginToken();
        }

        if (testCaBaseUrl != null) {
            ensureOwnerSignCert();
            ensureDsTlsCertificate();
        }

        log.info("Security Server baseline is ready");
    }

    /**
     * Ensures a namespaced subsystem client exists for the given test namespace.
     * Idempotent: if the client already exists the existing ID is returned.
     *
     * @param adminSession authenticated admin session to use
     * @param namespace    per-test identifier appended to the subsystem code to guarantee uniqueness
     * @return the full client identifier ({@code DEV:COM:1234:<subsystemCode>})
     */
    public synchronized String seedSubsystem(AdminApiSession adminSession, String namespace) {
        return Step.given("test environment seeded: subsystem '%s'".formatted(namespace), () -> {
            var subsystemCode = SUBSYSTEM_SEED_NS + namespace;
            var clientId = "DEV:%s:%s:%s".formatted(SS_OWNER_CLASS, SS_OWNER_CODE, subsystemCode);
            var clients = new ClientsAdminClient(adminSession);

            if (clients.findClientByIdentifier(clientId) == null) {
                addSubsystem(clients, subsystemCode);
            }
            return clientId;
        });
    }

    /**
     * Ensures a namespaced subsystem client and an OPENAPI3 service description exist for the given test namespace.
     * The namespace token must be unique per test to prevent collisions.
     *
     * @param namespace   per-test identifier
     * @param specUrl     URL of the OpenAPI spec (reachable from inside the compose network)
     * @param serviceCode service code for the OPENAPI3 service description
     * @return seed result containing the client ID and service description ID
     */
    public synchronized SeedResult seedClientWithOpenApiService(String namespace, String specUrl, String serviceCode) {
        return Step.given("test environment seeded: OpenAPI service '%s' (ns='%s')".formatted(serviceCode, namespace), () -> {
            var subsystemCode = SUBSYSTEM_SEED_NS + namespace;
            var clientId = "DEV:%s:%s:%s".formatted(SS_OWNER_CLASS, SS_OWNER_CODE, subsystemCode);

            log.debug("Seeding client {} with OPENAPI3 service code={}, url={}", clientId, serviceCode, specUrl);

            var clients = new ClientsAdminClient(session);

            var existing = clients.findClientByIdentifier(clientId);
            if (existing == null) {
                addSubsystem(clients, subsystemCode);
            }

            var serviceDescriptions = clients.listServiceDescriptions(clientId);
            var existingDescription = serviceDescriptions.stream()
                    .filter(sd -> hasServiceCode(sd, serviceCode))
                    .findFirst();

            String serviceDescriptionId;
            if (existingDescription.isEmpty()) {
                serviceDescriptionId = addOpenApiServiceDescription(clients, clientId, specUrl, serviceCode);
            } else {
                serviceDescriptionId = existingDescription.get().id();
            }

            return new SeedResult(clientId, serviceDescriptionId);
        });
    }

    /**
     * Ensures a namespaced subsystem client and a REST service description exist for the given test namespace.
     * The namespace token must be unique per test to prevent collisions.
     *
     * @param namespace   per-test identifier (e.g. short hash of test method name)
     * @param serviceUrl  base URL for the REST service description
     * @param serviceCode service code for the REST service description
     * @return the client identifier ({@code DEV:COM:1234:<subsystemCode>}) for use in assertions
     */
    public synchronized SeedResult seedClientWithRestService(String namespace, String serviceUrl, String serviceCode) {
        return Step.given("test environment seeded: REST service '%s' (ns='%s')".formatted(serviceCode, namespace), () -> {
            var subsystemCode = SUBSYSTEM_SEED_NS + namespace;
            var clientId = "DEV:%s:%s:%s".formatted(SS_OWNER_CLASS, SS_OWNER_CODE, subsystemCode);

            log.debug("Seeding client {} with REST service code={}, url={}", clientId, serviceCode, serviceUrl);

            var clients = new ClientsAdminClient(session);

            var existing = clients.findClientByIdentifier(clientId);
            if (existing == null) {
                addSubsystem(clients, subsystemCode);
            }

            var serviceDescriptions = clients.listServiceDescriptions(clientId);
            var existingDescription = serviceDescriptions.stream()
                    .filter(sd -> hasServiceCode(sd, serviceCode))
                    .findFirst();

            String serviceDescriptionId;
            if (existingDescription.isEmpty()) {
                serviceDescriptionId = addRestServiceDescription(clients, clientId, serviceUrl, serviceCode);
            } else {
                serviceDescriptionId = existingDescription.get().id();
            }

            return new SeedResult(clientId, serviceDescriptionId);
        });
    }

    private void ensureOwnerSignCert() {
        var certs = session.given()
                .get("/clients/{id}/sign-certificates", OWNER_CLIENT_ID)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .<Object>getList("$");
        if (!certs.isEmpty()) {
            log.debug("Owner sign certificate already present — skipping seed");
            return;
        }
        log.info("Seeding sign certificate for owner member {}", OWNER_MEMBER_ID);
        var tokens = new TokensAdminClient(session);
        var signView = tokens.addKeyWithCsr(SOFT_TOKEN, "baseline-sign-key", KeyUsageTypeDto.SIGNING,
                TEST_CA, CsrFormatDto.PEM, OWNER_MEMBER_ID);
        var csrBytes = tokens.downloadCsr(signView.keyId(), signView.csrId(), CsrFormatDto.PEM);
        var signedCert = RestAssured.given()
                .relaxedHTTPSValidation()
                .multiPart("certreq", "sign.pem", csrBytes, "application/octet-stream")
                .multiPart("type", "sign")
                .post(testCaBaseUrl + "/sign")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
        tokens.importCertificate(signedCert).statusCode(201);
        log.info("Owner sign certificate seeded successfully");
    }

    /**
     * Ensures the tls/ds-https vault slot holds a certificate, provisioning it through the same
     * admin API flow an operator would use: generate the server-side key, fetch a DN-only CSR,
     * sign it at the stack's test CA with the SAN list the CA attaches, upload the certificate.
     * Skips when a certificate is already present, so re-runs never roll back a rotated or
     * ACME-enrolled certificate. The ds-* containers, waiting at startup on this slot, converge once this lands.
     */
    private void ensureDsTlsCertificate() {
        var status = session.given()
                .get("/ds-tls-certificate")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();
        if (Boolean.TRUE.equals(status.getBoolean("key_generated")) && status.get("certificate") != null) {
            log.debug("DS TLS certificate already present — skipping seed");
            return;
        }
        log.info("Seeding the DS TLS certificate through the admin API");
        session.given()
                .post("/ds-tls-certificate/key")
                .then()
                .statusCode(201);
        var csrBytes = session.given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"CN=ui"}
                        """)
                .post("/ds-tls-certificate/csr")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
        var signedCert = RestAssured.given()
                .relaxedHTTPSValidation()
                .multiPart("certreq", "ds-tls.csr.pem", csrBytes, "application/octet-stream")
                .multiPart("type", "auth")
                .multiPart("san", DS_TLS_SAN)
                .post(testCaBaseUrl + "/sign")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
        session.given()
                .multiPart("certificate", "ds-tls.crt", signedCert, "application/octet-stream")
                .post("/ds-tls-certificate/certificate")
                .then()
                .statusCode(200);
        log.info("DS TLS certificate seeded successfully");
    }

    private void bootstrapAdminUser(String baseUrl) {
        log.debug("Bootstrapping admin user (idempotent)");
        var response = RestAssuredFactory.givenSilent()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"xrd","password":"secret123!"}
                        """)
                .post(baseUrl + "/api/v1/initialization/admin-user");
        int status = response.statusCode();
        if (status != 201 && status != 409) {
            throw new IllegalStateException("Unexpected status bootstrapping admin user: " + status);
        }
        log.debug("Admin user bootstrap status: {}", status);
    }

    private InitializationStatusDto fetchInitializationStatus() {
        return session.given()
                .get("/initialization/status")
                .then()
                .statusCode(200)
                .extract()
                .as(InitializationStatusDto.class);
    }

    private void uploadAnchor() {
        log.info("Uploading configuration anchor");
        session.given()
                .multiPart("anchor", "anchor.xml", anchorBytes(), "application/xml")
                .post("/system/anchor")
                .then()
                .statusCode(201);
    }

    private void initializeServer() {
        log.info("Initializing Security Server (owner={}, code={})", SS_OWNER_CODE, SS_SERVER_CODE);
        session.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "owner_member_class": "%s",
                          "owner_member_code": "%s",
                          "security_server_code": "%s",
                          "software_token_pin": "%s",
                          "ignore_warnings": true
                        }
                        """.formatted(SS_OWNER_CLASS, SS_OWNER_CODE, SS_SERVER_CODE, SS_TOKEN_PIN))
                .post("/initialization")
                .then()
                .statusCode(201);
    }

    private void loginToken() {
        log.info("Logging in software token");
        session.given()
                .contentType(ContentType.JSON)
                .body("""
                        {"password":"%s"}
                        """.formatted(SS_TOKEN_PIN))
                .put("/tokens/0/login")
                .then()
                .statusCode(200);
    }

    private void addSubsystem(ClientsAdminClient clients, String subsystemCode) {
        var clientDto = new ClientDto(SS_OWNER_CLASS, SS_OWNER_CODE)
                .subsystemCode(subsystemCode)
                .connectionType(ConnectionTypeDto.HTTP);
        var request = new ClientAddDto(clientDto)
                .ignoreWarnings(true);

        clients.addClient(request).statusCode(201);
        log.debug("Added subsystem {}", subsystemCode);
    }

    private String addRestServiceDescription(ClientsAdminClient clients, String clientId,
                                             String serviceUrl, String serviceCode) {
        var request = new ServiceDescriptionAddDto(serviceUrl, ServiceTypeDto.REST)
                .restServiceCode(serviceCode)
                .ignoreWarnings(false);

        var serviceDescriptionId = clients.addServiceDescription(clientId, request)
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("id");
        log.debug("Added REST service description id={} for client {}", serviceDescriptionId, clientId);
        return serviceDescriptionId;
    }

    private String addOpenApiServiceDescription(ClientsAdminClient clients, String clientId,
                                                String specUrl, String serviceCode) {
        var request = new ServiceDescriptionAddDto(specUrl, ServiceTypeDto.OPENAPI3)
                .restServiceCode(serviceCode)
                .ignoreWarnings(true);

        var serviceDescriptionId = clients.addServiceDescription(clientId, request)
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("id");
        log.debug("Added OPENAPI3 service description id={} for client {}", serviceDescriptionId, clientId);
        return serviceDescriptionId;
    }

    private boolean hasServiceCode(ClientsAdminClient.ServiceDescriptionView sd, String serviceCode) {
        return sd.services().stream()
                .anyMatch(s -> serviceCode.equals(s.serviceCode()));
    }

    private byte[] anchorBytes() {
        try (var stream = getClass().getResourceAsStream("/files/trusted-anchor/configuration_anchor_CS_internal.xml")) {
            if (stream == null) {
                throw new IllegalStateException("Anchor file not found in classpath");
            }
            return stream.readAllBytes();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read anchor file", e);
        }
    }

    /**
     * Ensures a namespaced subsystem client and a WSDL service description exist for the given test namespace.
     * The namespace token must be unique per test to prevent collisions.
     *
     * @param namespace per-test identifier (e.g. short tag of the test method)
     * @param wsdlUrl   URL of the WSDL file served by the test stack's nginx
     *                  (e.g. {@code http://mock-server:1080/test-services/testservice1.wsdl})
     * @return the seeding result containing the client identifier and service description ID
     */
    public synchronized SeedResult seedClientWithWsdlService(String namespace, String wsdlUrl) {
        return Step.given("test environment seeded: WSDL service (ns='%s')".formatted(namespace), () -> {
            var subsystemCode = SUBSYSTEM_SEED_NS + namespace;
            var clientId = "DEV:%s:%s:%s".formatted(SS_OWNER_CLASS, SS_OWNER_CODE, subsystemCode);

            log.debug("Seeding client {} with WSDL service url={}", clientId, wsdlUrl);

            var clients = new ClientsAdminClient(session);

            var existing = clients.findClientByIdentifier(clientId);
            if (existing == null) {
                addSubsystem(clients, subsystemCode);
            }

            var serviceDescriptions = clients.listServiceDescriptions(clientId);
            var existingDescription = serviceDescriptions.stream()
                    .filter(sd -> wsdlUrl.equals(sd.url()))
                    .findFirst();

            String serviceDescriptionId;
            if (existingDescription.isEmpty()) {
                serviceDescriptionId = addWsdlServiceDescription(clients, clientId, wsdlUrl);
            } else {
                serviceDescriptionId = existingDescription.get().id();
            }

            return new SeedResult(clientId, serviceDescriptionId);
        });
    }

    private String addWsdlServiceDescription(ClientsAdminClient clients, String clientId, String wsdlUrl) {
        var request = new ServiceDescriptionAddDto(wsdlUrl, ServiceTypeDto.WSDL)
                .ignoreWarnings(false);

        var serviceDescriptionId = clients.addServiceDescription(clientId, request)
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("id");
        log.debug("Added WSDL service description id={} for client {}", serviceDescriptionId, clientId);
        return serviceDescriptionId;
    }

    /**
     * Result of seeding a client + service description.
     *
     * @param clientId             the full client identifier ({@code DEV:COM:1234:subsystem})
     * @param serviceDescriptionId the numeric service description ID
     */
    public record SeedResult(String clientId, String serviceDescriptionId) {
    }
}
