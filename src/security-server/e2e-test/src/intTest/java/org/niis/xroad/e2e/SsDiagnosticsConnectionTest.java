/*
 * The MIT License
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
package org.niis.xroad.e2e;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.e2e.parity.acl.AclParticipants;
import org.niis.xroad.e2e.parity.acl.RestRequests;
import org.niis.xroad.e2e.parity.acl.RestSender;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Runs the Security Server diagnostics connection test from ss1 to the management subsystem on ss0, as the
 * owner member and as a subsystem, with each internal connection type of the sender. The test goes through
 * the real client proxy of ss1 and the real provider proxy of ss0, so a client proxy that rejects the
 * sender's TLS client certificate shows as a failed test.
 *
 * <p>The proxy caches a client's connection type for the serverconf cache period, so after each change the
 * class polls two calls from the sender through the ss1 proxy until their answers match the type: a plaintext
 * call, and an HTTPS call that presents no client certificate. The proxy rejects the plaintext call with
 * {@code ssl_authentication_failed} for both HTTPS types, and the certificate-less HTTPS call for HTTPS only,
 * so the pair tells the three types apart. The wait is bounded by the cache period plus slack.
 *
 * <p>A fresh stack gives ss1 an internal TLS certificate that OpenBao issued, which the client proxy accepts
 * whichever key manager the diagnostics client uses. So before the cases the class replaces it, through the
 * admin API, with a certificate signed by a throwaway CA, and puts the original back afterwards. The admin
 * service caches the internal TLS key for the serverconf cache period, so the class waits that long after
 * the import.
 *
 * <p>Single-threaded, and ordered after the message log archive test and before the monitoring test. Each
 * sender's connection type is read before the first change and put back, with the same wait, after the last
 * case.
 */
@Slf4j
@Order(250)
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("SS diagnostics - connection test from ss1 to the management subsystem on ss0")
@SuppressWarnings("checkstyle:magicnumber")
class SsDiagnosticsConnectionTest extends E2eTest {

    private static final String SS1_ENV = "ss1";
    private static final String HTTP = "HTTP";
    private static final String HTTPS_NO_AUTH = "HTTPS_NO_AUTH";
    private static final String HTTPS = "HTTPS";
    private static final String SSL_AUTH_FAILED_FAULT_CODE = "server.clientproxy.ssl_authentication_failed";
    private static final String STATUS_CLASS_OK = "OK";
    private static final Duration CONNECTION_TYPE_PROPAGATION_SLACK = Duration.ofSeconds(30);
    private static final List<String> SENDERS = List.of(AclParticipants.MEMBER_OF_CONSUMER,
            AclParticipants.OTHER_CONSUMER);
    private static final List<String> CONNECTION_TYPES = List.of(HTTP, HTTPS_NO_AUTH, HTTPS);

    private static final String INTERNAL_TLS_DISTINGUISHED_NAME = "CN=ss1";
    private static final Duration INTERNAL_TLS_CACHE_MARGIN = Duration.ofSeconds(5);

    private static final Map<String, String> ORIGINAL_CONNECTION_TYPES = new LinkedHashMap<>();
    private static byte[] originalInternalTlsCertificate;

    @BeforeAll
    static void internalTlsCertificateIsNotIssuedByOpenBao(E2eEnvironment env) {
        var baseUrl = AdminApi.adminBaseUrl(env, SS1_ENV);
        var session = AdminApi.login(baseUrl);
        originalInternalTlsCertificate = certificatePem(AdminApi.exportInternalTlsCertificate(baseUrl, session));
        var csr = AdminApi.generateInternalTlsCsr(baseUrl, session, INTERNAL_TLS_DISTINGUISHED_NAME);

        var response = AdminApi.importInternalTlsCertificate(baseUrl, session, signWithThrowawayCa(csr));

        assertThat(response.getStatusCode()).as("import the signed internal TLS certificate: %s", response.asString())
                .isEqualTo(200);
        var cacheWait = env.serverConfCachePeriod().plus(INTERNAL_TLS_CACHE_MARGIN);
        Awaitility.await()
                .alias("the admin service reloads the internal TLS key")
                .pollDelay(cacheWait)
                .atMost(cacheWait.plusSeconds(10))
                .until(() -> true);
    }

