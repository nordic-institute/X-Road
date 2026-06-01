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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import ee.ria.xroad.common.identifier.ClientId;
import org.eclipse.edc.jwt.validation.jti.JtiValidationStore;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalConfMemberIdClaimVerifierTest {

    private static final String HOLDER_DID = "did:web:holder";
    private static final String ISSUER_DID = "did:web:issuer";

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
        clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);

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
    void returns_CLAIM_MISSING_for_null_or_blank_jws() {
        Result<ClientId> r1 = verifier.verify(null, HOLDER_DID, ISSUER_DID);
        Result<ClientId> r2 = verifier.verify("  ", HOLDER_DID, ISSUER_DID);

        assertEquals(MembershipVerificationReason.CLAIM_MISSING.name(), r1.getFailureDetail());
        assertEquals(MembershipVerificationReason.CLAIM_MISSING.name(), r2.getFailureDetail());
    }

    @Test
    void returns_GLOBALCONF_UNAVAILABLE_when_globalconf_not_ready() throws Exception {
        TestKeyAndCert keyAndCert = TestKeyAndCert.generate();
        String jws = signJwt(keyAndCert, HOLDER_DID, ISSUER_DID, false);
        doThrowOnVerifyValidity();

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertEquals(MembershipVerificationReason.GLOBALCONF_UNAVAILABLE.name(), result.getFailureDetail());
    }

    @Test
    void returns_CLAIM_MALFORMED_for_unparseable_jws() {
        Result<ClientId> result = verifier.verify("not-a-jws", HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationReason.CLAIM_MALFORMED.name()));
    }

    @Test
    void returns_CLAIM_MALFORMED_when_x5c_header_missing() throws Exception {
        TestKeyAndCert keyAndCert = TestKeyAndCert.generate();
        String jws = signJwt(keyAndCert, HOLDER_DID, ISSUER_DID, false);

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationReason.CLAIM_MALFORMED.name()));
    }

    @Test
    void returns_CERT_CHAIN_INVALID_when_certchain_validator_fails() throws Exception {
        TestKeyAndCert keyAndCert = TestKeyAndCert.generate();
        String jws = signJwt(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any()))
                .thenReturn(Result.failure(MembershipVerificationReason.CERT_CHAIN_INVALID.name() + ": untrusted"));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationReason.CERT_CHAIN_INVALID.name()));
    }

    @Test
    void returns_OCSP_INVALID_when_ocsp_verifier_fails() throws Exception {
        TestKeyAndCert keyAndCert = TestKeyAndCert.generate();
        String jws = signJwt(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        when(ocspVerifier.verify(any(), any()))
                .thenReturn(Result.failure(MembershipVerificationReason.OCSP_INVALID.name() + ": stale"));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.failed());
        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationReason.OCSP_INVALID.name()));
    }

    @Test
    void returns_SIGNATURE_INVALID_when_token_validation_fails_generic() throws Exception {
        TestKeyAndCert keyAndCert = TestKeyAndCert.generate();
        String jws = signJwt(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        when(tokenValidationService.validate(anyString(), any(), any(List.class)))
                .thenReturn(Result.failure("signature mismatch"));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationReason.SIGNATURE_INVALID.name()));
    }

    @Test
    void returns_CLAIM_REPLAYED_when_token_validation_reports_jti_reuse() throws Exception {
        TestKeyAndCert keyAndCert = TestKeyAndCert.generate();
        String jws = signJwt(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        when(tokenValidationService.validate(anyString(), any(), any(List.class)))
                .thenReturn(Result.failure("The JWT id 'xyz' was already used."));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationReason.CLAIM_REPLAYED.name()));
    }

    @Test
    void returns_CLAIM_EXPIRED_when_token_validation_reports_expired() throws Exception {
        TestKeyAndCert keyAndCert = TestKeyAndCert.generate();
        String jws = signJwt(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        when(tokenValidationService.validate(anyString(), any(), any(List.class)))
                .thenReturn(Result.failure("Token has expired (exp)"));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationReason.CLAIM_EXPIRED.name()));
    }

    @Test
    void returns_CLAIM_AUDIENCE_INVALID_when_token_validation_reports_aud_mismatch() throws Exception {
        TestKeyAndCert keyAndCert = TestKeyAndCert.generate();
        String jws = signJwt(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        when(tokenValidationService.validate(anyString(), any(), any(List.class)))
                .thenReturn(Result.failure("'aud' claim does not match expected audience"));

        Result<ClientId> result = verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        assertTrue(result.getFailureDetail().startsWith(MembershipVerificationReason.CLAIM_AUDIENCE_INVALID.name()));
    }

    @Test
    void returns_success_with_cert_subject_clientid_on_happy_path() throws Exception {
        TestKeyAndCert keyAndCert = TestKeyAndCert.generate();
        String jws = signJwt(keyAndCert, HOLDER_DID, ISSUER_DID, true);
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
    void passes_cert_public_key_to_token_validation_service() throws Exception {
        TestKeyAndCert keyAndCert = TestKeyAndCert.generate();
        String jws = signJwt(keyAndCert, HOLDER_DID, ISSUER_DID, true);
        when(certChainValidator.validate(any())).thenReturn(Result.success());
        ArgumentCaptor<org.eclipse.edc.keys.spi.PublicKeyResolver> resolverCaptor =
                ArgumentCaptor.forClass(org.eclipse.edc.keys.spi.PublicKeyResolver.class);
        when(tokenValidationService.validate(anyString(), resolverCaptor.capture(), any(List.class)))
                .thenReturn(Result.success(ClaimToken.Builder.newInstance().build()));
        when(globalConf.getInstanceIdentifier()).thenReturn("DEV");
        when(globalConf.getSubjectName(any(), any())).thenReturn(ClientId.Conf.create("DEV", "COM", "SS0"));

        verifier.verify(jws, HOLDER_DID, ISSUER_DID);

        PublicKey resolved = resolverCaptor.getValue().resolveKey("any-kid").getContent();
        assertEquals(keyAndCert.cert.getPublicKey(), resolved);
    }

    private void doThrowOnVerifyValidity() {
        doThrowOn(globalConf);
    }

    private static void doThrowOn(GlobalConfProvider mock) {
        org.mockito.Mockito.doThrow(new RuntimeException("not loaded")).when(mock).verifyValidity();
    }

    private static String signJwt(TestKeyAndCert keyAndCert, String holderDid, String issuerDid, boolean includeCert) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(holderDid)
                .subject(holderDid)
                .audience(issuerDid)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256);
        if (includeCert) {
            headerBuilder.x509CertChain(List.of(Base64.encode(keyAndCert.cert.getEncoded())));
        }
        SignedJWT jwt = new SignedJWT(headerBuilder.build(), claims);
        JWSSigner signer = new RSASSASigner(keyAndCert.privateKey);
        jwt.sign(signer);
        return jwt.serialize();
    }

    private record TestKeyAndCert(PrivateKey privateKey, X509Certificate cert) {
        static TestKeyAndCert generate() throws Exception {
            var kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            var kp = kpg.generateKeyPair();
            X509Certificate cert = SelfSignedCertGen.generate(kp.getPublic(), kp.getPrivate());
            return new TestKeyAndCert(kp.getPrivate(), cert);
        }
    }

    /**
     * Minimal self-signed cert generator using BouncyCastle's BC provider, which is already
     * on the classpath via globalconf-impl.
     */
    private static final class SelfSignedCertGen {
        static X509Certificate generate(PublicKey publicKey, PrivateKey privateKey) throws Exception {
            var name = new org.bouncycastle.asn1.x500.X500Name("CN=Test, O=NIIS, C=EE");
            var serial = BigInteger.valueOf(System.currentTimeMillis());
            var notBefore = new Date(System.currentTimeMillis() - 60_000);
            var notAfter = new Date(System.currentTimeMillis() + 3_600_000L);
            var builder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                    name, serial, notBefore, notAfter, name, publicKey);
            var signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSA").build(privateKey);
            var holder = builder.build(signer);
            return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(holder);
        }
    }

    @SuppressWarnings("unused")
    private static List<Object> emptyAdditional() {
        return Collections.emptyList();
    }
}
