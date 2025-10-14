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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.dto.ConnectionStatus;
import org.niis.xroad.common.core.dto.DownloadUrlConnectionStatus;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.util.HttpUrlConnectionConfigurer;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.util.AuthCertVerifier;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static org.niis.xroad.securityserver.restapi.service.PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID;

@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class DiagnosticConnectionService {
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final Integer PORT_80 = 80;
    private static final Integer PORT_443 = 443;
    static final String INVALID_SERVER_ADDRESS = "Invalid server address";

    private final GlobalConfProvider globalConfProvider;
    private final TokenService tokenService;
    private final AuthCertVerifier authCertVerifier;
    private final ManagementRequestSenderService managementRequestSenderService;
    private final HttpUrlConnectionConfigurer connectionConfigurer = new HttpUrlConnectionConfigurer();

    public List<DownloadUrlConnectionStatus> getGlobalConfStatusInfo() {
        Set<String> addresses = globalConfProvider.findSourceAddresses();

        return addresses.stream()
                .flatMap(address -> Stream.of(
                        getUrl(HTTP, address, PORT_80),
                        getUrl(HTTPS, address, PORT_443)
                ))
                .distinct()
                .map(this::checkAndGetConnectionStatus)
                .toList();
    }

    private static String getCenterInternalDirectory() {
        return SystemProperties.getCenterInternalDirectory();
    }

    private URL getUrl(String protocol, String address, int port) {
        try {
            return URI.create(getDownloadUrl(protocol, address, port)).toURL();
        } catch (MalformedURLException e) {
            log.error("Could not create URL from address {}", address, e);
        }
        return null;
    }

    private String getDownloadUrl(String protocol, String address, int port) {
        return String.format("%s://%s:%d/%s", protocol, address, port, getCenterInternalDirectory());
    }

    private String getDownloadUrl(URL url) {
        return getDownloadUrl(url.getProtocol(), url.getHost(), url.getPort());
    }

    private DownloadUrlConnectionStatus checkAndGetConnectionStatus(URL url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connectionConfigurer.apply(connection);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return DownloadUrlConnectionStatus.ok(getDownloadUrl(url));
            } else {
                var responseMessage = connection.getResponseMessage() != null ? connection.getResponseMessage() : "";
                throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_GET_VERSION_FAILED)
                        .details(String.format("%s — HTTP %d %s", getDownloadUrl(url), responseCode, responseMessage))
                        .build();
            }
        } catch (Exception e) {
            XrdRuntimeException result = XrdRuntimeException.systemException(e);
            return DownloadUrlConnectionStatus.error(getDownloadUrl(url), result.getErrorCode(), List.of(result.getDetails()));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public ConnectionStatus getAuthCertRegStatusInfo() {
        CertificateInfo certificateInfo = null;
        String certErrorCode = null;
        List<String> certValidationMetadata = new ArrayList<>();

        try {
            certificateInfo = getAuthCert().orElseThrow(() -> new CertificateNotFoundException("No active auth cert found"));
            authCertVerifier.verify(certificateInfo);
        } catch (CertificateNotFoundException | InvalidCertificateException | TokenCertificateService.SignCertificateNotSupportedException
                 | KeyNotFoundException | ActionNotPossibleException e) {
            certErrorCode = e.getErrorDeviation().code();
            certValidationMetadata = e.getErrorDeviation().metadata();
        }

        try {
            // if no certificate, the error is expected, but we want to verify that the connection can be established
            byte[] bytes = (certificateInfo != null) ? certificateInfo.getCertificateBytes() : new byte[0];
            managementRequestSenderService.sendAuthCertRegisterRequest(null, bytes, true);
        } catch (GlobalConfOutdatedException e) {
            return ConnectionStatus.fromErrorAndValidation(e.getErrorDeviation().code(), e.getErrorDeviation().metadata(), certErrorCode,
                    certValidationMetadata);
        } catch (XrdRuntimeException e) {
            return ConnectionStatus.fromErrorAndValidation(e.getErrorCode(), e.getDetails() != null ? List.of(e.getDetails()) : List.of(),
                    certErrorCode,
                    certValidationMetadata);
        } catch (CodedException e) {
            // special case: if no certificate or address validation error, the error is expected,
            // and we return only certificate validation exceptions (if any)
            if ((X_INVALID_REQUEST.equals(e.getFaultCode()) || "InvalidRequest".equals(e.getFaultCode()))
                    && (certificateInfo == null
                    || (e.getFaultString() != null && e.getFaultString().contains(INVALID_SERVER_ADDRESS)))) {
                return certErrorCode == null ? ConnectionStatus.ok() : ConnectionStatus.error(certErrorCode, certValidationMetadata);
            }
            return ConnectionStatus.fromErrorAndValidation(e.getFaultCode(),
                    e.getFaultString() != null ? List.of(e.getFaultString()) : List.of(), certErrorCode, certValidationMetadata);
        }
        return certErrorCode == null ? ConnectionStatus.ok() : ConnectionStatus.error(certErrorCode, certValidationMetadata);
    }

    private Optional<CertificateInfo> getAuthCert() throws CertificateNotFoundException {
        return tokenService.getToken(SOFTWARE_TOKEN_ID).getKeyInfo().stream()
                .filter(keyInfo -> keyInfo.getUsage() == KeyUsageInfo.AUTHENTICATION)
                .flatMap(keyInfo -> keyInfo.getCerts().stream())
                .filter(CertificateInfo::isActive)
                .findFirst();
    }
}
