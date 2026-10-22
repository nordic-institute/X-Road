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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.HttpStatus;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.exception.XrdRuntimeHttpException;
import org.niis.xroad.proxy.proto.ProxyRpcClient;
import org.niis.xroad.proxy.proto.dto.AgreementTokenKeyStatus;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Reads the agreement-token signing key status of the connected proxy and rotates the key.
 */
@Service
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class AgreementTokenKeyService {

    private static final Set<String> UNAVAILABLE_ERROR_CODES = Set.of(
            ErrorCode.AGREEMENT_TOKEN_KEY_NOT_AVAILABLE.code(),
            ErrorCode.AGREEMENT_TOKEN_KEY_STORE_FAILED.code());

    private final ProxyRpcClient proxyRpcClient;
    private final AuditDataHelper auditDataHelper;

    public AgreementTokenKeyStatus getStatus() {
        return proxyRpcClient.getAgreementTokenKeyStatus();
    }

    /**
     * @return the id of the newly generated key
     * @throws XrdRuntimeException carrying the proxy's error code; HTTP 503 when the proxy has no key material
     *                             or its secret store failed
     */
    public String rotate() {
        try {
            var newKeyId = proxyRpcClient.rotateAgreementTokenSigningKey();
            auditDataHelper.put(RestApiAuditProperty.KEY_ID, newKeyId);
            return newKeyId;
        } catch (XrdRuntimeException e) {
            if (UNAVAILABLE_ERROR_CODES.contains(e.getErrorCode())) {
                throw XrdRuntimeHttpException.from(e)
                        .cause(e)
                        .httpStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .build();
            }
            throw e;
        }
    }
}
