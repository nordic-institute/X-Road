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

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.CaseFormat;
import com.google.gson.annotations.JsonAdapter;

/**
 * Enumeration for data properties that are audit logged.
 * Values are named so that property value returned by {@link #getPropertyName()} are enum value converted to
 * lower camel case.
 * For example CLIENT_IDENTIFIER -> clientIdentifier.
 * JSON serialized using {@link #getPropertyName()}
 */
@JsonAdapter(RestApiAuditPropertyJsonAdapter.class) // changes how direct properties are JSON serialized
public enum RestApiAuditProperty {

    CLIENT_IDENTIFIER,
    CLIENT_IDENTIFIERS,
    IS_AUTHENTICATION,
    CLIENT_STATUS,
    MANAGEMENT_REQUEST_ID,
    CERT_HASHES,
    CERT_HASH_ALGORITHM,
    CERT_REQUEST_IDS,
    KEY_ID,
    WSDL_URL,
    WSDL_URLS,
    DISABLED,
    REFRESHED_DATE,
    DISABLED_NOTICE,
    WSDL, // TO DO: Refresh WSDL uses property "wsdl" instead of "wsdls"
    SERVICES_ADDED,
    SERVICES_DELETED,
    WSDL_URL_NEW,

    SERVICES,
    ID,
    URL,
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

    TO_DO_REMOVE_THIS; // TO DO: remove last one

    /**
     * Gets logged property name for the enum value.
     * Returns enum name converted to lower camel case.
     * For example CLIENT_IDENTIFIER -> clientIdentifier
     * @return
     */
    @JsonValue
    public String getPropertyName() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name());
    }

    @Override
    public String toString() {
        // changes how e.g. Map entries are JSON serialized
        return getPropertyName();
    }
}
