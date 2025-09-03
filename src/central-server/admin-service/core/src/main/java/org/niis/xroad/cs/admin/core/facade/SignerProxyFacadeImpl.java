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
package org.niis.xroad.cs.admin.core.facade;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * SignerProxy facade.
 * Pure facade / wrapper, just delegates to SignerProxy. Zero business logic.
 * Exists to make testing easier by offering non-static methods.
 */
@Slf4j
@Component
@Profile("!int-test")
@RequiredArgsConstructor
public class SignerProxyFacadeImpl implements SignerProxyFacade {

    private final SignerRpcClient signerRpcClient;
    private final SignerSignClient signerSignClient;

    public void initSoftwareToken(char[] password) {
        signerRpcClient.initSoftwareToken(password);
    }

    public List<TokenInfo> getTokens() {
        return signerRpcClient.getTokens();
    }

    public TokenInfo getToken(String tokenId) {
        return signerRpcClient.getToken(tokenId);
    }

    public void activateToken(String tokenId, char[] password) {
        signerRpcClient.activateToken(tokenId, password);
    }

    public void deactivateToken(String tokenId) {
        signerRpcClient.deactivateToken(tokenId);
    }

    public KeyInfo generateKey(String tokenId, String keyLabel, KeyAlgorithm algorithm) {
        return signerRpcClient.generateKey(tokenId, keyLabel, algorithm);
    }

    public byte[] generateSelfSignedCert(String keyId, ClientId.Conf memberId, KeyUsageInfo keyUsage,
                                         String commonName, Date notBefore, Date notAfter) {
        return signerRpcClient.generateSelfSignedCert(keyId, memberId, keyUsage,
                commonName, notBefore, notAfter);
    }

    public void deleteKey(String keyId, boolean deleteFromToken) {
        signerRpcClient.deleteKey(keyId, deleteFromToken);
    }

    public SignMechanism getSignMechanism(String keyId) {
        return signerRpcClient.getSignMechanism(keyId);
    }

    public byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] digest) {
        return signerSignClient.sign(keyId, signatureAlgorithmId, digest);
    }

}
