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
package org.niis.xroad.proxy.core.conf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.proxy.core.auth.AuthKey;
import org.niis.xroad.proxy.core.signedmessage.SignerSigningKey;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.signer.api.dto.AuthKeyInfo;
import org.niis.xroad.signer.client.SignerProxy;

import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.ria.xroad.common.util.CertUtils.getSha1Hashes;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertSha1HexHash;
import static ee.ria.xroad.common.util.CryptoUtils.loadPkcs12KeyStore;
import static ee.ria.xroad.common.util.EncoderUtils.decodeBase64;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;

/**
 * Encapsulates KeyConf related functionality.
 */
@Slf4j
class KeyConfImpl implements KeyConfProvider {
    protected final GlobalConfProvider globalConfProvider;
    protected final ServerConfProvider serverConfProvider;

    KeyConfImpl(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider) {
        this.globalConfProvider = globalConfProvider;
        this.serverConfProvider = serverConfProvider;
    }

    @Override
    public SigningCtx getSigningCtx(ClientId clientId) {
        log.debug("Retrieving signing info for member '{}'", clientId);

        try {
            SignerProxy.MemberSigningInfoDto signingInfo = SignerProxy.getMemberSigningInfo(clientId);

            return createSigningCtx(clientId, signingInfo.keyId(), signingInfo.cert().getCertificateBytes(),
                    signingInfo.signMechanismName());
        } catch (Exception e) {
            throw new CodedException(X_CANNOT_CREATE_SIGNATURE, "Failed to get signing info for member '%s': %s",
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

            AuthKeyInfo keyInfo = SignerProxy.getAuthKey(serverId);

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
        return getOcspResponse(calculateCertSha1HexHash(cert));
    }

    @Override
    public OCSPResp getOcspResponse(String certHash) throws Exception {
        String[] responses = SignerProxy.getOcspResponses(new String[]{certHash});

        for (String base64Encoded : responses) {
            return base64Encoded != null
                    ? new OCSPResp(decodeBase64(base64Encoded)) : null;
        }

        return null;
    }

    @Override
    public List<OCSPResp> getOcspResponses(List<X509Certificate> certs)
            throws Exception {
        String[] responses = SignerProxy.getOcspResponses(getSha1Hashes(certs));

        List<OCSPResp> ocspResponses = new ArrayList<>();
        for (String base64Encoded : responses) {
            if (base64Encoded != null) {
                ocspResponses.add(new OCSPResp(decodeBase64(base64Encoded)));
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
                    encodeBase64(responses.get(i).getEncoded());
        }

        SignerProxy.setOcspResponses(getSha1Hashes(certs), base64EncodedResponses);
    }

    SigningCtx createSigningCtx(ClientId subject, String keyId,
                                byte[] certBytes, SignMechanism signMechanismName) {
        return new SigningCtxImpl(
                globalConfProvider, this, subject,
                new SignerSigningKey(keyId, signMechanismName),
                CryptoUtils.readCertificate(certBytes)
        );
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

        KeyStore ks = loadPkcs12KeyStore(keyStoreFile, keyInfo.getPassword());

        PrivateKey privateKey = (PrivateKey) ks.getKey(keyInfo.getAlias(),
                keyInfo.getPassword());
        if (privateKey == null) {
            log.warn("Failed to read authentication key");
        }

        return privateKey;
    }
}
