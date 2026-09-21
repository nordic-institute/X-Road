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
package org.niis.xroad.cs.test.api.destructive;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.DsTlsCertificateAdminClient;
import org.niis.xroad.cs.test.api.admin.DsTlsCertificationAuthoritiesAdminClient;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;

import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * Verifies the Central Server DS TLS ACME renewal worker's auth/sign rule against the project's test ACME
 * server (acme2certifier), routed through the real public nginx config on {@code cs-admin-service}'s port 80 -
 * not a direct connection to the internal challenge listener. Central Server has no dedicated port-80 connector
 * the way Security Server does: nginx already binds that port for globalconf distribution, so proving the
 * renewal path here also proves the nginx {@code /.well-known/acme-challenge/} proxy rule actually works.
 * <p>
 * Every OpenBao path the flow touches (the certificate/key slot, the ACME account keypair, and the
 * enrollment-outcome bookkeeping) is stood in for by the stack's stateless MockServer instance, the same as
 * every other DS TLS test in this module (see {@code DsTlsCertificateApiTest}). A static HTTP mock cannot echo
 * back what a prior write put there: a GET always replays the same fixed response regardless of what any POST
 * just sent. That is enough to prove the worker never enrolls a first certificate on its own (a GET that always
 * reports "no certificate" makes that assertion trivially and permanently true) and it is enough to read the
 * outcome of an administrator-triggered order directly off that call's own synchronous response body - but it
 * is not enough to prove that a further worker cycle leaves an already-stored, not-due certificate's bookkeeping
 * untouched, since no subsequent read can ever reflect what the worker (or the order before it) actually wrote.
 * That last, genuinely stateful assertion is covered on the Security Server side instead, whose stack runs a
 * real {@code openbao} container (see {@code DsTlsCertificateLifecycleDestructiveTest}).
 * <p>
 * Runs on the destructive lane, not the shared parallel stack: several Phase 1 tests
 * ({@code DsTlsCertificationAuthoritiesApiTest}) deliberately designate DS TLS CAs with an ACME server URL to
 * exercise the admin API's own field handling, and never clean them up - left in place, those would interfere
 * with these tests' own CA designation.
 */
@DisplayName("DS TLS ACME certificate renewal")
@SuppressWarnings("checkstyle:magicnumber")
class DsTlsAcmeEnrollmentTest extends CsApiTest {

    private static final String CS_SERVICE = "cs-admin-service";
    private static final String ADMIN_SERVICE_LOG = "/var/log/xroad/centralserver-admin-service.log";

    private static final String PUBLIC_HOSTNAME = "cs-admin-service";
    private static final String ACME_DIRECTORY_URL = "http://testca:8887";
    private static final String DS_TLS_PROFILE_ID = "ds-tls-profile";
    // Must match the CA name under which 50-api-test.properties seeds the "dataspace-tls" EAB credential.
    private static final String CA_NAME = "Test DS TLS CA";

    // Every OpenBao path the DS TLS ACME flow touches: the certificate/key slot itself, the ACME account
    // keypair (path derived from the constant "dataspace-tls" EAB alias, base64: ZGF0YXNwYWNlLXRscw), and
    // the enrollment-outcome bookkeeping every cycle reads and conditionally rewrites regardless of outcome.
    private static final String VAULT_SECRET_PATH = "/v1/xrd-secret/tls/ds-https";
    private static final String VAULT_ACCOUNT_KEY_PATH = "/v1/xrd-secret/acme/account-keys/ZGF0YXNwYWNlLXRscw";
    private static final String VAULT_ENROLLMENT_STATUS_PATH = "/v1/xrd-secret/tls/ds-https-enrollment-status";
    private static final List<String> MOCKED_VAULT_PATHS =
            List.of(VAULT_SECRET_PATH, VAULT_ACCOUNT_KEY_PATH, VAULT_ENROLLMENT_STATUS_PATH);

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(3);
    // acme-renewal-interval is shortened to 15s for this module (see 50-api-test.properties) specifically so
    // this wait is bounded; the worker's first post-startup cycle plus a full ACME order (account creation,
    // HTTP-01 challenge via nginx, order finalization) is comfortably covered by 100s.
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(100);

