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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Assembles a compact-serialised JWS by combining a Nimbus {@link JWSHeader} + payload
 * with a signature computed externally (by the X-Road signer service over gRPC).
 *
 * <p>Why not use a Nimbus {@code JWSSigner} subclass: the signer service expects a
 * pre-hashed {@code digest} (e.g. SHA-256 of the JWS signing input) and returns the raw
 * RSA/ECDSA signature bytes. Nimbus's signer abstraction wants to take the JWS signing
 * input bytes and produce signature bytes — fine — but the digest-then-sign split is
 * cleaner expressed as a {@link BiFunction} that just calls signer-client.
 *
 * <p>Supports the RSA family ({@code RS256}/{@code RS384}/{@code RS512}). ECDSA
 * and EdDSA support require DER↔R||S signature transcoding and are deferred until a
 * deployment actually needs them — X-Road's typical sign cert profile uses RSA.
 */
final class JwsBuilder {

    /** JWS header name carrying the base64-encoded OCSP response (DER) pinned at sign time. */
    static final String OCSP_HEADER = "ocsp";

    private JwsBuilder() {
    }

    /**
     * Builds a compact JWS.
     *
     * @param claims    the JWT payload claims
     * @param cert      the signing certificate (embedded as {@code x5c[0]})
     * @param signAlgo  X-Road algorithm identifier (drives Nimbus alg + digest)
     * @param keyId     opaque signer-side key identifier to pass to {@code signFn}
     * @param signFn    function taking {@code (keyId, digestBytes)} and returning the raw
     *                  signature bytes (as the X-Road signer service does over gRPC)
     * @param ocspDer   DER bytes of the pinned OCSP response, or {@code null} to omit
     *                  the {@code ocsp} header (the issuer-side verifier rejects JWS
     *                  without it once OCSP enforcement is enabled)
     * @return the compact JWS string ({@code header.payload.signature})
     * @throws JwsBuildException if the algorithm is unsupported or assembly fails
     */
    static String build(JWTClaimsSet claims,
                        X509Certificate cert,
                        SignAlgorithm signAlgo,
                        String keyId,
                        BiFunction<String, byte[], byte[]> signFn,
                        byte[] ocspDer) {
        JWSAlgorithm jwsAlg = toJwsAlgorithm(signAlgo);
        Base64 certBase64;
        try {
            certBase64 = Base64.encode(cert.getEncoded());
        } catch (Exception e) {
            throw new JwsBuildException("cannot encode signing certificate", e);
        }
        JWSHeader.Builder headerBuilder = new JWSHeader.Builder(jwsAlg)
                .x509CertChain(List.of(certBase64));
        if (ocspDer != null && ocspDer.length > 0) {
            headerBuilder.customParam(OCSP_HEADER, Base64.encode(ocspDer).toString());
        }
        JWSHeader header = headerBuilder.build();

        SignedJWT jwt = new SignedJWT(header, claims);
        // Signing input bytes per JWS compact: ASCII(base64url(header) + "." + base64url(payload)).
        byte[] signingInput = jwt.getSigningInput();

        byte[] digest = digest(signAlgo, signingInput);
        byte[] signatureBytes = signFn.apply(keyId, digest);
        if (signatureBytes == null) {
            throw new JwsBuildException("signer returned null signature");
        }

        return jwt.getHeader().toBase64URL().toString()
                + "." + jwt.getPayload().toBase64URL().toString()
                + "." + Base64URL.encode(signatureBytes);
    }

    private static byte[] digest(SignAlgorithm signAlgo, byte[] input) {
        DigestAlgorithm digestAlgorithm = signAlgo.digest();
        if (digestAlgorithm == null) {
            // EdDSA path — no separate hash. Currently unsupported (would need Nimbus EdDSA alg).
            throw new JwsBuildException("Sign algorithm '" + signAlgo.name() + "' has no associated digest; currently unsupported");
        }
        try {
            MessageDigest md = MessageDigest.getInstance(digestAlgorithm.name());
            return md.digest(input);
        } catch (Exception e) {
            throw new JwsBuildException("cannot compute digest with algorithm " + digestAlgorithm.name(), e);
        }
    }

    private static JWSAlgorithm toJwsAlgorithm(SignAlgorithm signAlgo) {
        if (signAlgo.signMechanism() == SignMechanism.CKM_RSA_PKCS) {
            return switch (signAlgo.digest().name()) {
                case "SHA-256" -> JWSAlgorithm.RS256;
                case "SHA-384" -> JWSAlgorithm.RS384;
                case "SHA-512" -> JWSAlgorithm.RS512;
                default -> throw new JwsBuildException(
                        "Unsupported RSA-PKCS digest '" + signAlgo.digest().name() + "' for JWS");
            };
        }
        throw new JwsBuildException("Sign mechanism '" + signAlgo.signMechanism() + "' is not supported by the X-Road claim signer "
                + "(only CKM_RSA_PKCS is supported; ECDSA/PSS/EdDSA require DER↔R||S transcoding).");
    }

    /** ASCII-encode helper kept for completeness; not currently used. */
    @SuppressWarnings("unused")
    private static byte[] asciiBytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    static final class JwsBuildException extends RuntimeException {
        JwsBuildException(String message) {
            super(message);
        }

        JwsBuildException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
