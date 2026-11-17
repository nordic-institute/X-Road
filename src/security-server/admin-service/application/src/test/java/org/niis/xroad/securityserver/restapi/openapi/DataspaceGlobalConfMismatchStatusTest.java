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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceParticipantContextStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceProvisioningStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceServerStatusDto.HostStatusEnum;
import org.niis.xroad.securityserver.restapi.scheduling.DataspaceParticipantProvisioningWorker;
import org.niis.xroad.securityserver.restapi.scheduling.DataspacePublicationGlobalConfCheck;
import org.niis.xroad.securityserver.restapi.service.ControlPlaneProvisioningClient;
import org.niis.xroad.securityserver.restapi.service.IdentityHubProvisioningClient;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.niis.xroad.serverconf.model.PublicationState;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.memberDid;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.systemDid;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
@WithMockUser(authorities = "VIEW_DATASPACE_STATUS")
class DataspaceGlobalConfMismatchStatusTest extends AbstractApiControllerTestContext {

    private static final long MEMBER_ROW_ID = 9013L;
    private static final long SYSTEM_ROW_ID = 9014L;
    private static final ClientId.Conf MEMBER = TestUtils.getClientId("FI", "GOV", "M1", null);
    private static final SecurityServerId.Conf SS_ID = SecurityServerId.Conf.create(MEMBER, "TEST-INMEM-SS");
    private static final String ADDRESS = "ss.example.test";
    private static final String OTHER_HOST_ADDRESS = "other.example.test";
    private static final String BOUND_DID = memberDid(MEMBER, "ss.example.test:7183");
    private static final String OTHER_DID_SAME_HOST = memberDid(MEMBER, "ss.example.test:8443");
    private static final String OTHER_DID_OTHER_HOST = memberDid(MEMBER, "other.example.test:7183");
    private static final String BOUND_SYSTEM_DID = systemDid("ss.example.test:7183");
    private static final String OTHER_SYSTEM_DID_SAME_HOST = systemDid("ss.example.test:8443");
    private static final String OTHER_SYSTEM_DID_OTHER_HOST = systemDid("other.example.test:7183");
    private static final int DSP_PORT = Integer.parseInt(DspConfigKeys.DSP_PORT.defaultValue());
    private static final String BUILT_DSP_BASE_URL = DspConventions.dspBaseUrl(ADDRESS, DSP_PORT);
    private static final String OTHER_PORT_DSP_BASE_URL = DspConventions.dspBaseUrl(ADDRESS, DSP_PORT + 1);
    private static final String OTHER_HOST_DSP_BASE_URL = DspConventions.dspBaseUrl("other.example.test", DSP_PORT);

    @MockitoBean
    private DataspaceParticipantProvisioningWorker dataspaceParticipantProvisioningWorker;
    @MockitoBean
    private IdentityHubProvisioningClient identityHubClient;
    @MockitoBean
    private ControlPlaneProvisioningClient controlPlaneClient;
    @Autowired
    private DataspaceApiController dataspaceApiController;
    @Autowired
    private DataspacePublicationGlobalConfCheck globalConfCheck;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ParticipantRowTestUtils participantRows;

    @BeforeEach
    void setUpRows() {
        participantRows = new ParticipantRowTestUtils(jdbcTemplate, transactionManager);
        registerAddress(ADDRESS);
    }

    @AfterEach
    void deleteRows() {
        participantRows.deleteRows(MEMBER_ROW_ID, SYSTEM_ROW_ID);
    }

    static Stream<Arguments> memberMismatchCases() {
        return Stream.of(
                argumentSet("a submitted member whose carried DID differs", PublicationState.SUBMITTED,
                        OTHER_DID_SAME_HOST, true),
                argumentSet("a published member whose carried DID differs", PublicationState.PUBLISHED,
                        OTHER_DID_SAME_HOST, true),
                argumentSet("an equal carried DID", PublicationState.PUBLISHED, BOUND_DID, false),
                argumentSet("a not sent member", PublicationState.NOT_SENT, OTHER_DID_SAME_HOST, false),
                argumentSet("a rejected member", PublicationState.REJECTED, OTHER_DID_SAME_HOST, false),
                argumentSet("a stale carried DID", PublicationState.PUBLISHED, OTHER_DID_OTHER_HOST, false));
    }

