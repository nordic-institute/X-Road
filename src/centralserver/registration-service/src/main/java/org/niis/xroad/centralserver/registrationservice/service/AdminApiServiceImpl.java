/**
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
package org.niis.xroad.centralserver.registrationservice.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.identifier.SecurityServerId;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.niis.xroad.centralserver.openapi.model.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.centralserver.openapi.model.ErrorInfo;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestInfo;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestOrigin;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestType;
import org.niis.xroad.centralserver.openapi.model.XRoadId;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.http.RequestEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

@Service
class AdminApiServiceImpl implements AdminApiService {

    public static final String PREFIX = "xroad.registration-service.";
    public static final String TRUSTSTORE = PREFIX + "truststore";
    public static final String TRUSTSTORE_PASSWORD = PREFIX + "truststore-password";
    public static final String API_BASEURL = PREFIX + "api-baseurl";
    public static final String API_TOKEN = PREFIX + "api-token";
    public static final String REQUEST_FAILED = "Registration request failed";
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    @SuppressWarnings("checkstyle:MagicNumber")
    AdminApiServiceImpl(Environment env, RestTemplateBuilder builder, ObjectMapper mapper) {

        CloseableHttpClient client;
        try {
            client = HttpClients.custom()
                    .setSSLHostnameVerifier(new NoopHostnameVerifier())
                    .setSSLContext(SSLContexts.custom()
                            .setProtocol("TLSv1.3")
                            .loadTrustMaterial(
                                    Paths.get(env.getRequiredProperty(TRUSTSTORE)).toFile(),
                                    env.getRequiredProperty(TRUSTSTORE_PASSWORD).toCharArray())
                            .build())
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectTimeout(1000)
                            .setSocketTimeout(5000)
                            .setConnectionRequestTimeout(10000)
                            .build())
                    .disableAutomaticRetries()
                    .disableCookieManagement()
                    .disableRedirectHandling()
                    .setUserAgent("X-Road Registration Service/7")
                    .build();
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException | CertificateException e) {
            throw new IllegalStateException("Unable to create HTTP clients", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load trust material", e);
        }

        this.mapper = mapper;
        this.restTemplate = builder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(client))
                .rootUri(env.getProperty(API_BASEURL, "https://127.0.0.1:4000/api/v1"))
                .defaultHeader("Authorization", "X-ROAD-APIKEY TOKEN=" + env.getRequiredProperty(API_TOKEN))
                .build();
    }

    @Override
    public int addRegistrationRequest(SecurityServerId serverId, String address, byte[] certificate) {
        var request = new AuthenticationCertificateRegistrationRequest();

        request.setType(ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST);
        request.setOrigin(ManagementRequestOrigin.SECURITY_SERVER);
        request.setServerAddress(address);
        request.setAuthenticationCertificate(certificate);

        var sid = new org.niis.xroad.centralserver.openapi.model.SecurityServerId();
        sid.setType(XRoadId.TypeEnum.SERVER);
        sid.setInstanceId(serverId.getXRoadInstance());
        sid.setMemberClass(serverId.getMemberClass());
        sid.setMemberCode(serverId.getMemberCode());
        sid.setServerCode(serverId.getServerCode());
        request.setSecurityserverId(sid);

        try {
            var result = restTemplate.exchange(
                    RequestEntity.post("/management-requests").body(request),
                    ManagementRequestInfo.class);

            if (!result.hasBody()) {
                throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, "Empty response");
            } else {
                return result.getBody().getId();
            }
        } catch (RestClientResponseException e) {
            var response = e.getResponseBodyAsByteArray();
            try {
                var errorInfo = mapper.readValue(response, ErrorInfo.class);
                var detail = errorInfo.getError() != null ? errorInfo.getError().getCode() : REQUEST_FAILED;
                throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, e, "%s", detail);
            } catch (IOException ex) {
                throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, ex, REQUEST_FAILED);
            }
        } catch (RestClientException e) {
            throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, e, REQUEST_FAILED);
        }
    }
}
