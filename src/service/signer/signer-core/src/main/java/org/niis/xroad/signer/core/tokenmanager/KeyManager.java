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
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.core.service.TokenKeyService;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class KeyManager {
    protected final TokenRegistry tokenRegistry;
    protected final TokenKeyService tokenKeyService;

    /**
     * Adds a key with id and base64 public key to a token.
     *
     * @param tokenId         the token id
     * @param keyId           the key if
     * @param publicKeyBase64 the public key base64
     */
    public void addKey(String tokenId, String keyId, String publicKeyBase64, byte[] keyStore,
                       SignMechanism signMechanism,
                       String friendlyName, String label) {
        log.trace("addKey({}, {})", tokenId, keyId);

        tokenRegistry.writeRun(ctx -> {
            try {
                var token = ctx.findToken(tokenId);
                tokenKeyService.save(token.id(), keyId, publicKeyBase64, keyStore,
                        signMechanism, friendlyName, label, true);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }

    /**
     * Adds a key with id and base64 public key to a token.
     *
     * @param tokenId         the token id
     * @param keyId           the key if
     * @param publicKeyBase64 the public key base64
     */
    public void addKey(String tokenId, String keyId, String publicKeyBase64, SignMechanism signMechanism,
                       String friendlyName, String label) {
        log.trace("addKey({}, {})", tokenId, keyId);

        tokenRegistry.writeRun(ctx -> {
            try {
                var token = ctx.findToken(tokenId);

                tokenKeyService.save(token.id(), keyId, publicKeyBase64, null, signMechanism,
                        friendlyName, label, false);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }

    /**
     * Sets the key availability.
     *
     * @param keyId     the key id
     * @param available true if available
     */
    public void setKeyAvailable(String keyId,
                                boolean available) {
        log.trace("setKeyAvailable({}, {})", keyId, available);

        tokenRegistry.writeRun(ctx ->
                ctx.findKey(keyId).setAvailable(available));
    }

    /**
     * Sets the key friendly name.
     *
     * @param keyId        the key id
     * @param friendlyName the friendly name
     */
    public void setKeyFriendlyName(String keyId,
                                   String friendlyName) {
        log.trace("setKeyFriendlyName({}, {})", keyId, friendlyName);

        tokenRegistry.writeRun(ctx -> {
            try {
                var key = ctx.findKey(keyId);
                tokenKeyService.updateFriendlyName(key.id(), friendlyName);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to update friendly name for key " + keyId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }


    /**
     * Sets the key label.
     *
     * @param keyId the key id
     * @param label the label
     */
    public void setKeyLabel(String keyId, String label) {
        log.trace("setKeyLabel({}, {})", keyId, label);

        tokenRegistry.writeRun(ctx -> {
            try {
                var key = ctx.findKey(keyId);
                tokenKeyService.updateLabel(key.id(), label);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to update friendly name for key " + keyId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }

    /**
     * Sets the key usage.
     *
     * @param keyId    the key id
     * @param keyUsage the key usage
     */
    public void setKeyUsage(String keyId,
                            KeyUsageInfo keyUsage) {
        log.trace("setKeyUsage({}, {})", keyId, keyUsage);
        tokenRegistry.writeRun(ctx -> {
            try {
                var key = ctx.findKey(keyId);
                tokenKeyService.updateKeyUsage(key.id(), keyUsage);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to update friendly name for key " + keyId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }


    /**
     * Removes a key with key id.
     *
     * @param keyId the key id
     * @return true if key was removed
     */
    public boolean removeKey(String keyId) {
        log.trace("removeKey({})", keyId);

        return tokenRegistry.writeAction(ctx -> {
            try {
                var key = ctx.findKey(keyId);
                return tokenKeyService.delete(key.id());
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to delete key " + keyId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }


    /**
     * Sets the public key for a key.
     *
     * @param keyId           the key id
     * @param publicKeyBase64 the public key base64
     */
    public void setPublicKey(String keyId,
                             String publicKeyBase64) {
        log.trace("setPublicKey({}, {})", keyId, publicKeyBase64);

        tokenRegistry.writeRun(ctx -> {
            try {
                var key = ctx.findKey(keyId);
                tokenKeyService.updatePublicKey(key.id(), publicKeyBase64);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to update friendly name for key " + keyId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }
}
