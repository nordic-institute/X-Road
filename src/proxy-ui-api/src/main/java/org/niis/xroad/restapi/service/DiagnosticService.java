/**
 * The MIT License
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

import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.SystemProperties;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.niis.xroad.restapi.dto.OcspResponderDiagnosticsStatus;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * diagnostic service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class DiagnosticService {
    private static final int HTTP_CLIENT_TIMEOUT_MS = 60000;
    private final String diagnosticsGlobalconfUrl;
    private final String diagnosticsTimestampingServicesUrl;
    private final String diagnosticsOcspRespondersUrl;

    @Autowired
    public DiagnosticService(@Value("${url.diagnostics-globalconf}") String diagnosticsGlobalconfUrl,
            @Value("${url.diagnostics-timestamping-services}") String diagnosticsTimestampingServicesUrl,
            @Value("${url.diagnostics-ocsp-responders}") String diagnosticsOcspRespondersUrl) {

        this.diagnosticsGlobalconfUrl = String.format(diagnosticsGlobalconfUrl,
                SystemProperties.getConfigurationClientAdminPort());
        this.diagnosticsTimestampingServicesUrl = String.format(diagnosticsTimestampingServicesUrl,
                PortNumbers.ADMIN_PORT);
        this.diagnosticsOcspRespondersUrl = String.format(diagnosticsOcspRespondersUrl,
                SystemProperties.getSignerAdminPort());
    }

    /**
     * Query global configuration status from admin port over HTTP.
     * @return
     */
    public DiagnosticsStatus queryGlobalConfStatus() {
        try {
            JsonObject json = sendGetRequest(diagnosticsGlobalconfUrl);
            return new Gson().fromJson(json, DiagnosticsStatus.class);
        } catch (DiagnosticRequestException e) {
            throw new DeviationAwareRuntimeException(e, e.getErrorDeviation());
        }
    }

    /**
     * Query timestamping services status from admin port over HTTP.
     * @return
     */
    public List<DiagnosticsStatus> queryTimestampingStatus() {
        try {
            JsonObject json = sendGetRequest(diagnosticsTimestampingServicesUrl);
            return json.entrySet().stream().filter(e -> e.getValue() instanceof JsonObject)
                    .map(this::parseTimestampingStatus).collect(Collectors.toList());
        } catch (DiagnosticRequestException e) {
            throw new DeviationAwareRuntimeException(e, e.getErrorDeviation());
        }
    }

    /**
     * Query ocsp responders status from admin port over HTTP.
     * @return
     */
    public List<OcspResponderDiagnosticsStatus> queryOcspResponderStatus() {
        try {
            JsonObject json = sendGetRequest(diagnosticsOcspRespondersUrl);
            JsonObject certificationServiceStatusMap = json.getAsJsonObject("certificationServiceStatusMap");
            return certificationServiceStatusMap.entrySet().stream().filter(e -> e.getValue() instanceof JsonObject)
                    .map(this::parseOcspResponderDiagnosticsStatus).collect(Collectors.toList());
        } catch (DiagnosticRequestException e) {
            throw new DeviationAwareRuntimeException(e, e.getErrorDeviation());
        }
    }

    /**
     * Send HTTP GET request to the given address (http://localhost:{port}/{path}).
     * @param address
     * @return
     * @throws DiagnosticRequestException if sending a diagnostics requests fails or an error is returned
     */
    private JsonObject sendGetRequest(String address) throws DiagnosticRequestException {
        HttpGet request = new HttpGet(address);
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(HTTP_CLIENT_TIMEOUT_MS)
                .setConnectionRequestTimeout(HTTP_CLIENT_TIMEOUT_MS)
                .setSocketTimeout(HTTP_CLIENT_TIMEOUT_MS).build();

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
                CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity resEntity = response.getEntity();
            if (response.getStatusLine().getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR.value()
                    || resEntity == null) {
                log.error("unable to get a response");
                throw new DiagnosticRequestException();
            }
            String responseStr = IOUtils.toString(resEntity.getContent(), "UTF-8");
            return JsonParser.parseString(responseStr).getAsJsonObject();
        } catch (IOException ioe) {
            log.error("unable to connect to admin port (" + address + ")");
            throw new DiagnosticRequestException();
        }
    }

    /**
     * Parse DiagnosticsStatus representing a timestamping service diagnostics status
     * @param entry
     * @return
     */
    private DiagnosticsStatus parseTimestampingStatus(Map.Entry<String, JsonElement> entry) {
        DiagnosticsStatus diagnosticsStatus = new Gson().fromJson(entry.getValue(), DiagnosticsStatus.class);
        diagnosticsStatus.setDescription(entry.getKey());
        return diagnosticsStatus;
    }

    /**
     * Parse parse OcspResponderDiagnosticsStatus representing a certificate authority including the ocsp services
     * of the certificate authority
     * @param entry
     * @return
     */
    private OcspResponderDiagnosticsStatus parseOcspResponderDiagnosticsStatus(
            Map.Entry<String, JsonElement> entry) {
        JsonObject ca = entry.getValue().getAsJsonObject();
        OcspResponderDiagnosticsStatus status = new OcspResponderDiagnosticsStatus(
                ca.get("name").getAsString());
        JsonObject ocspResponderStatusMap = ca.get("ocspResponderStatusMap").getAsJsonObject();
        List<DiagnosticsStatus> statuses = ocspResponderStatusMap.entrySet().stream()
                .filter(e -> e.getValue() instanceof JsonObject)
                .map(this::parseOcspResponderStatus)
                .collect(Collectors.toList());
        status.setOcspResponderStatusMap(statuses);
        return status;
    }

    private DiagnosticsStatus parseOcspResponderStatus(Map.Entry<String, JsonElement> entry) {
        JsonObject json = entry.getValue().getAsJsonObject();
        // Parse "prevUpdate" and "nextUpdate" properties
        DiagnosticsStatus temp = new Gson().fromJson(json, DiagnosticsStatus.class);
        // Create a new update using parsed values and return it as a result
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(json.get("status").getAsInt(),
                temp.getPrevUpdate(), temp.getNextUpdate());
        diagnosticsStatus.setDescription(json.get("url").getAsString());
        return diagnosticsStatus;
    }

    /**
     * Thrown when trying to send a diagnostic request
     */
    public static class DiagnosticRequestException extends ServiceException {
        public static final String DIAGNOSTIC_REQUEST_FAILED = "diagnostic_request_failed";

        public DiagnosticRequestException() {
            super(new ErrorDeviation(DIAGNOSTIC_REQUEST_FAILED));
        }
    }
}
