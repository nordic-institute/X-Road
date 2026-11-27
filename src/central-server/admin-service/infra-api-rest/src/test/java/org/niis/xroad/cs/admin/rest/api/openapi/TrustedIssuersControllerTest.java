/*
 * The MIT License
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
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.TrustedIssuerRemoval;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerDidService;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.TRUSTED_ISSUER_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ISSUER_DID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.LAST_TRUSTED_ISSUER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TRUSTED_ISSUER_ID;

@ExtendWith(MockitoExtension.class)
class TrustedIssuersControllerTest {

    private static final int ID = 7;
    private static final String DID_VALUE = "did:web:cs.example%3A443:issuer";

    @Mock
    private DataspaceIssuerDidService dataspaceIssuerDidService;
    @Mock
    private AuditDataHelper auditDataHelper;

    @InjectMocks
    private TrustedIssuersController trustedIssuersController;

    @Test
    void deleteTrustedIssuerAuditsTheIdDidAndLastTrustedIssuerOnSuccess() {
        when(dataspaceIssuerDidService.remove(ID)).thenReturn(new TrustedIssuerRemoval(DID_VALUE, true));

        var response = trustedIssuersController.deleteTrustedIssuer(ID);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(auditDataHelper).put(TRUSTED_ISSUER_ID, ID);
        verify(auditDataHelper).put(ISSUER_DID, DID_VALUE);
        verify(auditDataHelper).put(LAST_TRUSTED_ISSUER, true);
        verifyNoMoreInteractions(auditDataHelper);
    }

    @Test
    void deleteTrustedIssuerAuditsOnlyTheIdWhenRemovalFails() {
        when(dataspaceIssuerDidService.remove(ID)).thenThrow(new NotFoundException(TRUSTED_ISSUER_NOT_FOUND.build()));

        assertThatThrownBy(() -> trustedIssuersController.deleteTrustedIssuer(ID))
                .isInstanceOf(NotFoundException.class);

        verify(auditDataHelper).put(TRUSTED_ISSUER_ID, ID);
        verifyNoMoreInteractions(auditDataHelper);
    }
}
