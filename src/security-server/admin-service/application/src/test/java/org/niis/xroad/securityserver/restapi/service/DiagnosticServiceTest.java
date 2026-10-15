/*
 * The MIT License
 *
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

import ee.ria.xroad.common.HttpStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.auxiliaryservice.proto.AuxiliaryServiceRpcClient;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.proxy.proto.ProxyRpcClient;
import org.niis.xroad.signer.client.SignerRpcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.OPERATIONAL_DATA_INTERVALS_OUT_OF_RANGE;

@ExtendWith(MockitoExtension.class)
class DiagnosticServiceTest {
    private static final long FROM = 1_700_000_040_000L;

    @Mock
    ConfClientRpcClient confClientRpcClient;
    @Mock
    SignerRpcClient signerRpcClient;
    @Mock
    ProxyRpcClient proxyRpcClient;
    @Mock
    AuxiliaryServiceRpcClient auxiliaryServiceRpcClient;
    @Mock
    OpMonitorClient opMonitorClient;

    private DiagnosticService diagnosticService;

    @BeforeEach
    void setUp() {
        diagnosticService = new DiagnosticService(confClientRpcClient, signerRpcClient, proxyRpcClient,
                auxiliaryServiceRpcClient, opMonitorClient);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0",
            "35792, 0, 0",
            "1, 1, 0",
            "1, 0, 5999940000",
            "1, 30000, 5999940000",
            "1440, 0, 8640000000000"
    })
    void outOfRangeRequestIsRejectedWithoutCallingOpMonitor(int interval, long fromOffset, long toOffset) {
        assertThatThrownBy(() -> diagnosticService.getOperationalDataIntervals(
                FROM + fromOffset, FROM + toOffset, interval, null, null, null))
                .isInstanceOfSatisfying(BadRequestException.class, e ->
                        assertThat(e.getErrorDeviation().code()).isEqualTo(OPERATIONAL_DATA_INTERVALS_OUT_OF_RANGE.code()));

        verifyNoInteractions(opMonitorClient);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 0, 0",
            "35791, 0, 0",
            "1, 0, 5999880000",
            "1, 30000, 5999939999"
    })
    void requestInRangeIsPassedToOpMonitor(int interval, long fromOffset, long toOffset) {
        var from = FROM + fromOffset;
        var to = FROM + toOffset;
        when(opMonitorClient.getOperationalDataIntervals(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        var result = diagnosticService.getOperationalDataIntervals(from, to, interval, null, null, null);

        assertThat(result).isEmpty();
        verify(opMonitorClient).getOperationalDataIntervals(from, to, interval, null, null, null);
    }

    @Test
    void rejectionIsAnHttpBadRequest() {
        assertThatThrownBy(() -> diagnosticService.getOperationalDataIntervals(FROM, FROM, 0, null, null, null))
                .isInstanceOfSatisfying(BadRequestException.class, e ->
                        assertThat(e.getHttpStatus()).contains(HttpStatus.BAD_REQUEST));
    }
}
