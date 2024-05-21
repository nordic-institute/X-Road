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
package org.niis.xroad.proxy.edc;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.proxy.util.HeadersComparator;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.http.message.BasicHeader;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.util.io.TeeOutputStream;
import org.niis.xroad.ssl.SSLContextBuilder;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_HASH;
import static org.apache.hc.core5.util.Timeout.ofSeconds;
import static org.niis.xroad.edc.sig.PocConstants.HEADER_XRD_SIG;

/**
 * Right now it is a simplified version of actual client. Consider adding improved configuration and error handling.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EdcDataPlaneHttpClient {

    public static RestResponse sendRestRequest(ClassicHttpRequest request, RestRequest req) {
        log.info("Will send REST [{}] request to {}", request.getMethod(), request.getRequestUri());
        try (CloseableHttpClient httpClient = createHttpClient()) {
            return httpClient.execute(request, response -> {
                log.info("EDC responded with code {}", response.getCode());

                List<org.apache.http.Header> headers = Arrays.stream(response.getHeaders())
                        .filter(h -> !HEADER_XRD_SIG.equalsIgnoreCase(h.getName()))
                        .map(h -> (org.apache.http.Header) new BasicHeader(h.getName(), h.getValue()))
                        .sorted(new HeadersComparator())
                        .toList();

                byte[] requestHashFromResponse = Base64.getDecoder().decode(response.getHeader(HEADER_REQUEST_HASH).getValue());
                var signature = response.getHeader(HEADER_XRD_SIG).getValue();

                RestResponse restResponse = new RestResponse(req.getClientId(), req.getQueryId(), requestHashFromResponse,
                        req.getServiceId(), response.getCode(), response.getReasonPhrase(), headers,
                        req.getXRequestId());

                try {
                    CachingStream responseBodyStream = new CachingStream();
                    DigestCalculator dc = CryptoUtils.createDigestCalculator(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID);
                    TeeOutputStream tos = new TeeOutputStream(responseBodyStream, dc.getOutputStream());
                    IOUtils.copyLarge(response.getEntity().getContent(), tos);

                    restResponse.setBody(responseBodyStream);
                    restResponse.setBodyDigest(encodeBase64(dc.getDigest()));
                    restResponse.setSignature(signature);
                } catch (Exception e) {
                    throw new CodedException(X_INTERNAL_ERROR, e, "Unable to read response body");
                }

                return restResponse;
            });
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e, "Error during edc dataplane request. Root Cause: " + e.getMessage());
        }
    }

    public static EdcSoapWrapper sendSoapRequest(ClassicHttpRequest request) {
        log.info("Will send [{}] request to {}", request.getMethod(), request.getRequestUri());
        try (CloseableHttpClient httpClient = createHttpClient()) {
            return httpClient.execute(request, response -> {
                log.info("EDC responded with code {}", response.getCode());

                Map<String, String> headers = Arrays.stream(response.getHeaders())
                        .collect(Collectors.toMap(Header::getName, Header::getValue));

                SoapParserImpl soapParser = new SoapParserImpl();
                Soap soap = soapParser.parse(response.getEntity().getContentType(), response.getEntity().getContent());
                return new EdcSoapWrapper(soap, headers);
            });
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e, "Error during edc dataplane request. Root Cause: " + e.getMessage());
        }
    }

    private static CloseableHttpClient createHttpClient() throws NoSuchAlgorithmException, KeyManagementException {
        var httpClientProperties = new HttpClientProperties();

        final SSLConnectionSocketFactory sslsf =
                new SSLConnectionSocketFactory(SSLContextBuilder.create().sslContext(), NoopHostnameVerifier.INSTANCE);
        final Registry<ConnectionSocketFactory> socketFactoryRegistry =
                RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("https", sslsf)
                        .register("http", new PlainConnectionSocketFactory())
                        .build();

        var connectionManager = new BasicHttpClientConnectionManager(socketFactoryRegistry);
        connectionManager.setConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(ofSeconds(httpClientProperties.getConnectionTimeoutSeconds()))
                .build());

        return HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(ofSeconds(httpClientProperties.getResponseTimeoutSeconds()))
                        .setConnectionRequestTimeout(ofSeconds(httpClientProperties.getConnectionRequestTimeoutSeconds()))
                        .build())
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableRedirectHandling()
                .setUserAgent("Proxy EDC client")
                .build();
    }

    @Getter
    @Setter
    @SuppressWarnings("checkstyle:MagicNumber")
    static class HttpClientProperties {
        private Integer connectionTimeoutSeconds = 5;
        private Integer connectionRequestTimeoutSeconds = 10;
        private Integer responseTimeoutSeconds = 5;
    }

    public record EdcSoapWrapper(
            Soap soapMessage,
            Map<String, String> headers
    ) {
    }

}
