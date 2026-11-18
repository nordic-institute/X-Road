/*
 * The MIT License
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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.HttpSender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.dsp.AcquisitionCandidate;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspAcquisition;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATA_PLANE_UNREACHABLE;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;

@ExtendWith(MockitoExtension.class)
class DspFailoverTest {

    private static final String SS0 = "https://ss0.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final String SS1 = "https://ss1.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final String SS2 = "https://ss2.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final String REQUEST_ID = "req-1";

    @Mock
    private DspRequestProcessor dspRequestProcessor;
    @Mock
    private HttpSenderProvider httpSenderProvider;
    @Mock
    private HttpSender httpSender;

    private DspFailover failover;
    private DspRequest request;

    @BeforeEach
    void setUp() {
        failover = new DspFailover(dspRequestProcessor, httpSenderProvider, new DspSendFailureHandler(dspRequestProcessor));
        var provider = ClientId.Conf.create("DEV", "COM", "222", "TESTSERVICE");
        request = new DspRequest(ServiceId.Conf.create(provider, "petstore"), provider, null, false);
        when(httpSenderProvider.createClientHttpSender()).thenReturn(httpSender);
    }

    @Test
    void aSuccessfulSendReturnsItsOutcomeWithoutAnyFailureHandling() throws Exception {
        acquisitionsWonBy(2, SS0);

        var outcome = failover.run(request, REQUEST_ID, true, () -> true, (_, access) -> access.winner().counterPartyAddress());

        assertThat(outcome).isEqualTo(SS0);
        verify(httpSender, never()).close();
        verify(dspRequestProcessor, never()).invalidateAssetAccessWonBy(any());
        verify(dspRequestProcessor, never()).reportCounterPartyFailure(any(), any());
    }

    @Test
    void aFailureBeforeSendMovesTheCallToTheSiblingWithTheFailedServerExcluded() throws Exception {
        acquisitionsWonBy(2, SS0, SS1);

        var outcome = failover.run(request, REQUEST_ID, true, () -> true, (_, access) -> {
            if (SS0.equals(access.winner().counterPartyAddress())) {
                throw new ConnectException("refused");
            }
            return access.winner().counterPartyAddress();
        });

        assertThat(outcome).isEqualTo(SS1);
        assertThat(acquiredRequests()).extracting(DspRequest::triedServers).containsExactly(Set.of(), Set.of(SS0));
        verify(dspRequestProcessor).invalidateAssetAccessWonBy(SS0);
        verify(dspRequestProcessor).reportCounterPartyFailure(winnerId(SS0), SS0);
        verify(httpSender).close();
    }

    @Test
    void everyCandidateIsTriedOnceThenTheCallerGetsTheDataPlaneError() {
        acquisitionsWonBy(3, SS0, SS1, SS2);
        var attempts = new AtomicInteger();

        assertThatThrownBy(() -> failover.run(request, REQUEST_ID, true, () -> true, (_, _) -> {
            attempts.incrementAndGet();
            throw new ConnectException("refused");
        })).isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
            assertThat(e.isCausedBy(IO_ERROR)).isTrue();
            assertThat(e.getErrorCodeMetadata()).contains("originalCode=" + DSP_DATA_PLANE_UNREACHABLE.code());
        });

        assertThat(attempts).hasValue(3);
        assertThat(acquiredRequests()).extracting(DspRequest::triedServers)
                .containsExactly(Set.of(), Set.of(SS0), Set.of(SS0, SS1));
    }

    @Test
    void aFailureAfterSendIsThrownUnchangedWithoutEvictionOrReport() {
        acquisitionsWonBy(2, SS0);
        var readTimeout = new SocketTimeoutException("Read timed out");

        assertThatThrownBy(() -> failover.run(request, REQUEST_ID, true, () -> true, (_, _) -> {
            throw readTimeout;
        })).isSameAs(readTimeout);

        verify(dspRequestProcessor, times(1)).execute(any());
        verify(httpSender).close();
        verify(dspRequestProcessor, never()).invalidateAssetAccessWonBy(any());
        verify(dspRequestProcessor, never()).reportCounterPartyFailure(any(), any());
    }

    @Test
    void aFailureAfterSendOnTheSiblingEndsWithThatOriginalError() {
        acquisitionsWonBy(3, SS0, SS1);
        var readTimeout = new SocketTimeoutException("Read timed out");

        assertThatThrownBy(() -> failover.run(request, REQUEST_ID, true, () -> true, (_, access) -> {
            if (SS0.equals(access.winner().counterPartyAddress())) {
                throw new ConnectException("refused");
            }
            throw readTimeout;
        })).isSameAs(readTimeout);

        verify(dspRequestProcessor, times(2)).execute(any());
        verify(dspRequestProcessor).invalidateAssetAccessWonBy(SS0);
        verify(dspRequestProcessor, never()).invalidateAssetAccessWonBy(SS1);
    }

    @Test
    void withRequestRetryOffAFailureBeforeSendIsHandledButNotFailedOver() {
        acquisitionsWonBy(2, SS0);

        assertThatThrownBy(() -> failover.run(request, REQUEST_ID, false, () -> true, (_, _) -> {
            throw new ConnectException("refused");
        })).isInstanceOfSatisfying(XrdRuntimeException.class,
                e -> assertThat(e.getErrorCodeMetadata()).contains("originalCode=" + DSP_DATA_PLANE_UNREACHABLE.code()));

        verify(dspRequestProcessor, times(1)).execute(any());
        verify(dspRequestProcessor).invalidateAssetAccessWonBy(SS0);
        verify(dspRequestProcessor).reportCounterPartyFailure(winnerId(SS0), SS0);
    }

    @Test
    void aSingleCandidateIsNotFailedOver() {
        acquisitionsWonBy(1, SS0);

        assertThatThrownBy(() -> failover.run(request, REQUEST_ID, true, () -> true, (_, _) -> {
            throw new ConnectException("refused");
        })).isInstanceOf(XrdRuntimeException.class);

        verify(dspRequestProcessor, times(1)).execute(any());
    }

    @Test
    void aBodyThatCannotBeResentIsNotFailedOver() {
        acquisitionsWonBy(2, SS0);

        assertThatThrownBy(() -> failover.run(request, REQUEST_ID, true, () -> false, (_, _) -> {
            throw new ConnectException("refused");
        })).isInstanceOf(XrdRuntimeException.class);

        verify(dspRequestProcessor, times(1)).execute(any());
    }

    @Test
    void theBodyIsOnlyCompletedWhenEveryOtherConditionHolds() throws Exception {
        var completions = new AtomicInteger();
        acquisitionsWonBy(2, SS0, SS0);

        assertThatThrownBy(() -> failover.run(request, REQUEST_ID, true, () -> completions.incrementAndGet() > 0, (_, _) -> {
            throw new SocketTimeoutException("Read timed out");
        })).isInstanceOf(SocketTimeoutException.class);
        assertThatThrownBy(() -> failover.run(request, REQUEST_ID, false, () -> completions.incrementAndGet() > 0, (_, _) -> {
            throw new ConnectException("refused");
        })).isInstanceOf(XrdRuntimeException.class);
        assertThat(completions).hasValue(0);
    }

    @Test
    void theBodyIsCompletedOnceWhenEveryOtherConditionHolds() throws Exception {
        var completions = new AtomicInteger();
        acquisitionsWonBy(2, SS0, SS1);

        failover.run(request, REQUEST_ID, true, () -> completions.incrementAndGet() > 0, (_, access) -> {
            if (SS0.equals(access.winner().counterPartyAddress())) {
                throw new ConnectException("refused");
            }
            return access;
        });
        assertThat(completions).hasValue(1);
    }

    /**
     * Makes each acquisition return the next winner. Like the real processor, every acquisition reports one candidate
     * fewer than the one before, since the previous winner is excluded. Further acquisitions fail the test.
     */
    private void acquisitionsWonBy(int candidateCount, String first, String... more) {
        var addresses = new ArrayList<String>();
        addresses.add(first);
        addresses.addAll(List.of(more));
        var acquisitions = new ArrayList<DspAcquisition>();
        for (var i = 0; i < addresses.size(); i++) {
            acquisitions.add(acquisition(addresses.get(i), candidateCount - i));
        }
        var rest = acquisitions.subList(1, acquisitions.size()).toArray(DspAcquisition[]::new);
        when(dspRequestProcessor.execute(any())).thenReturn(acquisitions.getFirst(), rest)
                .thenThrow(new AssertionError("more acquisitions than the test expects"));
    }

    private static DspAcquisition acquisition(String address, int candidateCount) {
        var winner = new AcquisitionCandidate(winnerId(address), address);
        return new DspAcquisition(new AssetAccessResponse("https://data.example.org/data", "token", winner), candidateCount);
    }

    private static String winnerId(String address) {
        return "did:web:" + address.substring("https://".length(), address.indexOf(':', "https://".length()));
    }

    private List<DspRequest> acquiredRequests() {
        var captor = ArgumentCaptor.forClass(DspRequest.class);
        verify(dspRequestProcessor, atLeastOnce()).execute(captor.capture());
        return captor.getAllValues();
    }
}
