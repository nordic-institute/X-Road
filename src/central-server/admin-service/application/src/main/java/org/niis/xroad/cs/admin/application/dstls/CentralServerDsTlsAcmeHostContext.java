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
package org.niis.xroad.cs.admin.application.dstls;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.acme.spring.dstls.DsTlsAcmeHostContext;
import org.niis.xroad.cs.admin.api.dto.DsTlsCertificationAuthority;
import org.niis.xroad.cs.admin.api.service.DsTlsCertificationAuthoritiesService;
import org.niis.xroad.cs.admin.core.dataspace.DataspaceIssuerProperties;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * The Central Server's own {@link DsTlsAcmeHostContext}: the public hostname comes from the co-located Issuer
 * Service's own configured host, the ACME account/EAB alias is the same fixed literal the Security Server uses,
 * and the designated ACME-capable CA is read directly from Central Server's own CA-management database rather
 * than from globalconf distribution. Whether the renewal scheduler runs at all is a separate, bean-wiring-time
 * decision made by {@link DsTlsAcmeCertificateRenewalSchedulingConfig}, not this class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CentralServerDsTlsAcmeHostContext implements DsTlsAcmeHostContext {

    static final String DS_TLS_ACME_ALIAS = "dataspace-tls";

    private final DataspaceIssuerProperties dataspaceIssuerProperties;
    private final DsTlsCertificationAuthoritiesService dsTlsCertificationAuthoritiesService;

    /**
     * @return the co-located Issuer Service's configured public hostname ({@code xroad.dataspace.issuer.host}),
     *     the same value {@code DataspaceIssuerProvisioningServiceImpl} already uses to build this component's
     *     own {@code did:web} identifier. Central Server has no DataSpace-enabled-style feature flag: the
     *     Issuer Service is an unconditional part of every Central Server install, so this is never {@code
     *     null} in practice — the blank check exists only so a future, currently-unreachable empty
     *     configuration value is treated as "skip this cycle", matching the shared worker's contract, rather
     *     than producing a certificate with an empty SAN.
     */
    @Override
    public String getPublicHostname() {
        String host = dataspaceIssuerProperties.getHost();
        return isBlank(host) ? null : host;
    }

    @Override
    public String getEabAlias() {
        return DS_TLS_ACME_ALIAS;
    }

    /**
     * @return every DS TLS certification authority Central Server's own {@link DsTlsCertificationAuthoritiesService}
     *     currently has on record. The shared worker filters this down to the ACME-capable entries itself.
     */
    @Override
    public List<ApprovedDsTlsCaInfo> getDsTlsCertificationAuthorities() {
        return dsTlsCertificationAuthoritiesService.findAll().stream()
                .map(CentralServerDsTlsAcmeHostContext::toApprovedDsTlsCaInfo)
                .toList();
    }

    /**
     * The shared ACME worker and {@code DsTlsAcmeService} only read {@code name}, {@code
     * acmeServerDirectoryUrl} and {@code dsTlsCertificateProfileId} off {@link ApprovedDsTlsCaInfo} — the
     * certificate-chain and ACME-server-IP fields exist solely for globalconf distribution and member-side
     * trust validation, neither of which this adapter feeds, so they are left unset here.
     */
    private static ApprovedDsTlsCaInfo toApprovedDsTlsCaInfo(DsTlsCertificationAuthority ca) {
        return new ApprovedDsTlsCaInfo(ca.getName(), null, List.of(), ca.getAcmeServerDirectoryUrl(), null,
                ca.getDsTlsCertificateProfileId());
    }

    /**
     * Logs the outcome at the level an administrator scanning logs would expect; Central Server has no
     * push-based outcome-notification mechanism to call into here. A failing or recovering enrollment/renewal
     * is already durably recorded by the shared worker itself (via {@code
     * DsTlsCertificateService.recordAcmeOutcome}, independent of this hook) and surfaced to administrators by
     * {@code NotificationServiceImpl} live-polling that recorded state — richer, dedicated notification
     * behavior (e.g. an in-app alert dismissal flow) is separate follow-up work.
     */
    @Override
    public void notifyEnrollmentSuccess(String hostname, boolean isRenewal) {
        log.info("DS TLS certificate successfully {} via ACME for {}", isRenewal ? "renewed" : "enrolled", hostname);
    }

    /**
     * Logs the outcome, for the same reason as {@link #notifyEnrollmentSuccess}: the worker's own error is
     * already recorded for {@code NotificationServiceImpl} to surface - this hook has no further system to
     * push into yet.
     */
    @Override
    public void notifyEnrollmentFailure(String hostname, String errorDescription) {
        log.error("DS TLS ACME enrollment/renewal failed for {}: {}", hostname, errorDescription);
    }
}
