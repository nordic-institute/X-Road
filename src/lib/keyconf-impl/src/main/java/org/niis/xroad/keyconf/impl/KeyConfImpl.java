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
package org.niis.xroad.keyconf.impl;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.EncoderUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.keyconf.SigningInfo;
import org.niis.xroad.keyconf.dto.AuthKey;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.signer.api.dto.AuthKeyInfo;
import org.niis.xroad.signer.client.SignerRpcClient;

import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Encapsulates KeyConf related functionality.
 */
@Slf4j
@RequiredArgsConstructor
class KeyConfImpl implements KeyConfProvider {
    protected final GlobalConfProvider globalConfProvider;
    protected final ServerConfProvider serverConfProvider;
    protected final SignerRpcClient signerRpcClient;

    @Override
    public SigningInfo getSigningInfo(ClientId clientId) {
        log.debug("Retrieving signing info for member '{}'", clientId);

        try {
            return createSigningInfo(clientId);
        } catch (Exception e) {
            throw new CodedException(ErrorCodes.X_CANNOT_CREATE_SIGNATURE, "Failed to get signing info for member '%s': %s",
                    clientId, e);
        }
    }

    @Override
    public AuthKey getAuthKey() {
        PrivateKey pkey = null;
        CertChain certChain = null;
        try {
            SecurityServerId serverId = serverConfProvider.getIdentifier();
            log.debug("Retrieving authentication info for security "
                    + "server '{}'", serverId);

            AuthKeyInfo keyInfo = signerRpcClient.getAuthKey(serverId);

            pkey = loadAuthPrivateKey(keyInfo);
            if (pkey == null) {
                log.warn("Failed to read authentication key");
            }

            certChain = getAuthCertChain(serverId.getXRoadInstance(),
                    keyInfo.getCert().getCertificateBytes());
            if (certChain == null) {
                log.warn("Failed to read authentication certificate");
            }
        } catch (Exception e) {
            log.error("Failed to get authentication key", e);
        }

        return new AuthKey(certChain, pkey);
    }

    @Override
    public OCSPResp getOcspResponse(X509Certificate cert) throws Exception {
        return getOcspResponse(CryptoUtils.calculateCertSha1HexHash(cert));
    }

    @Override
    public OCSPResp getOcspResponse(String certHash) throws Exception {
        String[] responses = signerRpcClient.getOcspResponses(new String[]{certHash});

        for (String base64Encoded : responses) {
            return base64Encoded != null
                    ? new OCSPResp(EncoderUtils.decodeBase64(base64Encoded)) : null;
        }

        return null;
    }

    @Override
    public List<OCSPResp> getOcspResponses(List<X509Certificate> certs)
            throws Exception {
        String[] responses = signerRpcClient.getOcspResponses(CertUtils.getSha1Hashes(certs));

        List<OCSPResp> ocspResponses = new ArrayList<>();
        for (String base64Encoded : responses) {
            if (base64Encoded != null) {
                ocspResponses.add(new OCSPResp(EncoderUtils.decodeBase64(base64Encoded)));
            } else {
                ocspResponses.add(null);
            }
        }

        return ocspResponses;
    }

    @Override
    public void setOcspResponses(List<X509Certificate> certs,
                                 List<OCSPResp> responses) throws Exception {
        String[] base64EncodedResponses = new String[responses.size()];

        for (int i = 0; i < responses.size(); i++) {
            base64EncodedResponses[i] =
                    EncoderUtils.encodeBase64(responses.get(i).getEncoded());
        }

        signerRpcClient.setOcspResponses(CertUtils.getSha1Hashes(certs), base64EncodedResponses);
    }

    protected SigningInfo createSigningInfo(ClientId clientId) throws Exception {
        log.debug("Retrieving signing info for member '{}'", clientId);

        SignerRpcClient.MemberSigningInfoDto signingInfo = signerRpcClient.getMemberSigningInfo(clientId);
        X509Certificate cert = CryptoUtils.readCertificate(signingInfo.cert().getCertificateBytes());
        OCSPResp ocsp = new OCSPResp(signingInfo.cert().getOcspBytes());

        //Signer already checks the validity of the signing certificate. Just record the bounds
        //the certificate and ocsp response is valid for.
        Date notAfter = calculateNotAfter(Collections.singletonList(ocsp), cert.getNotAfter());
        return new SigningInfo(signingInfo.keyId(), signingInfo.signMechanismName(), clientId, cert, new Date(),
                notAfter);
    }

    /*
     * Upper bound for validity is the minimum of certificates "notAfter" and OCSP responses validity time
     * An OCSP response validity time is min(thisUpdate + ocspFreshnessSeconds, nextUpdate) or just
     * (thisUpdate + ocspFreshnessSeconds) if nextUpdate is not enforced or missing
     */
    Date calculateNotAfter(List<OCSPResp> ocspResponses, Date notAfter) throws OCSPException {
        final long freshnessMillis = 1000L * globalConfProvider.getOcspFreshnessSeconds();
        final boolean verifyNextUpdate = globalConfProvider.getGlobalConfExtensions().shouldVerifyOcspNextUpdate();

        for (OCSPResp resp : ocspResponses) {
            //ok to expect only one response since we request ocsp responses for one certificate at a time
            final SingleResp singleResp = ((BasicOCSPResp) resp.getResponseObject()).getResponses()[0];
            final Date freshUntil = new Date(singleResp.getThisUpdate().getTime() + freshnessMillis);
            if (freshUntil.before(notAfter)) notAfter = freshUntil;
            if (verifyNextUpdate) {
                final Date nextUpdate = singleResp.getNextUpdate();
                if (nextUpdate != null && nextUpdate.before(notAfter)) {
                    notAfter = nextUpdate;
                }
            }
        }
        return notAfter;
    }

    CertChain getAuthCertChain(String instanceIdentifier,
                               byte[] authCertBytes) {
        X509Certificate authCert = CryptoUtils.readCertificate(authCertBytes);
        try {
            return globalConfProvider.getCertChain(instanceIdentifier, authCert);
        } catch (Exception e) {
            log.error("Failed to get cert chain for certificate {}", authCert.getSubjectX500Principal(), e);
        }

        return null;
    }

    static PrivateKey loadAuthPrivateKey(AuthKeyInfo keyInfo) throws Exception {
        File keyStoreFile = new File(keyInfo.getKeyStoreFileName());
        log.trace("Loading authentication key from key store '{}'",
                keyStoreFile);

        KeyStore ks = CryptoUtils.loadPkcs12KeyStore(keyStoreFile, keyInfo.getPassword());

        PrivateKey privateKey = (PrivateKey) ks.getKey(keyInfo.getAlias(),
                keyInfo.getPassword());
        if (privateKey == null) {
            log.warn("Failed to read authentication key");
        }

        return privateKey;
    }
}
