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
package org.niis.xroad.cs.test.api.issuerservice;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.ResourceLocks;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.IssuerServiceAdminClient;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for the Central Server Issuer Service host.
 */
@SuppressWarnings("checkstyle:magicnumber")
class IssuerServiceApiTest extends CsApiTest {

    /** Shared with {@code DsTlsCertificateApiTest}: both read the one connector TLS certificate slot. */
    private static final String VAULT_SECRET_PATH = "/v1/xrd-secret/tls/ds-https";
    private static final String VAULT_RESOURCE_LOCK = "ds-tls-vault-slot";
    private static final String LEASE_OWNER_NODE = "issuer-status-api-test";

    /**
     * This stack runs no Issuer Service: {@link org.niis.xroad.cs.admin.core.dataspace.IssuerProvisioningRpcClient
     * #canSign} always falls back to its deadline, so a "can sign" probe never answers quicker than that - a
     * generous margin above it still catches a probe that hangs instead of failing fast.
     */
    private static final Duration CAN_SIGN_PROBE_MARGIN = Duration.ofSeconds(10);

    @Test
    @ResourceLock("issuer-service-host")
    void settingTheHostIsReflectedByTheNextGet(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newSecurityOfficerSession());

        // The seeder sets a host before any test runs. Setting that host again keeps the seeded host for
        // the tests that follow; the destructive phase covers changing it to a different host.
        var stateBeforeSet = given("the current issuer service state", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .extract().jsonPath());

        var host = stateBeforeSet.getString("host");

