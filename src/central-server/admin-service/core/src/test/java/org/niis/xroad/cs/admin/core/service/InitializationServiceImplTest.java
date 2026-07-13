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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.dto.InitialServerConfDto;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerProvisioningService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.api.service.TokenPinValidator;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitializationServiceImplTest {

    private static final String INSTANCE_ID = "TEST";
    private static final String SERVER_ADDRESS = "cs.example.com";
    private static final String TOKEN_PIN = "pin123";
    private static final String GPG_KEY_PATH = "/usr/bin/gen-gpg.sh";
    private static final String GPG_HOME = "/var/lib/xroad/gpg";

    @Mock
    private SignerProxyFacade signerProxyFacade;
    @Mock
    private GlobalGroupRepository globalGroupRepository;
    @Mock
    private SystemParameterService systemParameterService;
    @Mock
    private TokenPinValidator tokenPinValidator;
    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private ExternalProcessRunner externalProcessRunner;
    @Mock
    private DataspaceIssuerProvisioningService issuerProvisioningService;

    private InitializationServiceImpl service;

    @BeforeEach
    void setup() throws Exception {
        var tokenInfo = mock(TokenInfo.class);
        when(tokenInfo.getStatus()).thenReturn(TokenStatusInfo.OK);
        when(signerProxyFacade.getToken(any())).thenReturn(tokenInfo);

        when(systemParameterService.getCentralServerAddress()).thenReturn("");
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_ID);

        when(globalGroupRepository.getByGroupCode(any())).thenReturn(Optional.of(new GlobalGroupEntity("owners")));
        when(externalProcessRunner.executeAndThrowOnFailure(any(), any(String[].class)))
                .thenReturn(new ExternalProcessRunner.ProcessResult("cmd", 0, List.of()));
    }

    private InitializationServiceImpl buildService(DataspaceIssuerProvisioningService provisioning, boolean dataspaceEnabled) {
        return new InitializationServiceImpl(
                signerProxyFacade,
                globalGroupRepository,
                systemParameterService,
                tokenPinValidator,
                auditDataHelper,
                new HAConfigStatus("node1", false),
                externalProcessRunner,
                provisioning,
                GPG_KEY_PATH,
                GPG_HOME,
                dataspaceEnabled
        );
    }

    private InitialServerConfDto dto() {
        var dto = new InitialServerConfDto();
        dto.setCentralServerAddress(SERVER_ADDRESS);
        dto.setInstanceIdentifier(INSTANCE_ID);
        dto.setSoftwareTokenPin(TOKEN_PIN);
        return dto;
    }

    @Test
    void issuerProvisioningFailurePropagates() {
        doThrow(new RuntimeException("gRPC failure")).when(issuerProvisioningService).provisionIssuer();

        service = buildService(issuerProvisioningService, true);

        assertThrows(RuntimeException.class, () -> service.initialize(dto()));
    }

    @Test
    void initializeSucceedsWhenDataspaceDisabled() {
        service = buildService(issuerProvisioningService, false);

        assertDoesNotThrow(() -> service.initialize(dto()));
        verifyNoInteractions(issuerProvisioningService);
    }

    @Test
    void issuerProvisioningCalledWhenDataspaceEnabled() {
        service = buildService(issuerProvisioningService, true);

        assertDoesNotThrow(() -> service.initialize(dto()));
        verify(issuerProvisioningService).provisionIssuer();
    }
}
