/*
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ConnectionStatus;
import ee.ria.xroad.common.DiagnosticStatus;
import ee.ria.xroad.common.DownloadUrlConnectionStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.util.HttpUrlConnectionConfigurer;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static org.niis.xroad.securityserver.restapi.service.TokenCertificateService.verifyAuthCert;

@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class DiagnosticConnectionService {
    private static final Integer E200 = 200;
    private static final Integer E300 = 300;
    private static final String TEST_ADDRESS = "TEST_ADDRESS";

    private final GlobalConfProvider globalConfProvider;
    private final TokenService tokenService;
    private final ManagementRequestSenderService managementRequestSenderService;
    private final HttpUrlConnectionConfigurer connectionConfigurer = new HttpUrlConnectionConfigurer();

    public List<DownloadUrlConnectionStatus> getGlobalConfStatusInfo() {
        List<DownloadUrlConnectionStatus> statusList = new ArrayList<>();
        List<String> addresses = globalConfProvider.findSourcesAddress();
        List<URL> urls = new ArrayList<>(addresses.stream()
                .map(address -> getUrl("http", address, "80"))
                .toList());
        urls.addAll(addresses.stream()
                .map(address -> getUrl("https", address, "443"))
                .toList());

        for (URL url : urls) {
            statusList.add(checkVersionLocationExists(url));
        }
        return statusList;
    }

    private URL getUrl(String httpType, String address, String port) {
        try {
            return URI.create(httpType + "://" + address + ":" + port + "/internalconf").toURL();
        } catch (MalformedURLException e) {
            log.error("Could not create URL from address {}", address, e);
        }
        return null;
    }

    private DownloadUrlConnectionStatus checkVersionLocationExists(URL url) {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) url.openConnection();
            connectionConfigurer.apply(connection);

            int responseCode = connection.getResponseCode();

            InputStream inputStream;
            if (responseCode == E200) {
                globalConfProvider.verifyValidity();
                return new DownloadUrlConnectionStatus(getDownloadUrl(url), new ConnectionStatus(DiagnosticStatus.OK));
            } else if (responseCode > E200 && responseCode < E300) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }

            if (inputStream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line).append("\n");
                    }
                    return getGlobalConfConnectionStatus(url, "HTTP" + responseCode, List.of(response.toString()));
                }
            } else {
                return null;
            }
        } catch (Exception e) {
            XrdRuntimeException result = XrdRuntimeException.systemException(e);
            return getGlobalConfConnectionStatus(url, result.getErrorCode(), List.of(result.getDetails()));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private DownloadUrlConnectionStatus getGlobalConfConnectionStatus(URL url, String errorCode, List<String> metadata) {
        return errorCode == null ? new DownloadUrlConnectionStatus(getDownloadUrl(url), new ConnectionStatus(DiagnosticStatus.OK))
                : new DownloadUrlConnectionStatus(getDownloadUrl(url), new ConnectionStatus(DiagnosticStatus.ERROR, errorCode, metadata));
    }

    private String getDownloadUrl(URL url) {
        return url.getProtocol() + "://" + url.getHost() + ":" + url.getPort() + "/internalconf";
    }

    public ConnectionStatus getAuthCertRegStatusInfo() {
        CertificateInfo cert = null;
        String certErrorCode = null;
        List<String> certValidationMetadata = new ArrayList<>();

        try {
            cert = getAuthCert().orElseThrow(() -> new CertificateNotFoundException("No active auth cert found"));
            verifyAuthCert(cert);
        } catch (CertificateNotFoundException | InvalidCertificateException | TokenCertificateService.SignCertificateNotSupportedException
                 | KeyNotFoundException | ActionNotPossibleException e) {
            certErrorCode = e.getErrorDeviation().code();
            certValidationMetadata = e.getErrorDeviation().metadata();
        }

        try {
            byte[] bytes = (cert != null) ? cert.getCertificateBytes() : new byte[0];
            managementRequestSenderService.sendAuthCertRegisterRequest(TEST_ADDRESS, bytes, true);
        } catch (GlobalConfOutdatedException e) {
            return getConnectionStatus(e.getErrorDeviation().code(), e.getErrorDeviation().metadata(), certErrorCode,
                    certValidationMetadata);
        } catch (XrdRuntimeException e) {
            return getConnectionStatus(e.getErrorCode(), List.of(e.getDetails()), certErrorCode, certValidationMetadata);
        } catch (CodedException e) {
            // special case: if no cert, the error is expected, and we return only cert validation result
            if (cert == null && X_INVALID_REQUEST.equals(e.getFaultCode())) {
                return getConnectionStatus(certErrorCode, certValidationMetadata);
            }
            return getConnectionStatus(e.getFaultCode(), List.of(e.getFaultString()), certErrorCode, certValidationMetadata);
        }

        return getConnectionStatus(certErrorCode, certValidationMetadata);
    }

    private ConnectionStatus getConnectionStatus(String errorCode, List<String> metadata) {
        return errorCode == null ? new ConnectionStatus(DiagnosticStatus.OK)
                : new ConnectionStatus(DiagnosticStatus.ERROR, errorCode, metadata);
    }

    private ConnectionStatus getConnectionStatus(String errorCode, List<String> metadata, String certErrorCode, List<String> certMetadata) {
        return certErrorCode == null ? new ConnectionStatus(DiagnosticStatus.ERROR, errorCode, metadata)
                : new ConnectionStatus(DiagnosticStatus.ERROR, errorCode, metadata, certErrorCode, certMetadata);
    }


    private Optional<CertificateInfo> getAuthCert() throws CertificateNotFoundException {
        return tokenService.getToken("0").getKeyInfo().stream()
                .filter(keyInfo -> keyInfo.getUsage() == KeyUsageInfo.AUTHENTICATION)
                .flatMap(keyInfo2 -> keyInfo2.getCerts().stream())
                .filter(CertificateInfo::isActive)
                // .map(CertificateInfo::getCertificateBytes)
                .findFirst();
    }
}

