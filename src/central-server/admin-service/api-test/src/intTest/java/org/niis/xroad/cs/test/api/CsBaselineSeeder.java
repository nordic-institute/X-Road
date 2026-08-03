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
package org.niis.xroad.cs.test.api;

import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.cs.openapi.model.MemberAddDto;
import org.niis.xroad.cs.openapi.model.MemberClassDto;
import org.niis.xroad.cs.openapi.model.NewMemberIdDto;
import org.niis.xroad.cs.openapi.model.NewSubsystemIdDto;
import org.niis.xroad.cs.openapi.model.SubsystemAddDto;
import org.niis.xroad.cs.openapi.model.TokenInitStatusDto;
import org.niis.xroad.cs.test.api.admin.AdminApiSession;
import org.niis.xroad.cs.test.api.admin.MemberClassesAdminClient;
import org.niis.xroad.cs.test.api.admin.MembersAdminClient;
import org.niis.xroad.cs.test.api.admin.SubsystemsAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import javax.security.auth.x500.X500Principal;

import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static java.lang.ClassLoader.getSystemResourceAsStream;

/**
 * Idempotent seeding facade for the Central Server API test warm substrate.
 *
 * <p>Two layers:
 * <ol>
 *   <li><b>Baseline</b> (get-or-create, once per JVM) — ensures a fully initialised CS:
 *       bootstrap API key seeded into the DB; CS initialized (instance identifier {@code CS},
 *       address {@code cs}, software token PIN); baseline member class {@code ORG} present.
 *       Skips each step that is already present on a warm stack. Baseline HTTP is routed through a
 *       non-reporting spec so it never attaches to any individual test report.</li>
 *   <li><b>Per-test preconditions</b> (seeded on demand) — adds namespaced resources that a test
 *       declares. The caller passes a per-test unique {@code namespace} token; every resource id
 *       embeds that token so two tests seeding "a member class" never collide. Each seeding method
 *       is {@code synchronized} and wraps its work in a labelled {@link Step#given} group for Allure.</li>
 * </ol>
 *
 * <p>Baseline contract after {@link #ensureBaseline()} returns:
 * <ul>
 *   <li>Bootstrap API key + SYSTEM_ADMINISTRATOR role is present in the DB.</li>
 *   <li>CS is fully initialized: instance identifier {@code CS}, address {@code cs},
 *       software token status {@code INITIALIZED}.</li>
 *   <li>Baseline member class {@code ORG} (description "Organisations") exists.</li>
 * </ul>
 */
@Slf4j
@SuppressWarnings("checkstyle:magicnumber")
public class CsBaselineSeeder {

    /** Baseline member class code guaranteed present after {@link #ensureBaseline()}. */
    public static final String BASELINE_MEMBER_CLASS = "ORG";

    /** Instance identifier of the initialized CS. */
    public static final String INSTANCE_IDENTIFIER = "CS";

    private static final String DB_PROPERTIES_PATH = "/etc/xroad/db.properties";
    private static final String DB_NAME = "centerui_production";
    private static final String BOOTSTRAP_ENCODED_KEY = "ad26a8235b3e847dc0b9ac34733d5acb39e2b6af634796e7eebe171165cdf2d1";
    private static final long BOOTSTRAP_KEY_ID = 9000000L;
    private static final List<String> BOOTSTRAP_ROLES = List.of(
            "XROAD_REGISTRATION_OFFICER",
            "XROAD_SECURITY_OFFICER",
            "XROAD_SYSTEM_ADMINISTRATOR",
            "XROAD_MANAGEMENT_SERVICE");
    private static final String REGISTRATION_OFFICER_ENCODED_KEY = "ed99ce2b1660cb681598b9d33bb84089f3023d6f511729a281066bed5a764ca0";
    private static final long REGISTRATION_OFFICER_KEY_ID = 9000001L;
    private static final String SECURITY_OFFICER_ENCODED_KEY = "e26a8562ff905ba186970fb889b429eab2ceb8195b81e58767dd170ca910583d";
    private static final String SYSTEM_ADMINISTRATOR_ONLY_ENCODED_KEY = "114dcc210324531621c34c0e78b52b11a13d0f029596aa2807f8d268ef1e59cd";
    private static final String MANAGEMENT_SERVICE_ONLY_ENCODED_KEY = "5917336923b9e8f039580b2244faa5ca370137e848e9302c67aa2b3a679b5fb2";

    /** DB id of the seeded security officer API key; history rows record the acting user as {@code api-key-<id>}. */
    public static final long SECURITY_OFFICER_KEY_ID = 9000003L;
    private static final long SYSTEM_ADMINISTRATOR_ONLY_KEY_ID = 9000004L;
    private static final long MANAGEMENT_SERVICE_ONLY_KEY_ID = 9000005L;
    private static final String MEMBER_CLASS_SEED_NS = "mclass";
    private static final String MEMBER_SEED_NS = "member";
    private static final String SUBSYSTEM_SEED_NS = "subsys";
    private static final int SIGNER_READY_MAX_ATTEMPTS = 40;
    private static final long SIGNER_READY_POLL_MILLIS = 3000L;

