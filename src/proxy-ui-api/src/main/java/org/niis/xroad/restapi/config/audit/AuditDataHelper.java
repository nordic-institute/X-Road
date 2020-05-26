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
import ee.ria.xroad.common.conf.serverconf.model.CertificateType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import com.google.common.base.Splitter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ANCHOR_FILE_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ANCHOR_FILE_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.BACKUP_FILE_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERT_HASHES;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERT_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CLIENT_IDENTIFIER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CLIENT_STATUS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.IS_AUTHENTICATION;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.KEY_FRIENDLY_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.KEY_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.KEY_USAGE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MANAGEMENT_REQUEST_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TOKEN_FRIENDLY_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TOKEN_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TOKEN_SERIAL_NUMBER;

/**
 * Methods for storing various {@link RestApiAuditProperty} values in request scope
 */
@Component
@Slf4j
@Profile("nontest")
public class AuditDataHelper {

    private final AuditEventLoggingFacade auditEventLoggingFacade;
    private final RequestScopedAuditDataHolder requestScopedAuditDataHolder;

    @Autowired
    public AuditDataHelper(AuditEventLoggingFacade auditEventLoggingFacade,
            RequestScopedAuditDataHolder requestScopedAuditDataHolder) {
        this.auditEventLoggingFacade = auditEventLoggingFacade;
        this.requestScopedAuditDataHolder = requestScopedAuditDataHolder;
    }

    /**
     * Put a generic property value
     */
    public void put(RestApiAuditProperty auditProperty, Object value) {
        requestScopedAuditDataHolder.getEventData().put(auditProperty, value);
    }


    /**
     * Add a new item to a property that contains a list of items
     * @param listProperty
     * @param value
     */
    public void addListPropertyItem(RestApiAuditProperty listProperty, Object value) {
        List<Object> data = Collections.synchronizedList(new ArrayList<>());
        requestScopedAuditDataHolder.getEventData().putIfAbsent(listProperty, data);
        List<Object> sharedListData = (List<Object>) requestScopedAuditDataHolder.getEventData()
                .get(listProperty);
        sharedListData.add(value);
    }

    /**
     * Creates a Map that will contain nested audit properties.
     * Map instance is not threadsafe.
     * Map has stable key ordering ({@link LinkedHashMap}).
     * Map is added to audit data with given RestApiAuditProperty key.
     * @param auditProperty
     * @return map for nested audit properties
     */
    public Map<RestApiAuditProperty, Object> putMap(RestApiAuditProperty auditProperty) {
        Map<RestApiAuditProperty, Object> map = new LinkedHashMap<>();
        put(auditProperty, map);
        return map;
    }

    /**
     * Whether current data is associated with the given event
     */
    public boolean dataIsForEvent(RestApiAuditEvent event) {
        RestApiAuditEvent other = requestScopedAuditDataHolder.getAuditEvent();
        return event == other;
    }

