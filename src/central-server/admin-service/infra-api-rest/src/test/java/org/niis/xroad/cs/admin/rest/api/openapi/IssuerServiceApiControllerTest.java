/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package org.niis.xroad.cs.admin.rest.api.openapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.cs.admin.api.dto.IssuerHostState;
import org.niis.xroad.cs.admin.api.exception.ErrorMessage;
import org.niis.xroad.cs.admin.api.service.IssuerHostService;
import org.niis.xroad.cs.openapi.model.IssuerServiceHostDto;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssuerServiceApiControllerTest {

    @Mock
    private IssuerHostService issuerHostService;
    @Mock
    private AuditDataHelper auditDataHelper;

    @InjectMocks
    private IssuerServiceApiController issuerServiceApiController;

    @Test
    void getIssuerServiceStateReturnsTheCurrentState() {
        when(issuerHostService.getState()).thenReturn(new IssuerHostState("cs.example.org", false));

        var response = issuerServiceApiController.getIssuerServiceState();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getHost()).isEqualTo("cs.example.org");
        assertThat(response.getBody().getBound()).isFalse();
    }

    @Test
    void setIssuerServiceHostAuditsTheNewHostAndStoresIt() {
        when(issuerHostService.setHost("cs.example.org")).thenReturn(new IssuerHostState("cs.example.org", false));

        var response = issuerServiceApiController.setIssuerServiceHost(new IssuerServiceHostDto().host("cs.example.org"));

        verify(auditDataHelper).put(RestApiAuditProperty.ISSUER_SERVICE_HOST, "cs.example.org");
        verify(issuerHostService).setHost("cs.example.org");
        verifyNoMoreInteractions(auditDataHelper);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getHost()).isEqualTo("cs.example.org");
        assertThat(response.getBody().getBound()).isFalse();
    }

    @Test
    void setIssuerServiceHostAuditsTheRejectedHostAndPropagatesTheException() {
        when(issuerHostService.setHost("not a valid host"))
                .thenThrow(new BadRequestException(ErrorMessage.INVALID_DNS_NAME.build()));

        assertThatThrownBy(() -> issuerServiceApiController.setIssuerServiceHost(new IssuerServiceHostDto().host("not a valid host")))
                .isInstanceOf(BadRequestException.class);

        verify(auditDataHelper).put(RestApiAuditProperty.ISSUER_SERVICE_HOST, "not a valid host");
        verifyNoMoreInteractions(auditDataHelper);
    }
}
