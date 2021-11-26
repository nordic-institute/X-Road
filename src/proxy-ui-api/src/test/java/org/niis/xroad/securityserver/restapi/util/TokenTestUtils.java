/**
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
package org.niis.xroad.securityserver.restapi.util;

import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Test utils for working with tokens
 */
public final class TokenTestUtils {

    private TokenTestUtils() {
        // noop
    }

    /**
     * Builder for TokenInfo objects.
     * Default values:
     * - type = SOFTWARE_MODULE_TYPE
     * - friendlyName = "friendly-name"
     * - id = "id"
     * - readOnly = false
     * - available = true
     * - active = true
     * - serialNumber = "serial-number"
     * - label = "label"
     * - slotIndex = 123
     * - tokenStatus = OK
     * - keyInfos = empty
     * - tokenInfo map = empty
     */
    public static class TokenInfoBuilder {
        private String id = "id";
        private String friendlyName = "friendly-name";
        private List<KeyInfo> keyInfos = new ArrayList<>();
        private boolean readOnly = false;
        private boolean available = true;
        private boolean active = true;
        private String type = TokenInfo.SOFTWARE_MODULE_TYPE;

        public TokenInfo build() {
            return new TokenInfo(type,
                    friendlyName,
                    id,
                    readOnly,
                    available,
                    active,
                    "serial-number",
                    "label",
                    123,
                    TokenStatusInfo.OK,
                    keyInfos,
                    new HashMap<>());
        }

        public TokenInfoBuilder active(boolean activeParam) {
            this.active = activeParam;
            return this;
        }

        public TokenInfoBuilder available(boolean availableParam) {
            this.available = availableParam;
            return this;
        }

        public TokenInfoBuilder readOnly(boolean readOnlyParam) {
            this.readOnly = readOnlyParam;
            return this;
        }

        public TokenInfoBuilder type(String typeParam) {
            this.type = typeParam;
            return this;
        }

        public TokenInfoBuilder id(String idParam) {
            this.id = idParam;
            return this;
        }

        public TokenInfoBuilder friendlyName(String friendlyNameParam) {
            this.friendlyName = friendlyNameParam;
            return this;
        }
        /**
         * Adds this item to keys, ensuring there are no duplicates
         */
        public TokenInfoBuilder key(KeyInfo keyInfo) {
            Set<KeyInfo> keys = new HashSet<>(this.keyInfos);
            keys.add(keyInfo);
            this.keyInfos = new ArrayList<>(keys);
            return this;
        }
    }

    /**
     * Builder for KeyInfo objects.
     * Default values:
     * - available = true
     * - usage = SIGNING
     * - friendlyName = "friendly-name"
     * - id = "id"
     * - label = "label"
     * - publicKey = "public-key"
     * - certs = empty
     * - certRequests = empty
     * - signMechanismName = "sign-mechanism-name"
     */
    public static class KeyInfoBuilder {
        private String id = "id";
        private String friendlyName = "friendly-name";
        private KeyUsageInfo keyUsageInfo = KeyUsageInfo.SIGNING;
        private List<CertRequestInfo> certRequests = new ArrayList<>();
        private List<CertificateInfo> certificates = new ArrayList<>();
        private boolean available = true;

        public KeyInfo build() {
            return new KeyInfo(available,
                    keyUsageInfo,
                    friendlyName,
                    id,
                    "label",
                    "public-key",
                    certificates,
                    certRequests,
                    "sign-mechanism-name");
        }

        public KeyInfoBuilder keyInfo(KeyInfo info) {
            id(info.getId());
            friendlyName(info.getFriendlyName());
            keyUsageInfo(info.getUsage());
            info.getCerts().stream().map(this::cert);
            info.getCertRequests().stream().map(this::csr);
            available(info.isAvailable());
            return this;
        }

        public KeyInfoBuilder id(String idParam) {
            this.id = idParam;
            return this;
        }

        public KeyInfoBuilder friendlyName(String friendlyNameParam) {
            this.friendlyName = friendlyNameParam;
            return this;
        }

        public KeyInfoBuilder keyUsageInfo(KeyUsageInfo keyUsageInfoParam) {
            this.keyUsageInfo = keyUsageInfoParam;
            return this;
        }

        public KeyInfoBuilder available(boolean availableParam) {
            this.available = availableParam;
            return this;
        }

        /**
         * Adds this item to csrs, ensuring there are no duplicates
         */
        public KeyInfoBuilder csr(CertRequestInfo certRequestInfo) {
            Set<CertRequestInfo> csrs = new HashSet<>(this.certRequests);
            csrs.add(certRequestInfo);
            this.certRequests = new ArrayList<>(csrs);
            return this;
        }

        /**
         * Adds this item to certs, ensuring there are no duplicates
         */
        public KeyInfoBuilder cert(CertificateInfo certificateInfo) {
            Set<CertificateInfo> certs = new HashSet<>(this.certificates);
            certs.add(certificateInfo);
            this.certificates = new ArrayList<>(certs);
            return this;
        }

    }
}