        if (!stateBeforeSet.getBoolean("bound")) {
            when("the current host is set again", () ->
                    issuerService.setIssuerServiceHost(host)
                            .statusCode(200)
                            .body("host", equalTo(host))
                            .body("bound", equalTo(false)));

            then("a following get returns the same host", () ->
                    issuerService.getIssuerServiceState()
                            .statusCode(200)
                            .body("host", equalTo(host))
                            .body("bound", equalTo(false)));
        } else {
            then("a following get still returns the already-bound host", () ->
                    issuerService.getIssuerServiceState()
                            .statusCode(200)
                            .body("host", equalTo(host)));
        }
    }

    @Test
    @ResourceLock(value = "issuer-service-host", mode = ResourceAccessMode.READ)
    void settingTheHostIsForbiddenWithoutThePermission(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newManagementServiceOnlySession());

        then("setting the host is forbidden", () ->
                issuerService.setIssuerServiceHost("cs.example.org")
                        .statusCode(403));
    }

    @Test
    @ResourceLock(value = "issuer-service-host", mode = ResourceAccessMode.READ_WRITE)
    void settingAnInvalidHostIsRejected(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newSecurityOfficerSession());

        var hostBeforeRejection = given("the current issuer service host", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .extract().jsonPath().getString("host"));

        when("an invalid host is set", () ->
                issuerService.setIssuerServiceHost("not a valid host")
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_dns_name")));

        then("the stored host is unchanged", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .body("host", equalTo(hostBeforeRejection)));
    }

    @Test
    @ResourceLock(value = "issuer-service-host", mode = ResourceAccessMode.READ_WRITE)
    void settingLocalhostIsRejected(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newSecurityOfficerSession());

        var hostBeforeRejection = given("the current issuer service host", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .extract().jsonPath().getString("host"));

        when("localhost is set", () ->
                issuerService.setIssuerServiceHost("localhost")
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_dns_name")));

        then("the stored host is unchanged", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .body("host", equalTo(hostBeforeRejection)));
    }

    @Test
    @ResourceLock(value = "issuer-service-host", mode = ResourceAccessMode.READ_WRITE)
    void startingSetUpIsRefusedWhenTheHostIsUnset(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newSecurityOfficerSession());

        var stateBeforeClear = given("the current issuer service state", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .extract().jsonPath());

        // The host can never be unset again once bound, so on a warm stack already bound by an earlier
        // run the host-unset path can no longer be produced.
        assumeFalse(stateBeforeClear.getBoolean("bound"), "the issuer is already bound; the host-unset path cannot be produced");

        var host = stateBeforeClear.getString("host");
        when("the issuer service host is cleared", seeder::clearIssuerServiceHost);
        try {
            then("set-up is refused because the host is unset", () ->
                    issuerService.startSetUp()
                            .statusCode(409)
                            .body("error.code", equalTo("issuer_service_host_not_set")));
        } finally {
            issuerService.setIssuerServiceHost(host).statusCode(200);
        }
    }

    @Test
    @ResourceLock(value = "issuer-service-host", mode = ResourceAccessMode.READ)
    void startingSetUpIsForbiddenWithoutThePermission(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newManagementServiceOnlySession());

        then("starting set-up is forbidden", () ->
                issuerService.startSetUp()
                        .statusCode(403));
    }

    /**
     * The lease is held directly against its Postgres row rather than through a real set-up run, because
     * this stack has no reachable Issuer Service to run one against. Once the issuer identity is bound,
     * holding the lease no longer changes the overall status (it only matters pre-bind), so on a warm
     * stack already bound by an earlier run's destructive phase this outcome can no longer be produced.
     */
    @Test
    @ResourceLocks({
            @ResourceLock(value = "issuer-service-host", mode = ResourceAccessMode.READ),
            @ResourceLock(value = VAULT_RESOURCE_LOCK, mode = ResourceAccessMode.READ_WRITE)
    })
    void statusShowsSettingUpWhileARunHoldsTheLeaseAndRevertsWhenItEnds(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newSecurityOfficerSession());

        var stateBefore = given("the current issuer service state", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .extract().jsonPath());

        assumeFalse(stateBefore.getBoolean("bound"),
                "the issuer is already bound; holding the lease no longer changes the overall status");

        var host = stateBefore.getString("host");
        var certificateMock = validConnectorCertificateMock(host);

        given("a valid connector TLS certificate mocked for the issuer host", () ->
                seeder.mockExpectation(certificateMock));
        try {
            var statusBeforeLease = when("the status is read with no run in progress", () ->
                    issuerService.getIssuerServiceState()
                            .statusCode(200)
                            .body("run_in_progress", equalTo(false))
                            .extract().jsonPath().getString("status"));

            given("a set-up run holds the lease", () -> seeder.acquireIssuerSetUpLease(LEASE_OWNER_NODE));
            try {
                then("the status reports a run in progress and the overall state as setting up", () ->
                        issuerService.getIssuerServiceState()
                                .statusCode(200)
                                .body("run_in_progress", equalTo(true))
                                .body("status", equalTo("SETTING_UP")));
            } finally {
                seeder.releaseIssuerSetUpLease();
            }

            and("the status reverts to its value from before the lease was held", () ->
                    issuerService.getIssuerServiceState()
                            .statusCode(200)
                            .body("run_in_progress", equalTo(false))
                            .body("status", equalTo(statusBeforeLease)));
        } finally {
            seeder.clearMockExpectations(VAULT_SECRET_PATH);
        }
    }

    /**
     * Runs in the destructive phase: binding is permanent, and there is no API to undo it. The issuer
     * identity is bound here by inserting an {@code issuer_dids} row directly, because this stack has no
     * reachable Issuer Service to run set-up against. Idempotent across reruns on a warm stack: if the
     * issuer identity is already bound from an earlier run, the already-bound host is used instead of
     * setting a new one.
     */
    @Test
    @Tag("destructive")
    @ResourceLock(value = "issuer-service-host", mode = ResourceAccessMode.READ_WRITE)
    void aHostChangeIsRefusedOnceTheIssuerIdentityIsBound(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newSecurityOfficerSession());

        var stateBeforeBinding = given("the current issuer service state", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .extract().jsonPath());

        String boundHost = stateBeforeBinding.getString("host");
        if (!stateBeforeBinding.getBoolean("bound")) {
            boundHost = when("a host is set before binding", () ->
                    issuerService.setIssuerServiceHost("issuer.example.org")
                            .statusCode(200)
                            .body("bound", equalTo(false))
                            .extract().jsonPath().getString("host"));

            seeder.bindIssuerIdentity();
        }
        var host = boundHost;

        then("a following get reports the host as bound", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .body("host", equalTo(host))
                        .body("bound", equalTo(true)));

        when("a different host is set after binding", () ->
                issuerService.setIssuerServiceHost("other-" + host)
                        .statusCode(409)
                        .body("error.code", equalTo("issuer_service_already_bound"))
                        .body("error.metadata[0]", equalTo(host)));

        when("the same host is set again after binding", () ->
                issuerService.setIssuerServiceHost(host)
                        .statusCode(409)
                        .body("error.code", equalTo("issuer_service_already_bound")));

        then("the stored host is unchanged and still reported as bound", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .body("host", equalTo(host))
                        .body("bound", equalTo(true)));
    }

    /**
     * Runs in the destructive phase alongside {@link #aHostChangeIsRefusedOnceTheIssuerIdentityIsBound}:
     * binds the issuer identity if it is not already bound (idempotent, see that test for why). This
     * stack runs no Issuer Service, so once bound the "can sign" probe always fails; which cause it
     * reports depends on how the RPC client fails in this tier, so only its presence is asserted here.
     */
    @Test
    @Tag("destructive")
    void statusShowsCannotSignWithACauseWhenTheIssuerServiceIsUnreachable(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newSecurityOfficerSession());

        seeder.bindIssuerIdentity();

        var start = Instant.now();
        then("the status reports cannot-sign with a cause and the overall state as failed, "
                + "within the can-sign deadline", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .body("bound", equalTo(true))
                        .body("can_sign", equalTo(false))
                        .body("can_sign_cause", notNullValue())
                        .body("status", equalTo("FAILED")));
        var elapsed = Duration.between(start, Instant.now());

        assertTrue(elapsed.compareTo(CAN_SIGN_PROBE_MARGIN) < 0,
                "status read took " + elapsed + ", expected it to return within the can-sign deadline");
    }

    /**
     * Builds a MockServer expectation for the shared {@code tls/ds-https} vault slot: a freshly
     * generated, currently valid certificate carrying the given host as its only DNS subject
     * alternative name, the way {@link org.niis.xroad.cs.admin.core.dataspace.ConnectorCertificateCheck}
     * requires it.
     */
    private static String validConnectorCertificateMock(String host) {
        try {
            var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            var keyPair = keyPairGenerator.generateKeyPair();

            var subject = new X500Name("CN=" + host);
            var certificateBuilder = new JcaX509v3CertificateBuilder(
                    subject,
                    BigInteger.valueOf(System.nanoTime()),
                    Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                    Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                    subject,
                    keyPair.getPublic());
            certificateBuilder.addExtension(Extension.subjectAlternativeName, false,
                    new GeneralNames(new GeneralName(GeneralName.dNSName, host)));
            var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            var certificate = new JcaX509CertificateConverter().getCertificate(certificateBuilder.build(signer));

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
                              "certificate": "%s",
                              "privateKey": "%s"
                            }
                          }
                        }
                      }
                    }
                    """.formatted(VAULT_SECRET_PATH,
                    pemEncode("CERTIFICATE", certificate.getEncoded()),
                    pemEncode("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build a connector TLS certificate for the issuer status test", e);
        }
    }

    private static String pemEncode(String type, byte[] encoded) {
        try {
            var writer = new StringWriter();
            try (var pemWriter = new PemWriter(writer)) {
                pemWriter.writeObject(new PemObject(type, encoded));
            }
            return writer.toString().replace("\r\n", "\\n").replace("\n", "\\n");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to PEM-encode a " + type + " for the issuer status test", e);
        }
    }
}