    private final CsApiTestContainerSetup stack;
    private final String adminBaseUrl;
    private final String mockServerBaseUrl;

    CsBaselineSeeder(CsApiTestContainerSetup stack) {
        this.stack = stack;
        var adminMapping = stack.getContainerMapping(CsApiTestContainerSetup.CS, Port.CS_ADMIN);
        this.adminBaseUrl = "http://%s:%d".formatted(adminMapping.host(), adminMapping.port());
        var mockMapping = stack.getContainerMapping(CsApiTestContainerSetup.MOCK_SERVER, Port.MOCK_SERVER);
        this.mockServerBaseUrl = "http://%s:%d".formatted(mockMapping.host(), mockMapping.port());
    }

    /**
     * Opens a new admin API session authenticated as system administrator.
     */
    public AdminApiSession newSession() {
        return Step.given("admin session opened", () -> new AdminApiSession(adminBaseUrl));
    }

    /**
     * Opens a new admin API session authenticated as registration officer only.
     * This session has {@code XROAD_REGISTRATION_OFFICER} but not {@code XROAD_MANAGEMENT_SERVICE},
     * so it may post management requests with {@code origin=CENTER}.
     */
    public AdminApiSession newRegistrationOfficerSession() {
        return Step.given("registration officer session opened",
                () -> new AdminApiSession(adminBaseUrl, AdminApiSession.REGISTRATION_OFFICER_TOKEN));
    }

    /**
     * Opens a new admin API session authenticated as security officer only ({@code XROAD_SECURITY_OFFICER}).
     */
    public AdminApiSession newSecurityOfficerSession() {
        return Step.given("security officer session opened",
                () -> new AdminApiSession(adminBaseUrl, AdminApiSession.SECURITY_OFFICER_TOKEN));
    }

    /**
     * Opens a new admin API session authenticated as system administrator only ({@code XROAD_SYSTEM_ADMINISTRATOR}).
     */
    public AdminApiSession newSystemAdministratorOnlySession() {
        return Step.given("system administrator only session opened",
                () -> new AdminApiSession(adminBaseUrl, AdminApiSession.SYSTEM_ADMINISTRATOR_ONLY_TOKEN));
    }

    /**
     * Opens a new admin API session authenticated as management service only ({@code XROAD_MANAGEMENT_SERVICE}).
     */
    public AdminApiSession newManagementServiceOnlySession() {
        return Step.given("management service only session opened",
                () -> new AdminApiSession(adminBaseUrl, AdminApiSession.MANAGEMENT_SERVICE_ONLY_TOKEN));
    }

    /**
     * Returns the admin base URL for unauthenticated or custom-auth requests.
     */
    public String getAdminBaseUrl() {
        return adminBaseUrl;
    }

    /**
     * Registers a MockServer expectation via the MockServer REST API.
     *
     * @param expectationJson the MockServer expectation JSON body
     */
    public void mockExpectation(String expectationJson) {
        RestAssuredFactory.givenSilent()
                .contentType(ContentType.JSON)
                .body(expectationJson)
                .put(mockServerBaseUrl + "/mockserver/expectation")
                .then()
                .statusCode(201);
    }

    /**
     * Clears MockServer expectations and recorded requests for a specific path only,
     * leaving all other expectations (including the baseline signer mock) intact.
     *
     * @param path the request path whose expectations should be cleared (exact match)
     */
    public void clearMockExpectations(String path) {
        RestAssuredFactory.givenSilent()
                .contentType(ContentType.JSON)
                .body("{\"path\": \"" + path + "\"}")
                .put(mockServerBaseUrl + "/mockserver/clear")
                .then();
    }

    /**
     * Queries a field from the history table for the most recent row matching the given new_value.
     *
     * @param field      column to select (e.g. {@code ha_node_name}, {@code user_name})
     * @param paramValue the new_value to match in the history row
     * @return the field value, or {@code null} if no row matched
     */
    public String queryHistory(String field, String paramValue) {
        var dbMapping = stack.getContainerMapping(CsApiTestContainerSetup.CS, Port.DB);
        var jdbcUrl = "jdbc:postgresql://%s:%d/%s?currentSchema=centerui,public"
                .formatted(dbMapping.host(), dbMapping.port(), DB_NAME);
        var username = readContainerProperty("spring.datasource.username");
        var password = readContainerProperty("spring.datasource.password");
        try (var conn = DriverManager.getConnection(jdbcUrl, username, password);
                var stmt = conn.prepareStatement(
                        "SELECT " + field + " FROM history WHERE table_name = 'system_parameters' "
                                + "AND field_name = 'value' AND new_value = ? ORDER BY id DESC LIMIT 1")) {
            stmt.setString(1, paramValue);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        } catch (SQLException e) {
            throw new IllegalStateException("History query failed", e);
        }
    }

