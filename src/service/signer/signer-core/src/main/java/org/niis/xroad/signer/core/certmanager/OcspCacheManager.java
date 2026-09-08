/*
 * The MIT License
 *
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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.signer.core.model.BasicCertInfo;

import java.security.cert.X509Certificate;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class OcspCacheManager {
    private final OcspClient ocspClient;
    private final FileBasedOcspCache responseCache;

    public void refreshCache(Iterable<BasicCertInfo> certsToRefresh) {
        certsToRefresh.forEach(certInfo -> {
            try {
                getFromCacheOrDownload(certInfo.certificate(), certInfo.sha256hash());
            } catch (Exception e) {
                log.error("Error while refreshing OCSP response for certificate {}: {}", certInfo, e.getMessage(), e);
            }
        });
    }

    OCSPResp getFromCacheOrDownload(X509Certificate cert, String certHash) {
        OCSPResp ocspResponse = getFromCache(certHash);
        if (ocspResponse == null) {
            log.debug("No cached OCSP response available for cert {}", certHash);
            // if the response is not in local cache, download it
            ocspResponse = downloadOcspResponse(cert, certHash);
            if (ocspResponse != null) {
                addToCache(certHash, ocspResponse);
            }
        } else {
            log.debug("Found a cached OCSP response for cert {}", certHash);
        }

        return ocspResponse;
    }

    OCSPResp getFromCache(String certHash) {
        return responseCache.get(certHash);
    }

    public void removeOcspResponseFromTokenManagerIfExpiredOrNotInCache(String certHash) {
        //get verifies if the response is expired
        responseCache.get(certHash);
    }

    private OCSPResp downloadOcspResponse(X509Certificate cert, String certHash) {
        log.trace("downloadOcspResponse({})", certHash);

        try {
            log.debug("Downloading a new OCSP response for certificate {}", cert.getIssuerX500Principal());
            return ocspClient.queryCertStatus(cert);
        } catch (Exception e) {
            log.error("Error downloading OCSP response for certificate {} (hash: {}})",
                    cert.getSubjectX500Principal().getName(), certHash, e);
            return null;
        }
    }

    public void addToCache(String certHash, OCSPResp response) {
        log.debug("Setting a new response to cache for cert: {}", certHash);
        responseCache.put(certHash, response);
    }
}
