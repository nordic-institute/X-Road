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
import ee.ria.xroad.common.ProxyMemory;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.backupmanager.proto.BackupManagerRpcClient;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorDeviation;
import org.niis.xroad.common.rpc.mapper.DiagnosticStatusMapper;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.opmonitor.api.OperationalDataInterval;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.proxy.proto.ProxyRpcClient;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.securityserver.restapi.dto.OcspResponderDiagnosticsStatus;
import org.niis.xroad.signer.api.dto.CertificationServiceDiagnostics;
import org.niis.xroad.signer.api.dto.CertificationServiceStatus;
import org.niis.xroad.signer.api.dto.OcspResponderStatus;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.time.Instant.ofEpochMilli;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED;
import static org.niis.xroad.restapi.util.FormatUtils.fromInstantToOffsetDateTime;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DiagnosticService {

    private final ConfClientRpcClient confClientRpcClient;
    private final SignerRpcClient signerRpcClient;
    private final ProxyRpcClient proxyRpcClient;
    private final BackupManagerRpcClient backupManagerRpcClient;
    private final OpMonitorClient opMonitorClient;

    /**
     * Query global configuration status.
     *
     * @return
     */
    public DiagnosticsStatus queryGlobalConfStatus() {
        try {
            var status = confClientRpcClient.getStatus();
            DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(DiagnosticStatusMapper.mapStatus(status.getStatus()),
                    status.hasPrevUpdate() ? fromInstantToOffsetDateTime(ofEpochMilli(status.getPrevUpdate())) : null,
                    status.hasNextUpdate() ? fromInstantToOffsetDateTime(ofEpochMilli(status.getNextUpdate())) : null,
                    status.getDescription());
            if (status.hasErrorCode()) {
                diagnosticsStatus.setErrorCode(ErrorCode.valueOf(status.getErrorCode()));
            }
            return diagnosticsStatus;
        } catch (Exception e) {
            throw new DeviationAwareRuntimeException(e, buildErrorDiagnosticRequestFailed());
        }
    }

    /**
     * Query timestamping services status.
     *
     * @return
     */
    public Set<DiagnosticsStatus> queryTimestampingStatus() {
        log.info("Query timestamper status");

        try {
            Map<String, DiagnosticsStatus> response = proxyRpcClient.getTimestampingStatus();
            return Objects.requireNonNull(response)
                    .entrySet().stream()
                    .map(diagnosticsStatusEntry -> {
                        DiagnosticsStatus diagnosticsStatus = diagnosticsStatusEntry.getValue();
                        diagnosticsStatus.setDescription(diagnosticsStatusEntry.getKey());
                        return diagnosticsStatus;
                    }).collect(Collectors.toSet());
        } catch (Exception e) {
            throw new DeviationAwareRuntimeException(e, buildErrorDiagnosticRequestFailed());
        }
    }

    /**
     * Query ocsp responders status.
     *
     * @return
     */
    public List<OcspResponderDiagnosticsStatus> queryOcspResponderStatus() {
        log.info("Query OCSP status");
        try {
            CertificationServiceDiagnostics response = signerRpcClient.getCertificationServiceDiagnostics();

            return Objects.requireNonNull(response)
                    .getCertificationServiceStatusMap()
                    .entrySet()
                    .stream()
                    .map(this::parseOcspResponderDiagnosticsStatus)
                    .toList();
        } catch (Exception e) {
            throw new DeviationAwareRuntimeException(e.getMessage(), e, buildErrorDiagnosticRequestFailed());
        }
    }

    /**
     * Query proxy addons status.
     *
     * @return
     */
    public AddOnStatusDiagnostics queryAddOnStatus() {
        try {
            return proxyRpcClient.getAddOnStatus();
        } catch (Exception e) {
            throw new DeviationAwareRuntimeException(e, buildErrorDiagnosticRequestFailed());
        }
    }

    /**
     * Query proxy backup encryption status.
     * @return BackupEncryptionStatusDiagnostics
     */
    public BackupEncryptionStatusDiagnostics queryBackupEncryptionStatus() {
        try {
            return backupManagerRpcClient.getEncryptionStatus();
        } catch (Exception e) {
            throw new DeviationAwareRuntimeException(e, buildErrorDiagnosticRequestFailed());
        }
    }

    /**
     * Query proxy message log encryption status.
     *
     * @return MessageLogEncryptionStatusDiagnostics
     */
    public MessageLogEncryptionStatusDiagnostics queryMessageLogEncryptionStatus() {
        try {
            return proxyRpcClient.getMessageLogEncryptionStatus();
        } catch (Exception e) {
            throw new DeviationAwareRuntimeException(e, buildErrorDiagnosticRequestFailed());
        }
    }

    /**
     * Query proxy memory usage from admin port over HTTP.
     *
     * @return ProxyMemory
     */
    public ProxyMemory queryProxyMemoryUsage() {
        try {
            return proxyRpcClient.getProxyMemoryStatus();
        } catch (Exception e) {
            throw new DeviationAwareRuntimeException(e, buildErrorDiagnosticRequestFailed());
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

    private ErrorDeviation buildErrorDiagnosticRequestFailed() {
        return new ErrorDeviation(ERROR_DIAGNOSTIC_REQUEST_FAILED);
    }

}