    /**
     * Seeds the bootstrap API key, initialises the CS if needed, and verifies the stack is reachable.
     * Called once per JVM before any test starts. All HTTP here is silent (no Allure attachment).
     */
    public synchronized void ensureBaseline() {
        log.info("Checking Central Server baseline state");

        seedApiKey(BOOTSTRAP_KEY_ID, BOOTSTRAP_ENCODED_KEY, BOOTSTRAP_ROLES.toArray(new String[0]));
        seedApiKey(REGISTRATION_OFFICER_KEY_ID, REGISTRATION_OFFICER_ENCODED_KEY, "XROAD_REGISTRATION_OFFICER");
        seedApiKey(SECURITY_OFFICER_KEY_ID, SECURITY_OFFICER_ENCODED_KEY, "XROAD_SECURITY_OFFICER");
        seedApiKey(SYSTEM_ADMINISTRATOR_ONLY_KEY_ID, SYSTEM_ADMINISTRATOR_ONLY_ENCODED_KEY, "XROAD_SYSTEM_ADMINISTRATOR");
        seedApiKey(MANAGEMENT_SERVICE_ONLY_KEY_ID, MANAGEMENT_SERVICE_ONLY_ENCODED_KEY, "XROAD_MANAGEMENT_SERVICE");

        var session = new AdminApiSession(adminBaseUrl);
        ensureInitialized(session);
        ensureBaselineMemberClass(session);
        seedConfigurationTestData();

        log.info("Central Server baseline is ready");
    }

    /**
     * Ensures a namespaced member class exists for the given test namespace. Idempotent: if the
     * member class already exists the existing code is returned.
     *
     * @param session   authenticated admin session to use
     * @param namespace per-test identifier appended to the member class code to guarantee uniqueness
     * @return the member class code (e.g. {@code mclassMyTest01})
     */
    public synchronized String seedMemberClass(AdminApiSession session, String namespace) {
        return Step.given("test environment seeded: member class ns=" + namespace, () -> {
            var code = MEMBER_CLASS_SEED_NS + namespace;
            var client = new MemberClassesAdminClient(session);
            var existing = client.listMemberClassCodes();
            if (!existing.contains(code)) {
                client.addMemberClass(new MemberClassDto()
                        .code(code)
                        .description("Seeded member class " + namespace))
                        .statusCode(201);
                log.debug("Added member class {}", code);
            }
            return code;
        });
    }

    /**
     * Ensures a namespaced member exists for the given test namespace under the baseline member class.
     * Idempotent: if the member already exists the existing X-Road identifier is returned.
     * The baseline member class must already be present (call {@link #seedMemberClass} first if you
     * need a non-baseline class, or rely on the baseline {@code ORG} class).
     *
     * @param session       authenticated admin session to use
     * @param namespace     per-test identifier embedded in the member code to guarantee uniqueness
     * @param memberClass   member class code under which the member is created
     * @return the X-Road member identifier in {@code CS:memberClass:memberCode} form
     */
    public synchronized String seedMember(AdminApiSession session, String namespace, String memberClass) {
        return Step.given("test environment seeded: member ns=" + namespace + " class=" + memberClass, () -> {
            var memberCode = MEMBER_SEED_NS + namespace;
            var memberId = "%s:%s:%s".formatted(INSTANCE_IDENTIFIER, memberClass, memberCode);
            var members = new MembersAdminClient(session);
            var response = members.getMember(memberId);
            if (response.extract().statusCode() == 404) {
                members.addMember(new MemberAddDto()
                        .memberName("Seeded member " + namespace)
                        .memberId(new NewMemberIdDto()
                                .memberClass(memberClass)
                                .memberCode(memberCode)))
                        .statusCode(201);
                log.debug("Added member {}", memberId);
            }
            return memberId;
        });
    }

    /**
     * Ensures a namespaced subsystem exists for the given test namespace under the given member.
     * Idempotent: if the subsystem already exists the existing X-Road subsystem identifier is returned.
     * The member must already exist (call {@link #seedMember} first).
     *
     * @param session     authenticated admin session to use
     * @param namespace   per-test identifier embedded in the subsystem code to guarantee uniqueness
     * @param memberId    X-Road member identifier (CS:class:code) under which the subsystem is created
     * @return the X-Road subsystem identifier in {@code CS:class:code:subsystemCode} form
     */
    public synchronized String seedSubsystem(AdminApiSession session, String namespace, String memberId) {
        return Step.given("test environment seeded: subsystem ns=" + namespace + " member=" + memberId, () -> {
            var subsystemCode = SUBSYSTEM_SEED_NS + namespace;
            var subsystemId = memberId + ":" + subsystemCode;
            var idParts = memberId.split(":");
            var members = new MembersAdminClient(session);
            var subsystems = new SubsystemsAdminClient(session);
            var subsResponse = members.getMemberSubsystems(memberId);
            var existing = subsResponse.extract().jsonPath().getList("subsystemId.subsystemCode", String.class);
            if (!existing.contains(subsystemCode)) {
                var idDto = new NewSubsystemIdDto()
                        .subsystemCode(subsystemCode);
                idDto.setMemberClass(idParts[1]);
                idDto.setMemberCode(idParts[2]);
                subsystems.addSubsystem(new SubsystemAddDto().subsystemId(idDto))
                        .statusCode(201);
                log.debug("Added subsystem {}", subsystemId);
            }
            return subsystemId;
        });
    }

