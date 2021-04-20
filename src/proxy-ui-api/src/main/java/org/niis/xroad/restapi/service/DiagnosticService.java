/**
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.CertificationServiceDiagnostics;
import ee.ria.xroad.common.CertificationServiceStatus;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.OcspResponderStatus;
import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.dto.OcspResponderDiagnosticsStatus;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
    private static final int HTTP_CLIENT_TIMEOUT_MS = 60000;
    private final RestTemplate restTemplate;
    private final String diagnosticsGlobalconfUrl;
    private final String diagnosticsTimestampingServicesUrl;
    private final String diagnosticsOcspRespondersUrl;

    @Autowired
    public DiagnosticService(@Value("${url.diagnostics-globalconf}") String diagnosticsGlobalconfUrl,
            @Value("${url.diagnostics-timestamping-services}") String diagnosticsTimestampingServicesUrl,
            @Value("${url.diagnostics-ocsp-responders}") String diagnosticsOcspRespondersUrl,
            RestTemplateBuilder restTemplateBuilder) {

        this.diagnosticsGlobalconfUrl = String.format(diagnosticsGlobalconfUrl,
                SystemProperties.getConfigurationClientAdminPort());
        this.diagnosticsTimestampingServicesUrl = String.format(diagnosticsTimestampingServicesUrl,
                PortNumbers.ADMIN_PORT);
        this.diagnosticsOcspRespondersUrl = String.format(diagnosticsOcspRespondersUrl,
                SystemProperties.getSignerAdminPort());
        this.restTemplate = restTemplateBuilder
                .setReadTimeout(Duration.ofMillis(HTTP_CLIENT_TIMEOUT_MS))
                .additionalMessageConverters(new ByteArrayHttpMessageConverter())
                .build();
    }

    /**
     * Query global configuration status from admin port over HTTP.
     *
     * @return
     */
    public DiagnosticsStatus queryGlobalConfStatus() throws IOException {
        try {
            byte[] response = sendGetRequest(diagnosticsGlobalconfUrl);

            return JsonUtils
                    .getObjectReader()
                    .forType(DiagnosticsStatus.class)
                    .readValue(response);
        } catch (DiagnosticRequestException e) {
            throw new DeviationAwareRuntimeException(e, e.getErrorDeviation());
        }
    }

    /**
     * Query timestamping services status from admin port over HTTP.
     *
     * @return
     */
    public List<DiagnosticsStatus> queryTimestampingStatus() throws IOException {
        log.info("Query timestamper status");
        try {
            TypeReference<Map<String, DiagnosticsStatus>> paramType =
                    new TypeReference<Map<String, DiagnosticsStatus>>() {
                    };
            byte[] response = sendGetRequest(diagnosticsTimestampingServicesUrl);
            Map<String, DiagnosticsStatus> diagnosticsStatusMap = JsonUtils
                    .getObjectReader()
                    .forType(paramType)
                    .readValue(response);

            return diagnosticsStatusMap.entrySet().stream()
                    .map(diagnosticsStatusEntry -> {
                        DiagnosticsStatus diagnosticsStatus = diagnosticsStatusEntry.getValue();
                        diagnosticsStatus.setDescription(diagnosticsStatusEntry.getKey());
                        return diagnosticsStatus;
                    }).collect(Collectors.toList());
        } catch (DiagnosticRequestException e) {
            throw new DeviationAwareRuntimeException(e, e.getErrorDeviation());
        }
    }

    /**
     * Query ocsp responders status from admin port over HTTP.
     *
     * @return
     */
    public List<OcspResponderDiagnosticsStatus> queryOcspResponderStatus() throws IOException {
        log.info("Query OCSP status");
        try {
            byte[] response = sendGetRequest(diagnosticsOcspRespondersUrl);
            CertificationServiceDiagnostics certificationServiceDiagnostics = JsonUtils
                    .getObjectReader()
                    .forType(CertificationServiceDiagnostics.class)
                    .readValue(response);

            return certificationServiceDiagnostics
                    .getCertificationServiceStatusMap()
                    .entrySet()
                    .stream()
                    .map(this::parseOcspResponderDiagnosticsStatus)
                    .collect(Collectors.toList());
        } catch (DiagnosticRequestException e) {
            throw new DeviationAwareRuntimeException(e, e.getErrorDeviation());
        }
    }

    /**
     * Send HTTP GET request to the given address (http://localhost:{port}/{path}).
     *
     * @param address
     * @return the response as byte[]
     * @throws DiagnosticRequestException if sending a diagnostics requests fails or an error is returned
     */
    private byte[] sendGetRequest(String address) throws DiagnosticRequestException {
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(address, byte[].class);

            if (response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                    || response.getBody() == null) {
                log.error("unable to get a response");
                throw new DiagnosticRequestException();
            }

            return response.getBody();
        } catch (RestClientException e) {
            log.error("unable to connect to admin port (" + address + ")");
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
    public static class DiagnosticRequestException extends ServiceException {
        public DiagnosticRequestException() {
            super(new ErrorDeviation(ERROR_DIAGNOSTIC_REQUEST_FAILED));
        }
    }
}
