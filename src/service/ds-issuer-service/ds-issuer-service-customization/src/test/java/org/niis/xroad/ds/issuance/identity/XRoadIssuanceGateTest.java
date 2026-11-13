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
package org.niis.xroad.ds.issuance.identity;

import org.eclipse.edc.identityhub.protocols.dcp.issuer.spi.DcpIssuerService;
import org.eclipse.edc.identityhub.protocols.dcp.spi.model.CredentialRequestMessage;
import org.eclipse.edc.identityhub.protocols.dcp.spi.model.DcpRequestContext;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XRoadIssuanceGateTest {

    private static final Duration CHECK_INTERVAL = Duration.ofMillis(20);

    private final Monitor monitor = mock();
    private final DcpIssuerService delegate = mock();
    private final CredentialRequestMessage message = mock();
    private final DcpRequestContext context = mock();

    @Test
    void refusesTheRequestWhileUnboundAndNeverCallsTheDelegate() {
        var holder = new BoundIssuerIdentityHolder(() -> Optional::empty, monitor, CHECK_INTERVAL);
        var gate = new XRoadIssuanceGate(delegate, holder);

        var result = gate.initiateCredentialsIssuance("participant-1", message, context);

        assertThat(result.failed()).isTrue();
        verify(delegate, never()).initiateCredentialsIssuance(any(), any(), any());
    }

    @Test
    void letsTheRequestThroughAfterTheFirstBoundLoad() {
        var bound = identity("DID-GATE");
        var holder = new BoundIssuerIdentityHolder(() -> () -> Optional.of(bound), monitor, CHECK_INTERVAL);
        var gate = new XRoadIssuanceGate(delegate, holder);
        holder.start();
        var expected = ServiceResult.success(new CredentialRequestMessage.Response("process-1", false));
        when(delegate.initiateCredentialsIssuance("participant-1", message, context)).thenReturn(expected);

        var result = gate.initiateCredentialsIssuance("participant-1", message, context);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void theIdentityUsedStaysTheBoundOneAfterAFurtherPoll() {
        var bound = identity("DID-SEQUENCE");
        var holder = new BoundIssuerIdentityHolder(() -> () -> Optional.of(bound), monitor, CHECK_INTERVAL);
        var gate = new XRoadIssuanceGate(delegate, holder);
        holder.start();
        when(delegate.initiateCredentialsIssuance(any(), any(), any()))
                .thenReturn(ServiceResult.success(new CredentialRequestMessage.Response("process-1", false)));

        gate.initiateCredentialsIssuance("participant-1", message, context);
        var second = gate.initiateCredentialsIssuance("participant-1", message, context);

        assertThat(second.succeeded()).isTrue();
    }

    private static BoundIssuerIdentity identity(String did) {
        return new BoundIssuerIdentity(did, "issuer.example", 7183, "https://issuer.example/issuance", "https://issuer.example/statuslist");
    }
}