    /**
     * Registers a security server for the given member and approves the management request.
     * The server joins the {@code security-server-owners} global group as a side effect.
     * Idempotent: if the server is already registered the existing server identifier is returned.
     *
     * @param session   authenticated admin session to use
     * @param namespace per-test identifier used to build a unique server code
     * @param memberId  X-Road member identifier (CS:class:code) that owns the server
     * @return the security server identifier in {@code CS:class:code:serverCode} form
     */
    public synchronized String seedSecurityServer(AdminApiSession session, String namespace, String memberId) {
        return Step.given("test environment seeded: security server ns=" + namespace + " owner=" + memberId, () -> {
            var serverCode = "ss" + namespace;
            var serverId = memberId + ":" + serverCode;

            var certBytes = generateAuthCert("CN=" + serverId);

            var registrationRequest = new AuthenticationCertificateRegistrationRequestDto()
                    .serverAddress("ss-addr-" + namespace)
                    .authenticationCertificate(certBytes);
            registrationRequest.type(ManagementRequestTypeDto.AUTH_CERT_REGISTRATION_REQUEST);
            registrationRequest.origin(ManagementRequestOriginDto.SECURITY_SERVER);
            registrationRequest.securityServerId(serverId);

            var requestId = session.givenSilent()
                    .contentType(ContentType.JSON)
                    .body(registrationRequest)
                    .post("/management-requests")
                    .then()
                    .statusCode(202)
                    .extract()
                    .jsonPath()
                    .getInt("id");

            session.givenSilent()
                    .post("/management-requests/{id}/approval", requestId)
                    .then()
                    .statusCode(200);

            log.debug("Registered and approved security server {}", serverId);
            return serverId;
        });
    }

    /**
     * Generates an authentication certificate for the given security server identifier.
     */
    public byte[] generateCertForServer(String serverId) throws Exception {
        return generateAuthCert("CN=" + serverId);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private byte[] generateAuthCert(String subjectDn) throws Exception {
        var certificateFactory = CertificateFactory.getInstance("X.509");
        var privateKey = readCaPrivateKey();
        var caCert = (X509Certificate) certificateFactory
                .generateCertificate(getSystemResourceAsStream("container-files/etc/xroad/globalconf/root-ca.pem"));

        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
        var subject = new X500Principal(subjectDn);

        return new JcaX509v3CertificateBuilder(
                caCert.getSubjectX500Principal(),
                BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                subject,
                caCert.getPublicKey())
                .addExtension(Extension.create(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature)))
                .build(signer)
                .getEncoded();
    }

    private PrivateKey readCaPrivateKey() throws Exception {
        try (var keyReader = new InputStreamReader(
                getSystemResourceAsStream("container-files/etc/xroad/globalconf/root-ca.key"), StandardCharsets.UTF_8);
                var pemParser = new PEMParser(keyReader)) {
            var parsed = pemParser.readObject();
            var converter = new JcaPEMKeyConverter();
            if (parsed instanceof PEMKeyPair pemKeyPair) {
                return converter.getPrivateKey(pemKeyPair.getPrivateKeyInfo());
            }
            return converter.getPrivateKey((PrivateKeyInfo) parsed);
        }
    }

    private void ensureInitialized(AdminApiSession session) {
        var status = awaitAdminReady(session);
        log.info("Initialization status: instanceIdentifier={}, tokenStatus={}",
                status.instanceIdentifier(), status.softwareTokenInitStatus());

        if (status.softwareTokenInitStatus() == TokenInitStatusDto.INITIALIZED) {
            return;
        }
        setupSignerMock();
        initializeCentralServer(session);
        log.info("Central Server initialized: instance={}, address=cs", INSTANCE_IDENTIFIER);
    }

