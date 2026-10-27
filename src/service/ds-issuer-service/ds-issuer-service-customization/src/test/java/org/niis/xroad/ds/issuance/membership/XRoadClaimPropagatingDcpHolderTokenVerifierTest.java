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
package org.niis.xroad.ds.issuance.membership;

import org.eclipse.edc.identityhub.protocols.dcp.spi.DcpHolderTokenVerifier;
import org.eclipse.edc.identityhub.protocols.dcp.spi.model.DcpRequestContext;
import org.eclipse.edc.identityhub.spi.participantcontext.model.IdentityHubParticipantContext;
import org.eclipse.edc.issuerservice.spi.holder.model.Holder;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class XRoadClaimPropagatingDcpHolderTokenVerifierTest {

    private static final String ACCESS_TOKEN = "holder-access-token";

    private final DcpHolderTokenVerifier delegate = mock();
    private final Monitor monitor = mock();
    private final IdentityHubParticipantContext issuerContext = mock();

    private final XRoadClaimPropagatingDcpHolderTokenVerifier verifier =
            new XRoadClaimPropagatingDcpHolderTokenVerifier(delegate, monitor);

    @Test
    void verifyKeepsTheHoldersAccessToken() {
        var token = tokenWithPayload("{\"sub\":\"did:web:holder\"}");
        var holder = holder();
        when(delegate.verify(issuerContext, token)).thenReturn(ServiceResult.success(
                new DcpRequestContext(holder, Map.of(), ACCESS_TOKEN)));

        var result = verifier.verify(issuerContext, token);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent().accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.getContent().holder()).isSameAs(holder);
    }

    @Test
    void verifyPropagatesTheJwtClaimsUnderTheJwtKey() {
        var token = tokenWithPayload("{\"sub\":\"did:web:holder\"}");
        var holder = holder();
        when(delegate.verify(issuerContext, token)).thenReturn(ServiceResult.success(
                new DcpRequestContext(holder, Map.of(), ACCESS_TOKEN)));

        var result = verifier.verify(issuerContext, token);

        assertThat(result.getContent().claims()).containsOnlyKeys(XRoadClaimPropagatingDcpHolderTokenVerifier.JWT_CLAIM_TYPE);
        assertThat(result.getContent().claims().get(XRoadClaimPropagatingDcpHolderTokenVerifier.JWT_CLAIM_TYPE).getClaims())
                .containsEntry("sub", "did:web:holder");
    }

    @Test
    void verifyReturnsTheDelegateFailureUnchanged() {
        var token = tokenWithPayload("{\"sub\":\"did:web:holder\"}");
        when(delegate.verify(issuerContext, token)).thenReturn(ServiceResult.unauthorized("rejected"));
        when(issuerContext.getParticipantContextId()).thenReturn("issuer-context");

        var result = verifier.verify(issuerContext, token);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).isEqualTo("rejected");
    }

    @Test
    void verifyRejectsPayloadThatIsNotJsonWithBadRequest() {
        var token = tokenWithPayload("{not json");
        when(delegate.verify(issuerContext, token)).thenReturn(ServiceResult.success(
                new DcpRequestContext(holder(), Map.of(), ACCESS_TOKEN)));

        var result = verifier.verify(issuerContext, token);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).isEqualTo("Failed to decode JWT payload");
    }

    private static Holder holder() {
        return Holder.Builder.newInstance().participantContextId("issuer-context").holderId("holder-id").did("did:web:holder").build();
    }

    private static TokenRepresentation tokenWithPayload(String payloadJson) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var jwt = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8))
                + ".signature";
        return TokenRepresentation.Builder.newInstance().token(jwt).build();
    }
}
