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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceParticipantContextStatusDto;
import org.niis.xroad.securityserver.restapi.scheduling.DataspaceParticipantProvisioningWorker;
import org.niis.xroad.securityserver.restapi.scheduling.DataspacePublicationGlobalConfCheck;
import org.niis.xroad.securityserver.restapi.service.ControlPlaneProvisioningClient;
import org.niis.xroad.securityserver.restapi.service.IdentityHubProvisioningClient;
import org.niis.xroad.securityserver.restapi.util.ParticipantRowTestSupport;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.serverconf.model.PublicationState;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestSupport.inNewTransaction;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
@WithMockUser(authorities = "VIEW_DATASPACE_STATUS")
class DataspaceGlobalConfMismatchStatusTest extends AbstractApiControllerTestContext {

    private static final long MEMBER_ROW_ID = 9013L;
    private static final long SYSTEM_ROW_ID = 9014L;
    private static final ClientId.Conf MEMBER = TestUtils.getClientId("FI", "GOV", "M1", null);
    private static final SecurityServerId.Conf SS_ID = SecurityServerId.Conf.create(MEMBER, "TEST-INMEM-SS");
    private static final String ADDRESS = "ss.example.test";
    private static final String BOUND_DID = memberDid("ss.example.test:7183");
    private static final String OTHER_DID_SAME_HOST = memberDid("ss.example.test:8443");
    private static final String OTHER_DID_OTHER_HOST = memberDid("other.example.test:7183");
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

