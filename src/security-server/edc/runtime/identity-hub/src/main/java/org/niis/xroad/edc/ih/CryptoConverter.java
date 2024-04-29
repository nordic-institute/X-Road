/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.niis.xroad.edc.ih;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Requirement;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import lombok.experimental.UtilityClass;
import org.eclipse.edc.spi.EdcException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.EdECKey;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.EdECPoint;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

/**
 * Converter class that converts Java cryuptographic primitives (e.g. {@link PrivateKey}) into their Nimbus-counterparts needed to handle
 * Json Web Tokens.
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html">Defined Algorithm Standard Names</a>
 */
@SuppressWarnings("checkstyle:MagicNumber")
@UtilityClass
public class CryptoConverter {

    public static final String ALGORITHM_RSA = "RSA";
    public static final String ALGORITHM_EC = "EC";
    public static final String ALGORITHM_ECDSA = "EdDSA";
    public static final String ALGORITHM_ED25519 = "Ed25519";
    public static final List<String> SUPPORTED_ALGORITHMS = List.of(ALGORITHM_EC, ALGORITHM_RSA, ALGORITHM_ECDSA, ALGORITHM_ED25519);


    /**
     * Takes a Java {@link PrivateKey} object and creates a corresponding Nimbus {@link JWSSigner} for convenient use with JWTs.
     * Note that currently only the following key types are supported:
     * <ul>
     *     <li>RSA</li>
     *     <li>EC: {@code key} argument is expected to be instanceof {@link ECPrivateKey}</li>
     *     <li>EdDSA/Ed25519: {@code key} argument ist expected to be {@link EdECPrivateKey}. Both the Sun provider
     *     and the {@code org.bouncycastle.jce.provider.BouncyCastleProvider}  are supported.</li>
     * </ul>
     *
     * @param key the private key.
     * @return a {@link JWSSigner}
     * @throws IllegalArgumentException if the Curve of an EdDSA key is not "Ed25519" (x25519 and Ed448 are not supported!)
     * @throws IllegalArgumentException if the key is not in the list of supported algorithms
     *                                  ({@link CryptoConverter#SUPPORTED_ALGORITHMS})
     * @throws EdcException             if the {@link PrivateKey} is a EdDSA key and does not disclose its private bytes
     */
    public static JWSSigner createSignerFor(PrivateKey key) {
        try {
            return switch (key.getAlgorithm()) {
//                case ALGORITHM_EC -> getEcdsaSigner((ECPrivateKey) key);
                case ALGORITHM_RSA -> new RSASSASigner(key);
                case ALGORITHM_ECDSA, ALGORITHM_ED25519 -> createEdDsaVerifier(key);
                default -> throw new IllegalArgumentException(notSupportedError(key.getAlgorithm()));
            };
        } catch (JOSEException ex) {
            throw new EdcException(notSupportedError(key.getAlgorithm()), ex);
        }
    }


    /**
     * Takes a Java {@link PublicKey} object and creates a corresponding Nimbus {@link JWSVerifier} for convenient use with JWTs.
     * Note that currently only the following key types are supported:
     * <ul>
     *     <li>RSA</li>
     *     <li>EC: {@code key} argument is expected to be instanceof {@link ECPrivateKey}</li>
     *     <li>EdDSA/Ed25519: {@code key} argument ist expected to be {@link EdECPrivateKey}. Both the Sun provider
     *     and the {@code org.bouncycastle.jce.provider.BouncyCastleProvider}  are supported.</li>
     * </ul>
     *
     * @param publicKey the public key.
     * @return a {@link JWSSigner}
     * @throws IllegalArgumentException if the Curve of an EdDSA key is not "Ed25519" (x25519 and Ed448 are not supported!)
     * @throws IllegalArgumentException if the key is not in the list of supported algorithms
     * ({@link CryptoConverter#SUPPORTED_ALGORITHMS})
     * @throws EdcException             if the {@link PublicKey} is a EdDSA key and does not disclose its private bytes
     */
    public static JWSVerifier createVerifierFor(PublicKey publicKey) {
        try {
            return switch (publicKey.getAlgorithm()) {
//                case ALGORITHM_EC -> getEcdsaVerifier((ECPublicKey) publicKey);
                case ALGORITHM_RSA -> new RSASSAVerifier((RSAPublicKey) publicKey);
                case ALGORITHM_ECDSA, ALGORITHM_ED25519 -> createEdDsaVerifier(publicKey);
                default -> throw new IllegalArgumentException(notSupportedError(publicKey.getAlgorithm()));
            };
        } catch (JOSEException e) {
            throw new EdcException(notSupportedError(publicKey.getAlgorithm()), e);
        }
    }