    @Test
    @Tag("destructive")
    @ResourceLock("ds-tls-acme-capable-cas")
    @DisplayName("With no stored certificate, the worker never enrolls one on its own even once an ACME-capable "
            + "DS TLS CA is designated and its own schedule has elapsed")
    void dsTlsCertificateIsNeverSilentlyEnrolled(CsBaselineSeeder seeder, BaseComposeSetup stack) throws Exception {
        var caClient = new DsTlsCertificationAuthoritiesAdminClient(seeder.newSession());
        var dsTlsClient = new DsTlsCertificateAdminClient(seeder.newSession());

        given("every DS TLS certification authority left over from other tests with an ACME server configured "
                + "is removed", () -> removeAcmeCapableCertificationAuthorities(caClient));

        given("exactly one ACME-capable DS TLS CA, pointed at the project's test ACME server, is designated",
                () -> caClient.addDsTlsCertificationAuthority(
                                seeder.generateCertForServer("dstlsacme01-ca"), CA_NAME, ACME_DIRECTORY_URL, DS_TLS_PROFILE_ID)
                        .statusCode(201));

        given("every OpenBao path the worker touches is mocked empty on read and accepting on write", () -> {
            for (String path : MOCKED_VAULT_PATHS) {
                seeder.mockExpectation(notFoundMock(path));
                seeder.mockExpectation(postOkMock(path));
            }
        });

        try {
            then("across a full worker cycle no certificate is ever silently enrolled", () -> {
                await().pollDelay(POLL_TIMEOUT).atMost(POLL_TIMEOUT.plus(POLL_INTERVAL).plusSeconds(10))
                        .until(() -> true);
                int exitCode = stack.execInContainer(CS_SERVICE, "grep", "-Eq",
                                "DS TLS certificate successfully (renewed|enrolled) via ACME for " + PUBLIC_HOSTNAME,
                                ADMIN_SERVICE_LOG)
                        .getExitCode();
                assertThat(exitCode).isNotZero();
            });

            and("the enrollment status still reports no method configured and no error", () ->
                    dsTlsClient.getEnrollmentStatus()
                            .statusCode(200)
                            .body("enrollment_method", equalTo("NONE"))
                            .body("last_error", nullValue()));
        } finally {
            MOCKED_VAULT_PATHS.forEach(seeder::clearMockExpectations);
        }
    }

    @Test
    @Tag("destructive")
    @ResourceLock("ds-tls-acme-capable-cas")
    @DisplayName("Ordering the DS TLS certificate synchronously stores a chain whose subject and SAN equal the input")
    void orderStoresACertificateFromTheNamedAcmeCapableCa(CsBaselineSeeder seeder) {
        var caClient = new DsTlsCertificationAuthoritiesAdminClient(seeder.newSession());
        var dsTlsClient = new DsTlsCertificateAdminClient(seeder.newSession());
        var keyPair = generateRsaKeyPair();
        // The real test ACME server validates this over HTTP-01, so it must resolve on the compose network -
        // cs-admin-service (this test class's own PUBLIC_HOSTNAME) is the only name the network actually gives it.
        var multiAttributeDn = "C=FI, O=X-Road Test, OU=X-Road Test CA OU, CN=" + PUBLIC_HOSTNAME;
        var subjectAltName = PUBLIC_HOSTNAME;

        given("every DS TLS certification authority left over from other tests with an ACME server configured "
                + "is removed, so no stale designation interferes with this order", () ->
                removeAcmeCapableCertificationAuthorities(caClient));

        given("exactly one ACME-capable DS TLS CA, pointed at the project's test ACME server, is designated",
                () -> caClient.addDsTlsCertificationAuthority(
                                seeder.generateCertForServer("dstlsacme02-ca"), CA_NAME, ACME_DIRECTORY_URL, DS_TLS_PROFILE_ID)
                        .statusCode(201));

        given("the DS TLS vault slot already holds a private key and every touched vault path accepts writes", () -> {
            seeder.mockExpectation(vaultGetKeyOnlyMock(keyPair));
            seeder.mockExpectation(postOkMock(VAULT_SECRET_PATH));
            seeder.mockExpectation(notFoundMock(VAULT_ACCOUNT_KEY_PATH));
            seeder.mockExpectation(postOkMock(VAULT_ACCOUNT_KEY_PATH));
            seeder.mockExpectation(postOkMock(VAULT_ENROLLMENT_STATUS_PATH));
        });

        try {
            // The OpenBao paths below are a stateless MockServer stand-in (see the class Javadoc): a GET always
            // replays the fixed vaultGetKeyOnlyMock response regardless of what this order's POST just wrote, so
            // the issued certificate's own subject and SAN are asserted directly against the order's synchronous
            // response body - the same CertificateDetails the (unreadable-back-here) stored chain was built from -
            // rather than by re-reading it through a subsequent status call.
            then("ordering with a multi-attribute DN and a SAN returns the issued certificate's subject and SAN", () ->
                    dsTlsClient.orderCertificate(CA_NAME, multiAttributeDn, subjectAltName)
                            .statusCode(200)
                            .body("subject_distinguished_name", equalTo(multiAttributeDn))
                            .body("subject_alternative_names", equalTo("DNS:" + subjectAltName))
                            .body("hash", notNullValue()));

            and("the enrollment status reports ACME availability with the ordering CA listed and a scheduled "
                    + "next renewal", () ->
                    dsTlsClient.getEnrollmentStatus()
                            .statusCode(200)
                            .body("enrollment_method", equalTo("ACME"))
                            .body("next_renewal_time", notNullValue())
                            .body("acme_available", equalTo(true))
                            .body("acme_cas.name", hasItem(CA_NAME)));
        } finally {
            MOCKED_VAULT_PATHS.forEach(seeder::clearMockExpectations);
        }
    }

