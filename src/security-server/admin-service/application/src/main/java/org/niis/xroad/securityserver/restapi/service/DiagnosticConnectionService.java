/*
 * The MIT License
 *
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.ProtocolVersion;
import ee.ria.xroad.common.message.RestMessage;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapBuilder;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.HttpSender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.soap.SOAPException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.niis.xroad.common.core.dto.ConnectionStatus;
import org.niis.xroad.common.core.dto.DownloadUrlConnectionStatus;
import org.niis.xroad.common.core.exception.ErrorDeviation;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confclient.proto.CheckAndGetConnectionStatusRequest;
import org.niis.xroad.confclient.proto.CheckAndGetConnectionStatusResponse;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.ClientSslKeyManager;
import org.niis.xroad.securityserver.restapi.dto.ServiceProtocolType;
import org.niis.xroad.securityserver.restapi.util.AuthCertVerifier;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.MimeTypes.TEXT_XML_UTF8;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CLIENT_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_SECURITY_SERVER;
import static ee.ria.xroad.common.util.MimeUtils.getBaseContentType;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_REQUEST;
import static org.niis.xroad.securityserver.restapi.service.PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID;

@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class DiagnosticConnectionService {
    private static final Integer HTTP_200 = 200;
    private static final String INTERNAL_CONF = "internalconf";

    private final GlobalConfProvider globalConfProvider;
    private final TokenService tokenService;
    private final AuthCertVerifier authCertVerifier;
    private final ManagementRequestSenderService managementRequestSenderService;
    private final ConfClientRpcClient confClientRpcClient;
    private final ServerConfProvider serverConfProvider;
    private final AdminServiceProperties adminServiceProperties;

    public List<DownloadUrlConnectionStatus> getGlobalConfStatus() {
        var localInstance = globalConfProvider.getInstanceIdentifier();
        var allInstancesStream = getInstancesStream(localInstance);

        return allInstancesStream
                .flatMap(instance -> {
                    var addresses = globalConfProvider.getSourceAddresses(instance);
                    var confDirectory = localInstance.equals(instance)
                            ? INTERNAL_CONF
                            : globalConfProvider.getConfigurationDirectoryPath(instance);
                    return configsForAddress(localInstance, instance, addresses, confDirectory);
                })
                .map(this::checkConnection)
                .map(this::toDownloadStatuses)
                .flatMap(List::stream)
                .toList();
    }

    private @NotNull Stream<String> getInstancesStream(String localInstance) {
        return Stream.concat(
                Stream.of(localInstance),
                globalConfProvider.getInstanceIdentifiers().stream()
                        .filter(instance -> !localInstance.equals(instance))
                        .filter(StringUtils::isNotBlank)
                        .distinct()
        );
    }

    private Stream<ConnectionConfig> configsForAddress(String localInstance, String instance, Set<String> addresses, String directory) {
        return addresses.stream()
                .map(address -> new ConnectionConfig(localInstance, instance, address, directory));
    }

    private CheckAndGetConnectionStatusResponse checkConnection(ConnectionConfig c) {
        return confClientRpcClient.checkAndGetConnectionStatus(
                CheckAndGetConnectionStatusRequest.newBuilder()
                        .setLocalInstance(c.localInstance())
                        .setInstance(c.instance)
                        .setAddress(c.address())
                        .setDirectory(c.directory)
                        .build()
        );
    }

    private DownloadUrlConnectionStatus toDownloadStatus(org.niis.xroad.rpc.common.DownloadUrlConnectionStatus status) {
        if (!status.getErrorCode().isEmpty()) {
            return DownloadUrlConnectionStatus.error(
                    status.getDownloadUrl(),
                    status.getErrorCode(),
                    List.of(status.getErrorDetails())
            );
        }
        return verifyAndWrap(status.getDownloadUrl());
    }

    private List<DownloadUrlConnectionStatus> toDownloadStatuses(CheckAndGetConnectionStatusResponse response) {
        if (!response.getConnectionStatusesList().isEmpty()) {
            return response.getConnectionStatusesList().stream()
                    .map(this::toDownloadStatus)
                    .toList();
        }
        return List.of();
    }

    private DownloadUrlConnectionStatus verifyAndWrap(String downloadUrl) {
        try {
            globalConfProvider.verifyValidity();
            return DownloadUrlConnectionStatus.ok(downloadUrl);
        } catch (Exception e) {
            XrdRuntimeException x = XrdRuntimeException.systemException(e);
            return DownloadUrlConnectionStatus.error(downloadUrl, x.getErrorCode(), List.of(x.getDetails()));
        }
    }

    public ConnectionStatus getAuthCertReqStatus() {
        CertValidation certValidation = validateAuthCert();
        try {
            // the error is expected, but we want to verify that the connection can be established
            managementRequestSenderService.sendAuthCertRegisterRequest(null, new byte[0], true);
            throw new IllegalStateException("should not get here");
        } catch (XrdRuntimeException e) {
            // when certificate or address validation error, the error is expected,
            // and we return only certificate validation exceptions (if any)

            if (isExpectedInvalidRequest(e)) {
                return certValidation.isOk() ? ConnectionStatus.ok()
                        : ConnectionStatus.error(certValidation.errorCode, certValidation.metadata);
            }
            return certValidation.isOk() ? ConnectionStatus.error(e.getErrorCode(), listOrEmpty(e.getDetails()))
                    : ConnectionStatus.fromErrorAndValidation(
                    e.getErrorCode(),
                    listOrEmpty(e.getDetails()),
                    certValidation.errorCode, certValidation.metadata);
        }
    }

    private boolean isExpectedInvalidRequest(XrdRuntimeException e) {
        return INVALID_REQUEST.code().equals(e.getErrorCode());
    }

    public ConnectionStatus getOtherSecurityServerStatus(ServiceProtocolType protocolType, ClientId clientId,
                                                         ClientId targetClientId,
                                                         SecurityServerId securityServerId) {
        if (!ServiceProtocolType.REST.equals(protocolType) && !ServiceProtocolType.SOAP.equals(protocolType)) {
            throw new IllegalStateException("Unsupported protocol type: " + protocolType);
        }

        try (CloseableHttpClient proxyHttpClient = createProxyHttpClient()) {
            switch (protocolType) {
                case REST -> {
                    HttpGet request = getRestHttpGet(clientId, targetClientId, securityServerId);

                    try (CloseableHttpResponse response = proxyHttpClient.execute(request)) {
                        if (response.getStatusLine().getStatusCode() != HTTP_200) {
                            ObjectMapper mapper = new ObjectMapper();
                            String body = EntityUtils.toString(response.getEntity());
                            JsonNode json = mapper.readTree(body);

                            String errorCode = json.has("type") ? json.get("type").asText() : "Error";
                            String details = json.has("message") ? json.get("message").asText() : body;

                            return ConnectionStatus.error(errorCode, List.of(details));
                        }
                    }

                }
                case SOAP -> {
                    try (HttpSender sender = createSender(proxyHttpClient)) {
                        SoapMessageImpl soapMessage = buildListMethodsSoapMessage(
                                clientId, targetClientId, securityServerId);

                        send(sender, new URI(adminServiceProperties.getManagementProxyServerUrl()), soapMessage);
                    }
                }
                default -> throw new IllegalStateException("should not get here");
            }

        } catch (Exception e) {
            XrdRuntimeException result = XrdRuntimeException.systemException(e);
            return ConnectionStatus.error(result.getErrorCode(), List.of(result.getDetails()));
        }

        return ConnectionStatus.ok();
    }

    private CloseableHttpClient createProxyHttpClient() {
        try {
            return createProxyHttpClientWithInternalKey();
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                    .cause(e)
                    .details("Unable to initialize request client")
                    .build();
        }
    }

    @SuppressWarnings("java:S4830") // Won't fix: Works as designed ("Server certificates should be verified")
    private CloseableHttpClient createProxyHttpClientWithInternalKey() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // never called as this is trust manager of a client
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // localhost called so server is trusted
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[]{};
            }
        };

        return createHttpClient(new KeyManager[] {new ClientSslKeyManager(serverConfProvider)}, new TrustManager[] {trustManager});
    }

    private CloseableHttpClient createHttpClient(KeyManager[] keyManagers, TrustManager[] trustManagers)
            throws NoSuchAlgorithmException, KeyManagementException {

        SSLContext sslContext = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        sslContext.init(keyManagers, trustManagers, new SecureRandom());

        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

        int timeout = adminServiceProperties.getManagementProxyServerConnectTimeout();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setSocketTimeout(adminServiceProperties.getManagementProxyServerSocketTimeout())
                .build();

        return HttpClients.custom()
                .setSSLSocketFactory(sslSocketFactory)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();
    }

    private HttpSender createSender(CloseableHttpClient client) {
        HttpSender httpSender = new HttpSender(client, adminServiceProperties.isManagementProxyServerEnableConnectionReuse());
        httpSender.setConnectionTimeout(adminServiceProperties.getManagementProxyServerConnectTimeout());
        httpSender.setSocketTimeout(adminServiceProperties.getManagementProxyServerSocketTimeout());
        return httpSender;
    }

    private static void send(HttpSender sender, URI address, SoapMessageImpl soapMessage) throws XrdRuntimeException {
        try {
            sender.doPost(address, new ByteArrayInputStream(soapMessage.getBytes()), CHUNKED_LENGTH, TEXT_XML_UTF8);

            Soap response = new SoapParserImpl().parse(getBaseContentType(sender.getResponseContentType()), sender.getResponseContent());
            if (response instanceof SoapFault soapFault) {
                throw soapFault.toXrdRuntimeException();
            }

        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    private HttpGet getRestHttpGet(ClientId clientId, ClientId targetClientId, SecurityServerId securityServerId) {
        HttpGet request = new HttpGet(URI.create(adminServiceProperties.getManagementProxyServerUrl() + getRestPath(targetClientId)));
        request.setProtocolVersion(org.apache.http.HttpVersion.HTTP_1_1);
        request.addHeader("accept", "application/json");
        request.addHeader(HEADER_SECURITY_SERVER, String.format("%s/%s/%s/%s",
                securityServerId.getXRoadInstance(),
                securityServerId.getMemberClass(),
                securityServerId.getMemberCode(),
                securityServerId.getServerCode()));
        request.addHeader(HEADER_CLIENT_ID, clientId.getSubsystemCode() != null
                ? String.format("%s/%s/%s/%s",
                clientId.getXRoadInstance(),
                clientId.getMemberClass(),
                clientId.getMemberCode(),
                clientId.getSubsystemCode())
                : String.format("%s/%s/%s",
                clientId.getXRoadInstance(),
                clientId.getMemberClass(),
                clientId.getMemberCode()));
        return request;
    }

    private static SoapMessageImpl buildListMethodsSoapMessage(ClientId clientId, ClientId targetClientId,
                                                               SecurityServerId securityServerId)
            throws IllegalAccessException, SOAPException, JAXBException, IOException {

        SoapHeader header = new SoapHeader();
        header.setClient(clientId);
        header.setService(ServiceId.Conf.create(targetClientId, "listMethods"));
        header.setSecurityServer(SecurityServerId.Conf.create(
                securityServerId.getXRoadInstance(),
                securityServerId.getMemberClass(),
                securityServerId.getMemberCode(),
                securityServerId.getServerCode()
        ));
        header.setQueryId(UUID.randomUUID().toString());
        header.setProtocolVersion(new ProtocolVersion());

        SoapBuilder builder = new SoapBuilder();
        builder.setHeader(header);
        builder.setRpcEncoded(false);

        return builder.build();
    }

    private static String getRestPath(ClientId clientId) {
        return String.format("/r%d/%s/%s/%s/%s/listMethods",
                RestMessage.PROTOCOL_VERSION,
                clientId.getXRoadInstance(),
                clientId.getMemberClass(),
                clientId.getMemberCode(),
                clientId.getSubsystemCode());
    }

    private CertValidation validateAuthCert() {
        List<CertificateInfo> certificateInfos;
        try {
            certificateInfos = findAuthCerts(Set.of(CertificateInfo.STATUS_SAVED));
            if (certificateInfos.isEmpty()) {
                certificateInfos = findAuthCerts(Set.of(CertificateInfo.STATUS_REGISTERED, CertificateInfo.STATUS_REGINPROG));
                if (certificateInfos.isEmpty()) {
                    throw new CertificateNotFoundException("No auth cert found");
                }
            }
        } catch (CertificateNotFoundException e) {
            return CertValidation.error(e.getErrorDeviation().code(), e.getErrorDeviation().metadata());
        }

        ErrorDeviation lastError = null;
        for (CertificateInfo certificateInfo : certificateInfos) {
            try {
                authCertVerifier.verify(certificateInfo);
                return CertValidation.ok(certificateInfo);

            } catch (InvalidCertificateException | TokenCertificateService.SignCertificateNotSupportedException e) {
                lastError = e.getErrorDeviation();
            }
        }

        Optional.ofNullable(lastError).orElseThrow(() -> new IllegalStateException("should not get here"));
        return CertValidation.error(lastError.code(), lastError.metadata());

    }

    private List<String> listOrEmpty(String s) {
        return (s == null || s.isEmpty()) ? List.of() : List.of(s);
    }

    private List<CertificateInfo> findAuthCerts(Set<String> allowedStatuses) throws TokenNotFoundException {
        return tokenService.getToken(SOFTWARE_TOKEN_ID).getKeyInfo().stream()
                .filter(keyInfo -> KeyUsageInfo.AUTHENTICATION.equals(keyInfo.getUsage()))
                .flatMap(keyInfo -> keyInfo.getCerts().stream())
                .filter(certInfo -> allowedStatuses.contains(certInfo.getStatus()))
                .toList();
    }

    private static final class CertValidation {
        final String errorCode;
        final List<String> metadata;
        final CertificateInfo certificateInfo;

        private CertValidation(String errorCode, List<String> metadata, CertificateInfo info) {
            this.errorCode = errorCode;
            this.metadata = metadata;
            this.certificateInfo = info;
        }

        static CertValidation ok(CertificateInfo info) {
            return new CertValidation(null, List.of(), info);
        }

        static CertValidation error(String errorCode, List<String> meta) {
            return new CertValidation(errorCode, meta, null);
        }

        boolean isOk() {
            return errorCode == null;
        }
    }

    record ConnectionConfig(String localInstance, String instance, String address, String directory) {
    }
}