    @ParameterizedTest
    @MethodSource("memberMismatchCases")
    void theMemberMismatchFlagFollowsThePublicationStateAndTheCarriedDid(PublicationState state, String carriedDid,
            boolean expectedMismatch) {
        participantRows.memberRow(MEMBER_ROW_ID, MEMBER, BOUND_DID, state);
        carry(carriedDid);

        assertThat(memberContext().getGlobalconfMismatch()).isEqualTo(expectedMismatch);
    }

    @Test
    void theMismatchClearsOnceGlobalconfCarriesTheBoundDidAgain() {
        participantRows.memberRow(MEMBER_ROW_ID, MEMBER, BOUND_DID, PublicationState.PUBLISHED);
        carry(OTHER_DID_SAME_HOST);
        assertThat(memberContext().getGlobalconfMismatch()).isTrue();

        carry(BOUND_DID);

        assertThat(memberContext().getGlobalconfMismatch()).isFalse();
    }

    static Stream<Arguments> systemMismatchCases() {
        return Stream.of(
                argumentSet("a submitted system DID that differs", PublicationState.SUBMITTED,
                        OTHER_SYSTEM_DID_SAME_HOST, BUILT_DSP_BASE_URL, true, false),
                argumentSet("a submitted DSP port that changed on the same host", PublicationState.SUBMITTED,
                        BOUND_SYSTEM_DID, OTHER_PORT_DSP_BASE_URL, false, true),
                argumentSet("equal system values", PublicationState.PUBLISHED,
                        BOUND_SYSTEM_DID, BUILT_DSP_BASE_URL, false, false),
                argumentSet("system values that globalconf does not carry", PublicationState.PUBLISHED,
                        null, null, false, false),
                argumentSet("not sent system values", PublicationState.NOT_SENT,
                        OTHER_SYSTEM_DID_SAME_HOST, OTHER_PORT_DSP_BASE_URL, false, false),
                argumentSet("a system DID on another host", PublicationState.PUBLISHED,
                        OTHER_SYSTEM_DID_OTHER_HOST, OTHER_PORT_DSP_BASE_URL, false, false),
                argumentSet("a DSP base URL on another host", PublicationState.PUBLISHED,
                        OTHER_SYSTEM_DID_SAME_HOST, OTHER_HOST_DSP_BASE_URL, false, false));
    }

    @ParameterizedTest
    @MethodSource("systemMismatchCases")
    void theSystemMismatchFlagsFollowThePublicationStateAndTheCarriedValues(PublicationState state,
            String carriedDid, String carriedDspBaseUrl, boolean expectedSystemDidMismatch,
            boolean expectedDspBaseUrlMismatch) {
        participantRows.systemRow(SYSTEM_ROW_ID, BOUND_SYSTEM_DID, state);
        carrySystemValues(carriedDid, carriedDspBaseUrl);

        assertFlags(expectedSystemDidMismatch, expectedDspBaseUrlMismatch);
    }

    static Stream<Arguments> noChangeCases() {
        return Stream.of(
                argumentSet("a submitted member row", ParticipantType.MEMBER, PublicationState.SUBMITTED),
                argumentSet("a published member row", ParticipantType.MEMBER, PublicationState.PUBLISHED),
                argumentSet("a submitted system row", ParticipantType.SYSTEM, PublicationState.SUBMITTED),
                argumentSet("a published system row", ParticipantType.SYSTEM, PublicationState.PUBLISHED));
    }

