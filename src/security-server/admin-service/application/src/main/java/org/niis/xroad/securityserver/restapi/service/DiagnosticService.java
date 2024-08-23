/*
 * The MIT License
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.AddOnStatusDiagnostics;
import ee.ria.xroad.common.BackupEncryptionStatusDiagnostics;
import ee.ria.xroad.common.CertificationServiceDiagnostics;
import ee.ria.xroad.common.CertificationServiceStatus;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;
import ee.ria.xroad.common.OcspResponderStatus;
import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.JsonUtils;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.proxy.proto.ProxyRpcClient;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.service.ServiceException;
import org.niis.xroad.securityserver.restapi.dto.OcspResponderDiagnosticsStatus;
import org.niis.xroad.securityserver.restapi.facade.SignerProxyFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED;

/**
 * diagnostic service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class DiagnosticService {
    private static final int HTTP_CONNECT_TIMEOUT_MS = 1000;
    private static final int HTTP_CLIENT_TIMEOUT_MS = 60000;
    private final RestTemplate restTemplate;
    private final SignerProxyFacade signerProxyFacade;
    private final ProxyRpcClient proxyRpcClient;
    private final String diagnosticsGlobalconfUrl;
    private final String diagnosticsTimestampingServicesUrl;

    @Autowired
    public DiagnosticService(
            SignerProxyFacade signerProxyFacade,
            ProxyRpcClient proxyRpcClient,
            @Value("${url.diagnostics-globalconf}") String diagnosticsGlobalconfUrl,
            @Value("${url.diagnostics-timestamping-services}") String diagnosticsTimestampingServicesUrl,
            RestTemplateBuilder restTemplateBuilder) {

        this.signerProxyFacade = signerProxyFacade;
        this.proxyRpcClient = proxyRpcClient;
        this.diagnosticsGlobalconfUrl = String.format(diagnosticsGlobalconfUrl,
                SystemProperties.getConfigurationClientAdminPort());
        this.diagnosticsTimestampingServicesUrl = String.format(diagnosticsTimestampingServicesUrl,
                PortNumbers.ADMIN_PORT);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(
                JsonUtils.getObjectMapperCopy());
        List<MediaType> mediaTypes = new ArrayList<>(converter.getSupportedMediaTypes());
        mediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
        converter.setSupportedMediaTypes(mediaTypes);
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(HTTP_CONNECT_TIMEOUT_MS))
                .setReadTimeout(Duration.ofMillis(HTTP_CLIENT_TIMEOUT_MS))
                .messageConverters(converter)
                .build();
    }

    /**
     * Query global configuration status from admin port over HTTP.
     *
     * @return
     */
    public DiagnosticsStatus queryGlobalConfStatus() {
        try {
            ResponseEntity<DiagnosticsStatus> response = sendGetRequest(diagnosticsGlobalconfUrl,
                    DiagnosticsStatus.class);

            return response.getBody();
        } catch (DiagnosticRequestException e) {
            throw new DeviationAwareRuntimeException(e, e.getErrorDeviation());
        }
    }

    /**
     * Query timestamping services status from admin port over HTTP.
     *
     * @return
     */
    public Set<DiagnosticsStatus> queryTimestampingStatus() {
        log.info("Query timestamper status");
        try {
            ResponseEntity<TimestampingStatusResponse> response = sendGetRequest(diagnosticsTimestampingServicesUrl,
                    TimestampingStatusResponse.class);

            return Objects.requireNonNull(response.getBody())
                    .entrySet().stream()
                    .map(diagnosticsStatusEntry -> {
                        DiagnosticsStatus diagnosticsStatus = diagnosticsStatusEntry.getValue();
                        diagnosticsStatus.setDescription(diagnosticsStatusEntry.getKey());
                        return diagnosticsStatus;
                    }).collect(Collectors.toSet());
        } catch (DiagnosticRequestException e) {
            throw new DeviationAwareRuntimeException(e, e.getErrorDeviation());
        }
    }

    /**
     * Query ocsp responders status from admin port over HTTP.
     *
     * @return
     */
    public List<OcspResponderDiagnosticsStatus> queryOcspResponderStatus() {
        log.info("Query OCSP status");
        try {
            CertificationServiceDiagnostics response = signerProxyFacade.getCertificationServiceDiagnostics();

            return Objects.requireNonNull(response)
                    .getCertificationServiceStatusMap()
                    .entrySet()
                    .stream()
                    .map(this::parseOcspResponderDiagnosticsStatus)
                    .toList();
        } catch (Exception e) {
            throw new DeviationAwareRuntimeException(e.getMessage(),
                    new ErrorDeviation(ERROR_DIAGNOSTIC_REQUEST_FAILED));
        }
    }

    /**
     * Query proxy addons status from admin port over HTTP.
     *
     * @return AddOnStatusDiagnostics
     */
    public AddOnStatusDiagnostics queryAddOnStatus() {
        try {
            return proxyRpcClient.getAddOnStatus();
        } catch (Exception e) {
            throw new DeviationAwareRuntimeException(e, new ErrorDeviation(ERROR_DIAGNOSTIC_REQUEST_FAILED));
        }
    }

    /**
     * Query proxy backup encryption status from admin port over HTTP.
     *
     * @return BackupEncryptionStatusDiagnostics
     */
    public BackupEncryptionStatusDiagnostics queryBackupEncryptionStatus() {
        try {
            return proxyRpcClient.getBackupEncryptionStatus();
        } catch (Exception e) {
            throw new DeviationAwareRuntimeException(e, new ErrorDeviation(ERROR_DIAGNOSTIC_REQUEST_FAILED));
        }
    }

    /**
     * Query proxy message log encryption status from admin port over HTTP.
     *
     * @return MessageLogEncryptionStatusDiagnostics
     */
    public MessageLogEncryptionStatusDiagnostics queryMessageLogEncryptionStatus() {
        try {
            return proxyRpcClient.getMessageLogEncryptionStatus();
        } catch (Exception e) {
            throw new DeviationAwareRuntimeException(e, new ErrorDeviation(ERROR_DIAGNOSTIC_REQUEST_FAILED));
        }
    }

    /**
     * Send HTTP GET request to the given address (http://localhost:{port}/{path}).
     *
     * @param address
     * @return ResponseEntity with the provided type
     * @throws DiagnosticRequestException if sending a diagnostics requests fails or an error is returned
     */
    private <T> ResponseEntity<T> sendGetRequest(String address, Class<T> clazz) throws DiagnosticRequestException {
        try {
            ResponseEntity<T> response = restTemplate.getForEntity(address, clazz);

            if (response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                    || response.getBody() == null) {
                log.error("unable to get a response");
                throw new DiagnosticRequestException();
            }

            return response;
        } catch (RestClientException e) {
            log.error("unable to connect to admin port (" + address + ")", e);
            throw new DiagnosticRequestException();
        }
    }

    /**
     * Parse parse OcspResponderDiagnosticsStatus representing a certificate authority including the ocsp services
     * of the certificate authority
     *
     * @param entry
     * @return
     */
    private OcspResponderDiagnosticsStatus parseOcspResponderDiagnosticsStatus(
            Map.Entry<String, CertificationServiceStatus> entry) {
        CertificationServiceStatus ca = entry.getValue();
        OcspResponderDiagnosticsStatus status = new OcspResponderDiagnosticsStatus(ca.getName());
        Map<String, OcspResponderStatus> ocspResponderStatusMap = ca.getOcspResponderStatusMap();
        List<DiagnosticsStatus> statuses = ocspResponderStatusMap.values().stream()
                .map(ocspResponderStatus -> {
                    DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(ocspResponderStatus.getStatus(),
                            ocspResponderStatus.getPrevUpdate(), ocspResponderStatus.getNextUpdate());
                    diagnosticsStatus.setDescription(ocspResponderStatus.getUrl());
                    return diagnosticsStatus;
                })
                .collect(Collectors.toList());
        status.setOcspResponderStatusMap(statuses);

        return status;
    }

    /**
     * Thrown when trying to send a diagnostic request
     */
    public static final class DiagnosticRequestException extends ServiceException {
        public DiagnosticRequestException() {
            super(new ErrorDeviation(ERROR_DIAGNOSTIC_REQUEST_FAILED));
        }
    }

    private static final class TimestampingStatusResponse extends HashMap<String, DiagnosticsStatus> {
    }
}
