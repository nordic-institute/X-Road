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
package org.niis.xroad.ss.test.api.destructive;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.assertHealthcheckCheckDataString;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.assertHealthcheckNoErrors;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.assertHealthcheckStatus;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.healthcheckUrl;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Verifies that after wiping all signer keys and HSM tokens then re-seeding the registered
 * auth/sign key set via the serverconf database, the proxy healthcheck reports no errors.
 *
 * <p>This test runs on the disposable-stack destructive lane — it directly mutates the signer key
 * registry in the database.
 */
@Slf4j
@DisplayName("Proxy healthcheck: AUTH key wipe and re-seed")
@SuppressWarnings({"checkstyle:magicnumber", "checkstyle:LineLength"})
class AuthKeyWipeReseedTest extends SsSharedStackDestructiveTest {

    private static final int UI_PORT = 4000;
    private static final String TOKEN_ID = "0";
    private static final String DB = "serverconf";
    private static final String DB_USER = "postgres";

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(90);

    private static final String AUTH_KEY_ID = "DF9242D3CBDE6DAC8058D2878340C3B527041FD0";
    private static final String AUTH_CERT_ID = "5BC622B62052EE89F2020C2FA91872CB49EB1502";
    private static final String SIGN_KEY_1_ID = "1342B84B4829BB79226AB268B4D8E70B01068613";
    private static final String SIGN_CERT_1_ID = "15A0AFEE2602D2846621118997E268F5FA843C94";
    private static final String SIGN_KEY_2_ID = "FA73509F9E9DFB7A3D92B3D34DA6BD20374A24B0";
    private static final String SIGN_CERT_2_ID = "2383ECC7DCE9C81826F99FC79FE96393A342FE42";

    private static final String AUTH_KEY_PUBLIC =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApp1Ls34vBfJkD2bHtmnvb1HxhMBoBPP8rvwtcjGfVCTA7i+DlF3gTLV49k81FMi5gRHQNWLde1"
                    + "NmLTKTzFSoPUerCT7ohvTCTAm4h5W/328xoMo6m2h/nGyuIoAIIUJi/CKf+Ih+zZCklsZqWaOd1f1QIPJOtjQkoMl+2olj2tw1o4/Biim8B03aVTYXfkGh"
                    + "DRC2D6nZJm4Gi9EBZ+USMEAO6CCFobGLLThomWkHDUxjliSGsT4EJA3iR4h9gSuOfMpqHZv5/lY4X4axsR90c8oFEYMfuk9oZSL/dE0oqYpODW1mW7hEm/"
                    + "8afUfTR/8ZtGsvYZFT70VcGcYNNdfoxwIDAQAB";

    private static final String SIGN_KEY_1_PUBLIC =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArSTwszL4sROAQbi6WSuPoQ3+K/dPQoPTdLK/dZvCMkiWW5UmwZRx0PHCjNwUX+FtCYZZ6GF0V"
                    + "/9yrCwMvud+WAuKct/5n9bJLq+FXijupEvhXeyC0I/r6NaOUWK2jyXdMMdQOoBXojQTkNHECj/v7C3NZgHG0QDaXcLvLEJeL8tpec+9qctF0wyKiMvnN9"
                    + "hXiPYG3s9cOEouOn3QL+VYI02Hz/y3zxwDHFiGJ4FAHv2nxnYnhZgeCn5FVeH6aa1IUuS9YEAaqmYSCG6hOsaV5PiPiy51ZmsI8j8KpYTti79ejjN9TuG"
                    + "iEfk1gTPod2iv43sQiszZpcm89kwF3ZHCIwIDAQAB";

    private static final String SIGN_KEY_2_PUBLIC =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwvzMECjq7ImY9NHu6pGJsAQ1JliHd7KSASVf40WTBEbeIOlPTLKHQeZwxzTWZ2kzuUlmKmPY9"
                    + "S9jVhyJUrimB0vvqp1vu3UfTX9grJ4JyDXojn/gJfKeNmUTILWm+BU+VVv26UhOSMQKxZnX7ow+4NTy1tQWLRscTKjiMf3JtcI2HM7DpedBTHqGziCQzX"
                    + "9jQSSpfag95LEnUv2UwKwtSK2q/CS/TYSWbUCjLv/LAlV26qh9fSWAzgM9UqxxIUWsV1OPUoSUpDBC/SsuP365Bz8n9qRdt17mDE3bVjWiKOSAeiHMmcM"
                    + "EDrRLG0ajasfHZnQeYMQqrBc+rsZLk3cn4QIDAQAB";

