/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package org.niis.xroad.cs.admin.api.facade;

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

import org.niis.xroad.cs.admin.api.domain.MemberId;

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
    void initSoftwareToken(char[] password) throws Exception;

    /**
     * {@link SignerProxy#getTokens()}
     */
    List<TokenInfo> getTokens() throws Exception;

    /**
     * {@link SignerProxy#getToken(String)}
     */
    TokenInfo getToken(String tokenId) throws Exception;

    /**
     * {@link SignerProxy#activateToken(String, char[])}
     */
    void activateToken(String tokenId, char[] password) throws Exception;

    /**
     * {@link SignerProxy#deactivateToken(String)}
     */
    void deactivateToken(String tokenId) throws Exception;

    /**
     * {@link SignerProxy#setTokenFriendlyName(String, String)}
     */
    void setTokenFriendlyName(String tokenId, String friendlyName) throws Exception;

    /**
     * {@link SignerProxy#setKeyFriendlyName(String, String)}
     */
    void setKeyFriendlyName(String keyId, String friendlyName) throws Exception;

    /**
     * {@link SignerProxy#generateKey(String, String)}
     */
    KeyInfo generateKey(String tokenId, String keyLabel) throws Exception;

    /**
     * {@link SignerProxy#generateSelfSignedCert(String, ClientId.Conf, KeyUsageInfo, String, Date, Date)}
     */
    byte[] generateSelfSignedCert(String keyId, ClientId.Conf memberId, KeyUsageInfo keyUsage,
                                  String commonName, Date notBefore, Date notAfter) throws Exception;

    /**
     * {@link SignerProxy#importCert(byte[], String)}
     */
    String importCert(byte[] certBytes, String initialStatus) throws Exception;

    /**
     * {@link SignerProxy#importCert(byte[], String, ClientId.Conf)}
     */
    String importCert(byte[] certBytes, String initialStatus, ClientId.Conf clientId) throws Exception;

    /**
     * {@link SignerProxy#activateCert(String)}
     */
    void activateCert(String certId) throws Exception;

    /**
     * {@link SignerProxy#deactivateCert(String)}
     */
    void deactivateCert(String certId) throws Exception;

    /**
     * {@link SignerProxy#generateCertRequest(String, ClientId.Conf, KeyUsageInfo,
     * String, CertificateRequestFormat)}
     */
    GeneratedCertRequestInfo generateCertRequest(String keyId,
                                                 MemberId memberId,
                                                 KeyUsageInfo keyUsage,
                                                 String subjectName,
                                                 CertificateRequestFormat format) throws Exception;

    /**
     * {@link SignerProxy#regenerateCertRequest(String, CertificateRequestFormat)}
     */
    GeneratedCertRequestInfo regenerateCertRequest(String certRequestId, CertificateRequestFormat format)
            throws Exception;

    /**
     * {@link SignerProxy#deleteCertRequest(String)}
     */
    void deleteCertRequest(String certRequestId) throws Exception;

    /**
     * {@link SignerProxy#deleteCert(String)}
     */
    void deleteCert(String certId) throws Exception;

    /**
     * {@link SignerProxy#deleteKey(String, boolean)}
     */
    void deleteKey(String keyId, boolean deleteFromToken) throws Exception;

    /**
     * {@link SignerProxy#setCertStatus(String, String)}
     */
    void setCertStatus(String certId, String status) throws Exception;

    /**
     * {@link SignerProxy#getCertForHash(String)}
     */
    CertificateInfo getCertForHash(String hash) throws Exception;

    /**
     * {@link SignerProxy#getKeyIdForCertHash(String)}
     */
    String getKeyIdForCertHash(String hash) throws Exception;

    /**
     * {@link SignerProxy#getTokenAndKeyIdForCertHash(String)}
     */
    TokenInfoAndKeyId getTokenAndKeyIdForCertHash(String hash) throws Exception;

    /**
     * {@link SignerProxy#getTokenAndKeyIdForCertRequestId(String)}
     */
    TokenInfoAndKeyId getTokenAndKeyIdForCertRequestId(String certRequestId) throws Exception;

    /**
     * {@link SignerProxy#getTokenForKeyId(String)}
     */
    TokenInfo getTokenForKeyId(String keyId) throws Exception;

    /**
     * {@link SignerProxy#getOcspResponses(String[])}
     */
    String[] getOcspResponses(String[] certHashes) throws Exception;

    /**
     * {@link SignerClient#execute(Object message)}
     */
    <T> T execute(Object message) throws Exception;

    void updateSoftwareTokenPin(String tokenId, char[] oldPin, char[] newPin) throws Exception;
}
