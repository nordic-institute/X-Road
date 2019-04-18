/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.niis.xroad.restapi.repository.TokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * client service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("denyAll")
public class TokenService {

    private static final String CERT_PEM_FILENAME = "cert.pem";
    private static final String CERT_CER_FILENAME = "cert.cer";

    @Autowired
    private TokenRepository tokenRepository;

    /**
     * get all tokens
     * @return
     * @throws Exception
     */
    public List<TokenInfo> getAllTokens() throws Exception {
        return tokenRepository.getTokens();
    }

    /**
     * get all certificates for a given client.
     *
     * @param clientType client who's member certificates need to be
     *                   linked to
     * @return
     * @throws Exception
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public List<CertificateInfo> getAllTokens(ClientType clientType) throws Exception {
        List<TokenInfo> tokenInfos = getAllTokens();
        return tokenInfos.stream()
                .flatMap(tokenInfo -> tokenInfo.getKeyInfo().stream())
                .flatMap(keyInfo -> keyInfo.getCerts().stream())
                .filter(certificateInfo -> clientType.getIdentifier().memberEquals(certificateInfo.getMemberId()))
                .collect(toList());
    }

    /**
     * TO DO: correct permissions
     * Builds a tar.gz package which contains internal tls certificate as
     * two files:
     * - cert.pem PEM encoded certificate
     * - cert.cer DER encoded certificate
     * @return stream that contains the exported cert.tar.gz
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public InputStream getExportedInternalTlsCertificate() {
        X509Certificate certificate = tokenRepository.getInternalTlsCertificate();

        PipedInputStream pipedInputStream = new PipedInputStream();
        new Thread(new Runnable() {
            public void run() {
                try (
                        PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);
                        GzipCompressorOutputStream gzipCompressorOutputStream =
                                new GzipCompressorOutputStream(pipedOutputStream);
                        OutputStream bufferedOutputStream = new BufferedOutputStream(gzipCompressorOutputStream);
                        TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(bufferedOutputStream)
                ) {
                    ByteArrayOutputStream pemStream = new ByteArrayOutputStream();
                    CryptoUtils.writeCertificatePem(certificate.getEncoded(), pemStream);
                    byte[] pemBytes = pemStream.toByteArray();
                    TarArchiveEntry pemEntry = new TarArchiveEntry(CERT_PEM_FILENAME);
                    pemEntry.setSize(pemBytes.length);
                    writeArchiveEntry(tarOutputStream, pemBytes, pemEntry);

                    TarArchiveEntry derEntry = new TarArchiveEntry(CERT_CER_FILENAME);
                    byte[] derBytes = certificate.getEncoded();
                    derEntry.setSize(derBytes.length);
                    writeArchiveEntry(tarOutputStream, derBytes, derEntry);

                } catch (IOException | CertificateEncodingException e) {
                    log.error("writing certificate file failed", e);
                    throw new RuntimeException(e);
                }
            }

            private void writeArchiveEntry(TarArchiveOutputStream tarOutputStream,
                                           byte[] pemBytes, TarArchiveEntry pemEntry) throws IOException {
                tarOutputStream.putArchiveEntry(pemEntry);
                tarOutputStream.write(pemBytes);
                tarOutputStream.closeArchiveEntry();
            }
        }
        ).start();
        return pipedInputStream;
    }

}
