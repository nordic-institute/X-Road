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
package org.niis.xroad.proxy.core.util;

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.MimeTypes;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeConfig;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Contains utility methods for getting OCSP responses for certificates.
 */
@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public final class CertHashBasedOcspResponderClient {

    public static final String SHA_256_CERT_PARAM = "cert_hash";
    // Legacy SHA-1 hash parameter, kept so X-Road 8.x clients can fetch OCSP responses from 7.x security servers
    // whose responder only recognises this parameter name. Remove once 7.x interop is no longer required.
    static final String SHA_1_CERT_PARAM = "cert";
    private static final String METHOD = "GET";

    private static final List<Integer> VALID_RESPONSE_CODES = Arrays.asList(
            200, 201, 202, 203, 204, 205, 206, 207, 208, 226);

    private final ProxyProperties.OcspResponderProperties ocspResponderProperties;

    /**
     * Creates an GET request to the internal cert hash based OCSP responder and expects an OCSP responses.
     *
     * @param providerAddress URL of the OCSP response provider
     * @param certificates    certificates for which to get the responses
     * @return list of OCSP response objects
     */
    public List<OCSPResp> getOcspResponsesFromServer(String providerAddress, List<X509Certificate> certificates)
            throws CertificateEncodingException, URISyntaxException, IOException, OperatorCreationException, OCSPException {
        URL url = createUrl(providerAddress, certificates);
        return getOcspResponsesFromServer(url);
    }

    /**
     * Creates a GET request to the internal cert hash based OCSP responder and expects OCSP responses.
     *
     * @param destination URL of the OCSP response provider
     * @return list of OCSP response objects
     * @throws IOException   if I/O errors occurred
     * @throws OCSPException if the response could not be parsed
     */
    public List<OCSPResp> getOcspResponsesFromServer(URL destination) throws IOException, OCSPException {
        HttpURLConnection connection = (HttpURLConnection) destination.openConnection();
        connection.setRequestProperty("Accept", MimeTypes.MULTIPART_RELATED);
        connection.setDoOutput(true);
        connection.setConnectTimeout(ocspResponderProperties.clientConnectTimeout());
        connection.setReadTimeout(ocspResponderProperties.clientReadTimeout());
        connection.setRequestMethod(METHOD);
        connection.connect();

        if (!VALID_RESPONSE_CODES.contains(connection.getResponseCode())) {
            log.error("Invalid HTTP response ({}) from responder: {}", connection.getResponseCode(),
                    connection.getResponseMessage());

            throw new IOException(connection.getResponseMessage());
        }

        MimeConfig config = new MimeConfig.Builder().setHeadlessParsing(connection.getContentType()).build();

        final List<OCSPResp> responses = new ArrayList<>();
        final MimeStreamParser parser = new MimeStreamParser(config);

        parser.setContentHandler(new AbstractContentHandler() {
            @Override
            public void startMultipart(BodyDescriptor bd) {
                parser.setFlat();
            }

            @Override
            public void body(BodyDescriptor bd, InputStream is) throws MimeException, IOException {
                if (bd.getMimeType().equalsIgnoreCase(MimeTypes.OCSP_RESPONSE)) {
                    responses.add(new OCSPResp(IOUtils.toByteArray(is)));
                }
            }
        });

        try (InputStream is = connection.getInputStream()) {
            parser.parse(is);
        } catch (MimeException e) {
            throw new OCSPException("Error parsing response", e);
        }

        return responses;
    }

    private URL createUrl(String providerAddress, List<X509Certificate> certificates)
            throws URISyntaxException, IOException, CertificateEncodingException, OperatorCreationException {
        String[] sha256Hashes = CertUtils.getHashes(certificates);
        String[] sha1Hashes = getSha1Hashes(certificates);

        var uriBuilder = new URIBuilder().setScheme("http").setHost(providerAddress).setPort(ocspResponderProperties.port());
        // Send both parameter names so 7.x responders (only recognise "cert") and 8.x responders
        // (recognise "cert_hash") can both serve the request. Drop the SHA-1 parameter once 7.x interop is gone.
        Arrays.stream(sha1Hashes).forEach(hash -> uriBuilder.addParameter(SHA_1_CERT_PARAM, hash));
        Arrays.stream(sha256Hashes).forEach(hash -> uriBuilder.addParameter(SHA_256_CERT_PARAM, hash));
        var uri = uriBuilder.build();

        log.debug("Getting OCSP responses for hashes ({}) from: {}", Arrays.toString(sha256Hashes), uri.getHost());

        return uri.toURL();
    }

    private static String[] getSha1Hashes(List<X509Certificate> certs) throws CertificateEncodingException, IOException {
        String[] hashes = new String[certs.size()];
        for (int i = 0; i < certs.size(); i++) {
            hashes[i] = Digests.hexDigest(DigestAlgorithm.SHA1, certs.get(i).getEncoded());
        }
        return hashes;
    }
}