    @ParameterizedTest
    @MethodSource("noChangeCases")
    void aMismatchChangesNeitherTheBindingNorThePublicationState(ParticipantType type, PublicationState state) {
        var system = type == ParticipantType.SYSTEM;
        var rowId = system ? SYSTEM_ROW_ID : MEMBER_ROW_ID;
        var boundDid = system ? BOUND_SYSTEM_DID : BOUND_DID;
        if (system) {
            participantRows.systemRow(SYSTEM_ROW_ID, BOUND_SYSTEM_DID, state);
            carrySystemValues(OTHER_SYSTEM_DID_SAME_HOST, OTHER_PORT_DSP_BASE_URL);
        } else {
            participantRows.memberRow(MEMBER_ROW_ID, MEMBER, BOUND_DID, state);
            carry(OTHER_DID_SAME_HOST);
        }
        participantRows.inNewTransaction(() -> jdbcTemplate.update(
                "UPDATE ds_participant SET publication_changed_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(2))), rowId));
        registerAuthCert();

        assertMismatchShown(system);
        assertThat(errorsLoggedBy(() -> globalConfCheck.check(SS_ID))).isEmpty();
        assertMismatchShown(system);

        assertBindingStaysActiveWithDidAndPublicationState(rowId, boundDid, state);
    }

    private void assertBindingStaysActiveWithDidAndPublicationState(long rowId, String did, PublicationState state) {
        assertThat(jdbcTemplate.queryForMap("SELECT did, publication_state, state FROM ds_participant WHERE id = ?",
                rowId))
                .containsEntry("did", did)
                .containsEntry("publication_state", state.name())
                .containsEntry("state", "ACTIVE");
    }

    @Test
    void bindingsAllAtTheRegisteredHostShowHostStatusOk() {
        participantRows.memberRow(MEMBER_ROW_ID, MEMBER, BOUND_DID, PublicationState.PUBLISHED);
        participantRows.systemRow(SYSTEM_ROW_ID, BOUND_SYSTEM_DID, PublicationState.PUBLISHED);

        assertThat(hostStatus()).isEqualTo(HostStatusEnum.OK);
    }

    @Test
    void aMemberBindingAtAnotherHostShowsHostStatusDiffers() {
        participantRows.memberRow(MEMBER_ROW_ID, MEMBER, OTHER_DID_OTHER_HOST,
                PublicationState.PUBLISHED);
        participantRows.systemRow(SYSTEM_ROW_ID, BOUND_SYSTEM_DID, PublicationState.PUBLISHED);

        assertThat(hostStatus()).isEqualTo(HostStatusEnum.DIFFERS);
    }

    @Test
    void unavailableGlobalconfShowsHostStatusUnknown() {
        participantRows.memberRow(MEMBER_ROW_ID, MEMBER, OTHER_DID_OTHER_HOST,
                PublicationState.PUBLISHED);
        when(globalConfProvider.getSecurityServerAddress(SS_ID))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.NETWORK_ERROR).build());

        assertThat(hostStatus()).isEqualTo(HostStatusEnum.UNKNOWN);
    }

    @Test
    void aDecommissionedBindingAtAnotherHostDoesNotShowHostStatusDiffers() {
        participantRows.memberRow(MEMBER_ROW_ID, MEMBER, OTHER_DID_OTHER_HOST,
                ParticipantState.DECOMMISSIONED, PublicationState.PUBLISHED);
        participantRows.systemRow(SYSTEM_ROW_ID, BOUND_SYSTEM_DID, PublicationState.PUBLISHED);

        assertThat(hostStatus()).isEqualTo(HostStatusEnum.OK);
    }

    @Test
    void hostStatusClearsOnceTheBindingsAgreeWithTheRegisteredAddressAgain() {
        participantRows.memberRow(MEMBER_ROW_ID, MEMBER, OTHER_DID_OTHER_HOST,
                PublicationState.PUBLISHED);
        assertThat(hostStatus()).isEqualTo(HostStatusEnum.DIFFERS);

        participantRows.inNewTransaction(() -> jdbcTemplate.update("UPDATE ds_participant SET did = ? WHERE id = ?",
                BOUND_DID, MEMBER_ROW_ID));

        assertThat(hostStatus()).isEqualTo(HostStatusEnum.OK);
    }

