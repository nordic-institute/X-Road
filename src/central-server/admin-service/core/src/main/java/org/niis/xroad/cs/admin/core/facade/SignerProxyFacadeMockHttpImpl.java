/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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

package org.niis.xroad.cs.admin.core.facade;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.commonui.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoAndKeyId;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;
import ee.ria.xroad.signer.protocol.message.CertificateRequestFormat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.niis.xroad.cs.admin.api.domain.MemberId;
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
import java.util.Map;
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

        final CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(1000)
                        .setSocketTimeout(1000)
                        .setConnectionRequestTimeout(1000)
                        .build())
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
        final List<KeyInfo> keyInfoList = List.of();
        Map<String, String> tokenParams = Map.of();

        return new TokenInfo(json.get("type").asText(), json.get("friendlyName").asText(), json.get("id").asText(),
                json.get("readOnly").asBoolean(), json.get("available").asBoolean(), json.get("active").asBoolean(),
                json.get("serialNumber").asText(), json.get("label").asText(), json.get("slotIndex").asInt(),
                TokenStatusInfo.valueOf(json.get("status").asText()), keyInfoList, tokenParams);
    }

    @Override
    public void activateToken(String tokenId, char[] password) throws Exception {
        restTemplate.put("/activateToken/{tokenId}", password, tokenId);
    }

    @Override
    public void deactivateToken(String tokenId) throws Exception {
        restTemplate.put("/deactivateToken/{tokenId}", null, tokenId);
    }

    @Override
    public void setTokenFriendlyName(String tokenId, String friendlyName) throws Exception {
        throw new NotImplementedException("setTokenFriendlyName not implemented yet.");
    }

    @Override
    public void setKeyFriendlyName(String keyId, String friendlyName) throws Exception {
        throw new NotImplementedException("setKeyFriendlyName not implemented yet.");
    }

    @Override
    public KeyInfo generateKey(String tokenId, String keyLabel) throws Exception {
        throw new NotImplementedException("generateKey not implemented yet.");
    }

    @Override
    public byte[] generateSelfSignedCert(String keyId, ClientId.Conf memberId, KeyUsageInfo keyUsage,
                                         String commonName, Date notBefore, Date notAfter) throws Exception {
        throw new NotImplementedException("generateSelfSignedCert not implemented yet.");
    }

    @Override
    public String importCert(byte[] certBytes, String initialStatus) throws Exception {
        throw new NotImplementedException("importCert not implemented yet.");
    }

    @Override
    public String importCert(byte[] certBytes, String initialStatus, ClientId.Conf clientId) throws Exception {
        throw new NotImplementedException("importCert2 not implemented yet.");
    }

    @Override
    public void activateCert(String certId) throws Exception {
        throw new NotImplementedException("activateCert not implemented yet.");
    }

    @Override
    public void deactivateCert(String certId) throws Exception {
        throw new NotImplementedException("deactivateCert not implemented yet.");
    }

    @Override
    public SignerProxy.GeneratedCertRequestInfo generateCertRequest(
            String keyId, MemberId memberId, KeyUsageInfo keyUsage, String subjectName, CertificateRequestFormat format) throws Exception {
        throw new NotImplementedException("generateCertRequest not implemented yet.");
    }

    @Override
    public SignerProxy.GeneratedCertRequestInfo regenerateCertRequest(
            String certRequestId, CertificateRequestFormat format) throws Exception {
        throw new NotImplementedException("regenerateCertRequest not implemented yet.");
    }

    @Override
    public void deleteCertRequest(String certRequestId) throws Exception {
        throw new NotImplementedException("deleteCertRequest not implemented yet.");
    }

    @Override
    public void deleteCert(String certId) throws Exception {
        throw new NotImplementedException("deleteCert not implemented yet.");
    }

    @Override
    public void deleteKey(String keyId, boolean deleteFromToken) throws Exception {
        throw new NotImplementedException("deleteKey not implemented yet.");
    }

    @Override
    public void setCertStatus(String certId, String status) throws Exception {
        throw new NotImplementedException("setCertStatus not implemented yet.");
    }

    @Override
    public CertificateInfo getCertForHash(String hash) throws Exception {
        throw new NotImplementedException("getCertForHash not implemented yet.");
    }

    @Override
    public String getKeyIdForCertHash(String hash) throws Exception {
        throw new NotImplementedException("getKeyIdForCertHash not implemented yet.");
    }

    @Override
    public TokenInfoAndKeyId getTokenAndKeyIdForCertHash(String hash) throws Exception {
        throw new NotImplementedException("getTokenAndKeyIdForCertHash not implemented yet.");
    }

    @Override
    public TokenInfoAndKeyId getTokenAndKeyIdForCertRequestId(String certRequestId) throws Exception {
        throw new NotImplementedException("getTokenAndKeyIdForCertRequestId not implemented yet.");
    }

    @Override
    public TokenInfo getTokenForKeyId(String keyId) throws Exception {
        throw new NotImplementedException("getTokenForKeyId not implemented yet.");
    }

    @Override
    public String[] getOcspResponses(String[] certHashes) throws Exception {
        throw new NotImplementedException("getOcspResponses not implemented yet.");
    }

    @Override
    public <T> T execute(Object message) throws Exception {
        throw new NotImplementedException("execute not implemented yet.");
    }

    @Override
    public void updateSoftwareTokenPin(String tokenId, char[] oldPin, char[] newPin) throws Exception {
        throw new NotImplementedException("updateSoftwareTokenPin not implemented yet.");
    }

}
