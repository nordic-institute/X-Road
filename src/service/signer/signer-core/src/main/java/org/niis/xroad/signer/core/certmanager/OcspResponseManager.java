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

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.cert.CertChainVerifier;
import org.niis.xroad.signer.core.tokenmanager.TokenLookup;
import org.niis.xroad.signer.proto.SetOcspResponsesReq;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ee.ria.xroad.common.util.CertUtils.getHashes;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.EncoderUtils.decodeBase64;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;


/**
 * This class is responsible for managing the OCSP responses for certificates.
 * <p>
 * Certificates are identified by their SHA-256 fingerprint calculated over
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
@ApplicationScoped
@RequiredArgsConstructor
public class OcspResponseManager {
    private final GlobalConfProvider globalConfProvider;
    private final OcspClient ocspClient;

    /**
     * Maps a certificate hash to an OCSP response.
     */
    private final FileBasedOcspCache responseCache;
    private final TokenLookup tokenLookup;

    @PostConstruct
    public void init() {
        try {
            responseCache.reloadFromDisk();
        } catch (Exception e) {
            log.error("Failed to load OCSP responses from disk", e);
        }
    }

    public void refreshCache(Iterable<String> certHashesToRefresh) {
        certHashesToRefresh.forEach(hash -> {
            try {
                getFromCacheOrDownload(hash);
            } catch (Exception e) {
                log.error("Error while refreshing OCSP response for certificate {}: {}", hash, e.getMessage(), e);
            }
        });
    }

    public Map<String, String> handleGetOcspResponses(Collection<String> certHashes) {
        log.trace("handleGetOcspResponses()");

        final Map<String, String> ocspResponses = new HashMap<>();
        certHashes.forEach(hash ->
                getOcspResponse(hash)
                        .ifPresent(response -> ocspResponses.put(hash, encodeBase64(response))));

        return ocspResponses;
    }

    public Optional<byte[]> getOcspResponse(String certHash) {
        try {
            var ocspResponse = getFromCacheOrDownload(certHash);

            if (ocspResponse != null) {
                log.debug("Acquired an OCSP response for certificate {}", certHash);
                return Optional.ofNullable(ocspResponse.getEncoded());
            } else {
                log.warn("Could not acquire an OCSP response for certificate {}", certHash);
            }
        } catch (Exception e) {
            log.error("Error while getting OCSP response for certificate {}: {}", certHash, e.getMessage(), e);
        }

        return Optional.empty();
    }

    private OCSPResp getFromCacheOrDownload(String certHash) throws Exception {
        OCSPResp ocspResponse = getResponse(certHash);
        if (ocspResponse == null) {
            log.debug("No cached OCSP response available for cert {}", certHash);
            // if the response is not in local cache, download it
            ocspResponse = downloadOcspResponse(certHash);
            if (ocspResponse != null) {
                addToCache(certHash, ocspResponse);
            }
        } else {
            log.debug("Found a cached OCSP response for cert {}", certHash);
        }

        return ocspResponse;
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
            addToCache(message.getCertHashes(i), new OCSPResp(
                    decodeBase64(message.getBase64EncodedResponses(i))));
        }
    }

    public void removeOcspResponseFromTokenManagerIfExpiredOrNotInCache(String certHash) {
        //get verifies if the response is expired
        responseCache.get(certHash);
    }

    private OCSPResp getResponse(String certHash) {
        return responseCache.get(certHash);
    }

    private void addToCache(String certHash, OCSPResp response) {
        log.debug("Setting a new response to cache for cert: {}", certHash);
        responseCache.put(certHash, response);
    }

    /**
     * @param certHash the certificate SHA-1 hash in HEX
     * @return certificate matching certHash
     * @throws CertificateEncodingException if a certificate encoding error occurs
     * @throws OperatorCreationException    if digest calculator cannot be created
     * @throws IOException                  if an I/O error occurred
     */
    private X509Certificate getCertForCertHash(String certHash)
            throws CertificateEncodingException, IOException, OperatorCreationException {
        X509Certificate cert = tokenLookup.getCertificateForCerHash(certHash);
        if (cert != null) {
            return cert;
        }

        // not in key conf, look elsewhere
        for (X509Certificate caCert : globalConfProvider.getAllCaCerts()) {
            if (certHash.equals(calculateCertHexHash(caCert))) {
                return caCert;
            }
        }
        return null;
    }

    public void verifyOcspResponses(X509Certificate x509Certificate) throws Exception {
        CertChain certChain = globalConfProvider.getCertChain(globalConfProvider.getInstanceIdentifier(), x509Certificate);

        var result = handleGetOcspResponses(Arrays.asList(getHashes(certChain.getAllCertsWithoutTrustedRoot())));
        List<OCSPResp> ocspResponses = new ArrayList<>();
        for (String encodedResponse : result.values()) {
            if (encodedResponse != null) {
                ocspResponses.add(new OCSPResp(decodeBase64(encodedResponse)));
            } else {
                throw new IllegalStateException("OCSP Response was null");
            }
        }
        new CertChainVerifier(globalConfProvider, certChain).verifyOcspResponses(ocspResponses, new Date());
    }
}