    @Test
    void staleGlobalconfValuesWithAllBindingsAtTheRegisteredHostShowHostStatusOkAndNoMismatch() {
        participantRows.memberRow(MEMBER_ROW_ID, MEMBER, BOUND_DID, PublicationState.PUBLISHED);
        participantRows.systemRow(SYSTEM_ROW_ID, BOUND_SYSTEM_DID, PublicationState.PUBLISHED);
        carry(OTHER_DID_OTHER_HOST);
        carrySystemValues(OTHER_SYSTEM_DID_OTHER_HOST, OTHER_HOST_DSP_BASE_URL);

        assertThat(hostStatus()).isEqualTo(HostStatusEnum.OK);
        assertThat(memberContext().getGlobalconfMismatch()).isFalse();
        assertFlags(false, false);
    }

    @Test
    void aBindingAtTheOldHostShowsHostStatusDiffersAndAMemberMismatchWhenGlobalconfCarriesADidAtTheNewHost() {
        participantRows.memberRow(MEMBER_ROW_ID, MEMBER, BOUND_DID, PublicationState.PUBLISHED);
        registerAddress(OTHER_HOST_ADDRESS);
        when(globalConfProvider.getMemberDid(MEMBER, SS_ID)).thenReturn(Optional.of(OTHER_DID_OTHER_HOST));

        var status = dataspaceApiController.getDataspaceProvisioningStatus().getBody();

        assertThat(status.getServer().getHostStatus()).isEqualTo(HostStatusEnum.DIFFERS);
        assertThat(memberContext(status).getGlobalconfMismatch()).isTrue();
    }

    private List<ILoggingEvent> errorsLoggedBy(Runnable action) {
        var logger = (Logger) LoggerFactory.getLogger(DataspacePublicationGlobalConfCheck.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.ERROR);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(null);
        }
        return appender.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.ERROR)).toList();
    }

    private void assertFlags(boolean systemDid, boolean dspBaseUrl) {
        var server = dataspaceApiController.getDataspaceProvisioningStatus().getBody().getServer();

        assertThat(server.getSystemDidMismatch()).isEqualTo(systemDid);
        assertThat(server.getDspBaseUrlMismatch()).isEqualTo(dspBaseUrl);
    }

    private void assertMismatchShown(boolean system) {
        if (system) {
            assertFlags(true, true);
        } else {
            assertThat(memberContext().getGlobalconfMismatch()).isTrue();
        }
    }

    private void registerAuthCert() {
        var cert = new CertificateTestUtils.CertificateInfoBuilder()
                .certificateStatus(CertificateInfo.STATUS_REGISTERED)
                .build();
        var key = new TokenTestUtils.KeyInfoBuilder()
                .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                .cert(cert)
                .build();
        when(signerRpcClient.getTokens()).thenReturn(List.of(new TokenTestUtils.TokenInfoBuilder().key(key).build()));
    }

    private HostStatusEnum hostStatus() {
        return dataspaceApiController.getDataspaceProvisioningStatus().getBody().getServer().getHostStatus();
    }

    private void registerAddress(String address) {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(address);
    }

    private DataspaceParticipantContextStatusDto memberContext() {
        return memberContext(dataspaceApiController.getDataspaceProvisioningStatus().getBody());
    }

    private static DataspaceParticipantContextStatusDto memberContext(DataspaceProvisioningStatusDto status) {
        var memberCtxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        return status.getParticipantContexts().stream()
                .filter(context -> memberCtxId.equals(context.getParticipantId()))
                .findFirst()
                .orElseThrow();
    }

    private void carry(String did) {
        when(globalConfProvider.getMemberDid(MEMBER, SS_ID)).thenReturn(Optional.of(did));
    }

    private void carrySystemValues(String did, String dspBaseUrl) {
        when(globalConfProvider.getServerSystemValues(SS_ID)).thenReturn(
                did == null ? Optional.empty() : Optional.of(new ServerSystemValues(did, dspBaseUrl)));
    }
}
