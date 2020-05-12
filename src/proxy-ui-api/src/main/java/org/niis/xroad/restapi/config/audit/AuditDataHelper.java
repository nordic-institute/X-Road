/**
 * The MIT License
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
package org.niis.xroad.restapi.config.audit;

import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import com.google.common.base.Splitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERT_HASHES;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERT_REQUEST_IDS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CLIENT_IDENTIFIER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CLIENT_STATUS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.IS_AUTHENTICATION;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MANAGEMENT_REQUEST_ID;

/**
 * Helpers for setting audit log data properties
 */
@Component
@Slf4j
public class AuditDataHelper {

    private final AuditEventLoggingFacade auditEventLoggingFacade;


    @Autowired
    public AuditDataHelper(AuditEventLoggingFacade auditEventLoggingFacade) {
        this.auditEventLoggingFacade = auditEventLoggingFacade;
    }

    public void put(RestApiAuditProperty auditProperty, Object value) {
        auditEventLoggingFacade.putRequestScopedAuditData(auditProperty, value);
    }

    public void put(IsAuthentication isAuthentication) {
        String auditLoggedValue = getAuditLoggedValue(isAuthentication);
        auditEventLoggingFacade.putRequestScopedAuditData(IS_AUTHENTICATION, auditLoggedValue);
    }

    private String getAuditLoggedValue(IsAuthentication isAuthentication) {
        switch (isAuthentication) {
            case SSLAUTH:
                return "HTTPS";
            case SSLNOAUTH:
                return "HTTPS NO AUTH";
            case NOSSL:
                return "HTTP";
            default:
                throw new IllegalStateException("invalid isAuthentication " + isAuthentication);
        }
    }

    public void put(ClientId clientId) {
        auditEventLoggingFacade.putRequestScopedAuditData(CLIENT_IDENTIFIER, clientId);
    }

    public void putClientStatus(ClientType client) {
        String clientStatus = null;
        if (client != null) {
            clientStatus = client.getClientStatus();
        }
        auditEventLoggingFacade.putRequestScopedAuditData(CLIENT_STATUS, clientStatus);
    }

    public void putManagementRequestId(Integer requestId) {
        auditEventLoggingFacade.putRequestScopedAuditData(MANAGEMENT_REQUEST_ID, requestId);
    }

    public void addCertificateHash(CertificateInfo certificateInfo) {
        String hash = createFormattedHash(certificateInfo);
        auditEventLoggingFacade.addRequestScopedAuditListData(CERT_HASHES, hash);
        auditEventLoggingFacade.putRequestScopedAuditData(CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);
    }

    private String createFormattedHash(CertificateInfo certificateInfo) {
        String hash = null;
        try {
            hash = CryptoUtils.calculateCertHexHash(certificateInfo.getCertificateBytes());
            if (hash != null) {
                hash = hash.toUpperCase();
                hash = String.join(":", Splitter.fixedLength(2).split(hash));
            }
        } catch (Exception e) {
            log.error("audit logging certificate hash forming failed", e);
        }
        return hash;
    }

    public void addCsrId(String id) {
        auditEventLoggingFacade.addRequestScopedAuditListData(CERT_REQUEST_IDS, id);
    }


}
