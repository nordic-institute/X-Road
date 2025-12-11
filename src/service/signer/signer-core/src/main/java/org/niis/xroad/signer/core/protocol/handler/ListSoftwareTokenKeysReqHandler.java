/*
 * The MIT License
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
package org.niis.xroad.signer.core.protocol.handler;

import com.google.protobuf.ByteString;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.rpc.common.Empty;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.common.protocol.AbstractRpcHandler;
import org.niis.xroad.signer.core.passwordstore.PasswordStore;
import org.niis.xroad.signer.core.tokenmanager.TokenLookup;
import org.niis.xroad.signer.core.tokenmanager.TokenPinManager;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil;
import org.niis.xroad.signer.proto.ListSoftwareTokenKeysResp;
import org.niis.xroad.signer.proto.SoftwareTokenKeyInfo;

import java.security.PrivateKey;
import java.util.List;

/**
 * Handles requests for listing all software token private keys.
 * <p>
 * This handler retrieves all keys from the software token and returns them
 * with their PKCS#12 keystore bytes and availability status for synchronization
 * to softtoken-signer instances.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ListSoftwareTokenKeysReqHandler extends AbstractRpcHandler<Empty, ListSoftwareTokenKeysResp> {

    private final TokenLookup tokenLookup;
    private final TokenPinManager pinManager;

    @Override
    protected ListSoftwareTokenKeysResp handle(Empty request) {
        log.debug("Listing software token keys for synchronization");

        final List<TokenInfo> softwareTokens = tokenLookup.listTokens().stream()
                .filter(t -> TokenInfo.SOFTWARE_MODULE_TYPE.equals(t.getType()))
                .toList();

        final ListSoftwareTokenKeysResp.Builder responseBuilder = ListSoftwareTokenKeysResp.newBuilder();

        for (TokenInfo softwareToken : softwareTokens) {
            for (KeyInfo key : softwareToken.getKeyInfo()) {
                PrivateKey privateKey = loadPrivateKey(softwareToken.getId(), key.getId());

                SoftwareTokenKeyInfo softwareTokenKey = SoftwareTokenKeyInfo.newBuilder()
                        .setKeyId(key.getId())
                        .setPrivateKey(ByteString.copyFrom(privateKey.getEncoded()))
                        .setTokenActive(softwareToken.isActive())
                        .setKeyAvailable(key.isAvailable())
                        .setKeyLabel(key.getLabel())
                        .setSignMechanism(key.getSignMechanismName())
                        .build();
                responseBuilder.addKeys(softwareTokenKey);
            }
        }

        log.debug("Successfully listed {} software token keys", responseBuilder.getKeysCount());
        return responseBuilder.build();
    }

    private PrivateKey loadPrivateKey(String tokenId, String keyId) {
        log.trace("Loading pkcs#12 private key '{}' from", keyId);
        return tokenLookup.getSoftwareTokenKeyStore(keyId).map(privateKeyBytes -> {
            try {
                var pin = getPin(tokenId);
                return SoftwareTokenUtil.loadPrivateKey(privateKeyBytes, keyId, pin);
            } catch (Exception e) {
                log.error("Failed to load private key from key store", e);
                return null;
            }
        }).orElse(null);
    }

    private char[] getPin(String tokenId) {
        final char[] pin = PasswordStore.getPassword(tokenId).orElse(null);
        verifyPinProvided(pin);

        return pin;
    }

    private static void verifyPinProvided(char[] pin) {
        if (pin == null || pin.length == 0) {
            throw XrdRuntimeException.systemInternalError("PIN not provided");
        }
    }
}
