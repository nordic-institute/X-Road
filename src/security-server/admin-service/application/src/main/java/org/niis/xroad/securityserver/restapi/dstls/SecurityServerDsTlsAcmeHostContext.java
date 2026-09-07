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
package org.niis.xroad.securityserver.restapi.dstls;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.acme.spring.dstls.DsTlsAcmeHostContext;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.util.MailNotificationHelper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * The Security Server's own {@link DsTlsAcmeHostContext}: the public hostname comes from the configured
 * DataSpace IdentityHub URL, the ACME account/EAB alias is the fixed {@value #DS_TLS_ACME_ALIAS}, and outcomes
 * are reported via email through {@link MailNotificationHelper}. Whether the renewal scheduler runs at all is a
 * separate, bean-wiring-time decision made by {@link DsTlsAcmeCertificateRenewalSchedulingConfig}, not this
 * class.
 */
@Component
@RequiredArgsConstructor
class SecurityServerDsTlsAcmeHostContext implements DsTlsAcmeHostContext {

    static final String DS_TLS_ACME_ALIAS = "dataspace-tls";

    private static final List<String> NO_CONTACTS = List.of();

    private final AdminServiceProperties adminServiceProperties;
    private final MailNotificationHelper mailNotificationHelper;
    private final GlobalConfProvider globalConfProvider;
    private final DsTlsCertificateService dsTlsCertificateService;

    /**
     * @return the host component of the configured DataSpace IdentityHub URL, or {@code null} when DataSpace
     *     isn't enabled (blank URL) — the same value {@code DataspaceProvisioningService} already uses to
     *     construct this server's own {@code did:web} identifier.
     */
    @Override
    public String getPublicHostname() {
        String identityHubUrl = adminServiceProperties.getDataspace().getIdentityHubUrl();
        if (isBlank(identityHubUrl)) {
            return null;
        }

        String host = URI.create(identityHubUrl).getHost();
        if (isBlank(host)) {
            throw new IllegalArgumentException("Configured DataSpace IdentityHub URL has no host: " + identityHubUrl);
        }
        return host;
    }

    @Override
    public String getConfiguredHostnameSource() {
        return adminServiceProperties.getDataspace().getIdentityHubUrl();
    }

    /**
     * @return every DS TLS certification authority approved in globalconf — the same source
     *     {@code CertificateAuthorityService} and the DS TLS trust manager already read.
     */
    @Override
    public List<ApprovedDsTlsCaInfo> getDsTlsCertificationAuthorities() {
        return List.copyOf(globalConfProvider.getApprovedDsTlsCas(globalConfProvider.getInstanceIdentifier()));
    }

    @Override
    public String getEabAlias() {
        return DS_TLS_ACME_ALIAS;
    }

    @Override
    public List<String> getAccountContacts() {
        return requireNonNullElse(adminServiceProperties.getDataspace().getTlsCertificateContacts(), NO_CONTACTS);
    }

    @Override
    public void notifyEnrollmentSuccess(String hostname, boolean isRenewal) {
        mailNotificationHelper.sendDsTlsAcmeSuccessNotification(hostname, isRenewal);
    }

    @Override
    public void notifyEnrollmentFailure(String hostname, String errorDescription) {
        boolean isRenewal = dsTlsCertificateService.getEnrollmentStatus().method() != null;
        mailNotificationHelper.sendDsTlsAcmeFailureNotification(hostname, isRenewal, errorDescription);
    }
}
