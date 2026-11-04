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
package org.niis.xroad.cs.admin.core.dataspace;

import ee.ria.xroad.common.util.CertUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.dto.IssuerSetUpResult;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerProvisioningService;
import org.niis.xroad.cs.admin.api.service.IssuerHostService;
import org.niis.xroad.cs.admin.api.service.IssuerSetUpLeaseService;
import org.niis.xroad.cs.admin.api.service.IssuerSetUpService;
import org.niis.xroad.restapi.dstls.DsTlsCertificateStatus;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Starts the one-time dataspace issuer set-up: runs the synchronous prerequisite checks and, if they
 * pass, hands the provisioning sequence off to a background run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssuerSetUpServiceImpl implements IssuerSetUpService, DisposableBean {

    /** {@code GeneralName} tag for a DNS subject alternative name entry (RFC 5280). */
    private static final int GENERAL_NAME_DNS = 2;

    /** The provisioning run makes this many sequential gRPC calls, each bounded by the RPC deadline. */
    private static final int PROVISIONING_RPC_CALLS = 3;

    /** Extra headroom on top of the worst-case RPC time, included in the lease TTL. */
    private static final Duration LEASE_TTL_MARGIN = Duration.ofSeconds(30);

    private final IssuerHostService issuerHostService;
    private final DsTlsCertificateService dsTlsCertificateService;
    private final DataspaceIssuerProvisioningService dataspaceIssuerProvisioningService;
    private final IssuerSetUpLeaseService issuerSetUpLeaseService;
    private final IssuerProvisioningRpcChannelProperties issuerProvisioningRpcChannelProperties;
    private final HAConfigStatus haConfigStatus;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("issuer-set-up-", 0).factory());

    @Override
    public IssuerSetUpResult start() {
        var state = issuerHostService.getState();

        if (state.bound()) {
            return new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.ALREADY_SET_UP);
        }
        if (state.host() == null) {
            return new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.HOST_NOT_SET);
        }
        if (!connectorCertificateReadyFor(state.host())) {
            return new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.CERTIFICATE_NOT_READY);
        }
        if (issuerSetUpLeaseService.currentHolder().isPresent()) {
            return new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.ALREADY_RUNNING);
        }

        var node = haConfigStatus.getCurrentHaNodeName();
        if (!issuerSetUpLeaseService.tryAcquire(node, leaseTtl())) {
            return new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.ALREADY_RUNNING);
        }

        executor.execute(() -> runProvisioning(node));
        return new IssuerSetUpResult.Accepted();
    }

    /**
     * Longer than the provisioning run could ever take, so a node that crashes mid-run never blocks the
     * cluster past this point: every RPC call the run makes carries the same deadline, and a margin is
     * added on top of the worst case of all of them running back to back.
     */
    private Duration leaseTtl() {
        return Duration.ofMillis(issuerProvisioningRpcChannelProperties.deadlineAfter())
                .multipliedBy(PROVISIONING_RPC_CALLS)
                .plus(LEASE_TTL_MARGIN);
    }

    private void runProvisioning(String node) {
        try {
            dataspaceIssuerProvisioningService.provisionIssuer();
            log.info("Issuer Service set-up finished");
        } catch (Exception e) {
            log.warn("Issuer Service set-up failed: {}", e.getMessage(), e);
        } finally {
            issuerSetUpLeaseService.release(node);
        }
    }

    /**
     * The connector TLS certificate check: a certificate must be present in the {@code tls/ds-https}
     * vault slot, unexpired, and carry the issuer host as a DNS subject alternative name.
     */
    private boolean connectorCertificateReadyFor(String host) {
        DsTlsCertificateStatus status = dsTlsCertificateService.getStatus();
        X509Certificate certificate = status.certificate();
        return certificate != null && CertUtils.isValid(certificate) && hasDnsSubjectAlternativeName(certificate, host);
    }

    private static boolean hasDnsSubjectAlternativeName(X509Certificate certificate, String host) {
        Collection<List<?>> subjectAlternativeNames;
        try {
            subjectAlternativeNames = certificate.getSubjectAlternativeNames();
        } catch (CertificateParsingException e) {
            return false;
        }
        if (subjectAlternativeNames == null) {
            return false;
        }
        return subjectAlternativeNames.stream()
                .filter(san -> san.size() > 1 && Integer.valueOf(GENERAL_NAME_DNS).equals(san.get(0)))
                .map(san -> san.get(1))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .anyMatch(host::equalsIgnoreCase);
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
