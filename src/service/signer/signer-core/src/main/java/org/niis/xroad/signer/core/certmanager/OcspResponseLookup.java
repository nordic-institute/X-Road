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
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.cert.CertChainVerifier;
import org.niis.xroad.signer.core.tokenmanager.TokenRegistry;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
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

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions") //TODO XRDDEV-2962 review and refactor if needed
public class OcspResponseLookup {
    private final GlobalConfProvider globalConfProvider;
    private final OcspCacheManager ocspCacheManager;
    private final TokenRegistry tokenRegistry;

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
            var ocspResponse = getOcspResp(certHash);

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

    private OCSPResp getOcspResp(String certHash) throws Exception {
        var cert = getCertForCertHash(certHash);
        if (cert == null) {
            log.warn("Could not find certificate for hash {}", certHash);
            // unknown certificate, but may be in cache
            return ocspCacheManager.getFromCache(certHash);
        }
        return ocspCacheManager.getFromCacheOrDownload(cert, certHash);
    }

    public void verifyOcspResponses(X509Certificate x509Certificate) throws Exception {
        CertChain certChain = globalConfProvider.getCertChain(globalConfProvider.getInstanceIdentifier(), x509Certificate);

        var result = handleGetOcspResponses(List.of(getHashes(certChain.getAllCertsWithoutTrustedRoot())));
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


    /**
     * @param certHash the certificate SHA-1 hash in HEX
     * @return certificate matching certHash
     * @throws CertificateEncodingException if a certificate encoding error occurs
     * @throws OperatorCreationException    if digest calculator cannot be created
     * @throws IOException                  if an I/O error occurred
     */
    private X509Certificate getCertForCertHash(String certHash)
            throws CertificateEncodingException, IOException, OperatorCreationException {
        X509Certificate cert = getCertificateForCerHash(certHash);
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

    /**
     * @param certSha256Hash the certificate SHA-256 hash in HEX
     * @return the certificate for the certificate hash or null
     */
    private X509Certificate getCertificateForCerHash(String certSha256Hash) {
        log.trace("getCertificateForCertHash({})", certSha256Hash);

        return tokenRegistry.readAction(ctx ->
                ctx.forCert((k, c) ->
                        certSha256Hash.equals(c.sha256hash()), (k, c) -> c.certificate()).orElse(null));
    }
}