    private InitStatus awaitAdminReady(AdminApiSession session) {
        for (int attempt = 0; attempt < SIGNER_READY_MAX_ATTEMPTS; attempt++) {
            var status = fetchInitializationStatus(session);
            if (status != null) {
                return status;
            }
            try {
                Thread.sleep(SIGNER_READY_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for admin API readiness", e);
            }
        }
        throw new IllegalStateException("Admin API initialization status did not become reachable within timeout");
    }

    private record InitStatus(String instanceIdentifier, TokenInitStatusDto softwareTokenInitStatus) {
    }

    private InitStatus fetchInitializationStatus(AdminApiSession session) {
        var response = session.givenSilent()
                .get("/initialization/status");
        if (response.statusCode() != 200) {
            return null;
        }
        var body = response.jsonPath();
        var tokenStatus = body.getString("software_token_init_status");
        var instanceId = body.getString("instance_identifier");
        return new InitStatus(instanceId, tokenStatus == null ? TokenInitStatusDto.NOT_INITIALIZED
                : TokenInitStatusDto.fromValue(tokenStatus));
    }

    /**
     * Re-registers the baseline {@code initSoftwareToken} signer mock (HTTP 204). Use to restore the
     * baseline after a test temporarily overrides that path with a different response.
     */
    public void restoreSignerMock() {
        setupSignerMock();
    }

    private void setupSignerMock() {
        log.debug("Registering signer mock expectation on mock-server");
        RestAssuredFactory.givenSilent()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "httpRequest": { "method": "PUT", "path": "/initSoftwareToken/" },
                          "httpResponse": { "statusCode": 204 }
                        }
                        """)
                .put(mockServerBaseUrl + "/mockserver/expectation")
                .then()
                .statusCode(201);
    }

    private void initializeCentralServer(AdminApiSession session) {
        log.info("Initializing Central Server (instanceId={}, address=cs)", INSTANCE_IDENTIFIER);
        session.givenSilent()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "instance_identifier": "%s",
                          "central_server_address": "cs",
                          "software_token_pin": "1234-VALID"
                        }
                        """.formatted(INSTANCE_IDENTIFIER))
                .post("/initialization")
                .then()
                .statusCode(200);
    }

    private void ensureBaselineMemberClass(AdminApiSession session) {
        var client = new MemberClassesAdminClient(session);
        var existing = client.listMemberClassCodes();
        if (!existing.contains(BASELINE_MEMBER_CLASS)) {
            client.addMemberClass(new MemberClassDto()
                    .code(BASELINE_MEMBER_CLASS)
                    .description("Organisations"))
                    .statusCode(201);
            log.info("Added baseline member class {}", BASELINE_MEMBER_CLASS);
        }
    }

    @SuppressWarnings({"checkstyle:MethodLength", "checkstyle:LineLength"})
    private void seedConfigurationTestData() {
        var dbMapping = stack.getContainerMapping(CsApiTestContainerSetup.CS, Port.DB);
        var jdbcUrl = "jdbc:postgresql://%s:%d/%s?currentSchema=centerui,public"
                .formatted(dbMapping.host(), dbMapping.port(), DB_NAME);
        var username = readContainerProperty("spring.datasource.username");
        var password = readContainerProperty("spring.datasource.password");

        try (var conn = DriverManager.getConnection(jdbcUrl, username, password);
                var stmt = conn.createStatement()) {

            stmt.execute("""
                    INSERT INTO centerui.configuration_sources
                    (source_type, active_key_id, anchor_file, anchor_file_hash, anchor_generated_at, ha_node_name)
                    VALUES ('internal', null,
                        decode('3c3f786d6c2076657273696f6e3d22312e302220656e636f64696e673d225554462d3822207374616e64616c6f6e653d22796573223f3e0a3c6e73333a636f6e66696775726174696f6e416e63686f7220786d6c6e733a6e73323d22687474703a2f2f782d726f61642e65752f7873642f6964656e746966696572732220786d6c6e733a6e73333d22687474703a2f2f782d726f61642e65752f7873642f78726f61642e787364223e0a202020203c67656e65726174656441743e323032312d30332d31305430373a34303a35312e3730395a3c2f67656e65726174656441743e0a202020203c696e7374616e63654964656e7469666965723e43533c2f696e7374616e63654964656e7469666965723e0a202020203c736f757263653e0a20202020202020203c646f776e6c6f616455524c3e687474703a2f2f63732f696e7465726e616c636f6e663c2f646f776e6c6f616455524c3e0a20202020202020203c766572696669636174696f6e436572743e4d49494371544343415a476741774942416749424154414e42676b71686b694739773042415130464144414f4d517777436759445651514444414e4f4c3045774868634e4e7a41774d5441784d4441774d4441775768634e4d7a67774d5441784d4441774d444177576a414f4d517777436759445651514444414e4f4c304577676745694d4130474353714753496233445145424151554141344942447741776767454b416f494241514371773432762b4377504e6d49376a323443305548734e666878444b6134726b7067716b34694b6e513543797a537876656f64626475417a2b68326c484f485470496e4f6774695964384355544a552f413955304a575438574134794f5765414c786772526c416d52393434627754444b654748764a6e6955376552396850306d4b6458785055745a4f5030455265366e71484957472f66324e415832537836646757766f394e426e585a2f336c5161676155486b346d7a7456666b346a4e35457a42716b6772332f326f586b582f4f2f4d336855324f714e6f51536c484e627554456b6f776b52576b553755474b626b4575532b6c646d625977765443656151734a6e6a4e2b4b76447971544665783655586b7236396c376b4d44754e6c59787273456658632b35597936535a65676c574967694b2b63677261394537546636454a6d5a5177486b4d61303078542b346f416b372f41674d424141476a456a41514d41344741315564447745422f775145417749475144414e42676b71686b6947397730424151304641414f43415145414d554e36356d577166382f50794963715971364f4d4530444e4165526e333049372f69634c6875756d47454863645953737a65453839417452692b4b454675316c4b6a3472416e3648612b36304a502f3362494d74713830377231563652547736325a4849515462423268587151544974625749636e4e71557530525665717445304b4c6b7a314b2f73792b2b55705961626b6c2b7357516d345137727449724763476f4d493939376b32685a317679636648502f424a367a76316842693434616c387677512b623633784d634a58474c555a557230634d50424977776e6445554e316e6f656574622b4a454d6f425259677344634f70587a41645a4f624c556f542b53457755733968395a483031573056767a395831516147457765312b4c6d37484a78474f4f446a432b6f50535362513279782b56334f4d7133315a6a69394765544977366c3569542b7776796b6a54697958413d3d3c2f766572696669636174696f6e436572743e0a202020203c2f736f757263653e0a3c2f6e73333a636f6e66696775726174696f6e416e63686f723e0a', 'hex'),
                        '4D:72:A8:60:90:88:A2:5B:9C:6B:91:86:3C:D7:44:CE:9E:E1:1C:27:8E:33:F4:E5:31:68:F2:EC',
                        '2022-01-01 01:00:00.000000', 'test_node')
                    ON CONFLICT (source_type, ha_node_name) DO UPDATE
                        SET anchor_file = EXCLUDED.anchor_file,
                            anchor_file_hash = EXCLUDED.anchor_file_hash,
                            anchor_generated_at = EXCLUDED.anchor_generated_at
                        WHERE centerui.configuration_sources.anchor_file_hash IS NULL
                    """);

            stmt.execute("""
                    INSERT INTO centerui.configuration_sources
                    (source_type, active_key_id, anchor_file, anchor_file_hash, anchor_generated_at, ha_node_name)
                    VALUES ('external', null,
                        decode('3c3f786d6c2076657273696f6e3d22312e302220656e636f64696e673d225554462d3822207374616e64616c6f6e653d22796573223f3e0a3c6e73333a636f6e66696775726174696f6e416e63686f7220786d6c6e733a6e73323d22687474703a2f2f782d726f61642e65752f7873642f6964656e746966696572732220786d6c6e733a6e73333d22687474703a2f2f782d726f61642e65752f7873642f78726f61642e787364223e0a20203c67656e65726174656441743e323032312d30332d31305430373a34303a35372e3431315a3c2f67656e65726174656441743e0a20203c696e7374616e63654964656e7469666965723e43533c2f696e7374616e63654964656e7469666965723e0a20203c736f757263653e0a202020203c646f776e6c6f616455524c3e687474703a2f2f63732f65787465726e616c636f6e663c2f646f776e6c6f616455524c3e0a202020203c766572696669636174696f6e436572743e4d49494371544343415a476741774942416749424154414e42676b71686b694739773042415130464141444f4d517777436759445651514444414e4f4c3045774868634e4e7a41774d5441784d4441774d4441775768634e4d7a67774d5441784d4441774d444177576a414f4d517777436759445651514444414e4f4c304577676745694d4130474353714753496233445145424151554141344942447741776767454b416f494241514342554e6a397a7547425a596769513171346a78655079374370653862627233372b79536e6a6b514b6c384430472f36764972505a50717a3252526b3151614b6d5631776b466c63436b7379752b305156506e706d6a525936416c6b5752524363695432316a434870436566396b776a542b2b4e7755415638674a2b736f497161735755435a704a5045446b435672422b2f6a4f3232576c74647a70716d6a337958324e317a524947366464716f4f49657a355730633273747937773173486e432b6164466f424957735a63744e48664c6c496e4b456b5374546137385854724b38456e6a5238717a705a56596a683057784f5a764e376f714e5a4464556765473367423239544c706a326f34482f725462556c674a54306742532f414849784d7a525055624d566165444e476170364f666a652f384a633174624f644c35776a596339705962553570777a2f6f53474d493674614241674d424141476a456a41514d41344741315564447745422f775145417749475144414e42676b71686b6947397730424151304641414f434151454155564d2b776f415245776b4b46593175455a564f53484e7931536c7a72454a443367506d2f75692b5939334c76477243537277564a5655716433646b623949734844364a65762f53586a45462f706847565679305748533845584a706b4c4764562f6c526c2f546e685a7651366b377135616d556434336278494862334a706e347a33667057675830372f6f7033534f575342534b564f44346d72684951516b774a514b54536b6a464458724a45696a596379542b72675555654f57694e536e6269376e476e42674a6c4941356666316f6a6d62396b426946554e533374485947306865456b583436727336416c3967716a5a746169444b36586b5a776c6b71783736394c7a78526c6e6c72554c7a5170573153774579455164567a5a502f6b7153737166794d4e6d656f6b4f31686b2b467446766f5269335847317545594a4f2b4d386f50766f39392b47797534354951726d69673d3d3c2f766572696669636174696f6e436572743e0a20203c2f736f757263653e0a3c2f6e73333a636f6e66696775726174696f6e416e63686f723e', 'hex'),
                        '4D:72:A8:60:90:88:A2:5B:9C:6B:91:86:3C:D7:44:CE:9E:E1:1C:27:8E:33:F4:E5:31:68:F2:EC',
                        '2022-01-01 01:00:00.000000', 'test_node')
                    ON CONFLICT (source_type, ha_node_name) DO UPDATE
                        SET anchor_file = EXCLUDED.anchor_file,
                            anchor_file_hash = EXCLUDED.anchor_file_hash,
                            anchor_generated_at = EXCLUDED.anchor_generated_at
                        WHERE centerui.configuration_sources.anchor_file_hash IS NULL
                    """);

            stmt.execute("""
                    INSERT INTO centerui.distributed_files
                    (version, file_name, file_data, content_identifier, file_updated_at, ha_node_name)
                    SELECT 2, 'shared-params.xml', '<conf/>', 'SHARED-PARAMETERS', '2022-01-01 01:00:00.000000', 'test_node'
                    WHERE NOT EXISTS (
                        SELECT 1 FROM centerui.distributed_files
                        WHERE content_identifier = 'SHARED-PARAMETERS' AND version = 2 AND ha_node_name = 'test_node')
                    """);

            stmt.execute("""
                    INSERT INTO centerui.distributed_files
                    (version, file_name, file_data, content_identifier, file_updated_at, ha_node_name)
                    SELECT 2, 'private-params.xml', '<conf/>', 'PRIVATE-PARAMETERS', '2022-01-01 01:00:00.000000', 'test_node'
                    WHERE NOT EXISTS (
                        SELECT 1 FROM centerui.distributed_files
                        WHERE content_identifier = 'PRIVATE-PARAMETERS' AND version = 2 AND ha_node_name = 'test_node')
                    """);

            stmt.execute("""
                    INSERT INTO centerui.distributed_files
                    (version, file_name, file_data, content_identifier, file_updated_at, ha_node_name)
                    SELECT 0, 'test-fetchinterval-part.xml', '<conf/>', 'FETCHINTERVAL', '2022-01-01 01:00:00.000000', 'test_node'
                    WHERE NOT EXISTS (
                        SELECT 1 FROM centerui.distributed_files
                        WHERE content_identifier = 'FETCHINTERVAL' AND version = 0 AND ha_node_name = 'test_node')
                    """);

            stmt.execute("""
                    INSERT INTO centerui.configuration_signing_keys
                    (configuration_source_id, key_identifier, cert, key_generated_at, token_identifier)
                    SELECT
                        (SELECT id FROM centerui.configuration_sources WHERE source_type = 'internal' AND ha_node_name = 'test_node'),
                        'F397AF7369B15D42D7190E90ECA9508D48275FAB',
                        decode('308202a930820191a003020102020101300d06092a864886f70d01010d0500300e310c300a06035504030c034e2f41301e170d3730303130313030303030305a170d3338303130313030303030305a300e310c300a06035504030c034e2f4130820122300d06092a864886f70d01010105000382010f003082010a0282010100a7c38daff82c0f36623b8f6e02d141ec35f8710ca6b8ae4a60aa4e222a74390b2cd2c6f7a875b76e033fa1da51ce1d3a489ce82d89877c0944c953f03d5342564fc580e323967802f182b46502647de386f04c329e187bc99e253b791f613f498a757c4f52d64e3f41117ba9ea1c8586fdfd8d017d92c7a7605afa3d3419d767fde541a81a5079389b3b557e4e2337913306a920af7ff6a17917fcefccde15363aa36841294735bb93124a309115a453b50629b904b92fa57666d85af4c279a42c2678cdf8abc3caa4c57b1e945e4afaf65ee4303b8d958c6bb047d773ee58cba4997a095622088af9c82b6bd13b4dfe84266650c0790c6b4d314fee28024eff0203010001a3123010300e0603551d0f0101ff040403020640300d06092a864886f70d01010d0500038201010031437ae665aa7fcfcfc8872a62ae8e304d033407919f7d08eff89c2e1bae98610771d612b33784f3d02d462f8a105bb594a8f8ac09fa1dafbad093ffddb20cb6af34eebd55e914f0eb66472104db076857a904c8b5b58872736a52ed1155eaad13428b933d4afeccbef94a5869b925fac5909b843baed22b19c1a8308f7dee4da1675bf271f1cffc127acefd61062e386a5f2fc10f9beb7c4c7095c62d4654af470c3c1230c2774450dd67a1e7ad6fe244328051620b0370ea57cc075939b2d4a13f9213052cf61f591f4d56d15bf3f57d506861307b5f8b9bb1c9c4638e0e30bea0f4926d0db2c7e57738cab7d598e2f46793230ea5e624fec2fca48d38b25c', 'hex'),
                        '2022-01-02 01:00:00.000000',
                        '0'
                    WHERE NOT EXISTS (
                        SELECT 1 FROM centerui.configuration_signing_keys
                        WHERE key_identifier = 'F397AF7369B15D42D7190E90ECA9508D48275FAB')
                    """);

            stmt.execute("""
                    INSERT INTO centerui.configuration_signing_keys
                    (configuration_source_id, key_identifier, cert, key_generated_at, token_identifier)
                    SELECT
                        (SELECT id FROM centerui.configuration_sources WHERE source_type = 'external' AND ha_node_name = 'test_node'),
                        'BAE59C0FA417D2CD65FAB7DB5E493D0D6053B777',
                        decode('308202a930820191a003020102020101300d06092a864886f70d01010d0500300e310c300a06035504030c034e2f41301e170d3730303130313030303030305a170d3338303130313030303030305a300e310c300a06035504030c034e2f4130820122300d06092a864886f70d01010105000382010f003082010a02820101008150d8fdcee181658822435ab88f178fcbb0a97bc6dbaf7efec929e39102a5f03d06ffabc8acf64fab3d91464d5068a995d7090595c0a4b32bbed1054f9e99a3458e809645914427224f6d63087a4279ff64c234fef8dc14015f2027eb2822a6ac594099a493c40e4095ac1fbf8cedb65a5b5dce9aa68f7c97d8dd734481ba75daa83887b3e56d1cdacb72ef0d6c1e70be69d1680485ac65cb4d1df2e5227284912b536bbf174eb2bc1278d1f2ace96556238745b1399bcdee8a8d64375481e1b7801dbd4cba63da8e07feb4db5258094f48014bf00723133344f51b31569e0cd19aa7a39f8deffc25cd6d6ce74be708d873da586d4e69c33fe8486308ead6810203010001a3123010300e0603551d0f0101ff040403020640300d06092a864886f70d01010d0500038201010051533ec2801113090a158d6e11954e487372d52973ac4243de03e6fee8be63ddcbbc6ac24abc1525552a7777646fd22c1c3e897affd25e3105fe9846555cb45874bc11726990b19d57f95197f4e7859bd0ea4eeae5a994778ddbc481dbdc9a67e33ddfa56817d3bfe8a7748e592052295383e26ae1210424c0940a4d29231435eb2448a361cc93fab81451e39688d4a76e2ee71a7060265200e5f7f5a2399bf64062154352ded1d81b485e1245f8eabb3a025f60aa366d6a20cae97919c2592ac7bebd2f3c5196796b50bcd0a56d52c04c8441d57364ffe4a92b2a7f230d99ea243b5864f85b45be8462dd71b5b846093be33ca0fbe8f7df86caee39210ae68a', 'hex'),
                        '2022-01-02 01:00:00.000000',
                        '0'
                    WHERE NOT EXISTS (
                        SELECT 1 FROM centerui.configuration_signing_keys
                        WHERE key_identifier = 'BAE59C0FA417D2CD65FAB7DB5E493D0D6053B777')
                    """);

            stmt.execute("""
                    UPDATE centerui.configuration_sources
                    SET active_key_id = (
                        SELECT id FROM centerui.configuration_signing_keys
                        WHERE key_identifier = 'F397AF7369B15D42D7190E90ECA9508D48275FAB')
                    WHERE source_type = 'internal' AND ha_node_name = 'test_node' AND active_key_id IS NULL
                    """);

            stmt.execute("""
                    UPDATE centerui.configuration_sources
                    SET active_key_id = (
                        SELECT id FROM centerui.configuration_signing_keys
                        WHERE key_identifier = 'BAE59C0FA417D2CD65FAB7DB5E493D0D6053B777')
                    WHERE source_type = 'external' AND ha_node_name = 'test_node' AND active_key_id IS NULL
                    """);

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed configuration test data into CS DB", e);
        }
    }

    /**
     * Seeds an API key with the given DB id and roles, if a key with the same encoded value
     * doesn't already exist. Idempotent per key and per role.
     *
     * @param id         DB id to assign to the key row on first insert
     * @param encodedKey the (already hashed) key value stored in {@code centerui.apikey.encodedkey}
     * @param roles      roles to grant the key, e.g. {@code XROAD_SECURITY_OFFICER}
     */
    private void seedApiKey(long id, String encodedKey, String... roles) {
        var dbMapping = stack.getContainerMapping(CsApiTestContainerSetup.CS, Port.DB);
        var jdbcUrl = "jdbc:postgresql://%s:%d/%s?currentSchema=centerui,public"
                .formatted(dbMapping.host(), dbMapping.port(), DB_NAME);
        var username = readContainerProperty("spring.datasource.username");
        var password = readContainerProperty("spring.datasource.password");

        try (var conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO centerui.apikey (id, encodedkey)
                    SELECT ?, ?
                    WHERE NOT EXISTS (SELECT 1 FROM centerui.apikey WHERE encodedkey = ?)
                    """)) {
                stmt.setLong(1, id);
                stmt.setString(2, encodedKey);
                stmt.setString(3, encodedKey);
                stmt.execute();
            }
            for (String role : roles) {
                try (var stmt = conn.prepareStatement("""
                        INSERT INTO centerui.apikey_roles (id, apikey_id, role)
                        SELECT nextval('centerui.apikey_roles_id_seq'), a.id, ?
                        FROM centerui.apikey a
                        WHERE a.encodedkey = ?
                          AND NOT EXISTS (
                              SELECT 1 FROM centerui.apikey_roles r WHERE r.apikey_id = a.id AND r.role = ?)
                        """)) {
                    stmt.setString(1, role);
                    stmt.setString(2, encodedKey);
                    stmt.setString(3, role);
                    stmt.execute();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed API key (id=" + id + ") into CS DB", e);
        }
    }

    private String readContainerProperty(String key) {
        var result = stack.execInContainer(CsApiTestContainerSetup.CS,
                "awk", "/^" + key + "/ {print $3}", DB_PROPERTIES_PATH);
        return result.getStdout().trim();
    }
}
