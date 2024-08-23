/*
 * The MIT License
 *
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

package ee.ria.xroad.proxy.admin.handler;

import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.DiagnosticsUtils;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.proxy.messagelog.MessageLog;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.proxy.proto.TimestampStatusResp;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static ee.ria.xroad.common.util.TimeUtils.offsetDateTimeToEpochMillis;
import static java.util.Optional.ofNullable;

/**
 * Diagnostics for timestamping.
 * First check the simple connection to timestamp server. If OK, check the status of the previous timestamp request.
 * If the previous request has failed or the simple connection cannot be made, DiagnosticsStatus tells the reason.
 */
@Slf4j
public class TimestampStatusHandler {

    private static final int DIAGNOSTICS_CONNECTION_TIMEOUT_MS = 1200;
    private static final int DIAGNOSTICS_READ_TIMEOUT_MS = 15000; // 15 seconds

    public TimestampStatusResp handle() {
        Map<String, DiagnosticsStatus> statuses = collectStatus();

        TimestampStatusResp.Builder responseBuilder = TimestampStatusResp.newBuilder();
        statuses.forEach((key, status) -> {
            var builder = org.niis.xroad.proxy.proto.DiagnosticsStatus.newBuilder();
            builder.setReturnCode(status.getReturnCode());
            if (StringUtils.isNotBlank(status.getDescription())) {
                builder.setDescription(status.getDescription());
            }
            ofNullable(offsetDateTimeToEpochMillis(status.getPrevUpdate())).ifPresent(builder::setPrevUpdate);
            ofNullable(offsetDateTimeToEpochMillis(status.getNextUpdate())).ifPresent(builder::setNextUpdate);

            responseBuilder.putDiagnosticsStatus(key, builder.build());
        });

        return responseBuilder.build();
    }

    private Map<String, DiagnosticsStatus> collectStatus() {
        log.info("collecting timestampstatus");

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

        return result;
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

    private void transmuteErrorCodes(Map<String, DiagnosticsStatus> map, int oldErrorCode, int newErrorCode) {
        map.forEach((key, value) -> {
            if (value != null && oldErrorCode == value.getReturnCode()) {
                value.setReturnCodeNow(newErrorCode);
            }
        });
    }

}
