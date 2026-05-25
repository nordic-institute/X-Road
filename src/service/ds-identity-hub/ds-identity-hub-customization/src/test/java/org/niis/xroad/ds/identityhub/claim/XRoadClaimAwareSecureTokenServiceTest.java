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

package org.niis.xroad.ds.identityhub.claim;

import ee.ria.xroad.common.identifier.ClientId;
import org.eclipse.edc.identityhub.spi.authentication.ParticipantSecureTokenService;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.IdentityHubParticipantContext;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XRoadClaimAwareSecureTokenServiceTest {

    private static final String CONTEXT_ID = "test-part-ctx";
    private static final String HOLDER_DID = "did:web:holder";
    private static final String ISSUER_DID = "did:web:issuer";

    private ParticipantSecureTokenService delegate;
    private IdentityHubParticipantContextService participantContextService;
    private MemberClaimSigner signer;
    private XRoadClaimAwareSecureTokenService wrapper;

    @BeforeEach
    void setUp() {
        delegate = mock(ParticipantSecureTokenService.class);
        participantContextService = mock(IdentityHubParticipantContextService.class);
        signer = mock(MemberClaimSigner.class);
        Monitor monitor = mock(Monitor.class);
        wrapper = new XRoadClaimAwareSecureTokenService(delegate, participantContextService, signer, monitor);

        when(delegate.createToken(anyString(), anyMap(), any()))
                .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token("outer-jwt").build()));
    }

    @Test
    void parses_three_part_member_id_and_calls_signer_with_audience_from_claims() {
        stubParticipantContextWithMemberId("DEV/COM/SS0");
        when(signer.sign(any(), anyString(), anyString())).thenReturn(Result.success("signed-jws"));
        Map<String, String> incoming = new HashMap<>();
        incoming.put("aud", ISSUER_DID);

        wrapper.createToken(CONTEXT_ID, incoming, null);

        ArgumentCaptor<ClientId> idCaptor = ArgumentCaptor.forClass(ClientId.class);
        ArgumentCaptor<String> didCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> audCaptor = ArgumentCaptor.forClass(String.class);
        verify(signer).sign(idCaptor.capture(), didCaptor.capture(), audCaptor.capture());
        assertEquals("DEV", idCaptor.getValue().getXRoadInstance());
        assertEquals("COM", idCaptor.getValue().getMemberClass());
        assertEquals("SS0", idCaptor.getValue().getMemberCode());
        assertEquals(HOLDER_DID, didCaptor.getValue());
        assertEquals(ISSUER_DID, audCaptor.getValue());
    }

    @Test
    void embeds_signed_jws_under_xroadMemberClaim_in_outer_claims() {
        stubParticipantContextWithMemberId("DEV/COM/SS0");
        when(signer.sign(any(), anyString(), any())).thenReturn(Result.success("signed-jws"));

        wrapper.createToken(CONTEXT_ID, new HashMap<>(Map.of("aud", ISSUER_DID)), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(delegate).createToken(eq(CONTEXT_ID), claimsCaptor.capture(), any());
        assertEquals("signed-jws", claimsCaptor.getValue().get("xroadMemberClaim"));
    }

    @Test
    void skips_signing_when_member_id_property_absent() {
        stubParticipantContextWithoutMemberId();

        wrapper.createToken(CONTEXT_ID, new HashMap<>(Map.of("aud", ISSUER_DID)), null);

        verify(signer, never()).sign(any(), anyString(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(delegate).createToken(eq(CONTEXT_ID), claimsCaptor.capture(), any());
        assertFalse(claimsCaptor.getValue().containsKey("xroadMemberClaim"));
    }

    @Test
    void skips_signing_when_member_id_property_is_malformed() {
        stubParticipantContextWithMemberId("DEV/COM");

        wrapper.createToken(CONTEXT_ID, new HashMap<>(Map.of("aud", ISSUER_DID)), null);

        verify(signer, never()).sign(any(), anyString(), any());
    }

    @Test
    void skips_signing_when_member_id_segment_is_blank() {
        stubParticipantContextWithMemberId("DEV//SS0");

        wrapper.createToken(CONTEXT_ID, new HashMap<>(Map.of("aud", ISSUER_DID)), null);

        verify(signer, never()).sign(any(), anyString(), any());
    }

    private void stubParticipantContextWithMemberId(String memberId) {
        IdentityHubParticipantContext ctx = IdentityHubParticipantContext.Builder.newInstance()
                .participantContextId(CONTEXT_ID)
                .did(HOLDER_DID)
                .apiTokenAlias("alias")
                .property(XRoadClaimAwareSecureTokenService.MEMBER_ID_PROPERTY, memberId)
                .build();
        when(participantContextService.getParticipantContext(CONTEXT_ID))
                .thenReturn(ServiceResult.success(ctx));
    }

    private void stubParticipantContextWithoutMemberId() {
        IdentityHubParticipantContext ctx = IdentityHubParticipantContext.Builder.newInstance()
                .participantContextId(CONTEXT_ID)
                .did(HOLDER_DID)
                .apiTokenAlias("alias")
                .build();
        when(participantContextService.getParticipantContext(CONTEXT_ID))
                .thenReturn(ServiceResult.success(ctx));
    }
}
