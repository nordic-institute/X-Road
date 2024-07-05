/*
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
package ee.ria.xroad.proxy;

import ee.ria.xroad.common.AddOnStatusDiagnostics;
import ee.ria.xroad.common.BackupEncryptionStatusDiagnostics;
import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.DiagnosticsUtils;
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;
import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.util.AdminPort;
import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.common.util.healthcheck.HealthCheckPort;
import ee.ria.xroad.proxy.messagelog.MessageLog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.MimeTypes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ProxyAdminPortConfig {
    private static final int DIAGNOSTICS_CONNECTION_TIMEOUT_MS = 1200;
    private static final int DIAGNOSTICS_READ_TIMEOUT_MS = 15000; // 15 seconds

    private final AddOnStatusDiagnostics addOnStatus;
    private final BackupEncryptionStatusDiagnostics backupEncryptionStatusDiagnostics;
    private final MessageLogEncryptionStatusDiagnostics messageLogEncryptionStatusDiagnostics;
    private final Optional<HealthCheckPort> healthCheckPort;

    @Bean(initMethod = "start", destroyMethod = "stop")
    AdminPort createAdminPort() {
        AdminPort adminPort = new AdminPort(PortNumbers.ADMIN_PORT);

        addTimestampStatusHandler(adminPort);

        addMaintenanceHandler(adminPort);

        addClearCacheHandler(adminPort);

        addAddOnStatusHandler(adminPort);

        addBackupEncryptionStatus(adminPort);

        addMessageLogEncryptionStatus(adminPort);

        return adminPort;
    }

    private void addAddOnStatusHandler(AdminPort adminPort) {
        adminPort.addHandler("/addonstatus", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(RequestWrapper request, ResponseWrapper response) {
                writeJsonResponse(addOnStatus, response);
            }
        });
    }

    private void addBackupEncryptionStatus(AdminPort adminPort) {
        adminPort.addHandler("/backup-encryption-status", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(RequestWrapper request, ResponseWrapper response) {
                writeJsonResponse(backupEncryptionStatusDiagnostics, response);
            }
        });
    }

    private void addMessageLogEncryptionStatus(AdminPort adminPort) {
        adminPort.addHandler("/message-log-encryption-status", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(RequestWrapper request, ResponseWrapper response) {
                writeJsonResponse(messageLogEncryptionStatusDiagnostics, response);
            }
        });
    }

    private void addClearCacheHandler(AdminPort adminPort) {
        adminPort.addHandler("/clearconfcache", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(RequestWrapper request, ResponseWrapper response) {
                ServerConf.clearCache();
                try (var pw = new PrintWriter(response.getOutputStream())) {
                    response.setContentType(MimeTypes.Type.APPLICATION_JSON_UTF_8);
                    pw.println("Configuration cache cleared");
                }
            }
        });
    }

    private void addMaintenanceHandler(AdminPort adminPort) {
        adminPort.addHandler("/maintenance", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(RequestWrapper request, ResponseWrapper response) throws Exception {

                String result = "Invalid parameter 'targetState', request ignored";
                String param = request.getParameter("targetState");

                if (param != null && (param.equalsIgnoreCase("true") || param.equalsIgnoreCase("false"))) {
                    result = setHealthCheckMaintenanceMode(Boolean.parseBoolean(param));
                }
                try (var pw = new PrintWriter(response.getOutputStream())) {
                    response.setContentType(MimeTypes.Type.APPLICATION_JSON_UTF_8);
                    pw.println(result);
                }
            }
        });
    }

    /**
     * Diagnostics for timestamping.
     * First check the simple connection to timestamp server. If OK, check the status of the previous timestamp request.
     * If the previous request has failed or the simple connection cannot be made, DiagnosticsStatus tells the reason.
     */
    private void addTimestampStatusHandler(AdminPort adminPort) {
        adminPort.addHandler("/timestampstatus", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(RequestWrapper request, ResponseWrapper response) {
                log.info("/timestampstatus");

                Map<String, DiagnosticsStatus> statusesFromSimpleConnectionCheck = checkConnectionToTimestampUrl();
                Map<String, DiagnosticsStatus> result = new HashMap<>();

                log.info("simple connection check result {}", statusesFromSimpleConnectionCheck);

                try {
                    Map<String, DiagnosticsStatus> statusesFromLogManager = MessageLog.getDiagnosticStatus();

                    log.info("statusesFromLogManager {}", statusesFromLogManager.toString());

                    // go through all simple connection statuses, and enrich using LogManager status info
                    for (Map.Entry<String, DiagnosticsStatus> simpleConnectionUrlStatus
                            : statusesFromSimpleConnectionCheck.entrySet()) {

                        String timestamperUrl = simpleConnectionUrlStatus.getKey();
                        DiagnosticsStatus finalStatus = determineDiagnosticsStatus(timestamperUrl,
                                simpleConnectionUrlStatus.getValue(), statusesFromLogManager.get(timestamperUrl));
                        result.put(timestamperUrl, finalStatus);
                    }
                } catch (Exception e) {
                    log.error("Unable to connect to LogManager, immediate timestamping status unavailable", e);
                    result = statusesFromSimpleConnectionCheck;
                    transmuteErrorCodes(result, DiagnosticsErrorCodes.RETURN_SUCCESS,
                            DiagnosticsErrorCodes.ERROR_CODE_LOGMANAGER_UNAVAILABLE);
                }

                writeJsonResponse(result, response);
            }
        });
    }

    private void writeJsonResponse(Object jsonObj, ResponseWrapper response) {
        try (var writer = new PrintWriter(response.getOutputStream())) {
            response.setContentType(MimeTypes.Type.APPLICATION_JSON_UTF_8);
            JsonUtils.getObjectWriter().writeValue(writer, jsonObj);
        } catch (IOException e) {
            logResponseIOError(e);
        }
    }

    private String setHealthCheckMaintenanceMode(boolean targetState) {
        return healthCheckPort.map(port -> port.setMaintenanceMode(targetState))
                .orElse("No HealthCheckPort found, maintenance mode not set");
    }

    private void transmuteErrorCodes(Map<String, DiagnosticsStatus> map, int oldErrorCode, int newErrorCode) {
        map.forEach((key, value) -> {
            if (value != null && oldErrorCode == value.getReturnCode()) {
                value.setReturnCodeNow(newErrorCode);
            }
        });
    }

    private Map<String, DiagnosticsStatus> checkConnectionToTimestampUrl() {
        Map<String, DiagnosticsStatus> statuses = new HashMap<>();

        for (String tspUrl : ServerConf.getTspUrl()) {
            try {
                URL url = new URL(tspUrl);

                log.info("Checking timestamp server status for url {}", url);

                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(DIAGNOSTICS_CONNECTION_TIMEOUT_MS);
                con.setReadTimeout(DIAGNOSTICS_READ_TIMEOUT_MS);
                con.setDoOutput(true);
                con.setDoInput(true);
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-type", "application/timestamp-query");
                con.connect();

                log.info("Checking timestamp server con {}", con);

                if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    log.warn("Timestamp check received HTTP error: {} - {}. Might still be ok", con.getResponseCode(),
                            con.getResponseMessage());
                    statuses.put(tspUrl,
                            new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, TimeUtils.offsetDateTimeNow(),
                                    tspUrl));
                } else {
                    statuses.put(tspUrl,
                            new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, TimeUtils.offsetDateTimeNow(),
                                    tspUrl));
                }

            } catch (Exception e) {
                log.warn("Timestamp status check failed", e);

                statuses.put(tspUrl,
                        new DiagnosticsStatus(DiagnosticsUtils.getErrorCode(e), TimeUtils.offsetDateTimeNow(), tspUrl));
            }
        }
        return statuses;

    }

    /**
     * Logic that determines correct DiagnosticsStatus based on simple connection check and LogManager status
     *
     * @param timestamperUrl                  url of timestamper
     * @param statusFromSimpleConnectionCheck status from simple connection check
     * @param statusFromLogManager            (possible) status from LogManager
     * @return
     */
    private DiagnosticsStatus determineDiagnosticsStatus(String timestamperUrl,
                                                         DiagnosticsStatus statusFromSimpleConnectionCheck,
                                                         DiagnosticsStatus statusFromLogManager) {
        DiagnosticsStatus status = statusFromSimpleConnectionCheck;

        // use the status either from simple connection check or from LogManager
        if (statusFromSimpleConnectionCheck.getReturnCode() == DiagnosticsErrorCodes.RETURN_SUCCESS) {
            // simple connection check = OK
            if (statusFromLogManager == null) {
                // missing LogManager status -> "uninitialized" error
                status.setReturnCodeNow(DiagnosticsErrorCodes.ERROR_CODE_TIMESTAMP_UNINITIALIZED);
            } else {
                // use the status from LogManager (ok or fail)
                log.info("Using time stamping status from LogManager for url {} status: {}",
                        timestamperUrl, statusFromLogManager);
                status = statusFromLogManager;
            }
        } else {
            // simple connection check = fail
            // Use fail status from LogManager, if one exists
            // Otherwise, retain the original simple connection check fail status.
            if (statusFromLogManager != null
                    && statusFromLogManager.getReturnCode() != DiagnosticsErrorCodes.RETURN_SUCCESS) {
                log.info("Using time stamping status from LogManager for url {} status: {}",
                        timestamperUrl, statusFromLogManager);
                status = statusFromLogManager;
            }
        }
        return status;
    }

    private void logResponseIOError(IOException e) {
        log.error("Unable to write to provided response, delegated request handling failed, response may"
                + " be malformed", e);
    }

}
