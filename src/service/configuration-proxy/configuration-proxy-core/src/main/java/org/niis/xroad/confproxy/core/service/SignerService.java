/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.confproxy.core.service;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.util.Date;

import static org.niis.xroad.signer.protocol.dto.KeyUsageInfo.SIGNING;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SignerService {
    private static final String SOFTWARE_TOKEN_ID = "0";
    private static final Date FAR_FUTURE = new Date(Integer.MAX_VALUE);
    private static final Date FAR_PAST = new Date(0);
    private final SignerRpcClient signerRpcClient;

    public boolean isTokenActive(final String tokenId) {
        var token = signerRpcClient.getToken(tokenId);
        return token.isActive();
    }

    public boolean isTokenActiveByKeyId(final String keyId) {
        var token = signerRpcClient.getTokenForKeyId(keyId);
        return token.isActive();
    }

    public KeyCert createCert(final String keyId) {
        return new KeyCert(keyId,
                signerRpcClient.generateSelfSignedCert(keyId, null, SIGNING, "N/A", FAR_PAST, FAR_FUTURE));
    }

    public KeyCert createCert(final String tokenId, final KeyAlgorithm keyAlgorithm) {
        KeyInfo keyInfo = signerRpcClient.generateKey(tokenId, "key-" + System.currentTimeMillis(), keyAlgorithm);
        return createCert(keyInfo.getId());
    }

    public void deleteKey(String keyId) {
        signerRpcClient.deleteKey(keyId, true);
    }

    public void initSoftToken(char[] password) {
        try {
            var token = signerRpcClient.getToken(SOFTWARE_TOKEN_ID);
            if (TokenStatusInfo.NOT_INITIALIZED.equals(token.getStatus())) {
                signerRpcClient.initSoftwareToken(password);
                signerRpcClient.activateToken(SOFTWARE_TOKEN_ID, password);
                log.info("Initialized soft token: {}", SOFTWARE_TOKEN_ID);
            }
        } catch (Exception e) {
            log.error("Failed to initialize soft token: {}", e.getMessage(), e);
        }
    }

    public record KeyCert(String keyId, byte[] cert) {
    }
}
