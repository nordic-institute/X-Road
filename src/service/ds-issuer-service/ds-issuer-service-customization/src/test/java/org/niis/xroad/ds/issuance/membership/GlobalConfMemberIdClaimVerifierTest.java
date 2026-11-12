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

import ee.ria.xroad.common.identifier.ClientId;

import org.eclipse.edc.jwt.validation.jti.JtiValidationStore;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.token.TokenValidationServiceImpl;
import org.eclipse.edc.token.spi.TokenValidationRule;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.ds.issuance.membership.MembershipTestSupport.HOLDER_DID;
import static org.niis.xroad.ds.issuance.membership.MembershipTestSupport.ISSUER_DID;

class GlobalConfMemberIdClaimVerifierTest {

    private GlobalConfProvider globalConf;
    private CertChainValidator certChainValidator;
    private OcspVerifier ocspVerifier;
    private TokenValidationService tokenValidationService;
    private JtiValidationStore jtiStore;
    private MemberClaimVerifierProperties properties;
    private GlobalConfMemberIdClaimVerifier verifier;
    private Clock clock;

    @BeforeEach
    void setUp() {
        globalConf = mock(GlobalConfProvider.class);
        certChainValidator = mock(CertChainValidator.class);
        ocspVerifier = mock(OcspVerifier.class);
        tokenValidationService = mock(TokenValidationService.class);
        jtiStore = mock(JtiValidationStore.class);
        properties = new MemberClaimVerifierProperties(300L, 30L);
        clock = Clock.fixed(Instant.now().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);

        lenient().when(ocspVerifier.verify(any(), any())).thenReturn(Result.success());

        verifier = new GlobalConfMemberIdClaimVerifier(globalConf,
                certChainValidator,
                ocspVerifier,
                tokenValidationService,
                jtiStore,
                properties,
                mock(Monitor.class),
                clock);
    }

    @Test
    void returnsClaimMissingForNullOrBlankJws() {
        Result<ClientId> r1 = verifier.verify(null, HOLDER_DID, ISSUER_DID);
        Result<ClientId> r2 = verifier.verify("  ", HOLDER_DID, ISSUER_DID);

        assertEquals(MembershipVerificationFailureReason.CLAIM_MISSING.name(), r1.getFailureDetail());
        assertEquals(MembershipVerificationFailureReason.CLAIM_MISSING.name(), r2.getFailureDetail());
    }

