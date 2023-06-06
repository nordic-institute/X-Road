/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.config.audit;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.CaseFormat;

/**
 * Enumeration for data properties that are audit logged.
 * Values are named so that property value returned by {@link #getPropertyName()} are enum value converted to
 * lower camel case.
 * For example CLIENT_IDENTIFIER -> clientIdentifier.
 * JSON serialized using {@link #getPropertyName()}
 */
public enum RestApiAuditProperty {

    USER, // only when not available via UsernameHelper, e.g. when form login fails

    CLIENT_IDENTIFIER,
    CLIENT_IDENTIFIERS,
    IS_AUTHENTICATION,
    CLIENT_STATUS,
    MANAGEMENT_REQUEST_ID,
    CERT_HASHES,
    CERT_HASH_ALGORITHM,
    CERT_REQUEST_IDS,
    KEY_ID,
    SERVICE_TYPE,
    DISABLED,
    REFRESHED_DATE,
    DISABLED_NOTICE,
    WSDL,
    SERVICES_ADDED,
    SERVICES_DELETED,
    URL,
    URL_NEW,

    SERVICES,
    ID,
    TIMEOUT,
    TLS_AUTH,

    SERVICE_CODE,
    SUBJECT_IDS,

    SERVICE_CODES,
    SUBJECT_ID,

    GROUP_CODE,
    GROUP_DESCRIPTION,
    MEMBER_IDENTIFIERS,

    CERT_HASH,
    UPLOAD_FILE_NAME,

    TOKEN_ID,
    TOKEN_SERIAL_NUMBER,
    TOKEN_FRIENDLY_NAME,
    KEY_LABEL,
    KEY_FRIENDLY_NAME,
    KEY_USAGE,

    SUBJECT_NAME,
    CERTIFICATION_SERVICE_NAME,
    CSR_FORMAT,
    CERT_ID,
    CSR_ID,
    CA_ID,
    OCSP_ID,
    OCSP_URL,
    OCSP_CERT_HASH,
    OCSP_CERT_HASH_ALGORITHM,
    INTERMEDIATE_CA_ID,
    INTERMEDIATE_CA_CERT_HASH,
    INTERMEDIATE_CA_CERT_HASH_ALGORITHM,
    CERT_FILE_NAME,
    ADDRESS,
    CERT_STATUS,

    BACKUP_FILE_NAME,

    ANCHOR_FILE_HASH,
    ANCHOR_FILE_HASH_ALGORITHM,
    ANCHOR_URLS,
    GENERATED_AT,
    TSP_NAME,
    TSP_URL,

    OWNER_IDENTIFIER,
    OWNER_CLASS,
    OWNER_CODE,
    SERVER_CODE,

    API_KEY_ID,
    API_KEY_ROLES,

    INSTANCE_IDENTIFIER,
    CENTRAL_SERVER_ADDRESS,
    HA_NODE,

    MEMBER_NAME,
    MEMBER_CLASS,
    MEMBER_CODE,

    MEMBER_SUBSYSTEM_CODE,

    CODE,
    DESCRIPTION,

    AUTHENTICATION_ONLY,
    CERTIFICATE_PROFILE_INFO,

    TSA_ID,
    TSA_NAME,
    TSA_URL,
    TSA_CERT_HASH,
    TSA_CERT_HASH_ALGORITHM,
    SOURCE_TYPE,
    PART_FILE_NAME,
    UPLOAD_FILE_HASH,
    UPLOAD_FILE_HASH_ALGORITHM,
    CONTENT_IDENTIFIER,

    SERVICE_PROVIDER_IDENTIFIER,
    SERVICE_PROVIDER_NAME;

    /**
     * Gets logged property name for the enum value.
     * Returns enum name converted to lower camel case.
     * For example CLIENT_IDENTIFIER -> clientIdentifier
     *
     * @return enum name converted to lower camel case.
     */
    @JsonValue
    String getPropertyName() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name());
    }

    @Override
    public String toString() {
        // changes how e.g. Map entries are JSON serialized
        return getPropertyName();
    }
}
