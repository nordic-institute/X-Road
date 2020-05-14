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
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class DoNothingAuditDataHelper extends AuditDataHelper {
    @Autowired
    public DoNothingAuditDataHelper() {
        super(null);
    }

    @Override
    public void put(RestApiAuditProperty auditProperty, Object value) {
    }

    @Override
    public void addListPropertyItem(RestApiAuditProperty listProperty, Object value) {
    }

    @Override
    public Map<RestApiAuditProperty, Object> putMap(RestApiAuditProperty auditProperty) {
        return new HashMap<>();
    }

    @Override
    public boolean dataIsForEvent(RestApiAuditEvent event) {
        return true;
    }

    @Override
    public void put(IsAuthentication isAuthentication) {
    }

    @Override
    public void put(ClientId clientId) {
    }

    @Override
    public void putClientStatus(ClientType client) {
    }

    @Override
    public void putManagementRequestId(Integer requestId) {
    }

    @Override
    public void addCertificateHash(CertificateInfo certificateInfo) {
    }

    @Override
    public void put(CertificateType certificateType) {
    }

    @Override
    public void putDefaultHashAlgorithm() {
    }

    @Override
    public void putDateTime(RestApiAuditProperty property, OffsetDateTime dateTime) {
    }

    @Override
    public void put(TokenInfo tokenInfo) {
    }

    @Override
    public void put(KeyInfo keyInfo) {
    }
}
