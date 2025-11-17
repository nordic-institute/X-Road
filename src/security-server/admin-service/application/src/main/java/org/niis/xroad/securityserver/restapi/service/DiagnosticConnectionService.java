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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.dto.ConnectionStatus;
import org.niis.xroad.common.core.dto.DownloadUrlConnectionStatus;
import org.niis.xroad.common.core.exception.ErrorDeviation;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confclient.proto.CheckAndGetConnectionStatusRequest;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.util.AuthCertVerifier;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_REQUEST;
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

    private final GlobalConfProvider globalConfProvider;
    private final TokenService tokenService;
    private final AuthCertVerifier authCertVerifier;
    private final ManagementRequestSenderService managementRequestSenderService;
    private final ConfClientRpcClient confClientRpcClient;

    public List<DownloadUrlConnectionStatus> getGlobalConfStatus() {
        return globalConfProvider.findSourceAddresses().stream()
                .flatMap(this::configsForAddress)
                .distinct()
                .map(this::checkConnection)
                .map(this::toDownloadStatus)
                .toList();
    }

    private Stream<ConnectionConfig> configsForAddress(String address) {
        return Stream.of(HTTP, HTTPS)
                .map(protocol -> new ConnectionConfig(protocol, address, portFor(protocol)));
    }

    private int portFor(String protocol) {
        return HTTP.equals(protocol) ? PORT_80 : PORT_443;
    }

    private org.niis.xroad.rpc.common.DownloadUrlConnectionStatus checkConnection(ConnectionConfig c) {
        return confClientRpcClient.checkAndGetConnectionStatus(
                CheckAndGetConnectionStatusRequest.newBuilder()
                        .setProtocol(c.protocol())
                        .setAddress(c.address())
                        .setPort(c.port())
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
            return certValidation.isOk() ? ConnectionStatus.error(e.getErrorCode(), listOrEmpty(e.getFaultString()))
                    : ConnectionStatus.fromErrorAndValidation(
                    e.getErrorCode(),
                    listOrEmpty(e.getDetails()),
                    certValidation.errorCode, certValidation.metadata);
        }
    }

    private boolean isExpectedInvalidRequest(XrdRuntimeException e) {
        return INVALID_REQUEST.code().equals(e.getErrorCode());
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

    record ConnectionConfig(String protocol, String address, int port) {
    }
}
