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

package org.niis.xroad.cs.admin.core.facade;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoProto;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Profile("int-test")
@ConditionalOnProperty(value = "signerProxyMockUri")
@Component
@Slf4j
public class SignerProxyFacadeMockHttpImpl implements SignerProxyFacade {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("checkstyle:MagicNumber")
    public SignerProxyFacadeMockHttpImpl(RestTemplateBuilder builder, @Value("${signerProxyMockUri}") String signerProxyMockUri,
                                         ObjectMapper objectMapper) {
        log.info("Creating mocked SignerProxyFacade");

        var connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(1000, TimeUnit.MILLISECONDS))
                .setSocketTimeout(Timeout.of(1000, TimeUnit.MILLISECONDS))
                .build();
        var httpClientConnectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .build();
        final CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(httpClientConnectionManager)
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableRedirectHandling()
                .setUserAgent("X-Road SignerProxyFacade")
                .build();

        this.restTemplate = builder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(client))
                .rootUri(signerProxyMockUri)
                .build();

        this.objectMapper = objectMapper;
    }

    @Override
    public void initSoftwareToken(char[] password) {
        restTemplate.put("/initSoftwareToken/", password);
    }

    @Override
    public List<TokenInfo> getTokens() throws Exception {
        final String response = restTemplate.getForObject("/getTokens", String.class);
        return parseTokenInfoList(response);
    }

    @Override
    public TokenInfo getToken(String tokenId) throws Exception {
        final String response = restTemplate.getForObject("/getToken/{tokenId}", String.class, tokenId);
        return parseTokenInfo(response);
    }

    private List<TokenInfo> parseTokenInfoList(String tokenListString) throws JsonProcessingException {
        final JsonNode json = objectMapper.readTree(tokenListString);
        return StreamSupport.stream(json.spliterator(), true).map(this::parseTokenInfo).collect(Collectors.toList());
    }

    private TokenInfo parseTokenInfo(String tokenString) throws JsonProcessingException {
        final JsonNode json = objectMapper.readTree(tokenString);
        return parseTokenInfo(json);
    }

    private TokenInfo parseTokenInfo(JsonNode json) {
        return new TokenInfo(TokenInfoProto.newBuilder()
                .setType(json.get("type").asText())
                .setFriendlyName(json.get("friendlyName").asText())
                .setId(json.get("id").asText())
                .setReadOnly(json.get("readOnly").asBoolean())
                .setAvailable(json.get("available").asBoolean())
                .setActive(json.get("active").asBoolean())
                .setSerialNumber(json.get("serialNumber").asText())
                .setLabel(json.get("label").asText())
                .setSlotIndex(json.get("slotIndex").asInt())
                .setStatus(TokenStatusInfo.valueOf(json.get("status").asText()))
                .build());
    }

    @Override
    public void activateToken(String tokenId, char[] password) {
        restTemplate.put("/activateToken/{tokenId}", password, tokenId);
    }

    @Override
    public void deactivateToken(String tokenId) {
        restTemplate.put("/deactivateToken/{tokenId}", null, tokenId);
    }

    @Override
    public KeyInfo generateKey(String tokenId, String keyLabel) {
        throw new NotImplementedException("generateKey not implemented yet.");
    }

    @Override
    public byte[] generateSelfSignedCert(String keyId, ClientId.Conf memberId, KeyUsageInfo keyUsage,
                                         String commonName, Date notBefore, Date notAfter) {
        throw new NotImplementedException("generateSelfSignedCert not implemented yet.");
    }

    @Override
    public void deleteKey(String keyId, boolean deleteFromToken) {
        throw new NotImplementedException("deleteKey not implemented yet.");
    }

    @Override
    public String getSignMechanism(String keyId) {
        throw new NotImplementedException("getSignMechanism not implemented getSignMechanism.");
    }

    @Override
    public byte[] sign(String keyId, String signatureAlgorithmId, byte[] digest) {
        throw new NotImplementedException("sign not implemented getSignMechanism.");
    }

}
