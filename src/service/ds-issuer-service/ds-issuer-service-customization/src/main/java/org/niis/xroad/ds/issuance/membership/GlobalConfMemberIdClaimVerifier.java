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

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.identifier.ClientId;
import org.eclipse.edc.jwt.validation.jti.JtiValidationStore;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.token.rules.AudienceValidationRule;
import org.eclipse.edc.token.rules.ExpirationIssuedAtValidationRule;
import org.eclipse.edc.token.spi.TokenValidationRule;
import org.eclipse.edc.verifiablecredentials.jwt.rules.JtiValidationRule;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Production {@link MemberIdClaimVerifier} that anchors trust in X-Road global conf.
 *
 * <p>Validation flow:
 * <ol>
 *   <li>Parse the compact JWS (must be a signed JWT — rejects {@code alg=none}).</li>
 *   <li>Extract {@code x5c[0]} → X.509 cert.</li>
 *   <li>{@link CertChainValidator} PKIX path validation against globalconf trust anchors.</li>
 *   <li><strong>OCSP</strong> — the pinned OCSP response from the {@code ocsp} JWS header is
 *       verified via {@link OcspVerifier}; a missing or invalid response is rejected.</li>
 *   <li>JWS signature verification by feeding the cert's public key to EDC's
 *       {@link TokenValidationService} together with the registered rules
 *       (audience, sub-equals, JTI replay, iat/exp window). Nimbus does the signature
 *       check inside the validation service; the rules check claim semantics.</li>
 *   <li>Extract verified {@link ClientId} via
 *       {@link GlobalConfProvider#getSubjectName(SignCertificateProfileInfo.Parameters,
 *       X509Certificate)} — single source of truth for the member identity.</li>
 * </ol>
 *
 * <p>Cold-start: if {@code globalConf.verifyValidity()} throws (async load not yet
 * complete after a fresh boot, or confclient unreachable), the verifier returns
 * {@link MembershipVerificationReason#GLOBALCONF_UNAVAILABLE}. CRM retries naturally.
 */
class GlobalConfMemberIdClaimVerifier implements MemberIdClaimVerifier {

    /** Custom JWS header carrying the base64-encoded OCSP response (DER). Matches holder side. */
    static final String OCSP_HEADER = "ocsp";

    private final GlobalConfProvider globalConf;
    private final CertChainValidator certChainValidator;
    private final OcspVerifier ocspVerifier;
    private final TokenValidationService tokenValidationService;
    private final JtiValidationStore jtiValidationStore;
    private final MemberClaimVerifierProperties properties;
    private final Monitor monitor;
    private final Clock clock;

    GlobalConfMemberIdClaimVerifier(GlobalConfProvider globalConf,
                                    CertChainValidator certChainValidator,
                                    OcspVerifier ocspVerifier,
                                    TokenValidationService tokenValidationService,
                                    JtiValidationStore jtiValidationStore,
                                    MemberClaimVerifierProperties properties,
                                    Monitor monitor,
                                    Clock clock) {
        this.globalConf = globalConf;
        this.certChainValidator = certChainValidator;
        this.ocspVerifier = ocspVerifier;
        this.tokenValidationService = tokenValidationService;
        this.jtiValidationStore = jtiValidationStore;
        this.properties = properties;
        this.monitor = monitor;
        this.clock = clock;
    }

    @Override
    public Result<ClientId> verify(String compactJws, String expectedHolderDid, String expectedIssuerDid) {
        if (compactJws == null || compactJws.isBlank()) {
            return Result.failure(MembershipVerificationReason.CLAIM_MISSING.name());
        }
        if (!globalConfReady()) {
            return Result.failure(MembershipVerificationReason.GLOBALCONF_UNAVAILABLE.name());
        }

        SignedJWT signedJwt;
        X509Certificate cert;
        try {
            var parsed = JWTParser.parse(compactJws);
            if (!(parsed instanceof SignedJWT s)) {
                return Result.failure(MembershipVerificationReason.CLAIM_MALFORMED.name() + ": JWS not a signed JWT");
            }
            signedJwt = s;
            cert = extractLeafCertificate(signedJwt);
            if (cert == null) {
                return Result.failure(MembershipVerificationReason.CLAIM_MALFORMED.name() + ": missing x5c header");
            }
        } catch (ParseException | CertificateException e) {
            return Result.failure(MembershipVerificationReason.CLAIM_MALFORMED.name() + ": " + e.getMessage());
        }

        Result<Void> chainResult = certChainValidator.validate(cert);
        if (chainResult.failed()) {
            return chainResult.mapFailure();
        }

        Object pinnedOcsp = signedJwt.getHeader().getCustomParam(OCSP_HEADER);
        Result<Void> ocspResult = ocspVerifier.verify(pinnedOcsp == null ? null : pinnedOcsp.toString(), cert);
        if (ocspResult.failed()) {
            return ocspResult.mapFailure();
        }

        Result<ClaimToken> validation = tokenValidationService.validate(
                compactJws,
                keyId -> Result.success(cert.getPublicKey()),
                rules(expectedHolderDid, expectedIssuerDid));
        if (validation.failed()) {
            return Result.failure(mapValidationFailure(validation.getFailureDetail()));
        }

        return extractSubjectIdentity(cert);
    }

    private List<TokenValidationRule> rules(String expectedHolderDid, String expectedIssuerDid) {
        return List.of(
                new AudienceValidationRule(expectedIssuerDid),
                new SubjectEqualsRule(expectedHolderDid),
                new JtiValidationRule(jtiValidationStore, monitor),
                new ExpirationIssuedAtValidationRule(clock, (int) properties.leewaySeconds(), false));
    }

    private boolean globalConfReady() {
        try {
            globalConf.verifyValidity();
            return true;
        } catch (Exception e) {
            monitor.warning("X-Road claim verifier: globalconf not ready: " + e.getMessage());
            return false;
        }
    }

    private static X509Certificate extractLeafCertificate(JWSObject jws) throws CertificateException {
        var chain = jws.getHeader().getX509CertChain();
        if (chain == null || chain.isEmpty()) {
            return null;
        }
        var certBytes = chain.get(0).decode();
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(certBytes));
    }

    private Result<ClientId> extractSubjectIdentity(X509Certificate cert) {
        try {
            var profileParams = new SignCertificateProfileInfoParameters(
                    ClientId.Conf.create(globalConf.getInstanceIdentifier(), "PLACEHOLDER", "PLACEHOLDER"), "");
            ClientId.Conf subjectId = globalConf.getSubjectName(profileParams, cert);
            if (subjectId == null) {
                return Result.failure(MembershipVerificationReason.CERT_CHAIN_INVALID.name()
                        + ": cert subject is not a valid X-Road ClientId");
            }
            return Result.success(subjectId);
        } catch (Exception e) {
            return Result.failure(MembershipVerificationReason.CERT_CHAIN_INVALID.name() + ": " + e.getMessage());
        }
    }

    private static String mapValidationFailure(String detail) {
        // Token-validation failures collapse into bucketed reasons; the detail message
        // distinguishes the specific cause in logs.
        if (detail == null) {
            return MembershipVerificationReason.SIGNATURE_INVALID.name();
        }
        var lower = detail.toLowerCase(Locale.ROOT);
        if (lower.contains("jwt id") || lower.contains("jti") || lower.contains("already used")) {
            return MembershipVerificationReason.CLAIM_REPLAYED.name() + ": " + detail;
        }
        if (lower.contains("aud") || lower.contains("audience") || lower.contains("sub ")) {
            return MembershipVerificationReason.CLAIM_AUDIENCE_INVALID.name() + ": " + detail;
        }
        if (lower.contains("expired") || lower.contains("expiration") || lower.contains("issued at") || lower.contains("iat")) {
            return MembershipVerificationReason.CLAIM_EXPIRED.name() + ": " + detail;
        }
        return MembershipVerificationReason.SIGNATURE_INVALID.name() + ": " + detail;
    }

    /**
     * Sub-equals validation rule (mirrors EDC's {@code IssuerEqualsValidationRule} for the
     * {@code sub} claim instead of {@code iss}).
     */
    private record SubjectEqualsRule(String expectedSubject) implements TokenValidationRule {
        @Override
        public Result<Void> checkRule(@NotNull ClaimToken toVerify, @Nullable Map<String, Object> additional) {
            var sub = toVerify.getStringClaim("sub");
            return Objects.equals(sub, expectedSubject)
                    ? Result.success()
                    : Result.failure("'sub' claim does not match expected, expected '%s', got '%s'"
                            .formatted(expectedSubject, sub));
        }
    }
}