    /**
     * Converts a Java {@link KeyPair} into its JWK counterpart from Nimbus.
     * Currently, only RSA, EC and EdDSA keys are supported, specifically:
     * <ul>
     *     <li>EC: supports all named curves. If both private and public keys are specified,
     *     the conversion is straight forward. If only the public key is specified, then the
     *     resulting JWK will not contain a private component (usually "d"). If only the private key is specified,
     *     then the public key is restored using elliptic curve multiplication. This is a fairly
     *     costly operation, so it is avoided if possible.</li>
     *     <li>EdDSA: </li>
     * </ul>
     * <p>
     * Note that the "kid" parameter will be null, and the "key-use" will be set to {@link KeyUse#SIGNATURE}.
     * If needed, the "kid" parameter can be set by
     * re-generating the resulting JWK using {@link JWK#toJSONObject()} and {@link JWK#parse(Map)}.
     *
     * @param keypair Must either contain the {@link PrivateKey}, the {@link PublicKey} or both.
     *               If neither is set, an {@link IllegalArgumentException} is thrown.
     * @return A Nimbus JWK.
     */
    public static JWK createJwk(KeyPair keypair) {
        return createJwk(keypair, null);
    }

    /**
     * Converts a Java {@link KeyPair} into its JWK counterpart from Nimbus.
     * Currently, only RSA, EC and EdDSA keys are supported, specifically:
     * <ul>
     *     <li>EC: supports all named curves. If both private and public keys are specified,
     *     the conversion is straight forward. If only the public key is specified, then the
     *     resulting JWK will not contain a private component (usually "d"). If only the private key is specified,
     *     then the public key is restored using elliptic curve multiplication. This is a fairly
     *     costly operation, so it is avoided if possible.</li>
     *     <li>EdDSA: </li>
     * </ul>
     * <p>
     * Note that the "kid" parameter will be null, and the "key-use" will be set to {@link KeyUse#SIGNATURE}.
     * If needed, the "kid" parameter can be set by
     * re-generating the resulting JWK using {@link JWK#toJSONObject()} and {@link JWK#parse(Map)}.
     *
     * @param keypair Must either contain the {@link PrivateKey}, the {@link PublicKey} or both. If neither is set,
     *                an {@link IllegalArgumentException} is thrown.
     * @param kid     The key-ID that will be included in the JWK as 'kid' property. Can be null.
     * @return A Nimbus JWK.
     */
    public static JWK createJwk(KeyPair keypair, @Nullable String kid) {
        if (keypair.getPrivate() == null && keypair.getPublic() == null) {
            throw new IllegalArgumentException("Invalid KeyPair: public and private key were both null!");
        }
        var alg = ofNullable((Key) keypair.getPrivate()).orElse(keypair.getPublic()).getAlgorithm();
        return switch (alg) {
//            case ALGORITHM_EC -> convertEcKey(keypair, kid);
            case ALGORITHM_RSA -> convertRsaKey(keypair, kid);
            case ALGORITHM_ECDSA, ALGORITHM_ED25519 -> convertEdDsaKey(keypair, kid);
            default -> throw new IllegalArgumentException(notSupportedError(keypair.getPublic().getAlgorithm()));
        };
    }

    /**
     * Attempts to determine the best suitable {@link JWSAlgorithm} for any given signer. Some signers support multiple, in
     * which case the first one marked RECOMMENDED is returned. If none is marked such, the first one is returned.
     *
     * @param signer the {@link JWSSigner}
     * @return the only {@link JWSAlgorithm}, or the one marked RECOMMENDED,
     * or simply the first one. Returns null if no {@link JWSAlgorithm} can be determined.
     */
    public static JWSAlgorithm getRecommendedAlgorithm(JWSSigner signer) {
        return getWithRequirement(signer, Requirement.REQUIRED)
                .orElseGet(() -> getWithRequirement(signer, Requirement.RECOMMENDED)
                        .orElseGet(() -> getWithRequirement(signer, Requirement.OPTIONAL)
                                .orElse(null)));

    }

