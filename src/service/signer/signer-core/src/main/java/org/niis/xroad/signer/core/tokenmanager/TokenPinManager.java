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
package org.niis.xroad.signer.core.tokenmanager;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.CryptoUtils;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.core.model.BasicKeyInfo;
import org.niis.xroad.signer.core.model.RuntimeToken;
import org.niis.xroad.signer.core.service.TokenWriteService;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwarePinHasher;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.niis.xroad.common.core.exception.ErrorCode.PIN_INCORRECT;
import static org.niis.xroad.common.core.exception.ErrorCode.WRONG_CERT_USAGE;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public final class TokenPinManager {
    private final TokenRegistry tokenRegistry;
    private final TokenWriteService tokenWriteService;
    private final SoftwarePinHasher softwarePinHasher;

    public void setTokenPin(String tokenId, char[] pin) {
        log.trace("setTokenPin({}, pin)", tokenId);

        var pinHash = softwarePinHasher.hashPin(pin);
        tokenRegistry.writeRun(ctx -> {
            try {
                var token = ctx.findToken(tokenId);
                tokenWriteService.setInitialTokenPin(token.id(), pinHash);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw XrdRuntimeException.systemInternalError("Failed to set PIN for token " + tokenId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }

    public void updateTokenPin(String tokenId, char[] oldPin, char[] newPin) {
        tokenRegistry.writeRun(ctx -> {
            try {
                var token = ctx.findToken(tokenId);
                validateTokenPin(token, tokenId);

                var newPinHash = softwarePinHasher.hashPin(newPin);
                var updatedKeys = updateKeyStores(token, oldPin, newPin);

                tokenWriteService.updateTokenPin(token.id(), updatedKeys, newPinHash);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw XrdRuntimeException.systemException(WRONG_CERT_USAGE)
                        .details("Failed to update PIN for token " + tokenId + ": ")
                        .cause(e)
                        .build();
            } finally {
                ctx.invalidateCache();
            }
        });
    }

    private void validateTokenPin(RuntimeToken token, String tokenId) {
        if (token.softwareTokenPinHash().isEmpty()) {
            throw XrdRuntimeException.systemException(PIN_INCORRECT)
                    .details("PIN not set for token " + tokenId)
                    .build();
        }
    }

    private Map<Long, byte[]> updateKeyStores(RuntimeToken token, char[] oldPin, char[] newPin) {
        Map<Long, byte[]> updatedKeys = new HashMap<>();

        for (var key : token.keys()) {
            key.softwareKeyStore().ifPresentOrElse(
                    keystore -> updateKeyStore(keystore, key, oldPin, newPin, updatedKeys),
                    () -> log.debug("Key {} does not have a keystore, skipping PIN update", key.id())
            );
        }

        return updatedKeys;
    }

    private void updateKeyStore(byte[] keystore, BasicKeyInfo key, char[] oldPin, char[] newPin,
                                Map<Long, byte[]> updatedKeys) {
        try {
            var oldKeyStore = CryptoUtils.loadPkcs12KeyStore(new ByteArrayInputStream(keystore), oldPin);
            var newKeyStore = SoftwareTokenUtil.rewriteKeyStoreWithNewPin(oldKeyStore, key.externalId(), oldPin, newPin);

            try (var outputStream = new ByteArrayOutputStream()) {
                newKeyStore.store(outputStream, newPin);
                updatedKeys.put(key.id(), outputStream.toByteArray());
            }
        } catch (CodedException signerException) {
            throw signerException;
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(WRONG_CERT_USAGE)
                    .details("Failed to update key store for key " + key.id() + ": " + e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    public boolean verifyTokenPin(String tokenId, char[] pin) {
        var pinHash = softwarePinHasher.hashPin(pin);
        var actualHash = tokenRegistry.readAction(ctx ->
                ctx.findToken(tokenId).softwareTokenPinHash().orElse(null));

        var result = Arrays.equals(pinHash, actualHash);

        log.debug("verifyTokenPin({}, pin) pin found in db? {}, result = {}", tokenId, actualHash != null, result);
        return result;
    }

    public boolean tokenHasPin(String tokenId) {
        return tokenRegistry.readAction(ctx ->
                        ctx.findToken(tokenId).softwareTokenPinHash())
                .map(array -> array.length > 0)
                .orElse(false);
    }
}
