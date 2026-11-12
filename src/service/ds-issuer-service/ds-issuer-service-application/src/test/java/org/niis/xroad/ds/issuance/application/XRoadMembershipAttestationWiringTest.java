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

package org.niis.xroad.ds.issuance.application;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.IdentityHubParticipantContext;
import org.eclipse.edc.issuerservice.issuance.attestation.AttestationDefinitionValidatorRegistryImpl;
import org.eclipse.edc.issuerservice.issuance.attestation.AttestationPipelineImpl;
import org.eclipse.edc.issuerservice.spi.holder.model.Holder;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationContext;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationDefinitionStore;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationDefinitionValidatorRegistry;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationSourceFactory;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationSourceFactoryRegistry;
import org.eclipse.edc.issuerservice.spi.issuance.model.AttestationDefinition;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.jwt.validation.jti.JtiValidationStore;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.ds.issuance.membership.MembershipTestSupport;
import org.niis.xroad.ds.issuance.membership.XRoadMembershipAttestationExtension;
import org.niis.xroad.ds.issuance.membership.XRoadMembershipAttestationSourceFactory;
import org.niis.xroad.edc.extension.bridge.ExtensionScanGuard;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.ds.issuance.membership.MembershipTestSupport.HOLDER_DID;
import static org.niis.xroad.ds.issuance.membership.MembershipTestSupport.ISSUER_DID;

/**
 * Exercises the production {@code holder} attestation wiring against the extension set the
 * Issuer Service actually boots with: {@link ExtensionScanGuard} applies the same
 * {@code application.yaml} exclusion the runtime does before {@link XRoadMembershipAttestationExtension}
 * is constructed and initialized, so the resulting registrations reflect the production exclusion
 * rather than whatever order {@code ServiceLoader} happens to return.
 */
@ExtendWith(DependencyInjectionExtension.class)
class XRoadMembershipAttestationWiringTest {

    private static final String PARTICIPANT_CONTEXT_ID = "member-1";
    private static final String ATTESTATION_ID = "att-1";
    private static final String HOLDER_ATTESTATION_TYPE = "holder";
    private static final String EDC_HOLDER_ATTESTATION_EXTENSION =
            "org.eclipse.edc.issuerservice.issuance.attestation.holder.HolderAttestationExtension";

    @Test
    void productionExclusionLeavesXRoadsExtensionAsTheOnlyHolderRegistrant() {
        var scanned = ExtensionScanGuard.scanNonExcluded(getClass().getResource("/application.yaml"));

        assertThat(scanned)
                .filteredOn(XRoadMembershipAttestationExtension.class::isInstance)
                .hasSize(1);
        assertThat(scanned)
                .extracting(extension -> extension.getClass().getName())
                .doesNotContain(EDC_HOLDER_ATTESTATION_EXTENSION);
    }

    @Test
    void extensionRegistersXRoadsFactoryAndAcceptsTheHolderDefinition(ServiceExtensionContext context, ObjectFactory factory) {
        assertThat(ExtensionScanGuard.scanNonExcluded(getClass().getResource("/application.yaml")))
                .anyMatch(XRoadMembershipAttestationExtension.class::isInstance);

        var sourceRegistry = spy(new AttestationPipelineImpl(mock(AttestationDefinitionStore.class)));
        var validatorRegistry = new AttestationDefinitionValidatorRegistryImpl();
        registerCollaborators(context, sourceRegistry, validatorRegistry, mock(GlobalConfProvider.class),
                mock(TokenValidationService.class), mock(IdentityHubParticipantContextService.class));

        var extension = factory.constructInstance(XRoadMembershipAttestationExtension.class);
        extension.initialize(context);

        var registeredFactory = ArgumentCaptor.forClass(AttestationSourceFactory.class);
        verify(sourceRegistry).registerFactory(eq(HOLDER_ATTESTATION_TYPE), registeredFactory.capture());
        assertThat(registeredFactory.getValue()).isInstanceOf(XRoadMembershipAttestationSourceFactory.class);

        assertThat(validatorRegistry.validateDefinition(holderDefinition()).succeeded()).isTrue();
    }

