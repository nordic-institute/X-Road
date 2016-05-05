/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeConfig;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.jetty.http.HttpSchemes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.PortNumbers;

/**
 * Contains utility methods for getting OCSP responses for certificates.
 */
// TODO use POST method since there might be many hashes and query string has limit
public final class CertHashBasedOcspResponderClient {

    private static final Logger LOG =
            LoggerFactory.getLogger(CertHashBasedOcspResponderClient.class);

    private static final String METHOD = "GET";
    private static final String CERT_PARAM = "cert";

    private static final int DEFAULT_CONNECT_TIMEOUT = 20000;

    private static final List<Integer> VALID_RESPONSE_CODES = Arrays.asList(
        200, 201, 202, 203, 204, 205, 206, 207, 208, 226);

    private CertHashBasedOcspResponderClient() {
    }

    /**
     * Creates an GET request to the internal cert hash based OCSP responder
     * and expects an OCSP responses.
     * @param providerAddress URL of the OCSP response provider
     * @param hashes certificate hashes for which to get the responses
     * @return list of OCSP response objects
     * @throws IOException if I/O errors occurred
     * @throws OCSPException if the response could not be parsed
     */
    public static List<OCSPResp> getOcspResponsesFromServer(
            String providerAddress, String[] hashes)
                    throws IOException, OCSPException {
        URL url = createUrl(providerAddress, hashes);

        LOG.debug("Getting OCSP responses for hashes ({}) from: {}",
                Arrays.toString(hashes), url.getHost());

        return getOcspResponsesFromServer(url);
    }

    /**
     * Creates a GET request to the internal cert hash based OCSP responder
     * and expects OCSP responses.
     * @param destination URL of the OCSP response provider
     * @return list of OCSP response objects
     * @throws IOException if I/O errors occurred
     * @throws OCSPException if the response could not be parsed
     */
    public static List<OCSPResp> getOcspResponsesFromServer(URL destination)
            throws IOException, OCSPException {
        HttpURLConnection connection =
                (HttpURLConnection) destination.openConnection();
        connection.setRequestProperty("Accept", MimeTypes.MULTIPART_RELATED);
        connection.setDoOutput(true);
        connection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
        connection.setRequestMethod(METHOD);
        connection.connect();

        if (!VALID_RESPONSE_CODES.contains(connection.getResponseCode())) {
            LOG.error("Invalid HTTP response ({}) from responder: {}",
                    connection.getResponseCode(),
                    connection.getResponseMessage());
            throw new IOException(connection.getResponseMessage());
        }

        MimeConfig config = new MimeConfig();
        config.setHeadlessParsing(connection.getContentType());

        final List<OCSPResp> responses = new ArrayList<>();
        final MimeStreamParser parser = new MimeStreamParser(config);
        parser.setContentHandler(new AbstractContentHandler() {
            @Override
            public void startMultipart(BodyDescriptor bd) {
                parser.setFlat();
            }
            @Override
            public void body(BodyDescriptor bd, InputStream is)
                    throws MimeException, IOException {
                if (bd.getMimeType().equalsIgnoreCase(
                        MimeTypes.OCSP_RESPONSE)) {
                    responses.add(new OCSPResp(IOUtils.toByteArray(is)));
                }
            }
        });

        try {
            parser.parse(connection.getInputStream());
        } catch (MimeException e) {
            throw new OCSPException("Error parsing response", e);
        }

        return responses;
    }

    static URL createUrl(String providerAddress, String[] hashes)
            throws MalformedURLException {
        return new URL(HttpSchemes.HTTP, providerAddress,
                PortNumbers.PROXY_OCSP_PORT, "/?" + CERT_PARAM + "="
                        + StringUtils.join(hashes, "&" + CERT_PARAM + "="));
    }
}