    @Test
    void returnsGlobalconfUnavailableWhenGlobalconfNotReady() throws Exception {
        MembershipTestSupport.TestKeyAndCert keyAndCert = MembershipTestSupport.TestKeyAndCert.generate();
        String jws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, false);
        doThrowOnVerifyValidity();

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertEquals(MembershipVerificationFailureReason.GLOBALCONF_UNAVAILABLE.name(), result.getFailureDetail());
    }

    @Test
    void returnsClaimMalformedForUnparseableJws() {
        Result<ClientId> result = verifier.verify("not-a-jws", HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationFailureReason.CLAIM_MALFORMED.name()));
    }

    @Test
    void returnsClaimMalformedWhenX5cHeaderMissing() throws Exception {
        MembershipTestSupport.TestKeyAndCert keyAndCert = MembershipTestSupport.TestKeyAndCert.generate();
        String jws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, false);

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationFailureReason.CLAIM_MALFORMED.name()));
    }

    @Test
    void returnsCertChainInvalidWhenCertchainValidatorFails() throws Exception {
        MembershipTestSupport.TestKeyAndCert keyAndCert = MembershipTestSupport.TestKeyAndCert.generate();
        String jws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any()))
                .thenReturn(Result.failure(MembershipVerificationFailureReason.CERT_CHAIN_INVALID.name() + ": untrusted"));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationFailureReason.CERT_CHAIN_INVALID.name()));
    }

    @Test
    void returnsOcspInvalidWhenOcspVerifierFails() throws Exception {
        MembershipTestSupport.TestKeyAndCert keyAndCert = MembershipTestSupport.TestKeyAndCert.generate();
        String jws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        when(ocspVerifier.verify(any(), any()))
                .thenReturn(Result.failure(MembershipVerificationFailureReason.OCSP_INVALID.name() + ": stale"));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.failed());
        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationFailureReason.OCSP_INVALID.name()));
    }

    @Test
    void returnsSignatureInvalidWhenTokenValidationFailsGeneric() throws Exception {
        MembershipTestSupport.TestKeyAndCert keyAndCert = MembershipTestSupport.TestKeyAndCert.generate();
        String jws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        when(tokenValidationService.validate(anyString(), any(), any(List.class)))
                .thenReturn(Result.failure("signature mismatch"));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationFailureReason.SIGNATURE_INVALID.name()));
    }

    @Test
    void returnsClaimReplayedWhenTokenValidationReportsJtiReuse() throws Exception {
        MembershipTestSupport.TestKeyAndCert keyAndCert = MembershipTestSupport.TestKeyAndCert.generate();
        String jws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        when(tokenValidationService.validate(anyString(), any(), any(List.class)))
                .thenReturn(Result.failure("The JWT id 'xyz' was already used."));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationFailureReason.CLAIM_REPLAYED.name()));
    }

    @Test
    void returnsClaimExpiredWhenTokenValidationReportsExpired() throws Exception {
        MembershipTestSupport.TestKeyAndCert keyAndCert = MembershipTestSupport.TestKeyAndCert.generate();
        String jws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        when(tokenValidationService.validate(anyString(), any(), any(List.class)))
                .thenReturn(Result.failure("Token has expired (exp)"));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationFailureReason.CLAIM_EXPIRED.name()));
    }

    @Test
    void returnsClaimAudienceInvalidWhenTokenValidationReportsAudMismatch() throws Exception {
        MembershipTestSupport.TestKeyAndCert keyAndCert = MembershipTestSupport.TestKeyAndCert.generate();
        String jws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        when(tokenValidationService.validate(anyString(), any(), any(List.class)))
                .thenReturn(Result.failure("'aud' claim does not match expected audience"));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationFailureReason.CLAIM_AUDIENCE_INVALID.name()));
    }

    @Test
    void returnsClaimExpiredWhenTokenValidationReportsMissingIat() throws Exception {
        MembershipTestSupport.TestKeyAndCert keyAndCert = MembershipTestSupport.TestKeyAndCert.generate();
        String jws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        Result<Void> ageRuleFailure = new MemberClaimAgeRule(clock, properties.lifetimeSeconds(), properties.leewaySeconds())
                .checkRule(ClaimToken.Builder.newInstance().build(), null);
        when(tokenValidationService.validate(anyString(), any(), any(List.class)))
                .thenReturn(Result.failure(ageRuleFailure.getFailureMessages()));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationFailureReason.CLAIM_EXPIRED.name()));
    }

    @Test
    void returnsClaimExpiredWhenTokenValidationReportsClaimTooOld() throws Exception {
        MembershipTestSupport.TestKeyAndCert keyAndCert = MembershipTestSupport.TestKeyAndCert.generate();
        String jws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        var tooOldClaim = ClaimToken.Builder.newInstance()
                .claim("iat", clock.instant().minusSeconds(properties.lifetimeSeconds() + properties.leewaySeconds() + 1)
                        .getEpochSecond())
                .build();
        Result<Void> ageRuleFailure = new MemberClaimAgeRule(clock, properties.lifetimeSeconds(), properties.leewaySeconds())
                .checkRule(tooOldClaim, null);
        when(tokenValidationService.validate(anyString(), any(), any(List.class)))
                .thenReturn(Result.failure(ageRuleFailure.getFailureMessages()));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationFailureReason.CLAIM_EXPIRED.name()));
    }

    @Test
    void rulesIncludeAgeRuleFedFromConfiguredLifetimeAndLeeway() {
        List<TokenValidationRule> rules = verifier.rules(HOLDER_DID, ISSUER_DID);
        TokenValidationRule ageRule = rules.stream()
                .filter(MemberClaimAgeRule.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("MemberClaimAgeRule not found in rules()"));

        var atTheLifetimePlusLeewayBoundary = ClaimToken.Builder.newInstance()
                .claim("iat", clock.instant().minusSeconds(properties.lifetimeSeconds() + properties.leewaySeconds())
                        .getEpochSecond())
                .build();
        var pastTheLifetimePlusLeewayBoundary = ClaimToken.Builder.newInstance()
                .claim("iat", clock.instant().minusSeconds(properties.lifetimeSeconds() + properties.leewaySeconds() + 1)
                        .getEpochSecond())
                .build();

        assertTrue(ageRule.checkRule(atTheLifetimePlusLeewayBoundary, null).succeeded());
        assertTrue(ageRule.checkRule(pastTheLifetimePlusLeewayBoundary, null).failed());
    }

    @Test
    void realRulesPipelineAcceptsFreshClaimAndRejectsOneOlderThanLifetimePlusLeeway() throws Exception {
        MemberClaimVerifierProperties shortProperties = new MemberClaimVerifierProperties(100L, 10L);
        MembershipTestSupport.TestKeyAndCert keyAndCert = MembershipTestSupport.TestKeyAndCert.generate();
        JtiValidationStore realJtiStore = mock(JtiValidationStore.class);
        when(realJtiStore.storeEntry(any())).thenReturn(StoreResult.success());
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        when(globalConf.getInstanceIdentifier()).thenReturn("DEV");
        when(globalConf.getSubjectName(any(), any())).thenReturn(ClientId.Conf.create("DEV", "COM", "SS0"));
        GlobalConfMemberIdClaimVerifier realRulesVerifier = new GlobalConfMemberIdClaimVerifier(globalConf,
                certChainValidator,
                ocspVerifier,
                new TokenValidationServiceImpl(),
                realJtiStore,
                shortProperties,
                mock(Monitor.class),
                clock);

        String freshJws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, true, clock.instant());
        Result<ClientId> freshResult = realRulesVerifier.verify(freshJws, HOLDER_DID, ISSUER_DID);
        assertTrue(freshResult.succeeded(), freshResult.getFailureDetail());

        Instant tooOldIssuedAt = clock.instant()
                .minusSeconds(shortProperties.lifetimeSeconds() + shortProperties.leewaySeconds() + 1);
        String tooOldJws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, true, tooOldIssuedAt);
        Result<ClientId> tooOldResult = realRulesVerifier.verify(tooOldJws, HOLDER_DID, ISSUER_DID);
        assertTrue(tooOldResult.failed());
        assertTrue(tooOldResult.getFailureDetail().startsWith(MembershipVerificationFailureReason.CLAIM_EXPIRED.name()));
    }

    @Test
    void returnsSuccessWithCertSubjectClientidOnHappyPath() throws Exception {
        MembershipTestSupport.TestKeyAndCert keyAndCert = MembershipTestSupport.TestKeyAndCert.generate();
        String jws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        when(tokenValidationService.validate(anyString(), any(), any(List.class)))
                .thenReturn(Result.success(ClaimToken.Builder.newInstance().build()));
        when(globalConf.getInstanceIdentifier()).thenReturn("DEV");
        ClientId.Conf expected = ClientId.Conf.create("DEV", "COM", "SS0");
        when(globalConf.getSubjectName(any(), any())).thenReturn(expected);

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.succeeded(), result.getFailureDetail());
        assertEquals(expected, result.getContent());
    }

    @Test
    void passesCertPublicKeyToTokenValidationService() throws Exception {
        MembershipTestSupport.TestKeyAndCert keyAndCert = MembershipTestSupport.TestKeyAndCert.generate();
        String jws = MembershipTestSupport.signMemberClaim(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        ArgumentCaptor<org.eclipse.edc.keys.spi.PublicKeyResolver> resolverCaptor =
                ArgumentCaptor.forClass(org.eclipse.edc.keys.spi.PublicKeyResolver.class);
        when(tokenValidationService.validate(anyString(), resolverCaptor.capture(), any(List.class)))
                .thenReturn(Result.success(ClaimToken.Builder.newInstance().build()));
        when(globalConf.getInstanceIdentifier()).thenReturn("DEV");
        when(globalConf.getSubjectName(any(), any())).thenReturn(ClientId.Conf.create("DEV", "COM", "SS0"));

        verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        PublicKey resolved = resolverCaptor.getValue().resolveKey("any-kid").getContent();
        assertEquals(keyAndCert.cert().getPublicKey(), resolved);
    }

    private void doThrowOnVerifyValidity() {
        doThrowOn(globalConf);
    }

    private static void doThrowOn(GlobalConfProvider mock) {
        org.mockito.Mockito.doThrow(new RuntimeException("not loaded")).when(mock).verifyValidity();
    }

    @SuppressWarnings("unused")
    private static List<Object> emptyAdditional() {
        return Collections.emptyList();
    }
}
