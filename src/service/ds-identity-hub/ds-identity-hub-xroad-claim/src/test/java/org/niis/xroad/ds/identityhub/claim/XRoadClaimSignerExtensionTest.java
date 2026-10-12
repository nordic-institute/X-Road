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

import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.iam.decentralizedclaims.sts.spi.model.StsAccount;
import org.eclipse.edc.iam.decentralizedclaims.sts.spi.model.StsAccountTokenAdditionalParams;
import org.eclipse.edc.identityhub.spi.authentication.ParticipantSecureTokenService;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.IdentityHubParticipantContext;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class XRoadClaimSignerExtensionTest {

    private static final String CONTEXT_ID = "test-part-ctx";
    private static final String HOLDER_DID = "did:web:holder";
    private static final String AUDIENCE = "did:web:verifier";
    private static final String SCOPE = "scope";

    private final ParticipantSecureTokenService delegate = mock();
    private final IdentityHubParticipantContextService participantContextService = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(ParticipantSecureTokenService.class, delegate);
        context.registerService(IdentityHubParticipantContextService.class, participantContextService);
        context.registerService(SignerRpcClient.class, mock(SignerRpcClient.class));
        context.registerService(SignerSignClient.class, mock(SignerSignClient.class));
        context.registerService(Clock.class, Clock.systemUTC());
        when(delegate.createToken(anyString(), anyMap(), any()))
                .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token("jwt").build()));
        var participantContext = IdentityHubParticipantContext.Builder.newInstance()
                .participantContextId(CONTEXT_ID)
                .did(HOLDER_DID)
                .apiTokenAlias("alias")
                .property(XRoadClaimAwareSecureTokenService.MEMBER_ID_PROPERTY, "DEV/COM/SS0")
                .build();
        when(participantContextService.getParticipantContext(CONTEXT_ID))
                .thenReturn(ServiceResult.success(participantContext));
    }

    @Test
    void dspTokenFromClientTokenServiceCarriesNoMemberClaim(ServiceExtensionContext context, ObjectFactory factory) {
        try (MockedConstruction<SignerServiceMemberClaimSigner> signers = signerReturningClaim()) {
            var extension = factory.constructInstance(XRoadClaimSignerExtension.class);
            extension.initialize(context);

            var result = extension.clientTokenService().tokenFor(stsAccount(), additionalParams());

            assertThat(result.succeeded()).isTrue();
            assertThat(delegateClaims()).doesNotContainKey(XRoadClaimAwareSecureTokenService.CLAIM_KEY);
            verify(signers.constructed().getFirst(), never()).sign(any(), any(), any());
        }
    }

    @Test
    void credentialRequestTokenFromRegisteredServiceCarriesMemberClaim(ServiceExtensionContext context,
                                                                       ObjectFactory factory) {
        try (MockedConstruction<SignerServiceMemberClaimSigner> ignored = signerReturningClaim()) {
            var extension = factory.constructInstance(XRoadClaimSignerExtension.class);
            extension.initialize(context);

            var registered = context.getService(ParticipantSecureTokenService.class);
            registered.createToken(CONTEXT_ID, Map.of("aud", AUDIENCE), null);

            assertThat(registered).isInstanceOf(XRoadClaimAwareSecureTokenService.class);
            assertThat(delegateClaims()).containsEntry(XRoadClaimAwareSecureTokenService.CLAIM_KEY, "signed-jws");
        }
    }

    @Test
    void clientTokenExpirationDefaultsToFiveMinutes(ServiceExtensionContext context, ObjectFactory factory) {
        var extension = factory.constructInstance(XRoadClaimSignerExtension.class);

        var result = extension.clientTokenService().tokenFor(stsAccount(), additionalParams());

        assertThat(result.getContent().getExpiresIn()).isEqualTo(300L);
    }

    @Test
    void clientTokenExpirationFollowsConfiguredMinutes(ServiceExtensionContext context, ObjectFactory factory) {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of("edc.iam.sts.token.expiration", "7")));
        var extension = factory.constructInstance(XRoadClaimSignerExtension.class);

        var result = extension.clientTokenService().tokenFor(stsAccount(), additionalParams());

        assertThat(result.getContent().getExpiresIn()).isEqualTo(420L);
    }

    private Map<String, String> delegateClaims() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> claims = ArgumentCaptor.forClass(Map.class);
        verify(delegate).createToken(eq(CONTEXT_ID), claims.capture(), any());
        return claims.getValue();
    }

    private static MockedConstruction<SignerServiceMemberClaimSigner> signerReturningClaim() {
        return mockConstruction(SignerServiceMemberClaimSigner.class,
                (signer, mockContext) -> when(signer.sign(any(), anyString(), any()))
                        .thenReturn(Result.success("signed-jws")));
    }

    private static StsAccount stsAccount() {
        return StsAccount.Builder.newInstance()
                .id("account")
                .clientId("client")
                .name("name")
                .did(HOLDER_DID)
                .secretAlias("secret")
                .participantContextId(CONTEXT_ID)
                .build();
    }

    private static StsAccountTokenAdditionalParams additionalParams() {
        return StsAccountTokenAdditionalParams.Builder.newInstance()
                .audience(AUDIENCE)
                .bearerAccessScope(SCOPE)
                .build();
    }
}