    @Test
    void validSignedClaimWithinTheLifetimeYieldsMemberClaimsThroughThePipeline(ServiceExtensionContext context,
                                                                               ObjectFactory factory) throws Exception {
        var memberCert = TestCertUtil.getConsumer().certChain[0];
        var memberKey = TestCertUtil.getConsumer().key;
        var caCert = TestCertUtil.getCaCert();
        var caKey = TestCertUtil.getCa().key;
        var jws = signedClaimWithPinnedOcsp(memberCert, memberKey, caCert, caKey);

        var definitionStore = mock(AttestationDefinitionStore.class);
        when(definitionStore.resolveDefinition(PARTICIPANT_CONTEXT_ID, ATTESTATION_ID)).thenReturn(holderDefinition());
        var sourceRegistry = new AttestationPipelineImpl(definitionStore);
        var validatorRegistry = new AttestationDefinitionValidatorRegistryImpl();

        var globalConf = mock(GlobalConfProvider.class);
        when(globalConf.getInstanceIdentifier()).thenReturn("DEV");
        when(globalConf.getCaCert(eq("DEV"), any())).thenReturn(caCert);
        when(globalConf.getSubjectName(any(), any())).thenReturn(ClientId.Conf.create("DEV", "COM", "SS0"));
        when(globalConf.getOcspFreshnessSeconds()).thenReturn(3600);

        var tokenValidationService = mock(TokenValidationService.class);
        when(tokenValidationService.validate(anyString(), any(), any(List.class)))
                .thenReturn(Result.success(ClaimToken.Builder.newInstance().build()));

        var participantContextService = mock(IdentityHubParticipantContextService.class);
        var issuerParticipantContext = IdentityHubParticipantContext.Builder.newInstance()
                .id(PARTICIPANT_CONTEXT_ID)
                .did(ISSUER_DID)
                .apiTokenAlias("token-alias")
                .build();
        when(participantContextService.getParticipantContext(PARTICIPANT_CONTEXT_ID))
                .thenReturn(ServiceResult.success(issuerParticipantContext));

        registerCollaborators(context, sourceRegistry, validatorRegistry, globalConf, tokenValidationService,
                participantContextService);
        context.registerService(Clock.class, Clock.fixed(TestCertUtil.getDefaultValidCertDate().toInstant(), ZoneOffset.UTC));

        var extension = factory.constructInstance(XRoadMembershipAttestationExtension.class);
        extension.initialize(context);

        var result = sourceRegistry.evaluate(Set.of(ATTESTATION_ID), holderAttestationContext(jws));

        assertThat(result.succeeded()).withFailMessage(result::getFailureDetail).isTrue();
        assertThat(result.getContent())
                .containsEntry("membershipType", "X-Road")
                .containsEntry("xroadInstance", "DEV")
                .containsEntry("memberClass", "COM")
                .containsEntry("memberCode", "SS0");
    }

    private static void registerCollaborators(ServiceExtensionContext context,
                                               AttestationSourceFactoryRegistry sourceRegistry,
                                               AttestationDefinitionValidatorRegistry validatorRegistry,
                                               GlobalConfProvider globalConf,
                                               TokenValidationService tokenValidationService,
                                               IdentityHubParticipantContextService participantContextService) {
        context.registerService(AttestationSourceFactoryRegistry.class, sourceRegistry);
        context.registerService(AttestationDefinitionValidatorRegistry.class, validatorRegistry);
        context.registerService(GlobalConfProvider.class, globalConf);
        context.registerService(TokenValidationService.class, tokenValidationService);
        context.registerService(JtiValidationStore.class, mock(JtiValidationStore.class));
        context.registerService(IdentityHubParticipantContextService.class, participantContextService);
        context.registerService(Clock.class, Clock.systemUTC());
    }

    private static String signedClaimWithPinnedOcsp(X509Certificate memberCert, PrivateKey memberKey,
                                                     X509Certificate caCert, PrivateKey caKey) throws Exception {
        var ocspResponse = OcspTestUtils.createOCSPResponse(memberCert, caCert, caCert, caKey, CertificateStatus.GOOD);
        var pinnedOcsp = Base64.getEncoder().encodeToString(ocspResponse.getEncoded());
        return MembershipTestSupport.signMemberClaimWithOcsp(memberCert, memberKey, HOLDER_DID, ISSUER_DID, pinnedOcsp);
    }

    private static AttestationDefinition holderDefinition() {
        return AttestationDefinition.Builder.newInstance()
                .id(ATTESTATION_ID)
                .participantContextId(PARTICIPANT_CONTEXT_ID)
                .attestationType(HOLDER_ATTESTATION_TYPE)
                .configuration(Map.of())
                .build();
    }

    private static AttestationContext holderAttestationContext(String memberClaimJws) {
        var holder = Holder.Builder.newInstance()
                .holderId("holder-1")
                .participantContextId(PARTICIPANT_CONTEXT_ID)
                .did(HOLDER_DID)
                .build();
        var outerToken = ClaimToken.Builder.newInstance()
                .claim("sub", HOLDER_DID)
                .claim("xroadMemberClaim", memberClaimJws)
                .build();
        return new AttestationContext() {
            @Override
            @Nullable
            public ClaimToken getClaimToken(String type) {
                return "jwt".equals(type) ? outerToken : null;
            }

            @Override
            public String participantContextId() {
                return PARTICIPANT_CONTEXT_ID;
            }

            @Override
            public Holder holder() {
                return holder;
            }
        };
    }
}