    /**
     * Creates a {@link JWK} out of a map that represents a JSON structure.
     *
     * @param jsonObject The map containing the JSON
     * @return the corresponding key.
     * @throws RuntimeException if the JSON was malformed, or the JWK type was unknown. Typically, this wraps a {@link ParseException}
     */
    public static JWK create(Map<String, Object> jsonObject) {
        if (jsonObject == null) return null;
        try {
            return JWK.parse(jsonObject);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a {@link JWK} out of a JSON string containing the key properties
     *
     * @param json The string containing plain JSON
     * @return the corresponding key.
     * @throws RuntimeException if the JSON was malformed, or the JWK type was unknown. Typically, this wraps a {@link ParseException}
     */
    public static JWK create(String json) {
        if (json == null) return null;
        try {
            return JWK.parse(json);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a {@link JWSVerifier} from the base class {@link JWK}. Currently only supports EC, OKP and RSA keys.
     *
     * @param jwk The {@link JWK} for which the {@link JWSVerifier} is to be created.
     * @return the {@link JWSVerifier}
     * @throws UnsupportedOperationException if the verifier could not be created,
     * in which case the root cause would be {@link JOSEException}
     */
    public static JWSVerifier createVerifier(JWK jwk) {
        Objects.requireNonNull(jwk, "jwk cannot be null");
        var value = jwk.getKeyType().getValue();
        try {
            return switch (value) {
                case "EC" -> new ECDSAVerifier((ECKey) jwk);
                case "OKP" -> new Ed25519Verifier((OctetKeyPair) jwk);
                case "RSA" -> new RSASSAVerifier((RSAKey) jwk);
                default -> throw new UnsupportedOperationException(format("Cannot create JWSVerifier for JWK-type [%s], currently only"
                        + " supporting EC, OKP and RSA", value));
            };
        } catch (JOSEException ex) {
            throw new UnsupportedOperationException(ex);
        }
    }

    /**
     * Creates a {@link JWSSigner} from the base class {@link JWK}. Currently only supports EC, OKP and RSA keys.
     *
     * @param jwk The {@link JWK} for which the {@link JWSSigner} is to be created.
     * @return the {@link JWSSigner}
     * @throws UnsupportedOperationException if the signer could not be created, in which case the root cause would be {@link JOSEException}
     */
    public static JWSSigner createSigner(JWK jwk) {
        var value = jwk.getKeyType().getValue();
        try {
            return switch (value) {
                case "EC" -> new ECDSASigner((ECKey) jwk);
                case "OKP" -> new Ed25519Signer((OctetKeyPair) jwk);
                case "RSA" -> new RSASSASigner((RSAKey) jwk);
                default -> throw new UnsupportedOperationException(format("Cannot create JWSVerifier for JWK-type [%s], currently only"
                        + " supporting EC, OKP and RSA", value));
            };
        } catch (JOSEException ex) {
            throw new UnsupportedOperationException(ex);
        }
    }

    /**
     * Obtains the {@link Curve} from an EdDSA key, throwing an {@link IllegalArgumentException} if the curve was not in the
     * list of allowed algorithms/curves
     *
     * @param edKey         either the private or public key
     * @param allowedCurves All curve names that are acceptable
     * @throws IllegalArgumentException if the key was not created on one of the accepted curves.
     */
    private static Curve getCurveAllowing(EdECKey edKey, String... allowedCurves) {
        var curveName = edKey.getParams().getName();

        if (!Arrays.asList(allowedCurves).contains(curveName)) {
            throw new IllegalArgumentException("Only the following curves is supported: %s.".formatted(String.join(",",
                    allowedCurves)));
        }
        return Curve.parse(curveName);
    }

    private static RSAKey convertRsaKey(KeyPair keypair, @Nullable String kid) {

        if (keypair.getPublic() == null && keypair.getPrivate() == null) {
            throw new IllegalArgumentException("Either the public or the private key of a keypair must be non-null when"
                    + " converting RSA -> JWK");
        }
        var key = Optional.ofNullable(keypair.getPublic()).orElseGet(() -> {
            var keySpec = new java.security.spec.RSAPublicKeySpec(((RSAPrivateCrtKey) keypair.getPrivate()).getModulus(),
                    ((RSAPrivateCrtKey) keypair.getPrivate()).getPublicExponent());
            try {
                var gen = KeyFactory.getInstance("RSA");
                return gen.generatePublic(keySpec);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new RuntimeException(e);
            }
        });
        var builder = new RSAKey.Builder((RSAPublicKey) key);
        if (keypair.getPrivate() != null) {
            builder.privateKey(keypair.getPrivate());
        }
        return builder.keyID(kid).keyUse(KeyUse.SIGNATURE).build();
    }


    /**
     * reverses an array in-place
     */
    private static byte[] reverseArray(byte[] array) {
        for (var i = 0; i < array.length / 2; i++) {
            var temp = array[i];
            array[i] = array[array.length - 1 - i];
            array[array.length - 1 - i] = temp;
        }
        return array;
    }

    private static Ed25519Verifier createEdDsaVerifier(PublicKey publicKey) throws JOSEException {
        var edKey = (EdECPublicKey) publicKey;
        var curve = getCurveAllowing(edKey, ALGORITHM_ED25519);


        var urlX = encodeX(edKey.getPoint());
        var okp = new OctetKeyPair.Builder(curve, urlX).build();
        return new Ed25519Verifier(okp);

    }


    @NotNull
    private static Optional<JWSAlgorithm> getWithRequirement(JWSSigner signer, Requirement requirement) {
        return signer.supportedJWSAlgorithms().stream().filter(alg -> alg.getRequirement() == requirement).findFirst();
    }

    private static Ed25519Signer createEdDsaVerifier(PrivateKey key) throws JOSEException {
        var edKey = (EdECPrivateKey) key;
        var curve = getCurveAllowing(edKey, ALGORITHM_ED25519);


        var urlX = Base64URL.encode(new byte[0]);
        var urlD = encodeD(edKey);

        // technically, urlX should be the public bytes (i.e. public key), but we don't have that here, and we don't need it.
        // that is because internally, the Ed25519Signer only wraps the Ed25519Sign class from the Tink library,
        //
        // using only the private bytes ("d")
        var octetKeyPair = new OctetKeyPair.Builder(curve, urlX).d(urlD).build();
        return new Ed25519Signer(octetKeyPair);
    }

    /**
     * Convert a KeyPair, that is expected to contain an EdDSA KeyPair, into the Nimbus type {@link OctetKeyPair}.
     * Further, it is assumed that
     * either the private key, or the public key, or both are supplied. This method won't check that again.
     * <ul>
     *  <li>If the private key <em>and</em> the public key are provided, the resulting JWK will contain a private component.</li>
     *  <li>If only the public key is provided, the resulting JWK only contains the public parameters.</li>
     *  <li>If only the private key is provided, the public key is restored from it. </li>
     * </ul>
     */
    private static OctetKeyPair convertEdDsaKey(KeyPair keypair, @Nullable String kid) {
        var pub = (EdECPublicKey) keypair.getPublic();
        var priv = (EdECPrivateKey) keypair.getPrivate();

        // if the public key is not present, an empty byte array is set, because as with all elliptic curves the public
        // key can be recovered from the private key. OctetKeyPairs do this for us behind the scenes.
        var urlX = ofNullable(pub).map(pubkey -> encodeX(pubkey.getPoint())).orElseGet(() -> Base64URL.encode(new byte[0]));
        var urlD = ofNullable(priv).map(CryptoConverter::encodeD).orElse(null);

        var curveName = ofNullable((EdECKey) priv).orElse(pub).getParams().getName();

        return new OctetKeyPair.Builder(Curve.parse(curveName), urlX).d(urlD).keyID(kid).build();
    }

    /**
     * Encodes the private key part of an EdDSA key as {@link Base64URL}, throws an exception if the binary representation can't be obtained
     */
    @NotNull
    private static Base64URL encodeD(EdECPrivateKey edKey) {
        var bytes = edKey.getBytes().orElseThrow(() -> new EdcException("Private key is not willing to disclose its bytes"));
        return Base64URL.encode(bytes);
    }

    /**
     * Encodes the public key part of an EdDSA key as {@link Base64URL}
     */
    @NotNull
    private static Base64URL encodeX(EdECPoint point) {
        var bytes = reverseArray(point.getY().toByteArray());

        // when the X-coordinate of the curve is odd, we flip the highest-order bit of the first (or last, since we reversed) byte
        if (point.isXOdd()) {
            var mask = (byte) 128; // is 1000 0000 binary
            bytes[bytes.length - 1] ^= mask; // XOR means toggle the left-most bit
        }

        return Base64URL.encode(bytes);
    }

    private static String notSupportedError(String algorithm) {
        return ("Could not convert PrivateKey to a JWSSigner, currently only the following types are supported: %s. "
                + "The specified key was a %s").formatted(String.join(",", SUPPORTED_ALGORITHMS), algorithm);
    }
}
