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
package org.niis.xroad.signer.core.tokenmanager.token;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import iaik.pkcs.pkcs11.Mechanism;
import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.objects.PrivateKey;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.core.config.SignerHwTokenAddonProperties;

import java.io.Closeable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_UNSUPPORTED_SIGN_ALGORITHM;
import static org.niis.xroad.signer.core.util.ExceptionHelper.loginFailed;

@Slf4j
public class HardwareTokenSigner implements Closeable {
    private final SignPrivateKeyProvider privateKeyProvider;

    // maps signature algorithm id and signing mechanism
    private final Map<SignAlgorithm, Mechanism> signMechanisms;
    private final boolean pinVerificationPerSigning;

    private final Supplier<SessionProvider> sessionSupplier;

    public static HardwareTokenSigner create(SignPrivateKeyProvider privateKeyProvider,
                                             TokenDefinition tokenDefinition,
                                             Token token, String tokenId, SignerHwTokenAddonProperties properties) {
        Supplier<SessionProvider> session;
        if (properties.poolEnabled()) {
            final var sessionPool = new HardwareTokenSessionPool(properties, token, tokenId);
            session = () -> {
                try {
                    return sessionPool;
                } catch (Exception e) {
                    throw new CodedException(X_INTERNAL_ERROR, "Could not generate public key");
                }
            };

            log.info("HSM sign session pool created for token '{}'", tokenId);
        } else {
            session = privateKeyProvider::getManagementSessionProvider;
            log.info("HSM sign session pool is disabled for token '{}'. Management session will be used.", tokenId);
        }
        return new HardwareTokenSigner(privateKeyProvider, tokenDefinition, session);
    }

    HardwareTokenSigner(SignPrivateKeyProvider privateKeyProvider,
                        TokenDefinition tokenDefinition, Supplier<SessionProvider> sessionSupplier) {
        var tempSignMechanisms = new HashMap<SignAlgorithm, Mechanism>();


        Arrays.stream(KeyAlgorithm.values())
                .map(tokenDefinition::resolveSignMechanismName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(HardwareTokenUtil::createSignMechanisms)
                .forEach(tempSignMechanisms::putAll);

        this.privateKeyProvider = privateKeyProvider;
        this.signMechanisms = Map.copyOf(tempSignMechanisms);
        this.pinVerificationPerSigning = tokenDefinition.pinVerificationPerSigning();
        this.sessionSupplier = sessionSupplier;
    }

    protected byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] data) {
        log.trace("sign({}, {})", keyId, signatureAlgorithmId);

        var sessionProvider = sessionSupplier.get();
        if (sessionProvider == null) {
            throw XrdRuntimeException.systemInternalError("Session provider is null");
        }
        return sessionProvider.executeWithSession(session -> {
            try {
                return doSign(session, keyId, signatureAlgorithmId, data);
            } catch (TokenException e) {
                throw XrdRuntimeException.systemException(ErrorCode.CANNOT_SIGN)
                        .cause(e)
                        .details("Signing failed: " + e.getMessage())
                        .build();
            }
        });
    }

    private byte[] doSign(ManagedPKCS11Session session, String keyId, SignAlgorithm signatureAlgorithm, byte[] data) throws TokenException {
        pinVerificationPerSigningLogin(session);

        var rawSession = session.get();

        PrivateKey key = privateKeyProvider.getPrivateKey(session, keyId);
        if (key == null) {
            throw CodedException.tr(X_KEY_NOT_FOUND, "key_not_found_on_token", "Key '%s' not found on token '%s'",
                    keyId, session.getTokenId());
        }

        log.debug("Signing with key '{}' and signature algorithm '{}'", keyId, signatureAlgorithm);
        try {
            var signMechanism = verifyAndReturnSignMechanism(signatureAlgorithm, KeyAlgorithm.valueOf(key.getKeyType().toString()));

            rawSession.signInit(signMechanism, key);
            return rawSession.sign(data);
        } finally {
            pinVerificationPerSigningLogout(session);
        }
    }

    private void pinVerificationPerSigningLogin(ManagedPKCS11Session session) {
        if (pinVerificationPerSigning) {
            try {
                session.login();
            } catch (Exception e) {
                log.warn("Login failed", e);

                throw loginFailed(e.getMessage());
            }
        }
    }

    private void pinVerificationPerSigningLogout(ManagedPKCS11Session session) {
        if (pinVerificationPerSigning) {
            try {
                session.logout();
            } catch (Exception e) {
                log.error("Logout failed", e);
            }
        }
    }

    private Mechanism verifyAndReturnSignMechanism(SignAlgorithm signatureAlgorithmId, KeyAlgorithm algorithm) {
        Mechanism signMechanism = signMechanisms.get(signatureAlgorithmId);

        if (signMechanism == null) {
            throw CodedException.tr(X_UNSUPPORTED_SIGN_ALGORITHM, "unsupported_sign_algorithm",
                    "Unsupported signature algorithm '%s'", signatureAlgorithmId);
        }

        if (!algorithm.equals(signatureAlgorithmId.algorithm())) {
            throw CodedException.tr(X_UNSUPPORTED_SIGN_ALGORITHM, "unsupported_sign_algorithm",
                    "Unsupported signature algorithm '%s' for key algorithm '%s'", signatureAlgorithmId.name(), algorithm);
        }

        return signMechanism;
    }

    @Override
    public void close() {
        var sessionProvider = sessionSupplier.get();

        if (sessionProvider instanceof HardwareTokenSessionPool sessionPool) {
            sessionPool.close();
        }
    }

    public interface SignPrivateKeyProvider {
        PrivateKey getPrivateKey(ManagedPKCS11Session session, String keyId) throws TokenException;

        SessionProvider getManagementSessionProvider();
    }
}