    @BeforeAll
    static void readOriginalConnectionTypes(E2eEnvironment env) {
        var baseUrl = AdminApi.adminBaseUrl(env, SS1_ENV);
        var session = AdminApi.login(baseUrl);
        for (var sender : SENDERS) {
            ORIGINAL_CONNECTION_TYPES.put(sender, AdminApi.getConnectionType(baseUrl, session, sender));
        }
        log.info("Connection types before the diagnostics cases: {}", ORIGINAL_CONNECTION_TYPES);
    }

    @AfterAll
    static void connectionTypesAreRestored(E2eEnvironment env) {
        var baseUrl = AdminApi.adminBaseUrl(env, SS1_ENV);
        var session = AdminApi.login(baseUrl);
        assertAll(ORIGINAL_CONNECTION_TYPES.entrySet().stream().map(original -> (Executable) () -> {
            try {
                AdminApi.setConnectionType(baseUrl, session, original.getKey(), original.getValue());
            } finally {
                awaitProxyUsesConnectionType(env, original.getKey(), original.getValue());
            }
        }));
    }

    @AfterAll
    static void internalTlsCertificateIsRestored(E2eEnvironment env) {
        if (originalInternalTlsCertificate == null) {
            return;
        }
        var baseUrl = AdminApi.adminBaseUrl(env, SS1_ENV);
        var session = AdminApi.login(baseUrl);
        var imported = AdminApi.importInternalTlsCertificate(baseUrl, session, originalInternalTlsCertificate);
        if (imported.getStatusCode() != 200) {
            log.warn("Importing the original internal TLS certificate failed with {}, regenerating it: {}",
                    imported.getStatusCode(), imported.asString());
            var regenerated = AdminApi.regenerateInternalTlsCertificate(baseUrl, session);
            assertThat(regenerated.getStatusCode()).as("regenerate the internal TLS certificate: %s",
                    regenerated.asString()).isEqualTo(201);
        }
    }

