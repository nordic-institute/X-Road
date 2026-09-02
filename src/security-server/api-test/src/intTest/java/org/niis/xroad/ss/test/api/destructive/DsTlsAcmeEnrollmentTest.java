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

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.ss.test.api.admin.AdminApiSession;
import org.niis.xroad.ss.test.api.admin.DataspaceAdminClient;
import org.niis.xroad.ss.test.api.admin.DsTlsCertificateAdminClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Verifies the DS TLS ACME enrollment worker end to end against the project's test ACME server (acme2certifier):
 * with DataSpace enabled and a single ACME-capable DS TLS CA designated in globalconf, a certificate is enrolled
 * automatically on the worker's own schedule — no synchronous admin-API call triggers it, unlike
 * {@link org.niis.xroad.ss.test.api.keys.AcmeOrderTest}.
 * <p>
 * Runs on the destructive lane, not the shared parallel stack: the baseline seeder always provisions a
 * long-lived test-CA-signed certificate into the {@code tls/ds-https} vault slot through the admin API
 * (see {@link org.niis.xroad.ss.test.api.keys.DsTlsCertificateTest}), which the worker correctly treats as not
 * yet due for renewal and leaves alone. Observing a genuine ACME enrollment therefore requires clearing that
 * slot first — a vault-mutating operation only safe once Phase 1's read-only {@code DsTlsCertificateTest}
 * assertions about the provisioned certificate have already run.
 */
@DisplayName("DS TLS ACME certificate enrollment")
@SuppressWarnings("checkstyle:magicnumber")
class DsTlsAcmeEnrollmentTest extends SsSharedStackDestructiveTest {

    private static final String PUBLIC_HOSTNAME = "ui";
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(3);
    // The worker's post-startup cadence is shortened for this module (compose.api.yaml,
    // xroad.proxy-ui-api.acme-renewal-interval=15s) specifically so this wait is bounded. Measured worst case:
    // the worker's first cycle (~5s after container start) almost always loses the race against globalconf
    // initialization, parking the next attempt a fixed 60s later; from generateKey() to a real ACME enrollment
    // completing (account creation, HTTP-01 challenge, order finalization) has been observed to take ~62s.
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(100);

    @Test
    @DisplayName("A DS TLS certificate is auto-enrolled via ACME with SAN matching the configured public hostname")
    void dsTlsCertificateIsAutoEnrolledViaAcme(SsApiTestContainerSetup stack) {
        var session = adminSession(stack);
        var dataspace = new DataspaceAdminClient(session);
        var dsTlsCertificate = new DsTlsCertificateAdminClient(session);

        given("the pre-seeded DS TLS certificate slot is cleared to an empty, keyed state, so the worker's next "
                + "cycle finds no current certificate to treat as already up to date", () ->
                dsTlsCertificate.generateKey().statusCode(201));

        JsonPath enrollmentStatus = when("DataSpace is enabled and a single ACME-capable DS TLS CA is designated "
                + "in the nginx-served globalconf fixture (the worker's own schedule enrolls without any "
                + "synchronous trigger)", () ->
                await()
                        .pollInterval(POLL_INTERVAL)
                        .atMost(POLL_TIMEOUT)
                        .until(() -> dataspace.getTlsCertificateEnrollmentStatus()
                                        .extract()
                                        .jsonPath(),
                                status -> "ACME".equals(status.getString("enrollment_method"))));

        then("the enrollment status reports ACME with a scheduled next renewal and no error", () -> {
            assertThat(enrollmentStatus.getString("enrollment_method")).isEqualTo("ACME");
            assertThat(enrollmentStatus.getString("next_renewal_time")).isNotBlank();
            assertThat(enrollmentStatus.getString("last_error")).isNull();
        });

        JsonPath certificateStatus = when("the DS TLS certificate details are read", () ->
                dsTlsCertificate.getStatus()
                        .statusCode(200)
                        .extract()
                        .jsonPath());

        then("a certificate has been generated and its Subject Alternative Name matches the public hostname", () -> {
            assertThat(certificateStatus.getBoolean("key_generated")).isTrue();
            assertThat(certificateStatus.getString("certificate.subject_alternative_names"))
                    .contains(PUBLIC_HOSTNAME);
            assertThat(certificateStatus.getString("certificate.issuer_distinguished_name")).isNotBlank();
        });
    }

    private AdminApiSession adminSession(SsApiTestContainerSetup stack) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        return new AdminApiSession("https://%s:%d".formatted(uiMapping.host(), uiMapping.port()));
    }
}
