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
package org.niis.xroad.cs.admin.api.facade;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;

import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.client.SignerProxy;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.util.Date;
import java.util.List;

/**
 * SignerProxy facade.
 * Pure facade / wrapper, just delegates to SignerProxy. Zero business logic.
 * Exists to make testing easier by offering non-static methods.
 */
public interface SignerProxyFacade {
    /**
     * {@link SignerProxy#initSoftwareToken(char[])}
     */
    void initSoftwareToken(char[] password) throws SignerException;

    /**
     * {@link SignerProxy#getTokens()}
     */
    List<TokenInfo> getTokens() throws SignerException;

    /**
     * {@link SignerProxy#getToken(String)}
     */
    TokenInfo getToken(String tokenId) throws SignerException;

    /**
     * {@link SignerProxy#activateToken(String, char[])}
     */
    void activateToken(String tokenId, char[] password) throws SignerException;

    /**
     * {@link SignerProxy#deactivateToken(String)}
     */
    void deactivateToken(String tokenId) throws SignerException;

    /**
     * {@link SignerProxy#generateKey(String, String, KeyAlgorithm)}
     */
    KeyInfo generateKey(String tokenId, String keyLabel, KeyAlgorithm algorithm) throws SignerException;

    /**
     * {@link SignerProxy#generateSelfSignedCert(String, ClientId.Conf, KeyUsageInfo, String, Date, Date)}
     */
    byte[] generateSelfSignedCert(String keyId, ClientId.Conf memberId, KeyUsageInfo keyUsage,
                                  String commonName, Date notBefore, Date notAfter) throws SignerException;

    /**
     * {@link SignerProxy#deleteKey(String, boolean)}
     */
    void deleteKey(String keyId, boolean deleteFromToken) throws SignerException;

    /**
     * {ling {@link SignerProxy#getSignMechanism(String)}}
     */
    SignMechanism getSignMechanism(String keyId) throws SignerException;

    /**
     * {@link SignerProxy#sign(String, SignAlgorithm, byte[])}
     */
    byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] digest) throws SignerException;

}
