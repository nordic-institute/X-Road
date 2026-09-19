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
package org.niis.xroad.ss.test.api.destructive;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.ss.test.api.admin.AdminApiSession;
import org.niis.xroad.ss.test.api.admin.DsTlsCertificateAdminClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Verifies the DS TLS ACME renewal worker's auth/sign rule end to end against the project's test ACME server
 * (acme2certifier): the worker never enrolls a first certificate on its own, and once a certificate exists it is
 * renewed only when its issuer is a designated DS TLS CA with an ACME server and it is actually due -
 * administrator-triggered ordering ({@link org.niis.xroad.ss.test.api.keys.AcmeOrderTest} and
 * {@link DsTlsCertificateLifecycleDestructiveTest#orderStoresACertificateFromTheNamedAcmeCapableCa}) is the only
 * way a first certificate is ever obtained.
 * <p>
 * Runs on the destructive lane, not the shared parallel stack: the baseline seeder always pre-provisions a
 * long-lived test-CA-signed certificate into the {@code tls/ds-https} vault slot through the admin API
 * (see {@link org.niis.xroad.ss.test.api.keys.DsTlsCertificateTest}), which the worker correctly treats as not
 * yet due for renewal and leaves alone. Clearing that slot to observe the never-enrolls rule, and replacing its
 * content to observe the not-due rule, are vault-mutating operations only safe once Phase 1's read-only
 * {@code DsTlsCertificateTest} assertions about the seeded certificate have already run.
 */
@DisplayName("DS TLS ACME certificate renewal")
@SuppressWarnings("checkstyle:magicnumber")
class DsTlsAcmeEnrollmentTest extends SsSharedStackDestructiveTest {

    private static final String PUBLIC_HOSTNAME = "ui";
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(3);
    // The worker's post-startup cadence is shortened for this module (compose.api.yaml,
    // xroad.proxy-ui-api.acme-renewal-interval=15s) specifically so this wait is bounded. Measured worst case:
    // the worker's first cycle (~5s after container start) almost always loses the race against globalconf
    // initialization, parking the next attempt a fixed 60s later.
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(100);

    @Test
    @DisplayName("With no stored certificate, the worker never enrolls one on its own even once an ACME-capable "
            + "DS TLS CA is designated and its own schedule has elapsed")
    void dsTlsCertificateIsNeverSilentlyEnrolled(SsApiTestContainerSetup stack) {
        var session = adminSession(stack);
        var dsTlsCertificate = new DsTlsCertificateAdminClient(session);

        given("the pre-seeded DS TLS certificate slot is cleared to an empty, keyed state, so the worker finds "
                + "no current certificate to renew", () ->
                dsTlsCertificate.generateKey().statusCode(201));

        then("across a full worker cycle, no certificate is ever enrolled and no error is recorded", () -> {
            await().pollDelay(POLL_TIMEOUT).atMost(POLL_TIMEOUT.plus(POLL_INTERVAL).plusSeconds(10))
                    .until(() -> true);

            dsTlsCertificate.getStatus()
                    .statusCode(200)
                    .body("key_generated", equalTo(true))
                    .body("certificate", nullValue());

            dsTlsCertificate.getEnrollmentStatus()
                    .statusCode(200)
                    .body("enrollment_method", equalTo("NONE"))
                    .body("last_error", nullValue());
        });
    }

    @Test
    @DisplayName("A certificate manually re-signed by the same designated CA after an ACME order is not yet due "
            + "and is left untouched across a further worker cycle")
    void manualUploadAfterAnAcmeOrderIsLeftUntouchedByAFurtherCycle(SsApiTestContainerSetup stack) throws Exception {
        var session = adminSession(stack);
        var dsTlsCertificate = new DsTlsCertificateAdminClient(session);
        var testCaMapping = stack.getContainerMapping(SsApiTestContainerSetup.TESTCA, Port.TEST_CA);
        var testCaBaseUrl = "http://%s:%d/testca".formatted(testCaMapping.host(), testCaMapping.port());
        var caName = "Test DS TLS CA";

        given("a fresh DS TLS key is generated and an ACME order stores a certificate for it", () -> {
            dsTlsCertificate.generateKey().statusCode(201);
            dsTlsCertificate.orderCertificate(caName, "CN=" + PUBLIC_HOSTNAME, PUBLIC_HOSTNAME).statusCode(200);
        });

        byte[] csrBytes = given("a CSR is generated for the same key and subject", () ->
                dsTlsCertificate.generateCsr("CN=" + PUBLIC_HOSTNAME));

        byte[] manuallySignedCert = when("the CSR is signed out of band by the same test CA the ACME order used",
                () -> signCsrAtTestCa(testCaBaseUrl, csrBytes));

        when("the manually signed certificate is uploaded, replacing the ACME-enrolled one", () ->
                dsTlsCertificate.uploadCertificate(manuallySignedCert).statusCode(200));

        then("the enrollment status now reports MANUAL with no scheduled renewal", () ->
                dsTlsCertificate.getEnrollmentStatus()
                        .statusCode(200)
                        .body("enrollment_method", equalTo("MANUAL"))
                        .body("next_renewal_time", nullValue()));

        String hashAfterUpload = and("the certificate's hash right after upload is recorded", () ->
                dsTlsCertificate.getStatus().statusCode(200).extract().jsonPath().getString("certificate.hash"));

        then("across a further worker cycle the not-due certificate is left alone, its hash unchanged", () -> {
            await().pollDelay(POLL_TIMEOUT).atMost(POLL_TIMEOUT.plus(POLL_INTERVAL).plusSeconds(10))
                    .until(() -> true);

            JsonPath statusAfterCycle = dsTlsCertificate.getStatus().statusCode(200).extract().jsonPath();
            assertThat(statusAfterCycle.getString("certificate.hash")).isEqualTo(hashAfterUpload);
        });
    }

    private AdminApiSession adminSession(SsApiTestContainerSetup stack) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        return new AdminApiSession("https://%s:%d".formatted(uiMapping.host(), uiMapping.port()));
    }

    private byte[] signCsrAtTestCa(String testCaBaseUrl, byte[] csrBytes) {
        return RestAssured.given()
                .relaxedHTTPSValidation()
                .multiPart("certreq", "ds-tls.pem", csrBytes, "application/octet-stream")
                .multiPart("type", "auth")
                .post(testCaBaseUrl + "/sign")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
    }
}
