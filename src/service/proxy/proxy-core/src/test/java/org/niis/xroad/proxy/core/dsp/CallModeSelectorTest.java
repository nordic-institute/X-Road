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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.service.DefaultServiceAddressResolver;
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver.ProviderAddress;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    private final ProxyProperties proxyProperties = mock(ProxyProperties.class);
    private final DspRequestProcessor dspRequestProcessor = mock(DspRequestProcessor.class);
    private final CallModeSelector selector =
            new CallModeSelector(globalConf, serviceAddressResolver, proxyProperties, dspRequestProcessor);

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
    void addressWithoutMatchingServerGivesUnnegotiated() {
        publishMember(SS_A, HOST_A);

        var mode = selector.select(CalledTarget.of(serviceOf(PROVIDER), false), addresses("other.example"));

        assertThat(mode).isEqualTo(new CallMode.Unnegotiated());
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
        publishMemberDid(SS_A, HOST_A, "first-bad-did");
        publishMemberDid(SS_B, HOST_B, "second-bad-did");
        var target = CalledTarget.of(serviceOf(PROVIDER), false);

        assertThatThrownBy(() -> selector.select(target, List.of(new ProviderAddress(SS_A, HOST_A), new ProviderAddress(SS_B, HOST_B))))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("first-bad-did")
                .hasMessageNotContaining("second-bad-did");
    }

    @Test
    void memberDidPublishedWithoutSystemValuesThrowsInsteadOfUnnegotiated() {
        publishMemberDidOnly(SS_A, HOST_A);
        var target = CalledTarget.of(serviceOf(PROVIDER), false);

        assertThatThrownBy(() -> selector.select(target, addresses(HOST_A)))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("ss-a");
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
    void overDspIsEmptyWhenDspIsDisabled() {
        when(proxyProperties.dspEnabled()).thenReturn(false);

        assertThat(selector.overDsp(requestOf(PROVIDER))).isEmpty();
        verify(serviceAddressResolver, never()).resolveCandidates(any(), any());
    }

    @Test
    void overDspIsEmptyWhenTheRequestBypassesDsp() {
        when(proxyProperties.dspEnabled()).thenReturn(true);
        var request = requestOf(PROVIDER);
        when(dspRequestProcessor.bypassesDsp(request)).thenReturn(true);

        assertThat(selector.overDsp(request)).isEmpty();
        verify(serviceAddressResolver, never()).resolveCandidates(any(), any());
    }

    @Test
    void overDspIsEmptyWhenNoServerPublishedTheCalledDid() {
        when(proxyProperties.dspEnabled()).thenReturn(true);
        when(serviceAddressResolver.resolveCandidates(any(), any())).thenReturn(addresses(HOST_A));

        assertThat(selector.overDsp(requestOf(PROVIDER))).isEmpty();
    }

    @Test
    void overDspCarriesThePublishedCandidates() {
        when(proxyProperties.dspEnabled()).thenReturn(true);
        when(serviceAddressResolver.resolveCandidates(any(), any())).thenReturn(addresses(HOST_A));
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
                .isInstanceOf(XrdRuntimeException.class);
    }

    private void publishMember(SecurityServerId serverId, String host) {
        publishMemberDid(serverId, host, DspConventions.memberCounterPartyId(MEMBER, host).toString());
    }

    private void publishMemberDid(SecurityServerId serverId, String host, String did) {
        when(globalConf.getMemberDid(PROVIDER, serverId)).thenReturn(Optional.of(did));
        when(globalConf.getServerSystemValues(serverId)).thenReturn(Optional.of(systemValues(host)));
    }

    private void publishMemberDidOnly(SecurityServerId serverId, String host) {
        when(globalConf.getMemberDid(PROVIDER, serverId))
                .thenReturn(Optional.of(DspConventions.memberCounterPartyId(MEMBER, host).toString()));
    }

    private void publishSystem(SecurityServerId serverId, String host) {
        when(globalConf.getServerSystemValues(serverId)).thenReturn(Optional.of(systemValues(host)));
    }

    private static ServerSystemValues systemValues(String host) {
        return new ServerSystemValues(DspConventions.systemCounterPartyId(host).toString(), "https://" + host + ":8183/api/dsp");
    }

    private static DspRequest requestOf(ClientId provider) {
        return new DspRequest(serviceOf(provider), MEMBER, null, false);
    }

    private static ServiceId serviceOf(ClientId provider) {
        return ServiceId.Conf.create(provider, "testService");
    }

    private static List<ProviderAddress> addresses(String... hosts) {
        return Arrays.stream(hosts).map(host -> new ProviderAddress(null, host)).toList();
    }
}
