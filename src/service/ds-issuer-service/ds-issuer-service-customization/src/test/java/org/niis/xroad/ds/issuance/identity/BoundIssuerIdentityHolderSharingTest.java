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
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.ds.issuance.identity.StatusListCredentialFixture.statusListCredential;

/**
 * Checks that the {@code Hostname} provider, the statuslist publisher and the issuance gate all observe the one
 * {@link BoundIssuerIdentityHolder} that {@link XRoadIssuerHostnameExtension} owns, rather than each building
 * its own: a binding the extension applies is seen, at the same time, by every one of them.
 */
class BoundIssuerIdentityHolderSharingTest {

    private static final Duration CHECK_INTERVAL = Duration.ofMillis(20);

    private final Monitor monitor = mock();

    @Test
    void theHostnameProviderThePublisherAndTheGateUseTheSameHolderInstance() {
        var bound = new BoundIssuerIdentity("DID-SHARED", "issuer.example", 7183,
                "https://issuer.example/issuance", "https://issuer.example/statuslist");
        var holder = new BoundIssuerIdentityHolder(() -> () -> Optional.of(bound), monitor, CHECK_INTERVAL, CHECK_INTERVAL);
        var hostnameExtension = new XRoadIssuerHostnameExtension(holder);

        assertThat(hostnameExtension.boundIssuerIdentityHolder()).isSameAs(holder);

        var sharedHolder = hostnameExtension.boundIssuerIdentityHolder();
        var publisherExtension = new XRoadStatusListCredentialPublisherExtension(sharedHolder);
        var delegate = mock(DcpIssuerService.class);
        var gateExtension = new XRoadIssuanceGateExtension(delegate, sharedHolder);
        var context = mock(ServiceExtensionContext.class);

        hostnameExtension.start();
        try {
            assertThat(hostnameExtension.hostname().get()).isEqualTo("issuer.example");

            var publisher = publisherExtension.statusListCredentialPublisher();
            assertThat(publisher.publish(statusListCredential("credential-1")).succeeded()).isTrue();
            assertThat(publisher.publicUrl("credential-1")).isEqualTo("https://issuer.example/statuslist/credential-1");

            gateExtension.initialize(context);
            var gate = capturedGate(context);
            when(delegate.initiateCredentialsIssuance(any(), any(), any()))
                    .thenReturn(ServiceResult.success(new CredentialRequestMessage.Response("process-1", false)));

            var result = gate.initiateCredentialsIssuance(
                    "participant-1", mock(CredentialRequestMessage.class), mock(DcpRequestContext.class));

            assertThat(result.succeeded()).isTrue();
        } finally {
            hostnameExtension.shutdown();
        }
    }

    private static DcpIssuerService capturedGate(ServiceExtensionContext context) {
        var captor = forClass(DcpIssuerService.class);
        verify(context).registerService(eq(DcpIssuerService.class), captor.capture());
        return captor.getValue();
    }
}