    @AfterEach
    void deleteRows() {
        inNewTransaction(transactionManager, () -> {
            jdbcTemplate.update("DELETE FROM ds_participant WHERE id = ?", MEMBER_ROW_ID);
            jdbcTemplate.update("DELETE FROM ds_participant WHERE id = ?", SYSTEM_ROW_ID);
        });
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"SUBMITTED", "PUBLISHED"})
    void aSentMemberWhoseCarriedDidDiffersShowsAMismatch(PublicationState state) {
        insertMemberRow(state.name());
        carry(OTHER_DID_SAME_HOST);

        assertThat(memberContext().getGlobalconfMismatch()).isTrue();
    }

    @Test
    void anEqualCarriedDidShowsNoMismatch() {
        insertMemberRow("PUBLISHED");
        carry(BOUND_DID);

        assertThat(memberContext().getGlobalconfMismatch()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"NOT_SENT", "REJECTED"})
    void aMemberThatIsNotSentOrIsRejectedShowsNoMismatch(PublicationState state) {
        insertMemberRow(state.name());
        carry(OTHER_DID_SAME_HOST);

        assertThat(memberContext().getGlobalconfMismatch()).isFalse();
    }

    @Test
    void aStaleCarriedDidShowsNoMismatch() {
        insertMemberRow("PUBLISHED");
        carry(OTHER_DID_OTHER_HOST);

        assertThat(memberContext().getGlobalconfMismatch()).isFalse();
    }

    @Test
    void theMismatchClearsOnceGlobalconfCarriesTheBoundDidAgain() {
        insertMemberRow("PUBLISHED");
        carry(OTHER_DID_SAME_HOST);
        assertThat(memberContext().getGlobalconfMismatch()).isTrue();

        carry(BOUND_DID);

        assertThat(memberContext().getGlobalconfMismatch()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"SUBMITTED", "PUBLISHED"})
    void aMismatchChangesNeitherTheBindingNorThePublicationState(PublicationState state) {
        insertMemberRow(state.name());
        carry(OTHER_DID_SAME_HOST);

        assertThat(memberContext().getGlobalconfMismatch()).isTrue();
        assertThat(errorsLoggedBy(() -> globalConfCheck.check(SS_ID))).isEmpty();
        assertThat(memberContext().getGlobalconfMismatch()).isTrue();

        assertThat(jdbcTemplate.queryForMap("SELECT did, publication_state, state FROM ds_participant WHERE id = ?",
                MEMBER_ROW_ID))
                .containsEntry("did", BOUND_DID)
                .containsEntry("publication_state", state.name())
                .containsEntry("state", "ACTIVE");
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"SUBMITTED", "PUBLISHED"})
    void aSentSystemDidThatDiffersFromTheBoundOneShowsASystemDidMismatch(PublicationState state) {
        insertSystemRow(state.name());
        carrySystemValues(OTHER_SYSTEM_DID_SAME_HOST, BUILT_DSP_BASE_URL);

        assertFlags(true, false);
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"SUBMITTED", "PUBLISHED"})
    void aChangedStoredDspPortWithAnUnchangedHostShowsADspBaseUrlMismatch(PublicationState state) {
        insertSystemRow(state.name());
        carrySystemValues(BOUND_SYSTEM_DID, OTHER_PORT_DSP_BASE_URL);

        assertFlags(false, true);
    }

    @Test
    void equalSystemValuesShowNoMismatch() {
        insertSystemRow("PUBLISHED");
        carrySystemValues(BOUND_SYSTEM_DID, BUILT_DSP_BASE_URL);

        assertFlags(false, false);
    }

    @Test
    void systemValuesThatGlobalconfDoesNotCarryShowNoMismatch() {
        insertSystemRow("PUBLISHED");
        carrySystemValues(null, null);

        assertFlags(false, false);
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"NOT_SENT", "REJECTED"})
    void systemValuesThatAreNotSentOrAreRejectedShowNoMismatch(PublicationState state) {
        insertSystemRow(state.name());
        carrySystemValues(OTHER_SYSTEM_DID_SAME_HOST, OTHER_PORT_DSP_BASE_URL);

        assertFlags(false, false);
    }

    @Test
    void aSystemDidOnAnotherHostMakesThePairStaleAndShowsNoMismatch() {
        insertSystemRow("PUBLISHED");
        carrySystemValues(OTHER_SYSTEM_DID_OTHER_HOST, OTHER_PORT_DSP_BASE_URL);

        assertFlags(false, false);
    }

    @Test
    void aDspBaseUrlOnAnotherHostMakesThePairStaleAndShowsNoMismatch() {
        insertSystemRow("PUBLISHED");
        carrySystemValues(OTHER_SYSTEM_DID_SAME_HOST, OTHER_HOST_DSP_BASE_URL);

        assertFlags(false, false);
    }

    @Test
    void theSystemMismatchClearsOnceGlobalconfCarriesTheBuiltValuesAgain() {
        insertSystemRow("PUBLISHED");
        carrySystemValues(OTHER_SYSTEM_DID_SAME_HOST, OTHER_PORT_DSP_BASE_URL);
        assertFlags(true, true);

        carrySystemValues(BOUND_SYSTEM_DID, BUILT_DSP_BASE_URL);

        assertFlags(false, false);
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"SUBMITTED", "PUBLISHED"})
    void aSystemMismatchChangesNeitherTheBindingNorThePublicationState(PublicationState state) {
        insertSystemRow(state.name());
        carrySystemValues(OTHER_SYSTEM_DID_SAME_HOST, OTHER_PORT_DSP_BASE_URL);

        assertFlags(true, true);
        assertThat(errorsLoggedBy(() -> globalConfCheck.check(SS_ID))).isEmpty();
        assertFlags(true, true);

        assertThat(jdbcTemplate.queryForMap("SELECT did, publication_state, state FROM ds_participant WHERE id = ?",
                SYSTEM_ROW_ID))
                .containsEntry("did", BOUND_SYSTEM_DID)
                .containsEntry("publication_state", state.name())
                .containsEntry("state", "ACTIVE");
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

    private DataspaceParticipantContextStatusDto memberContext() {
        var memberCtxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        return dataspaceApiController.getDataspaceProvisioningStatus().getBody().getParticipantContexts().stream()
                .filter(context -> memberCtxId.equals(context.getParticipantId()))
                .findFirst()
                .orElseThrow();
    }

    private void carry(String did) {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(ADDRESS);
        when(globalConfProvider.getMemberDid(MEMBER, SS_ID)).thenReturn(Optional.of(did));
    }

    private void carrySystemValues(String did, String dspBaseUrl) {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(ADDRESS);
        when(globalConfProvider.getServerSystemValues(SS_ID)).thenReturn(
                did == null ? Optional.empty() : Optional.of(new ServerSystemValues(did, dspBaseUrl)));
    }

    private void insertSystemRow(String publicationState) {
        ParticipantRowTestSupport.insertSystemRow(jdbcTemplate, transactionManager, SYSTEM_ROW_ID, BOUND_SYSTEM_DID,
                publicationState);
    }

    private void insertMemberRow(String publicationState) {
        inNewTransaction(transactionManager, () -> jdbcTemplate.update("INSERT INTO ds_participant"
                        + " (id, participant_type, member_identifier, ctx_id, did, scheme_version, state,"
                        + " publication_state, created_at, updated_at)"
                        + " VALUES (?, 'MEMBER', (SELECT id FROM identifier WHERE object_type = 'MEMBER'"
                        + " AND xroad_instance = ? AND member_class = ? AND member_code = ?),"
                        + " ?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                MEMBER_ROW_ID, MEMBER.getXRoadInstance(), MEMBER.getMemberClass(), MEMBER.getMemberCode(),
                ParticipantIdentifierScheme.memberCtxId(MEMBER), BOUND_DID, ParticipantIdentifierScheme.SCHEME_VERSION,
                publicationState));
    }

    private static String systemDid(String authority) {
        return ParticipantIdentifierScheme.systemDid(authority).toString();
    }

    private static String memberDid(String authority) {
        return ParticipantIdentifierScheme.memberDid(MEMBER, authority).toString();
    }
}
