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
import ee.ria.xroad.common.SystemProperties;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * diagnostic service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class DiagnosticService {
    private static final String DIAGNOSTICS_BASE_URL = "http://localhost";
    private static final int CONF_CLIENT_ADMIN_PORT = SystemProperties.getConfigurationClientAdminPort();
    private static final String CONF_CLIENT_ADMIN_PATH = "status";

    public DiagnosticsStatus queryGlobalConfStatus() {
        try {
            JsonObject json = sendGetRequest(buildUri(CONF_CLIENT_ADMIN_PORT, CONF_CLIENT_ADMIN_PATH));
            return new Gson().fromJson(json, DiagnosticsStatus.class);
        } catch (DiagnosticRequestException e) {
            throw new DeviationAwareRuntimeException(e, e.getErrorDeviation());
        }
    }

    private JsonObject sendGetRequest(String address) throws DiagnosticRequestException {
        HttpGet request = new HttpGet(address);

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
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

    private String buildUri(int port, String path) {
        StringBuilder sb = new StringBuilder(DIAGNOSTICS_BASE_URL);
        sb.append(":").append(port).append("/").append(path);
        return sb.toString();
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
