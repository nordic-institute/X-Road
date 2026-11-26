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
package org.niis.xroad.proxy.core.dsp;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.proxy.core.service.DefaultServiceAddressResolver;
import org.niis.xroad.proxy.core.service.DefaultServiceAddressResolver.ProviderCandidates;
import org.niis.xroad.proxy.core.service.DefaultServiceAddressResolver.ServerInMaintenance;
import org.niis.xroad.proxy.core.service.LegacyCallRefusalPolicy;
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver.ProviderAddress;
import org.niis.xroad.proxy.core.test.PublishedDspValues;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CallModeSelectorTest {

    private static final ClientId PROVIDER = ClientId.Conf.create("DEV", "COM", "222", "Provider");
    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "222");
    private static final SecurityServerId SS_A = SecurityServerId.Conf.create("DEV", "COM", "222", "ss-a");
    private static final SecurityServerId SS_B = SecurityServerId.Conf.create("DEV", "COM", "222", "ss-b");
    private static final String HOST_A = "ss-a.example";
    private static final String HOST_B = "ss-b.example";

    private final GlobalConfProvider globalConf = mock(GlobalConfProvider.class);
    private final DefaultServiceAddressResolver serviceAddressResolver = mock(DefaultServiceAddressResolver.class);
    private final DspRequestProcessor dspRequestProcessor = mock(DspRequestProcessor.class);
    private final LegacyCallRefusalPolicy legacyCallRefusalPolicy = mock(LegacyCallRefusalPolicy.class);
    private final CallModeSelector selector =
            new CallModeSelector(globalConf, serviceAddressResolver, dspRequestProcessor,
                    legacyCallRefusalPolicy);

    private final List<LogCapture> logCaptures = new CopyOnWriteArrayList<>();

    @AfterEach
    void detachLogCaptures() {
        var logger = Logger.getLogger(CallModeSelector.class.getName());
        logCaptures.forEach(logger::removeHandler);
        logger.setLevel(null);
    }

    @BeforeEach
    void servers() {
        when(globalConf.getClientSecurityServers(any())).thenReturn(Set.of(SS_A, SS_B));
        when(globalConf.getSecurityServerAddress(SS_A)).thenReturn(HOST_A);
        when(globalConf.getSecurityServerAddress(SS_B)).thenReturn(HOST_B);
    }

    @Test
    void publishedMemberDidGivesDspCandidateAtPublishedBaseUrlAndParticipantPath() {
        publishMember(SS_A, HOST_A);

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), addresses(HOST_A));

        assertThat(mode).isEqualTo(new CallMode.Dsp(List.of(new AcquisitionCandidate(
                "did:web:ss-a.example%3A7183:v1:DEV:COM:222",
                "https://ss-a.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1"))));
    }

    @Test
    void allPublishedGivesEveryServerAsCandidate() {
        publishMember(SS_A, HOST_A);
        publishMember(SS_B, HOST_B);

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), addresses(HOST_A, HOST_B));

        assertThat(mode).isInstanceOfSatisfying(CallMode.Dsp.class, dsp -> assertThat(dsp.candidates())
                .extracting(AcquisitionCandidate::counterPartyAddress)
                .containsExactlyInAnyOrder(
                        "https://ss-a.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1",
                        "https://ss-b.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1"));
    }

    @Test
    void mixedSetGivesOnlyThePublishedServerAsCandidate() {
        publishMember(SS_A, HOST_A);

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), addresses(HOST_A, HOST_B));

        assertThat(mode).isInstanceOfSatisfying(CallMode.Dsp.class, dsp -> assertThat(dsp.candidates())
                .extracting(AcquisitionCandidate::counterPartyAddress)
                .containsExactly("https://ss-a.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1"));
    }

    @Test
    void nonePublishedGivesUnnegotiated() {
        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), addresses(HOST_A, HOST_B));

        assertThat(mode).isEqualTo(new CallMode.Unnegotiated());
    }

    @Test
    void publishedSystemValuesWithoutMemberDidGiveUnnegotiated() {
        publishSystem(SS_A, HOST_A);

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), addresses(HOST_A));

        assertThat(mode).isEqualTo(new CallMode.Unnegotiated());
    }

    @Test
    void addressWithoutMatchingServerGivesUnnegotiatedAndLogsAWarnNamingTheAddress() {
        publishMember(SS_A, HOST_A);
        var capture = captureSelectorLog();

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), addresses("other.example"));

        assertThat(mode).isEqualTo(new CallMode.Unnegotiated());
        assertThat(capture.records)
                .filteredOn(logRecord -> capture.formatted(logRecord).contains("other.example"))
                .singleElement()
                .satisfies(logRecord -> assertThat(logRecord.getLevel()).isEqualTo(Level.WARNING));
    }

    @Test
    void emptyServerSetGivesUnnegotiated() {
        when(globalConf.getClientSecurityServers(any())).thenReturn(Set.of());

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), addresses(HOST_A));

        assertThat(mode).isEqualTo(new CallMode.Unnegotiated());
    }

    @Test
    void hintedAddressIsUsedWithoutLookingUpTheProvidersServers() {
        publishMember(SS_B, HOST_B);

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), List.of(new ProviderAddress(SS_B, HOST_B)));

        assertThat(mode).isInstanceOfSatisfying(CallMode.Dsp.class, dsp -> assertThat(dsp.candidates())
                .extracting(AcquisitionCandidate::counterPartyAddress)
                .containsExactly("https://ss-b.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1"));
        verify(globalConf, never()).getClientSecurityServers(any());
    }

    @Test
    void systemTargetUsesThePublishedSystemDid() {
        publishSystem(SS_A, HOST_A);
        var monitoring = ServiceId.Conf.create(MEMBER, "getSecurityServerMetrics");

        var mode = selector.select(CalledTarget.of(monitoring, false), addresses(HOST_A));

        assertThat(mode).isEqualTo(new CallMode.Dsp(List.of(new AcquisitionCandidate(
                "did:web:ss-a.example%3A7183:v1:system",
                "https://ss-a.example:8183/api/dsp/system/http-dsp-profile-2025-1"))));
    }

    @Test
    void unusablePublishedDidIsDroppedWhileAnotherServerIsLeft() {
        publishMember(SS_A, HOST_A);
        publishMemberDid(SS_B, HOST_B, "not-a-did");

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), addresses(HOST_A, HOST_B));

        assertThat(mode).isInstanceOfSatisfying(CallMode.Dsp.class, dsp -> assertThat(dsp.candidates())
                .extracting(AcquisitionCandidate::counterPartyAddress)
                .containsExactly("https://ss-a.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1"));
    }

    @Test
    void allPublishedDidsUnusableThrowsTheFirstFailureInsteadOfUnnegotiated() {
        var versionTwoDid = "did:web:ss-b.example%3A7183:v2:DEV:COM:222";
        publishMemberDid(SS_A, HOST_A, "first-bad-did");
        publishMemberDid(SS_B, HOST_B, versionTwoDid);
        var target = CalledTarget.of(serviceOf(PROVIDER), false);

        assertThatThrownBy(() -> selector.select(target, List.of(new ProviderAddress(SS_A, HOST_A), new ProviderAddress(SS_B, HOST_B))))
                .satisfies(thrown -> assertSanitized(thrown, ErrorCode.INVALID_ENCODED_ID, "first-bad-did", versionTwoDid));
    }

    @Test
    void memberDidPublishedWithoutSystemValuesThrowsInsteadOfUnnegotiated() {
        publishMemberDidOnly(SS_A, HOST_A);
        var target = CalledTarget.of(serviceOf(PROVIDER), false);

        assertThatThrownBy(() -> selector.select(target, addresses(HOST_A)))
                .satisfies(thrown -> assertSanitized(thrown, ErrorCode.VALIDATION_ERROR, "ss-a", "DEV:COM:222"));
    }

    @Test
    void memberDidPublishedWithoutSystemValuesIsDroppedWhileAnotherServerIsLeft() {
        publishMemberDidOnly(SS_A, HOST_A);
        publishMember(SS_B, HOST_B);

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), addresses(HOST_A, HOST_B));

        assertThat(mode).isInstanceOfSatisfying(CallMode.Dsp.class, dsp -> assertThat(dsp.candidates())
                .extracting(AcquisitionCandidate::counterPartyAddress)
                .containsExactly("https://ss-b.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1"));
    }

    @Test
    void providersServersAreLookedUpOnceForSeveralAddresses() {
        publishMember(SS_A, HOST_A);
        publishMember(SS_B, HOST_B);

        selector.select(CalledTarget.of(serviceOf(PROVIDER), false), addresses(HOST_A, HOST_B, "other.example"));

        verify(globalConf, times(1)).getClientSecurityServers(any());
    }

    @Test
    void overDspIsEmptyWhenTheRequestBypassesDsp() {
        var request = requestOf(PROVIDER);
        when(dspRequestProcessor.bypassesDsp(request)).thenReturn(true);

        assertThat(selector.overDsp(request)).isEmpty();
        verify(serviceAddressResolver, never()).resolveCandidates(any(), any());
        verifyNoInteractions(legacyCallRefusalPolicy);
    }

    @Test
    void overDspIsEmptyWhenNoServerPublishedTheCalledDid() {
        when(serviceAddressResolver.resolveCandidates(any(), any())).thenReturn(new ProviderCandidates(addresses(HOST_A), List.of()));

        assertThat(selector.overDsp(requestOf(PROVIDER))).isEmpty();
        verify(legacyCallRefusalPolicy).checkConsumerCallAllowed(serviceOf(PROVIDER));
    }

    @Test
    void overDspRefusesTheUnnegotiatedCallWhenLegacyCallsAreRefused() {
        when(serviceAddressResolver.resolveCandidates(any(), any())).thenReturn(new ProviderCandidates(addresses(HOST_A), List.of()));
        var refusal = XrdRuntimeException.systemException(ErrorCode.LEGACY_CALL_REFUSED, "legacy calls are refused");
        doThrow(refusal).when(legacyCallRefusalPolicy).checkConsumerCallAllowed(serviceOf(PROVIDER));
        var request = requestOf(PROVIDER);

        assertThatThrownBy(() -> selector.overDsp(request)).isSameAs(refusal);
    }

    @Test
    void overDspDoesNotCheckTheRefusalWhenTheCalledDidIsPublished() {
        when(serviceAddressResolver.resolveCandidates(any(), any())).thenReturn(new ProviderCandidates(addresses(HOST_A), List.of()));
        publishMember(SS_A, HOST_A);

        assertThat(selector.overDsp(requestOf(PROVIDER))).isPresent();
        verifyNoInteractions(legacyCallRefusalPolicy);
    }

    @Test
    void overDspTurnsAPublishedDidFailureIntoInternalErrorWithoutTheDid() {
        when(serviceAddressResolver.resolveCandidates(any(), any())).thenReturn(new ProviderCandidates(addresses(HOST_A), List.of()));
        publishMemberDid(SS_A, HOST_A, "not-a-did");
        var request = requestOf(PROVIDER);

        assertThatThrownBy(() -> selector.overDsp(request))
                .satisfies(thrown -> assertSanitized(thrown, ErrorCode.INVALID_ENCODED_ID, "not-a-did"));
        verifyNoInteractions(legacyCallRefusalPolicy);
    }

    @Test
    void overDspKeepsTheCodeOfAFailureThatIsNotAPublishedDidFailure() {
        when(serviceAddressResolver.resolveCandidates(any(), any())).thenReturn(new ProviderCandidates(addresses(HOST_A), List.of()));
        var readFailure = XrdRuntimeException.systemException(ErrorCode.IO_ERROR, "global configuration is not readable");
        when(globalConf.getClientSecurityServers(any())).thenThrow(readFailure);
        var request = requestOf(PROVIDER);

        assertThatThrownBy(() -> selector.overDsp(request)).isSameAs(readFailure);
        verifyNoInteractions(legacyCallRefusalPolicy);
    }

    @Test
    void overDspOfAManagementRequestCarriesTheSystemCandidate() {
        when(serviceAddressResolver.resolveCandidates(any(), any())).thenReturn(new ProviderCandidates(addresses(HOST_A), List.of()));
        publishSystem(SS_A, HOST_A);
        var request = new DspRequest(ServiceId.Conf.create(PROVIDER, "clientReg"), MEMBER, null, true);

        var routed = selector.overDsp(request);

        assertThat(routed).hasValueSatisfying(dspRequest -> assertThat(dspRequest.candidates())
                .containsExactly(new AcquisitionCandidate(PublishedDspValues.systemDidAt(HOST_A),
                        "https://ss-a.example:8183/api/dsp/system/http-dsp-profile-2025-1")));
        verifyNoInteractions(legacyCallRefusalPolicy);
    }

    @Test
    void overDspKeepsAnErrorOfTheCandidateResolutionBeforeTheLegacyCallRefusalIsChecked() {
        var unknownMember = XrdRuntimeException.systemException(ErrorCode.UNKNOWN_MEMBER, "no server serves the provider");
        when(serviceAddressResolver.resolveCandidates(any(), any())).thenThrow(unknownMember);
        var request = requestOf(PROVIDER);

        assertThatThrownBy(() -> selector.overDsp(request)).isSameAs(unknownMember);
        verifyNoInteractions(legacyCallRefusalPolicy);
    }

    @Test
    void overDspKeepsTheMaintenanceErrorUnchanged() {
        publishMember(SS_A, HOST_A);
        var maintenanceError = XrdRuntimeException.systemException(ErrorCode.MAINTENANCE_MODE, "ss-a is in maintenance mode");
        when(serviceAddressResolver.resolveCandidates(any(), any())).thenReturn(new ProviderCandidates(addresses(HOST_B),
                List.of(new ServerInMaintenance(new ProviderAddress(null, HOST_A), maintenanceError))));
        var request = requestOf(PROVIDER);

        assertThatThrownBy(() -> selector.overDsp(request)).isSameAs(maintenanceError);
        verifyNoInteractions(legacyCallRefusalPolicy);
    }

    @Test
    void overDspCarriesThePublishedCandidates() {
        when(serviceAddressResolver.resolveCandidates(any(), any())).thenReturn(new ProviderCandidates(addresses(HOST_A), List.of()));
        publishMember(SS_A, HOST_A);

        var routed = selector.overDsp(requestOf(PROVIDER));

        assertThat(routed).hasValueSatisfying(request -> assertThat(request.candidates())
                .extracting(AcquisitionCandidate::counterPartyAddress)
                .containsExactly("https://ss-a.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1"));
    }

    @Test
    void oneUnpublishedAndOneUnusablePublishedServerThrowsInsteadOfUnnegotiated() {
        publishMemberDid(SS_B, HOST_B, "not-a-did");
        var target = CalledTarget.of(serviceOf(PROVIDER), false);

        assertThatThrownBy(() -> selector.select(target, List.of(new ProviderAddress(SS_A, HOST_A), new ProviderAddress(SS_B, HOST_B))))
                .satisfies(thrown -> assertSanitized(thrown, ErrorCode.INVALID_ENCODED_ID, "not-a-did"));
    }

    @Test
    void hintedServerThatPublishedNothingGivesUnnegotiatedWhileASiblingPublished() {
        publishMember(SS_B, HOST_B);

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), List.of(new ProviderAddress(SS_A, HOST_A)));

        assertThat(mode).isEqualTo(new CallMode.Unnegotiated());
    }

    @Test
    void hintedServerWithUnusablePublishedDidThrowsWhileASiblingIsUsable() {
        publishMemberDid(SS_A, HOST_A, "not-a-did");
        publishMember(SS_B, HOST_B);
        var target = CalledTarget.of(serviceOf(PROVIDER), false);

        assertThatThrownBy(() -> selector.select(target, List.of(new ProviderAddress(SS_A, HOST_A))))
                .satisfies(thrown -> assertSanitized(thrown, ErrorCode.INVALID_ENCODED_ID, "not-a-did"));
    }

    @Test
    void schemeVersionV2FailsAsInternalErrorWithoutNamingThePublishedDid() {
        var did = "did:web:ss-a.example%3A7183:v2:DEV:COM:222";
        publishMemberDid(SS_A, HOST_A, did);
        var target = CalledTarget.of(serviceOf(PROVIDER), false);

        assertThatThrownBy(() -> selector.select(target, addresses(HOST_A)))
                .satisfies(thrown -> assertSanitized(thrown, ErrorCode.DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED, did, "ss-a.example"));
    }

    @Test
    void memberDidOfAnotherMemberIsUnusable() {
        publishMemberDid(SS_A, HOST_A, PublishedDspValues.memberDidAt(ClientId.Conf.create("DEV", "COM", "333"), HOST_A));
        var target = CalledTarget.of(serviceOf(PROVIDER), false);

        assertThatThrownBy(() -> selector.select(target, addresses(HOST_A)))
                .satisfies(thrown -> assertSanitized(thrown, ErrorCode.VALIDATION_ERROR, "DEV:COM:333", "ss-a.example"));
    }

    @Test
    void systemDidPublishedAsMemberDidIsUnusable() {
        publishMemberDid(SS_A, HOST_A, PublishedDspValues.systemDidAt(HOST_A));
        var target = CalledTarget.of(serviceOf(PROVIDER), false);

        assertThatThrownBy(() -> selector.select(target, addresses(HOST_A)))
                .satisfies(thrown -> assertSanitized(thrown, ErrorCode.VALIDATION_ERROR, "ss-a.example"));
    }

    @Test
    void memberDidOfAnotherMemberIsDroppedWhileAnotherServerIsLeft() {
        publishMemberDid(SS_A, HOST_A, PublishedDspValues.memberDidAt(ClientId.Conf.create("DEV", "COM", "333"), HOST_A));
        publishMember(SS_B, HOST_B);

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), addresses(HOST_A, HOST_B));

        assertThat(mode).isInstanceOfSatisfying(CallMode.Dsp.class, dsp -> assertThat(dsp.candidates())
                .extracting(AcquisitionCandidate::counterPartyAddress)
                .containsExactly("https://ss-b.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1"));
    }

    @Test
    void systemTargetWithAMemberDidPublishedAsSystemDidIsUnusable() {
        when(globalConf.getServerSystemValues(SS_A)).thenReturn(Optional.of(new ServerSystemValues(
                PublishedDspValues.memberDidAt(MEMBER, HOST_A), "https://" + HOST_A + ":8183/api/dsp")));
        var target = CalledTarget.of(ServiceId.Conf.create(MEMBER, "getSecurityServerMetrics"), false);

        assertThatThrownBy(() -> selector.select(target, addresses(HOST_A)))
                .satisfies(thrown -> assertSanitized(thrown, ErrorCode.VALIDATION_ERROR, "ss-a.example", "DEV:COM:222"));
    }

    @Test
    void foreignInstanceWithoutPublishedValuesGivesUnnegotiated() {
        var foreignServer = SecurityServerId.Conf.create("FOR", "GOV", "111", "ss-f");
        when(globalConf.getClientSecurityServers(any())).thenReturn(Set.of(foreignServer));
        when(globalConf.getSecurityServerAddress(foreignServer)).thenReturn("ss-f.example");
        var foreignProvider = ClientId.Conf.create("FOR", "GOV", "111", "Provider");

        var mode = selector.select(CalledTarget.of(serviceOf(foreignProvider), false), addresses("ss-f.example"));

        assertThat(mode).isEqualTo(new CallMode.Unnegotiated());
    }

    @Test
    void foreignInstanceWithPublishedValuesGivesDsp() {
        var foreignServer = SecurityServerId.Conf.create("FOR", "GOV", "111", "ss-f");
        var foreignMember = ClientId.Conf.create("FOR", "GOV", "111");
        var foreignProvider = ClientId.Conf.create("FOR", "GOV", "111", "Provider");
        when(globalConf.getClientSecurityServers(any())).thenReturn(Set.of(foreignServer));
        when(globalConf.getSecurityServerAddress(foreignServer)).thenReturn("ss-f.example");
        when(globalConf.getMemberDid(foreignProvider, foreignServer))
                .thenReturn(Optional.of(PublishedDspValues.memberDidAt(foreignMember, "ss-f.example")));
        when(globalConf.getServerSystemValues(foreignServer)).thenReturn(Optional.of(systemValues("ss-f.example")));

        var mode = selector.select(CalledTarget.of(serviceOf(foreignProvider), false), addresses("ss-f.example"));

        assertThat(mode).isInstanceOfSatisfying(CallMode.Dsp.class, dsp -> assertThat(dsp.candidates())
                .extracting(AcquisitionCandidate::counterPartyAddress)
                .containsExactly("https://ss-f.example:8183/api/dsp/FOR:GOV:111/http-dsp-profile-2025-1"));
    }

    @Test
    void publishedServerInMaintenanceWithUnpublishedSiblingFailsWithTheMaintenanceError() {
        publishMember(SS_A, HOST_A);
        var maintenanceError = XrdRuntimeException.systemException(ErrorCode.MAINTENANCE_MODE, "ss-a is in maintenance mode");
        var servers = new ProviderCandidates(addresses(HOST_B),
                List.of(new ServerInMaintenance(new ProviderAddress(null, HOST_A), maintenanceError)));

        assertThatThrownBy(() -> selector.select(CalledTarget.of(serviceOf(PROVIDER), false), servers))
                .isSameAs(maintenanceError);
    }

    @Test
    void publishedServerInMaintenanceDoesNotStopDspToAnAvailablePublishedSibling() {
        publishMember(SS_A, HOST_A);
        publishMember(SS_B, HOST_B);
        var servers = new ProviderCandidates(addresses(HOST_B), List.of(new ServerInMaintenance(new ProviderAddress(null, HOST_A),
                XrdRuntimeException.systemException(ErrorCode.MAINTENANCE_MODE, "ss-a is in maintenance mode"))));

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), servers);

        assertThat(mode).isInstanceOfSatisfying(CallMode.Dsp.class, dsp -> assertThat(dsp.candidates())
                .extracting(AcquisitionCandidate::counterPartyAddress)
                .containsExactly("https://ss-b.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1"));
    }

    @Test
    void unpublishedServerInMaintenanceWithUnpublishedSiblingGivesUnnegotiated() {
        var servers = new ProviderCandidates(addresses(HOST_B), List.of(new ServerInMaintenance(new ProviderAddress(null, HOST_A),
                XrdRuntimeException.systemException(ErrorCode.MAINTENANCE_MODE, "ss-a is in maintenance mode"))));

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), servers);

        assertThat(mode).isEqualTo(new CallMode.Unnegotiated());
    }

    private LogCapture captureSelectorLog() {
        var capture = new LogCapture();
        var logger = Logger.getLogger(CallModeSelector.class.getName());
        logger.setLevel(Level.ALL);
        logger.addHandler(capture);
        logCaptures.add(capture);
        return capture;
    }

    private static void assertSanitized(Throwable thrown, ErrorCode originalCode, String... hidden) {
        assertThat(thrown).isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
            assertThat(e.isCausedBy(ErrorCode.INTERNAL_ERROR)).isTrue();
            assertThat(e.getErrorCodeMetadata().getFirst()).isEqualTo("originalCode=" + originalCode.code());
            assertThat(e.getCause()).isNull();
            assertThat(e.getMessage()).doesNotContain(hidden);
            assertThat(e.getDetails()).doesNotContain(hidden);
        });
    }

    private void publishMember(SecurityServerId serverId, String host) {
        publishMemberDid(serverId, host, PublishedDspValues.memberDidAt(MEMBER, host));
    }

    private void publishMemberDid(SecurityServerId serverId, String host, String did) {
        when(globalConf.getMemberDid(PROVIDER, serverId)).thenReturn(Optional.of(did));
        when(globalConf.getServerSystemValues(serverId)).thenReturn(Optional.of(systemValues(host)));
    }

    private void publishMemberDidOnly(SecurityServerId serverId, String host) {
        when(globalConf.getMemberDid(PROVIDER, serverId))
                .thenReturn(Optional.of(PublishedDspValues.memberDidAt(MEMBER, host)));
    }

    private void publishSystem(SecurityServerId serverId, String host) {
        when(globalConf.getServerSystemValues(serverId)).thenReturn(Optional.of(systemValues(host)));
    }

    private static ServerSystemValues systemValues(String host) {
        return new ServerSystemValues(PublishedDspValues.systemDidAt(host), "https://" + host + ":8183/api/dsp");
    }

    private static DspRequest requestOf(ClientId provider) {
        return new DspRequest(serviceOf(provider), MEMBER, null, false);
    }

    private static final class LogCapture extends Handler {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord logRecord) {
            records.add(logRecord);
        }

        String formatted(LogRecord logRecord) {
            return logRecord instanceof ExtLogRecord ext ? ext.getFormattedMessage() : logRecord.getMessage();
        }

        @Override
        public void flush() {
            // nothing is buffered
        }

        @Override
        public void close() {
            records.clear();
        }
    }

    private static ServiceId serviceOf(ClientId provider) {
        return ServiceId.Conf.create(provider, "testService");
    }

    private static List<ProviderAddress> addresses(String... hosts) {
        return Arrays.stream(hosts).map(host -> new ProviderAddress(null, host)).toList();
    }
}