    static List<Case> cases() {
        return SENDERS.stream()
                .flatMap(sender -> CONNECTION_TYPES.stream().map(type -> new Case(sender, type)))
                .toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void soapAndRestConnectionTestsToManagementSucceed(Case testCase, E2eEnvironment env) {
        var baseUrl = AdminApi.adminBaseUrl(env, SS1_ENV);
        var session = AdminApi.login(baseUrl);

        given("the sender %s has connection type %s".formatted(testCase.sender(), testCase.connectionType()), () -> {
            AdminApi.setConnectionType(baseUrl, session, testCase.sender(), testCase.connectionType());
            awaitProxyUsesConnectionType(env, testCase.sender(), testCase.connectionType());
        });

        for (var protocolType : List.of("SOAP", "REST")) {
            var reply = when("the %s connection test to the management subsystem is run".formatted(protocolType),
                    () -> {
                        var response = AdminApi.getOtherSecurityServerStatus(baseUrl, session, protocolType,
                                testCase.sender(), AclParticipants.MANAGEMENT, AclParticipants.OWNER_SECURITY_SERVER);
                        assertThat(response.getStatusCode()).as("diagnostics call: %s", response.asString())
                                .isEqualTo(200);
                        return response.jsonPath();
                    });

            then("the %s connection test reports %s".formatted(protocolType, STATUS_CLASS_OK), () ->
                    assertThat(reply.getString("status_class"))
                            .as("%s connection test as %s with %s, error code: %s", protocolType,
                                    testCase.sender(), testCase.connectionType(), reply.getString("error.code"))
                            .isEqualTo(STATUS_CLASS_OK));
        }

        and("the sender still has connection type %s".formatted(testCase.connectionType()), () ->
                assertThat(AdminApi.getConnectionType(baseUrl, session, testCase.sender()))
                        .isEqualTo(testCase.connectionType()));
    }

    /**
     * Polls a plaintext and a certificate-less HTTPS {@code listMethods} call from the sender through the ss1
     * proxy until the answers match {@code connectionType}.
     */
    private static void awaitProxyUsesConnectionType(E2eEnvironment env, String sender, String connectionType) {
        var expected = expectedProbes(connectionType);
        var observed = new AtomicReference<ProxyProbes>();
        try {
            Awaitility.await()
                    .alias("the ss1 proxy uses connection type %s for %s".formatted(connectionType, sender))
                    .pollDelay(Duration.ZERO)
                    .pollInterval(DisabledServiceFault.SERVERCONF_CACHE_EXPIRY_POLL_INTERVAL)
                    .timeout(env.serverConfCachePeriod().plus(CONNECTION_TYPE_PROPAGATION_SLACK))
                    .until(() -> {
                        observed.set(probeProxy(env, sender));
                        return expected.equals(observed.get());
                    });
        } catch (ConditionTimeoutException e) {
            fail("the ss1 proxy did not use connection type %s for %s: expected %s, last observed %s"
                    .formatted(connectionType, sender, expected, observed.get()), e);
        }
    }

    private static ProxyProbes expectedProbes(String connectionType) {
        return switch (connectionType) {
            case HTTP -> new ProxyProbes(false, false);
            case HTTPS_NO_AUTH -> new ProxyProbes(true, false);
            case HTTPS -> new ProxyProbes(true, true);
            default -> throw new IllegalArgumentException("Unknown connection type " + connectionType);
        };
    }

    private static ProxyProbes probeProxy(E2eEnvironment env, String sender) {
        var plaintext = env.getContainerMapping(SS1_ENV, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        var plaintextUrl = RestRequests.metaserviceUrl("http://%s:%s".formatted(plaintext.host(), plaintext.port()),
                AclParticipants.MANAGEMENT, "listMethods", null);
        var tls = env.getContainerMapping(SS1_ENV, SsStackSetup.PROXY, SsStackSetup.Port.PROXY_TLS);
        var tlsUrl = RestRequests.metaserviceUrl("https://%s:%s".formatted(tls.host(), tls.port()),
                AclParticipants.MANAGEMENT, "listMethods", null);
        var clientHeader = RestRequests.clientHeader(sender);

        var plaintextBody = RestSender.send("GET", plaintextUrl, clientHeader).body();
        var tlsBody = RestAssuredFactory.givenSilent()
                .header(RestRequests.CLIENT_HEADER, clientHeader)
                .get(tlsUrl)
                .asString();
        return new ProxyProbes(plaintextBody.contains(SSL_AUTH_FAILED_FAULT_CODE),
                tlsBody.contains(SSL_AUTH_FAILED_FAULT_CODE));
    }

    @SneakyThrows
    private static byte[] certificatePem(byte[] tarGz) {
        try (var tar = new TarArchiveInputStream(new GZIPInputStream(new ByteArrayInputStream(tarGz)))) {
            for (var entry = tar.getNextEntry(); entry != null; entry = tar.getNextEntry()) {
                if (entry.getName().endsWith("cert.pem")) {
                    return tar.readAllBytes();
                }
            }
        }
        return fail("the exported internal TLS certificate archive has no cert.pem entry");
    }

    @SneakyThrows
    private static byte[] signWithThrowawayCa(byte[] csrPem) {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var caKeys = generator.generateKeyPair();
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(caKeys.getPrivate());
        try (var parser = new PEMParser(new StringReader(new String(csrPem, StandardCharsets.US_ASCII)))) {
            var csr = new JcaPKCS10CertificationRequest((PKCS10CertificationRequest) parser.readObject());
            var builder = new JcaX509v3CertificateBuilder(new X500Name("CN=throwaway test CA"),
                    BigInteger.valueOf(System.nanoTime()),
                    Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                    Date.from(Instant.now().plus(30, ChronoUnit.DAYS)),
                    csr.getSubject(), csr.getPublicKey())
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            return pem(new JcaX509CertificateConverter().getCertificate(builder.build(signer)));
        }
    }

    @SneakyThrows
    private static byte[] pem(X509Certificate certificate) {
        var writer = new StringWriter();
        try (var pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(certificate);
        }
        return writer.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /** Whether the proxy rejected each probe from a sender with {@code ssl_authentication_failed}. */
    private record ProxyProbes(boolean plaintextRejected, boolean tlsWithoutCertificateRejected) {
    }

    record Case(String sender, String connectionType) {

        @Override
        public String toString() {
            return "%s with %s".formatted(sender, connectionType);
        }
    }
}
