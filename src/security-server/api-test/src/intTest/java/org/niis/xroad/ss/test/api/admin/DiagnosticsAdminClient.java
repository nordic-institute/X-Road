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
package org.niis.xroad.ss.test.api.admin;

import io.restassured.response.ValidatableResponse;

import java.util.List;
import java.util.Map;

/**
 * RestAssured client for the Security Server diagnostics admin API resources.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class DiagnosticsAdminClient {

    private final AdminApiSession session;

    public DiagnosticsAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Returns global configuration diagnostics.
     */
    public ValidatableResponse getGlobalConf() {
        return session.given()
                .get("/diagnostics/globalconf")
                .then();
    }

    /**
     * Returns OCSP responders diagnostics as a list of raw maps.
     */
    public List<Map<String, Object>> getOcspRespondersRaw() {
        return session.given()
                .get("/diagnostics/ocsp-responders")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
    }

    /**
     * Returns timestamping services diagnostics as a list of raw maps.
     */
    public List<Map<String, Object>> getTimestampingServicesRaw() {
        return session.given()
                .get("/diagnostics/timestamping-services")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
    }

    /**
     * Returns addon-status diagnostics as a raw map.
     */
    public Map<String, Object> getAddonStatusRaw() {
        return session.given()
                .get("/diagnostics/addon-status")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
    }

    /**
     * Returns backup encryption status as a raw map.
     */
    public Map<String, Object> getBackupEncryptionStatusRaw() {
        return session.given()
                .get("/diagnostics/backup-encryption-status")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
    }

    /**
     * Returns message log encryption status as a raw map.
     */
    public Map<String, Object> getMessageLogEncryptionStatusRaw() {
        return session.given()
                .get("/diagnostics/message-log-encryption-status")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
    }

    /**
     * Returns proxy memory usage status as a raw map.
     */
    public Map<String, Object> getProxyMemoryUsageRaw() {
        return session.given()
                .get("/diagnostics/proxy-memory-usage-status")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
    }

    /**
     * Downloads the diagnostics report and returns the raw response for assertion.
     */
    public ValidatableResponse downloadDiagnosticsReport() {
        return session.given()
                .get("/diagnostics/info/download")
                .then();
    }

    /**
     * Returns security server version info as a raw map (includes {@code using_supported_java_version}).
     */
    public Map<String, Object> getVersionInfoRaw() {
        return session.given()
                .get("/system/version")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
    }

    /**
     * Returns mail notification status as a raw map.
     */
    public Map<String, Object> getMailNotificationStatusRaw() {
        return session.given()
                .get("/mail/mail-notification-status")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
    }

    /**
     * Returns authentication certificate registration request status as a raw map.
     */
    public Map<String, Object> getAuthCertReqStatusRaw() {
        return session.given()
                .get("/diagnostics/auth-cert-req-status")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
    }

    /**
     * Returns global configuration download connection statuses as a list of raw maps.
     */
    public List<Map<String, Object>> getGlobalConfStatusRaw() {
        return session.given()
                .get("/diagnostics/global-conf-status")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
    }

    /**
     * Runs an other-security-server connection test with the given parameters and returns the raw response.
     *
     * @param protocolType   service protocol type (REST or SOAP)
     * @param clientId       the source client X-Road identifier
     * @param targetClientId the target subsystem X-Road identifier
     * @param securityServerId the target security server X-Road identifier
     */
    public ValidatableResponse getOtherSecurityServerStatus(String protocolType, String clientId,
                                                             String targetClientId, String securityServerId) {
        return session.given()
                .queryParam("protocol_type", protocolType)
                .queryParam("client_id", clientId)
                .queryParam("target_client_id", targetClientId)
                .queryParam("security_server_id", securityServerId)
                .get("/diagnostics/other-security-server-status")
                .then();
    }
}
