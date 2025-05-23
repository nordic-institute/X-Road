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
package org.niis.xroad.signer.core.certmanager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.cert.CertChainVerifier;
import org.niis.xroad.signer.api.message.GetOcspResponses;
import org.niis.xroad.signer.api.message.GetOcspResponsesResponse;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.proto.SetOcspResponsesReq;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import static ee.ria.xroad.common.util.CertUtils.getSha1Hashes;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertSha1HexHash;
import static ee.ria.xroad.common.util.EncoderUtils.decodeBase64;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;


/**
 * This class is responsible for managing the OCSP responses for certificates.
 * <p>
 * Certificates are identified by their SHA-1 fingerprint calculated over
 * the entire certificate.
 * <p>
 * When an OCSP response is added to the manager, it is first cached in memory
 * (overwriting any existing response) and then attempted to be written to disk
 * (overwriting any existing response file).
 * <p>
 * When an OCSP response is queried from the manager, first the cache is checked
 * for the response. If the response exists in the memory cache, it is returned.
 * If the response does not exist in the memory cache, the response will be
 * loaded from disk, if it exists and is cached in memory as well.
 */
@Slf4j
@RequiredArgsConstructor
public class OcspResponseManager {
    private final GlobalConfProvider globalConfProvider;
    private final OcspClient ocspClient;

    /**
     * Maps a certificate hash to an OCSP response.
     */
    private final FileBasedOcspCache responseCache;

    // ------------------------------------------------------------------------

    /**
     * Utility method for getting OCSP response for a certificate.
     *
     * @param cert the certificate
     * @return OCSP response as byte array
     * @throws Exception if an error occurs
     */
    public byte[] getOcspResponse(X509Certificate cert) throws Exception {
        return getOcspResponse(calculateCertSha1HexHash(cert));
    }

    /**
     * Utility method for getting OCSP response for a certificate hash.
     *
     * @param certHash the certificate hash
     * @return OCSP response as byte array
     * @throws Exception if an error occurs
     */
    private byte[] getOcspResponse(String certHash) throws Exception {
        GetOcspResponses message = new GetOcspResponses(new String[]{certHash});

        GetOcspResponsesResponse result = handleGetOcspResponses(message);

        if (result.getBase64EncodedResponses().length > 0
                && result.getBase64EncodedResponses()[0] != null) {
            return decodeBase64(result.getBase64EncodedResponses()[0]);
        } else {
            return null;
        }
    }

    // ------------------------------------------------------------------------

    public void init() {
        try {
            responseCache.reloadFromDisk();

            for (Entry<String, OCSPResp> e : responseCache.entrySet()) {
                TokenManager.setOcspResponse(e.getKey(), e.getValue());
            }
        } catch (Exception e) {
            log.error("Failed to load OCSP responses from disk", e);
        }
    }

    public GetOcspResponsesResponse handleGetOcspResponses(GetOcspResponses message) throws Exception {
        log.trace("handleGetOcspResponses()");

        String[] base64EncodedResponses = new String[message.getCertHash().length];
        for (int i = 0; i < message.getCertHash().length; i++) {
            OCSPResp ocspResponse = getResponse(message.getCertHash()[i]);
            if (ocspResponse == null) {
                log.debug("No cached OCSP response available for cert {}", message.getCertHash()[i]);
                // if the response is not in local cache, download it
                ocspResponse = downloadOcspResponse(message.getCertHash()[i]);
                if (ocspResponse != null) {
                    setResponse(message.getCertHash()[i], ocspResponse);
                }
            } else {
                log.debug("Found a cached OCSP response for cert {}", message.getCertHash()[i]);
            }

            if (ocspResponse != null) {
                log.debug("Acquired an OCSP response for certificate {}", message.getCertHash()[i]);
                base64EncodedResponses[i] = encodeBase64(ocspResponse.getEncoded());
            } else {
                log.warn("Could not acquire an OCSP response for certificate {}", message.getCertHash()[i]);
            }
        }

        return new GetOcspResponsesResponse(base64EncodedResponses);
    }

    private OCSPResp downloadOcspResponse(String certHash) throws Exception {
        log.trace("downloadOcspResponse({})", certHash);

        X509Certificate cert = getCertForCertHash(certHash);
        if (cert == null) {
            log.warn("Could not find certificate for hash {}", certHash);
            // unknown certificate
            return null;
        }

        try {
            log.debug("Downloading a new OCSP response for certificate {}", cert.getIssuerX500Principal());
            return ocspClient.queryCertStatus(cert);
        } catch (Exception e) {
            log.error("Error downloading OCSP response for certificate "
                    + cert.getSubjectX500Principal().getName()
                    + " (hash: " + certHash + ")", e);
            return null;
        }
    }

    public void handleSetOcspResponses(SetOcspResponsesReq message) throws Exception {
        log.trace("handleSetOcspResponses()");

        for (int i = 0; i < message.getCertHashesCount(); i++) {
            setResponse(message.getCertHashes(i), new OCSPResp(
                    decodeBase64(message.getBase64EncodedResponses(i))));
        }
    }

    public void removeOcspResponseFromTokenManagerIfExpiredOrNotInCache(String certHash) {
        OCSPResp response = responseCache.get(certHash);
        TokenManager.setOcspResponse(certHash, response);
    }

    private OCSPResp getResponse(String certHash) {
        return responseCache.get(certHash);
    }

    private void setResponse(String certHash, OCSPResp response) {
        log.debug("Setting a new response to cache for cert: {}", certHash);
        try {
            responseCache.put(certHash, response);
        } finally {
            TokenManager.setOcspResponse(certHash, response);
        }
    }

    /**
     * @param certSha1Hash the certificate SHA-1 hash in HEX
     * @return certificate matching certHash
     * @throws CertificateEncodingException if a certificate encoding error occurs
     * @throws OperatorCreationException    if digest calculator cannot be created
     * @throws IOException                  if an I/O error occurred
     */
    private X509Certificate getCertForCertHash(String certSha1Hash)
            throws CertificateEncodingException, IOException, OperatorCreationException {
        X509Certificate cert = TokenManager.getCertificateForCerHash(certSha1Hash);
        if (cert != null) {
            return cert;
        }

        // not in key conf, look elsewhere
        for (X509Certificate caCert : globalConfProvider.getAllCaCerts()) {
            if (certSha1Hash.equals(calculateCertSha1HexHash(caCert))) {
                return caCert;
            }
        }
        return null;
    }

    public void verifyOcspResponses(X509Certificate x509Certificate) throws Exception {
        CertChain certChain = globalConfProvider.getCertChain(globalConfProvider.getInstanceIdentifier(), x509Certificate);
        GetOcspResponses message = new GetOcspResponses(getSha1Hashes(certChain.getAllCertsWithoutTrustedRoot()));
        GetOcspResponsesResponse result = handleGetOcspResponses(message);
        List<OCSPResp> ocspResponses = new ArrayList<>();
        for (String encodedResponse : result.getBase64EncodedResponses()) {
            if (encodedResponse != null) {
                ocspResponses.add(new OCSPResp(decodeBase64(encodedResponse)));
            } else {
                throw new IllegalStateException("OCSP Response was null");
            }
        }
        new CertChainVerifier(globalConfProvider, certChain).verifyOcspResponses(ocspResponses, new Date());
    }
}
