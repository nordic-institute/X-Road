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
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;
import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.ProxyMemory;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.JsonUtils;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.globalconf.status.CertificationServiceDiagnostics;
import org.niis.xroad.globalconf.status.CertificationServiceStatus;
import org.niis.xroad.globalconf.status.OcspResponderStatus;
import org.niis.xroad.opmonitor.api.OperationalDataInterval;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.securityserver.restapi.dto.OcspResponderDiagnosticsStatus;
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

import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.DIAGNOSTIC_REQUEST_FAILED;

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
    private final String diagnosticsGlobalconfUrl;
    private final String diagnosticsTimestampingServicesUrl;
    private final String diagnosticsOcspRespondersUrl;
    private final String diagnosticsAddOnStatusUrl;
    private final String backupEncryptionStatusUrl;
    private final String messageLogEncryptionStatusUrl;
    private final String proxyMemoryUsageUrl;
    private final OpMonitorClient opMonitorClient;

    @Autowired
    public DiagnosticService(
            @Value("${url.diagnostics-globalconf}") String diagnosticsGlobalconfUrl,
            @Value("${url.diagnostics-timestamping-services}") String diagnosticsTimestampingServicesUrl,
            @Value("${url.diagnostics-ocsp-responders}") String diagnosticsOcspRespondersUrl,
            @Value("${url.diagnostics-addon-status}") String diagnosticsAddOnStatusUrl,
            @Value("${url.diagnostics-backup-encryption-status}") String backupEncryptionStatusUrl,
            @Value("${url.diagnostics-message-log-encryption-status}") String messageLogEncryptionStatusUrl,
            @Value("${url.diagnostics-proxy-memory-usage}") String proxyMemoryUsageUrl,
            RestTemplateBuilder restTemplateBuilder,
            OpMonitorClient opMonitorClient) {

        this.diagnosticsGlobalconfUrl = String.format(diagnosticsGlobalconfUrl,
                SystemProperties.getConfigurationClientAdminPort());
        this.diagnosticsTimestampingServicesUrl = String.format(diagnosticsTimestampingServicesUrl,
                PortNumbers.ADMIN_PORT);
        this.diagnosticsOcspRespondersUrl = String.format(diagnosticsOcspRespondersUrl,
                SystemProperties.getSignerAdminPort());
        this.diagnosticsAddOnStatusUrl = String.format(diagnosticsAddOnStatusUrl, PortNumbers.ADMIN_PORT);
        this.backupEncryptionStatusUrl = String.format(backupEncryptionStatusUrl,
                PortNumbers.ADMIN_PORT);
        this.messageLogEncryptionStatusUrl = String.format(messageLogEncryptionStatusUrl,
                PortNumbers.ADMIN_PORT);
        this.proxyMemoryUsageUrl = String.format(proxyMemoryUsageUrl,
                PortNumbers.ADMIN_PORT);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(
                JsonUtils.getObjectMapperCopy());
        List<MediaType> mediaTypes = new ArrayList<>(converter.getSupportedMediaTypes());
        mediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
        converter.setSupportedMediaTypes(mediaTypes);
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofMillis(HTTP_CONNECT_TIMEOUT_MS))
                .readTimeout(Duration.ofMillis(HTTP_CLIENT_TIMEOUT_MS))
                .messageConverters(converter)
                .build();
        this.opMonitorClient = opMonitorClient;
    }

    /**
     * Query global configuration status from admin port over HTTP.
     * @return
     */
    public DiagnosticsStatus queryGlobalConfStatus() {
        ResponseEntity<DiagnosticsStatus> response = sendGetRequest(diagnosticsGlobalconfUrl,
                DiagnosticsStatus.class);

        return response.getBody();
    }

    /**
     * Query timestamping services status from admin port over HTTP.
     * @return
     */
    public Set<DiagnosticsStatus> queryTimestampingStatus() {
        log.info("Query timestamper status");

        ResponseEntity<TimestampingStatusResponse> response = sendGetRequest(diagnosticsTimestampingServicesUrl,
                TimestampingStatusResponse.class);

        return Objects.requireNonNull(response.getBody())
                .entrySet().stream()
                .map(diagnosticsStatusEntry -> {
                    DiagnosticsStatus diagnosticsStatus = diagnosticsStatusEntry.getValue();
                    diagnosticsStatus.setDescription(diagnosticsStatusEntry.getKey());
                    return diagnosticsStatus;
                }).collect(Collectors.toSet());

    }

    /**
     * Query ocsp responders status from admin port over HTTP.
     * @return
     */
    public List<OcspResponderDiagnosticsStatus> queryOcspResponderStatus() {
        log.info("Query OCSP status");

        ResponseEntity<CertificationServiceDiagnostics> response = sendGetRequest(diagnosticsOcspRespondersUrl,
                CertificationServiceDiagnostics.class);

        return Objects.requireNonNull(response.getBody())
                .getCertificationServiceStatusMap()
                .entrySet()
                .stream()
                .map(this::parseOcspResponderDiagnosticsStatus)
                .collect(Collectors.toList());

    }

    /**
     * Query proxy addons status from admin port over HTTP.
     * @return
     */
    public AddOnStatusDiagnostics queryAddOnStatus() {

        return sendGetRequest(diagnosticsAddOnStatusUrl, AddOnStatusDiagnostics.class).getBody();

    }

    /**
     * Query proxy backup encryption status from admin port over HTTP.
     * @return BackupEncryptionStatusDiagnostics
     */
    public BackupEncryptionStatusDiagnostics queryBackupEncryptionStatus() {

        return sendGetRequest(backupEncryptionStatusUrl, BackupEncryptionStatusDiagnostics.class).getBody();

    }

    /**
     * Query proxy message log encryption status from admin port over HTTP.
     * @return MessageLogEncryptionStatusDiagnostics
     */
    public MessageLogEncryptionStatusDiagnostics queryMessageLogEncryptionStatus() {
        return sendGetRequest(messageLogEncryptionStatusUrl, MessageLogEncryptionStatusDiagnostics.class).getBody();

    }

    /**
     * Query proxy memory usage from admin port over HTTP.
     * @return ProxyMemory
     */
    public ProxyMemory queryProxyMemoryUsage() {
        return sendGetRequest(proxyMemoryUsageUrl, ProxyMemory.class).getBody();
    }

    /**
     * Send HTTP GET request to the given address (http://localhost:{port}/{path}).
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
                    DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(ocspResponderStatus.getDiagnosticStatus(),
                            ocspResponderStatus.getPrevUpdate(), ocspResponderStatus.getNextUpdate(), ocspResponderStatus.getErrorCode());
                    diagnosticsStatus.setDescription(ocspResponderStatus.getUrl());
                    return diagnosticsStatus;
                })
                .collect(Collectors.toList());
        status.setOcspResponderStatusMap(statuses);

        return status;
    }

    public List<OperationalDataInterval> getOperationalDataIntervals(Long recordsFromTimestamp,
                                                                     Long recordsToTimestamp,
                                                                     Integer interval,
                                                                     String securityServerType,
                                                                     ClientId memberId,
                                                                     ServiceId serviceId) {
        return opMonitorClient.getOperationalDataIntervals(recordsFromTimestamp,
                recordsToTimestamp,
                interval,
                securityServerType,
                memberId,
                serviceId);
    }

    /**
     * Thrown when trying to send a diagnostic request
     */
    public static final class DiagnosticRequestException extends InternalServerErrorException {
        public DiagnosticRequestException() {
            super(DIAGNOSTIC_REQUEST_FAILED.build());
        }
    }

    private static final class TimestampingStatusResponse extends HashMap<String, DiagnosticsStatus> {
    }
}