    @Test
    @DisplayName("proxy healthcheck has no errors after key wipe and re-seed of auth/sign keys")
    void healthcheckOkAfterKeyWipeAndReseed(SsApiTestContainerSetup stack) {
        var healthUrl = healthcheckUrl(stack);
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, UI_PORT);
        var uiBaseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());

        given("admin user is bootstrapped on the disposable stack", () ->
                bootstrapAdminUser(uiBaseUrl));

        given("proxy healthcheck has no errors before key wipe", () ->
                assertHealthcheckNoErrors(healthUrl, POLL_INTERVAL, POLL_TIMEOUT));

        when("HSM tokens are removed from the serverconf database", () ->
                execSql(stack, "DELETE FROM signer_tokens WHERE type != 'softToken'"));

        and("all signer keys are removed from the serverconf database", () ->
                execSql(stack, "DELETE FROM signer_keys"));

        and("keystore and cert files are copied to the database container", () ->
                copyKeystoreAndCertFiles(stack));

        and("auth key and cert are inserted into the signer token", () -> {
            insertKey(stack, AUTH_KEY_ID, "Auth key", "AUTHENTICATION", AUTH_KEY_PUBLIC);
            insertCert(stack, AUTH_CERT_ID, AUTH_KEY_ID, null);
        });

        and("sign key 1 and cert are inserted for member DEV:COM:1234", () -> {
            insertKey(stack, SIGN_KEY_1_ID, SIGN_KEY_1_ID, "SIGNING", SIGN_KEY_1_PUBLIC);
            insertCert(stack, SIGN_CERT_1_ID, SIGN_KEY_1_ID, "DEV:COM:1234");
        });

        and("sign key 2 and cert are inserted for member DEV:COM:4321", () -> {
            insertKey(stack, SIGN_KEY_2_ID, SIGN_KEY_2_ID, "SIGNING", SIGN_KEY_2_PUBLIC);
            insertCert(stack, SIGN_CERT_2_ID, SIGN_KEY_2_ID, "DEV:COM:4321");
        });

        and("signer is restarted to pick up the new key set", () ->
                stack.restartService(SsApiTestContainerSetup.SIGNER));

        and("the software token is logged in via admin API", () -> {
            var session = new org.niis.xroad.ss.test.api.admin.AdminApiSession(uiBaseUrl);
            loginToken(session);
        });

        then("PROXY_AUTH_KEY_OCSP_READINESS_CHECK recovers to UP with status OK after re-seed", () ->
                await()
                        .pollDelay(Duration.ZERO)
                        .pollInterval(POLL_INTERVAL)
                        .atMost(POLL_TIMEOUT)
                        .untilAsserted(() -> {
                            assertHealthcheckStatus(healthUrl, "PROXY_AUTH_KEY_OCSP_READINESS_CHECK", "UP");
                            assertHealthcheckCheckDataString(healthUrl, "PROXY_AUTH_KEY_OCSP_READINESS_CHECK", "status", "OK");
                        }));

        and("the proxy healthcheck has no errors after key re-seed", () ->
                assertHealthcheckNoErrors(healthUrl, POLL_INTERVAL, POLL_TIMEOUT));
    }

    private void copyKeystoreAndCertFiles(SsApiTestContainerSetup stack) {
        stack.copyFilesToContainer(
                SsApiTestContainerSetup.DB_SERVERCONF,
                "files/keystores",
                "/tmp/keystores");
    }

    private void insertKey(SsApiTestContainerSetup stack, String externalId, String friendlyName,
                           String usage, String publicKey) {
        var sql = """
                INSERT INTO signer_keys (external_id, token_id, type, public_key, keystore,
                                        sign_mechanism_name, friendly_name, label, usage)
                VALUES ('%s',
                        (SELECT id FROM signer_tokens WHERE external_id = '%s'),
                        'SOFTWARE',
                        '%s',
                        pg_read_binary_file('/tmp/keystores/%s.p12'),
                        'CKM_RSA_PKCS',
                        '%s', '%s', '%s')
                """.formatted(externalId, TOKEN_ID, publicKey,
                externalId, friendlyName, friendlyName, usage);
        execSql(stack, sql);
    }

    private void insertCert(SsApiTestContainerSetup stack, String certExternalId,
                            String keyExternalId, String memberId) {
        var certFile = "/tmp/keystores/certs/%s.pem".formatted(certExternalId);
        String memberIdExpr;
        if (memberId == null) {
            memberIdExpr = "NULL";
        } else {
            var parts = memberId.split(":");
            memberIdExpr = """
                    (SELECT id FROM identifier
                     WHERE object_type = 'MEMBER'
                       AND xroad_instance = '%s'
                       AND member_class = '%s'
                       AND member_code = '%s')
                    """.formatted(parts[0], parts[1], parts[2]);
        }

        var sql = """
                INSERT INTO signer_certificates (external_id, key_id, data, status, active, member_id)
                VALUES ('%s',
                        (SELECT id FROM signer_keys WHERE external_id = '%s'),
                        pg_read_binary_file('%s'),
                        'registered', true,
                        %s)
                """.formatted(certExternalId, keyExternalId, certFile, memberIdExpr);
        execSql(stack, sql);
    }

    private void execSql(SsApiTestContainerSetup stack, String sql) {
        try {
            var result = stack.execInContainer(
                    SsApiTestContainerSetup.DB_SERVERCONF,
                    "psql", "-U", DB_USER, DB, "-c", sql);
            log.debug("psql output: {}", result.getStdout());
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("psql failed: " + result.getStderr());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute SQL in db container", e);
        }
    }

    private void bootstrapAdminUser(String baseUrl) {
        var status = org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory.given()
                .contentType("application/json")
                .body("""
                        {"username":"xrd","password":"secret123!"}
                        """)
                .post(baseUrl + "/api/v1/initialization/admin-user")
                .statusCode();
        if (status != 201 && status != 409) {
            throw new IllegalStateException("Unexpected status bootstrapping admin user: " + status);
        }
    }

    private void loginToken(org.niis.xroad.ss.test.api.admin.AdminApiSession session) {
        var status = session.given()
                .contentType("application/json")
                .body("""
                        {"password":"Secret1234"}
                        """)
                .put("/tokens/0/login")
                .statusCode();
        if (status != 200 && status != 409) {
            throw new IllegalStateException("Unexpected status logging in software token: " + status);
        }
    }
}
