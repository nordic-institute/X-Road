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

import ee.ria.xroad.common.identifier.ClientId;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties.Dataspace;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.MemberParticipantIdentity;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceParticipantTombstoneServiceTest {

    private static final ClientId MEMBER = ClientId.Conf.create("TEST", "ORG", "MEMBER");
    private static final String CTX_ID = "TEST:ORG:MEMBER";
    private static final String DID = "did:web:ih.example.test:v1:TEST:ORG:MEMBER";
    private static final String IDENTITY_HUB_URL = "https://ih.example.test";

    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private Dataspace dataspace;
    @Mock
    private DataspaceProvisioningService dataspaceProvisioningService;
    @Mock
    private DsParticipantRepository dsParticipantRepository;

    private DataspaceParticipantTombstoneService service;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(DataspaceParticipantTombstoneService.class);
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        lenient().when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        service = new DataspaceParticipantTombstoneService(adminServiceProperties, dataspaceProvisioningService,
                dsParticipantRepository);

        originalLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void skipsWithoutWritingWhenIdentityHubUrlIsBlank(String identityHubUrl) {
        when(dataspace.getIdentityHubUrl()).thenReturn(identityHubUrl);

        service.decommission(MEMBER);

        verify(dataspaceProvisioningService, never()).deriveMemberIdentity(any());
        verify(dsParticipantRepository, never()).decommissionMember(any(), any(), any());
    }

    @Test
    void skipsWithoutWritingWhenIdentityCannotBeDerived() {
        when(dataspace.getIdentityHubUrl()).thenReturn(IDENTITY_HUB_URL);
        when(dataspaceProvisioningService.deriveMemberIdentity(MEMBER))
                .thenThrow(new IllegalArgumentException("malformed identity-hub URL"));

        service.decommission(MEMBER);

        verify(dsParticipantRepository, never()).decommissionMember(any(), any(), any());
        assertThat(appender.list).hasSize(1);
        var logged = appender.list.getFirst();
        assertThat(logged.getLevel()).isEqualTo(Level.WARN);
        assertThat(logged.getThrowableProxy()).isNotNull();
    }

    @Test
    void writesTombstoneEvenWhenDataspaceFeatureFlagIsDisabled() {
        when(dataspace.getIdentityHubUrl()).thenReturn(IDENTITY_HUB_URL);
        when(dataspaceProvisioningService.deriveMemberIdentity(MEMBER)).thenReturn(new MemberParticipantIdentity(CTX_ID, DID));

        service.decommission(MEMBER);

        verify(dsParticipantRepository).decommissionMember(MEMBER, CTX_ID, DID);
        verify(dataspace, never()).isEnabled();
    }

    @Test
    void writesTombstoneWhenIdentityHubUrlIsConfigured() {
        when(dataspace.getIdentityHubUrl()).thenReturn(IDENTITY_HUB_URL);
        when(dataspaceProvisioningService.deriveMemberIdentity(MEMBER)).thenReturn(new MemberParticipantIdentity(CTX_ID, DID));

        service.decommission(MEMBER);

        verify(dsParticipantRepository).decommissionMember(MEMBER, CTX_ID, DID);
    }
}
