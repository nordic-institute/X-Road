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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.domain.CanSignCause;
import org.niis.xroad.cs.admin.api.domain.CanSignResult;
import org.niis.xroad.cs.admin.api.domain.IssuerServiceStatus;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerDidService;
import org.niis.xroad.cs.admin.api.service.IssuerHostService;
import org.niis.xroad.cs.admin.api.service.IssuerServiceStatusService;
import org.niis.xroad.cs.admin.api.service.IssuerSetUpLeaseService;
import org.springframework.stereotype.Service;

/**
 * Gathers the Issuer Service status's six source facts and hands them to {@link IssuerServiceStatus#derive}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssuerServiceStatusServiceImpl implements IssuerServiceStatusService {

    private final IssuerHostService issuerHostService;
    private final ConnectorCertificateCheck connectorCertificateCheck;
    private final IssuerSetUpLeaseService issuerSetUpLeaseService;
    private final IssuerProvisioningRpcClient issuerProvisioningRpcClient;
    private final DataspaceIssuerProperties dataspaceIssuerProperties;
    private final DataspaceIssuerDidService dataspaceIssuerDidService;

    @Override
    public IssuerServiceStatus getStatus() {
        var state = issuerHostService.getState();
        boolean hostSet = state.host() != null;
        boolean certificateReady = hostSet && isCertificateReady(state.host());
        boolean runInProgress = issuerSetUpLeaseService.currentHolder().isPresent();
        String didToBind = hostSet ? IssuerDidFormat.didFor(state.host(), dataspaceIssuerProperties.getDidPort()) : null;
        var canSign = canSign();

        return IssuerServiceStatus.derive(hostSet, certificateReady, runInProgress, didToBind, boundDid(canSign), canSign);
    }

    private String boundDid(CanSignResult canSign) {
        var boundDids = dataspaceIssuerDidService.findAll();
        if (canSign.did() != null && boundDids.contains(canSign.did())) {
            return canSign.did();
        }
        return boundDids.stream().findFirst().orElse(null);
    }

    /**
     * Never throws: a certificate check that cannot read the connector TLS certificate (e.g. OpenBao is
     * unreachable) is a status to report as "not ready", not an error in reading the status.
     */
    private boolean isCertificateReady(String host) {
        try {
            return connectorCertificateCheck.isReadyFor(host);
        } catch (RuntimeException e) {
            log.warn("Issuer Service status read could not check the connector TLS certificate: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Never throws: an Issuer Service that fails to answer is a status to report, not an error in
     * reading the status. {@link IssuerProvisioningRpcClient#canSign} already turns a deadline overrun
     * or a transport failure into a {@link CanSignResult}; this catch is the last line of defense for
     * anything else the client could raise.
     */
    private CanSignResult canSign() {
        try {
            return issuerProvisioningRpcClient.canSign(DataspaceIssuerProvisioningServiceImpl.ISSUER_PARTICIPANT_ID);
        } catch (RuntimeException e) {
            log.warn("Issuer Service status read could not reach the Issuer Service: {}", e.getMessage(), e);
            return CanSignResult.cannotSign(CanSignCause.ISSUER_SERVICE_UNREACHABLE);
        }
    }
}