    @Test
    @Tag("destructive")
    @ResourceLock("ds-tls-acme-capable-cas")
    @DisplayName("Manual upload after an order is accepted for the same key, and a further worker cycle logs "
            + "no renewal or enrollment for it")
    void manualUploadAfterAnOrderIsAcceptedAndNeverRenewedOnAFurtherCycle(CsBaselineSeeder seeder, BaseComposeSetup stack)
            throws Exception {
        var caClient = new DsTlsCertificationAuthoritiesAdminClient(seeder.newSession());
        var dsTlsClient = new DsTlsCertificateAdminClient(seeder.newSession());
        var keyPair = generateRsaKeyPair();
        var manualSubjectDn = "CN=ds-manual-upload.example.org";

        given("every DS TLS certification authority left over from other tests with an ACME server configured "
                + "is removed", () -> removeAcmeCapableCertificationAuthorities(caClient));

        given("exactly one ACME-capable DS TLS CA, pointed at the project's test ACME server, is designated",
                () -> caClient.addDsTlsCertificationAuthority(
                                seeder.generateCertForServer("dstlsacme04-ca"), CA_NAME, ACME_DIRECTORY_URL, DS_TLS_PROFILE_ID)
                        .statusCode(201));

        given("the DS TLS vault slot already holds a private key and every touched vault path accepts writes", () -> {
            seeder.mockExpectation(vaultGetKeyOnlyMock(keyPair));
            seeder.mockExpectation(postOkMock(VAULT_SECRET_PATH));
            seeder.mockExpectation(notFoundMock(VAULT_ACCOUNT_KEY_PATH));
            seeder.mockExpectation(postOkMock(VAULT_ACCOUNT_KEY_PATH));
            seeder.mockExpectation(postOkMock(VAULT_ENROLLMENT_STATUS_PATH));
        });

        try {
            // Real HTTP-01 validation against the test ACME server, so the SAN has to resolve on the compose
            // network - cs-admin-service is the only name it actually gives this stack (see PUBLIC_HOSTNAME).
            // Only this setup order goes through ACME; the manual upload below never touches the network, so
            // its own subject can stay a fictional name.
            given("an ACME order has already stored a certificate for this key", () ->
                    dsTlsClient.orderCertificate(CA_NAME, "CN=" + PUBLIC_HOSTNAME, PUBLIC_HOSTNAME)
                            .statusCode(200));

            // As documented on the class: the vault GET mock cannot reflect this write either, so "method
            // MANUAL, no next renewal" is only observable through the upload's own synchronous response, and a
            // further worker cycle can only show that no renewal is logged - not that a stored hash stayed
            // byte-for-byte equal (that stronger, genuinely stateful check lives on the Security Server side).
            then("a manual upload for the same key is accepted and its own response carries the uploaded "
                    + "certificate's subject", () ->
                    dsTlsClient.uploadCertificate(selfSignedCertificatePem(keyPair, manualSubjectDn))
                            .statusCode(200)
                            .body("subject_distinguished_name", equalTo(manualSubjectDn)));

            and("across a further worker cycle, no renewal is ever logged for the manually uploaded certificate",
                    () -> {
                        await().pollDelay(POLL_TIMEOUT).atMost(POLL_TIMEOUT.plus(POLL_INTERVAL).plusSeconds(10))
                                .until(() -> true);
                        int exitCode = stack.execInContainer(CS_SERVICE, "grep", "-Eq",
                                        "DS TLS certificate successfully (renewed|enrolled) via ACME for "
                                                + "ds-manual-upload.example.org",
                                        ADMIN_SERVICE_LOG)
                                .getExitCode();
                        assertThat(exitCode).isNotZero();
                    });
        } finally {
            MOCKED_VAULT_PATHS.forEach(seeder::clearMockExpectations);
        }
    }

