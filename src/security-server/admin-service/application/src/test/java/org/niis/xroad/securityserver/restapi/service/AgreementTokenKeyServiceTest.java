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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.HttpStatusAware;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.proto.ProxyRpcClient;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementTokenKeyServiceTest {

    @Mock
    private ProxyRpcClient proxyRpcClient;
    @Mock
    private AuditDataHelper auditDataHelper;
    @InjectMocks
    private AgreementTokenKeyService service;

    @Test
    void rotateReturnsTheNewKeyIdAndRecordsItInTheAuditData() {
        when(proxyRpcClient.rotateAgreementTokenSigningKey()).thenReturn("3");

        assertThat(service.rotate()).isEqualTo("3");

        verify(auditDataHelper).put(RestApiAuditProperty.KEY_ID, "3");
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"AGREEMENT_TOKEN_KEY_NOT_AVAILABLE", "AGREEMENT_TOKEN_KEY_STORE_FAILED"})
    void rotateMapsUnavailableKeyMaterialToServiceUnavailableKeepingTheErrorCode(ErrorCode errorCode) {
        when(proxyRpcClient.rotateAgreementTokenSigningKey())
                .thenThrow(XrdRuntimeException.systemException(errorCode).details("proxy failure").build());

        assertThatThrownBy(() -> service.rotate())
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(errorCode.code());
                    assertThat(e).isInstanceOfSatisfying(HttpStatusAware.class,
                            status -> assertThat(status.getHttpStatus()).isEqualTo(Optional.of(HttpStatus.SERVICE_UNAVAILABLE)));
                });
        verifyNoInteractions(auditDataHelper);
    }

    @Test
    void rotateLeavesOtherProxyErrorsUntouched() {
        var failure = XrdRuntimeException.systemException(ErrorCode.AGREEMENT_TOKEN_KEY_GENERATION_FAILED).build();
        when(proxyRpcClient.rotateAgreementTokenSigningKey()).thenThrow(failure);

        assertThatThrownBy(() -> service.rotate()).isSameAs(failure);
    }
}
