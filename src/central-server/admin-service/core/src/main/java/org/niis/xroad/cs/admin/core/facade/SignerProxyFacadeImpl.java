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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.SignerProxy;
import ee.ria.xroad.signer.protocol.RpcSignerClient;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
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
public class SignerProxyFacadeImpl implements SignerProxyFacade {

    @PostConstruct
    void init() throws Exception {
        RpcSignerClient.init();
        log.info("SignerService rpcClient initialized with admin-service config");
    }

    /**
     * {@link SignerProxy#initSoftwareToken(char[])}
     */
    public void initSoftwareToken(char[] password) throws Exception {
        SignerProxy.initSoftwareToken(password);
    }

    /**
     * {@link SignerProxy#getTokens()}
     */
    public List<TokenInfo> getTokens() throws Exception {
        return SignerProxy.getTokens();
    }

    /**
     * {@link SignerProxy#getToken(String)}
     */
    public TokenInfo getToken(String tokenId) throws Exception {
        return SignerProxy.getToken(tokenId);
    }

    /**
     * {@link SignerProxy#activateToken(String, char[])}
     */
    public void activateToken(String tokenId, char[] password) throws Exception {
        SignerProxy.activateToken(tokenId, password);
    }

    /**
     * {@link SignerProxy#deactivateToken(String)}
     */
    public void deactivateToken(String tokenId) throws Exception {
        SignerProxy.deactivateToken(tokenId);
    }

    /**
     * {@link SignerProxy#generateKey(String, String)}
     */
    public KeyInfo generateKey(String tokenId, String keyLabel) throws Exception {
        return SignerProxy.generateKey(tokenId, keyLabel);
    }

    /**
     * {@link SignerProxy#generateSelfSignedCert(String, ClientId.Conf, KeyUsageInfo, String, Date, Date)}
     */
    public byte[] generateSelfSignedCert(String keyId, ClientId.Conf memberId, KeyUsageInfo keyUsage,
                                         String commonName, Date notBefore, Date notAfter) throws Exception {
        return SignerProxy.generateSelfSignedCert(keyId, memberId, keyUsage,
                commonName, notBefore, notAfter);
    }

    /**
     * {@link SignerProxy#deleteKey(String, boolean)}
     */
    public void deleteKey(String keyId, boolean deleteFromToken) throws Exception {
        SignerProxy.deleteKey(keyId, deleteFromToken);
    }

    /**
     * {ling {@link SignerProxy#getSignMechanism(String)}}
     */
    public String getSignMechanism(String keyId) throws Exception {
        return SignerProxy.getSignMechanism(keyId);
    }

    /**
     * {@link SignerProxy#sign(String, String, byte[])}
     */
    public byte[] sign(String keyId, String signatureAlgorithmId, byte[] digest) throws Exception {
        return SignerProxy.sign(keyId, signatureAlgorithmId, digest);
    }
}