    @Test
    @Tag("destructive")
    @ResourceLock("ds-tls-acme-capable-cas")
    @DisplayName("Ordering with a malformed distinguished name returns 400 invalid_distinguished_name")
    void orderFailsWithAMalformedDistinguishedName(CsBaselineSeeder seeder) {
        var caClient = new DsTlsCertificationAuthoritiesAdminClient(seeder.newSession());
        var dsTlsClient = new DsTlsCertificateAdminClient(seeder.newSession());
        var keyPair = generateRsaKeyPair();

        given("every DS TLS certification authority left over from other tests with an ACME server configured "
                + "is removed, so no stale designation interferes with this order", () ->
                removeAcmeCapableCertificationAuthorities(caClient));

        given("exactly one ACME-capable DS TLS CA, pointed at the project's test ACME server, is designated",
                () -> caClient.addDsTlsCertificationAuthority(
                                seeder.generateCertForServer("dstlsacme03-ca"), CA_NAME, ACME_DIRECTORY_URL, DS_TLS_PROFILE_ID)
                        .statusCode(201));

        given("the DS TLS vault slot already holds a private key", () ->
                seeder.mockExpectation(vaultGetKeyOnlyMock(keyPair)));

        try {
            then("ordering with a malformed distinguished name returns 400 invalid_distinguished_name", () ->
                    dsTlsClient.orderCertificate(CA_NAME, "not a distinguished name", "ds-order.example.org")
                            .statusCode(400)
                            .body("error.code", equalTo("invalid_distinguished_name")));
        } finally {
            seeder.clearMockExpectations(VAULT_SECRET_PATH);
        }
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair for DS TLS order test", e);
        }
    }

    private static byte[] selfSignedCertificatePem(KeyPair keyPair, String subjectDn) {
        try {
            var subject = new X500Name(subjectDn);
            var certBuilder = new JcaX509v3CertificateBuilder(
                    subject,
                    BigInteger.valueOf(System.nanoTime()),
                    Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                    Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                    subject,
                    keyPair.getPublic());
            var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));

            var certWriter = new StringWriter();
            try (var pemWriter = new PemWriter(certWriter)) {
                pemWriter.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
            }
            return certWriter.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build self-signed DS TLS certificate for manual upload test", e);
        }
    }

    private static String vaultGetKeyOnlyMock(KeyPair keyPair) {
        try {
            var keyWriter = new StringWriter();
            try (var pemWriter = new PemWriter(keyWriter)) {
                pemWriter.writeObject(new PemObject("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
            }
            return """
                    {
                      "httpRequest": {"method": "GET", "path": "%s"},
                      "httpResponse": {
                        "statusCode": 200,
                        "headers": {"Content-Type": ["application/json"]},
                        "body": {
                          "type": "JSON",
                          "json": {
                            "renewable": false,
                            "lease_duration": 0,
                            "data": {
                              "certificate": "",
                              "privateKey": "%s"
                            }
                          }
                        }
                      }
                    }
                    """.formatted(VAULT_SECRET_PATH, keyWriter.toString().replace("\r\n", "\\n").replace("\n", "\\n"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build vault GET mock for DS TLS order test", e);
        }
    }

    private static void removeAcmeCapableCertificationAuthorities(DsTlsCertificationAuthoritiesAdminClient caClient) {
        List<Integer> ids = caClient.listDsTlsCertificationAuthorities()
                .extract().jsonPath().getList("id", Integer.class);
        for (Integer id : ids) {
            String acmeUrl = caClient.getDsTlsCertificationAuthority(id)
                    .extract().jsonPath().getString("acme_server_directory_url");
            if (isNotBlank(acmeUrl)) {
                caClient.deleteDsTlsCertificationAuthority(id);
            }
        }
    }

    private static String notFoundMock(String path) {
        return """
                {
                  "httpRequest": {"method": "GET", "path": "%s"},
                  "httpResponse": {"statusCode": 404}
                }
                """.formatted(path);
    }

    private static String postOkMock(String path) {
        return """
                {
                  "httpRequest": {"method": "POST", "path": "%s"},
                  "httpResponse": {"statusCode": 200, "headers": {"Content-Type": ["application/json"]}}
                }
                """.formatted(path);
    }
}
