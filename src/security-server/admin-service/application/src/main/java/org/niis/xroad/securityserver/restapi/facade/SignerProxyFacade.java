/**
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
package org.niis.xroad.securityserver.restapi.facade;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.commonui.SignerProxy;
import ee.ria.xroad.commonui.SignerProxy.GeneratedCertRequestInfo;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoAndKeyId;
import ee.ria.xroad.signer.protocol.message.CertificateRequestFormat;

import lombok.extern.slf4j.Slf4j;
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
public class SignerProxyFacade {
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
     * {@link SignerProxy#setTokenFriendlyName(String, String)}
     */
    public void setTokenFriendlyName(String tokenId, String friendlyName) throws Exception {
        SignerProxy.setTokenFriendlyName(tokenId, friendlyName);
    }

    /**
     * {@link SignerProxy#setKeyFriendlyName(String, String)}
     */
    public void setKeyFriendlyName(String keyId, String friendlyName) throws Exception {
        SignerProxy.setKeyFriendlyName(keyId, friendlyName);
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
     * {@link SignerProxy#importCert(byte[], String)}
     */
    public String importCert(byte[] certBytes, String initialStatus) throws Exception {
        return SignerProxy.importCert(certBytes, initialStatus);
    }

    /**
     * {@link SignerProxy#importCert(byte[], String, ClientId.Conf)}
     */
    public String importCert(byte[] certBytes, String initialStatus, ClientId.Conf clientId) throws Exception {
        return SignerProxy.importCert(certBytes, initialStatus, clientId);
    }

    /**
     * {@link SignerProxy#activateCert(String)}
     */
    public void activateCert(String certId) throws Exception {
        SignerProxy.activateCert(certId);
    }

    /**
     * {@link SignerProxy#deactivateCert(String)}
     */
    public void deactivateCert(String certId) throws Exception {
        SignerProxy.deactivateCert(certId);
    }

    /**
     * {@link SignerProxy#generateCertRequest(String, ClientId.Conf, KeyUsageInfo, String, CertificateRequestFormat)}
     */
    public GeneratedCertRequestInfo generateCertRequest(String keyId, ClientId.Conf memberId, KeyUsageInfo keyUsage,
            String subjectName, CertificateRequestFormat format) throws Exception {
        return SignerProxy.generateCertRequest(keyId, memberId, keyUsage, subjectName, format);
    }

    /**
     * {@link SignerProxy#regenerateCertRequest(String, CertificateRequestFormat)}
     */
    public GeneratedCertRequestInfo regenerateCertRequest(String certRequestId, CertificateRequestFormat format)
            throws Exception {
        return SignerProxy.regenerateCertRequest(certRequestId, format);
    }

    /**
     * {@link SignerProxy#deleteCertRequest(String)}
     */
    public void deleteCertRequest(String certRequestId) throws Exception {
        SignerProxy.deleteCertRequest(certRequestId);
    }

    /**
     * {@link SignerProxy#deleteCert(String)}
     */
    public void deleteCert(String certId) throws Exception {
        SignerProxy.deleteCert(certId);
    }

    /**
     * {@link SignerProxy#deleteKey(String, boolean)}
     */
    public void deleteKey(String keyId, boolean deleteFromToken) throws Exception {
        SignerProxy.deleteKey(keyId, deleteFromToken);
    }

    /**
     * {@link SignerProxy#setCertStatus(String, String)}
     */
    public void setCertStatus(String certId, String status) throws Exception {
        SignerProxy.setCertStatus(certId, status);
    }

    /**
     * {@link SignerProxy#getCertForHash(String)}
     */
    public CertificateInfo getCertForHash(String hash) throws Exception {
        return SignerProxy.getCertForHash(hash);
    }

    /**
     * {@link SignerProxy#getKeyIdForCertHash(String)}
     */
    public String getKeyIdForCertHash(String hash) throws Exception {
        return SignerProxy.getKeyIdForCertHash(hash);
    }

    /**
     * {@link SignerProxy#getTokenAndKeyIdForCertHash(String)}
     */
    public TokenInfoAndKeyId getTokenAndKeyIdForCertHash(String hash) throws Exception {
        return SignerProxy.getTokenAndKeyIdForCertHash(hash);
    }

    /**
     * {@link SignerProxy#getTokenAndKeyIdForCertRequestId(String)}
     */
    public TokenInfoAndKeyId getTokenAndKeyIdForCertRequestId(String certRequestId) throws Exception {
        return SignerProxy.getTokenAndKeyIdForCertRequestId(certRequestId);
    }

    /**
     * {@link SignerProxy#getTokenForKeyId(String)}
     */
    public TokenInfo getTokenForKeyId(String keyId) throws Exception {
        return SignerProxy.getTokenForKeyId(keyId);
    }

    /**
     * {@link SignerProxy#getOcspResponses(String[])}
     */
    public String[] getOcspResponses(String[] certHashes) throws Exception {
        return SignerProxy.getOcspResponses(certHashes);
    }

    /**
     * {@link SignerClient#execute(Object message)}
     */
    public <T> T execute(Object message) throws Exception {
        return SignerClient.execute(message);
    }

    public void updateSoftwareTokenPin(String tokenId, char[] oldPin, char[] newPin) throws Exception {
        SignerProxy.updateTokenPin(tokenId, oldPin, newPin);
    }
}
