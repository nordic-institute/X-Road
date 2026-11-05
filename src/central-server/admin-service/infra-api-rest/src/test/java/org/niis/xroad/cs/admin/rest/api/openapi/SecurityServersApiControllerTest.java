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
package org.niis.xroad.cs.admin.rest.api.openapi;

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.SecurityServerNotFoundException;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.domain.SystemPairEdit;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.rest.api.converter.db.SecurityServerDtoConverter;
import org.niis.xroad.cs.openapi.model.SecurityServerDto;
import org.niis.xroad.cs.openapi.model.SecurityServerEditDto;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ADDRESS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DSP_BASE_URL;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SYSTEM_DID;

@ExtendWith(MockitoExtension.class)
class SecurityServersApiControllerTest {

    private static final String ID = "DEV:COM:1234:SS1";
    private static final String ADDRESS_VALUE = "ss.example.org";
    private static final String DID = "did:web:ss.example.org%3A7183";
    private static final String URL = "https://ss.example.org:7185/dsp";

    private final SecurityServerId serverId = SecurityServerId.Conf.create("DEV", "COM", "1234", "SS1");

    @Mock
    private SecurityServerService securityServerService;
    @Mock
    private SecurityServerIdConverter securityServerIdConverter;
    @Mock
    private SecurityServerDtoConverter securityServerDtoConverter;
    @Mock
    private AuditDataHelper auditDataHelper;

    @InjectMocks
    private SecurityServersApiController controller;

    @BeforeEach
    void resolveId() {
        lenient().when(securityServerIdConverter.convertId(ID)).thenReturn(serverId);
    }

    @Test
    void addressOnlyEditAuditsTheServerAndAddressAndPassesNoPair() {
        stubUpdate(null);

        var response = controller.updateSecurityServer(ID, new SecurityServerEditDto().serverAddress(ADDRESS_VALUE));

        assertThat(response.getBody()).isNotNull();
        verifyAudit(null, null);
        verify(securityServerService).editSecurityServer(serverId, ADDRESS_VALUE, null);
    }

    @Test
    void editWithAPairAuditsItAndPassesItOn() {
        var pair = new SystemPairEdit(DID, URL);
        stubUpdate(pair);

        controller.updateSecurityServer(ID, new SecurityServerEditDto().serverAddress(ADDRESS_VALUE).systemDid(DID).dspBaseUrl(URL));

        verifyAudit(DID, URL);
        verify(securityServerService).editSecurityServer(serverId, ADDRESS_VALUE, pair);
    }

    @Test
    void halfAPairIsRefusedAfterItIsAudited() {
        assertThatThrownBy(() -> controller.updateSecurityServer(ID,
                new SecurityServerEditDto().serverAddress(ADDRESS_VALUE).systemDid(DID)))
                .isInstanceOf(BadRequestException.class);

        verifyAudit(DID, null);
        verifyNoInteractions(securityServerService);
    }

    @Test
    void unknownServerIsReportedAsNotFound() {
        when(securityServerService.editSecurityServer(serverId, ADDRESS_VALUE, null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.updateSecurityServer(ID, new SecurityServerEditDto().serverAddress(ADDRESS_VALUE)))
                .isInstanceOf(SecurityServerNotFoundException.class);
    }

    @Test
    void theEditIsLoggedAsTheCentralServerEditEvent() throws NoSuchMethodException {
        var method = SecurityServersApiController.class.getMethod("updateSecurityServer", String.class, SecurityServerEditDto.class);

        assertThat(method.getAnnotation(AuditEventMethod.class).event()).isEqualTo(RestApiAuditEvent.EDIT_SECURITY_SERVER);
    }

    private void stubUpdate(SystemPairEdit pair) {
        var server = new SecurityServer();
        when(securityServerService.editSecurityServer(serverId, ADDRESS_VALUE, pair)).thenReturn(Optional.of(server));
        when(securityServerDtoConverter.toDto(any(SecurityServer.class))).thenReturn(new SecurityServerDto());
    }

    private void verifyAudit(String systemDid, String dspBaseUrl) {
        verify(auditDataHelper).put(SERVER_CODE, "SS1");
        verify(auditDataHelper).put(OWNER_CODE, "1234");
        verify(auditDataHelper).put(OWNER_CLASS, "COM");
        verify(auditDataHelper).put(ADDRESS, ADDRESS_VALUE);
        verify(auditDataHelper).put(SYSTEM_DID, systemDid);
        verify(auditDataHelper).put(DSP_BASE_URL, dspBaseUrl);
        verifyNoMoreInteractions(auditDataHelper);
    }
}