    /**
     * put {@link IsAuthentication} value, properly formatted for audit log
     * @param isAuthentication
     */
    public void put(IsAuthentication isAuthentication) {
        String auditLoggedValue = getAuditLoggedValue(isAuthentication);
        put(IS_AUTHENTICATION, auditLoggedValue);
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

    /**
     * put {@link ClientId}
     * @param clientId
     */
    public void put(ClientId clientId) {
        put(CLIENT_IDENTIFIER, clientId);
    }

    /**
     * put status of given client
     */
    public void putClientStatus(ClientType client) {
        String clientStatus = null;
        if (client != null) {
            clientStatus = client.getClientStatus();
        }
        put(CLIENT_STATUS, clientStatus);
    }

    /**
     * put management request id
     */
    public void putManagementRequestId(Integer requestId) {
        put(MANAGEMENT_REQUEST_ID, requestId);
    }

    /**
     * Add cert hash to _list property_ CERT_HASHES and put cert algo to regular property
     * @param certificateInfo
     */
    public void addCertificateHash(CertificateInfo certificateInfo) {
        String hash = createFormattedHash(certificateInfo.getCertificateBytes());
        addListPropertyItem(CERT_HASHES, hash);
        putDefaulCerttHashAlgorithm();
    }

    /**
     * calculates hash, formats it according to audit log format, and puts hash and default hash algorithm
     * @param certificate certificate
     */
    public void putCertificateHash(X509Certificate certificate) {
        try {
            putAlreadyFormattedCertificateHash(createFormattedHash(certificate.getEncoded()));
        } catch (CertificateEncodingException e) {
            log.error("Unable to audit log generated certificate hash", e);
        }
    }

    /**
     * calculates hash, formats it according to audit log format, and puts hash and hash algorithm properties
     * @param bytes anchor bytes
     */
    public void putAnchorHash(byte[] bytes) {
        String algorithm = CryptoUtils.DEFAULT_ANCHOR_HASH_ALGORITHM_ID;
        String hash = null;
        try {
            hash = CryptoUtils.hexDigest(algorithm, bytes);
        } catch (Exception e) {
            log.error("audit logging certificate hash forming failed", e);
        }
        put(ANCHOR_FILE_HASH, formatHash(hash));
        put(ANCHOR_FILE_HASH_ALGORITHM, algorithm);
    }

    /**
     * formats hash according to audit log format, and puts hash and default hash algorithm
     * @param unformattedHash unformatted hash "630b9f83", will be changed to formatted "63:0B:9F:83"
     */
    public void putCertificateHash(String unformattedHash) {
        putAlreadyFormattedCertificateHash(formatHash(unformattedHash));
    }

    /**
     * Puts hash and default hash algorithm
     * @param formattedHash formatted "63:0B:9F:83" hash
     */
    public void putAlreadyFormattedCertificateHash(String formattedHash) {
        put(CERT_HASH, formattedHash);
        putDefaulCerttHashAlgorithm();
    }


    /**
     * Put (only) cert hash, and hash default algorithm
     */
    public void put(CertificateType certificateType) {
        String hash = createFormattedHash(certificateType.getData());
        put(CERT_HASH, hash);
        putDefaulCerttHashAlgorithm();
    }

    /**
     * Put cert id, hash and default algorithm
     * @param id
     * @param unformattedHash unformatted hash "630b9f83", will be changed to formatted "63:0B:9F:83"
     */
    public void putCertificateData(String id, String unformattedHash) {
        put(CERT_ID, id);
        putCertificateHash(unformattedHash);
    }

    /**
     * Put cert id, hash, and hash default algorithm
     * @param certificateInfo
     */
    public void put(CertificateInfo certificateInfo) {
        String hash = createUnformattedHash(certificateInfo.getCertificateBytes());
        putCertificateData(certificateInfo.getId(), hash);
    }

    /**
     * Put default cert hash algorithm
     */
    public void putDefaulCerttHashAlgorithm() {
        put(CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);
    }

    /**
     * Change unformatted hash 630b9f83... to audit log -formatted hash 63:0B:9F:83..
     * @param unformattedHash
     * @return
     */
    public String formatHash(String unformattedHash) {
        String hash = unformattedHash.toUpperCase();
        return String.join(":", Splitter.fixedLength(2).split(hash));
    }

    /**
     * Create audit log -formatted hash 63:0B:9F:83 of a byte array, using default cert hash algorithm
     * @return
     */
    public String createFormattedHash(byte[] bytes) {
        return formatHash(createUnformattedHash(bytes));
    }

    private String createUnformattedHash(byte[] certBytes) {
        String hash = null;
        try {
            hash = CryptoUtils.calculateCertHexHash(certBytes);
        } catch (Exception e) {
            log.error("audit logging certificate hash forming failed", e);
        }
        return hash;
    }

    /**
     * Converts OffsetDateTime to correct audit log dateTime format, in system default time-zone
     */
    public void putDateTime(RestApiAuditProperty property, OffsetDateTime dateTime) {
        put(property, getDateTimeInAuditLogFormat(dateTime));
    }

    private static final DateTimeFormatter AUDIT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    private String getDateTimeInAuditLogFormat(OffsetDateTime dateTime) {
        if (dateTime != null) {
            return dateTime.atZoneSameInstant(ZoneId.systemDefault())
                    .format(AUDIT_FORMATTER);
        } else {
            return null;
        }
    }

    /**
     * Converts Date to correct audit log dateTime format, in system default time-zone
     */
    public void putDate(RestApiAuditProperty property, Date date) {
        putDateTime(property, FormatUtils.fromDateToOffsetDateTime(date));
    }

    /**
     * audit log token id, serial number and friendly name
     * @param tokenInfo
     */
    public void put(TokenInfo tokenInfo) {
        put(TOKEN_ID, tokenInfo.getId());
        put(TOKEN_SERIAL_NUMBER, tokenInfo.getSerialNumber());
        put(TOKEN_FRIENDLY_NAME, tokenInfo.getFriendlyName());
    }

    /**
     * audit log key id, friendly name and usage
     * @param keyInfo
     */
    public void put(KeyInfo keyInfo) {
        put(KEY_ID, keyInfo.getId());
        put(KEY_FRIENDLY_NAME, keyInfo.getFriendlyName());
        put(KEY_USAGE, keyInfo.getUsage());
    }

    /**
     * put backup filename
     */
    public void putBackupFilename(Path filePath) {
        String filename = null;
        if (filePath != null) {
            filename = filePath.toString();
        }
        put(BACKUP_FILE_NAME, filename);
    }

}
