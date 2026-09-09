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
package org.niis.xroad.cs.admin.application.acme;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.acme.config.AcmeConfig;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.CsAdminServiceConfigKeys;
import org.springframework.stereotype.Component;

/**
 * Central Server's {@link AcmeConfig}: the full interface is implemented (not just the narrower
 * {@code AcmeChallengeProperties}/{@code AcmeSchedulingProperties}) because the shared
 * {@code AcmeClient}/{@code AcmeChallengeController} — which the DS TLS ACME flow depends on — are wired
 * against the full interface, not either narrow one. Two methods belong exclusively to the member auth/sign
 * certificate flow, which Central Server has no equivalent of and never exercises; they return fixed values
 * since nothing on Central Server reads them.
 * <p>
 * Overrides {@link #getAcmeChallengeBindAddress()} to delegate to {@link CsAdminServiceConfigKeys#ACME_CHALLENGE_BIND_ADDRESS},
 * defaulting to loopback-only: unlike the Security Server, which never overrides this method (its listener
 * is itself the public HTTP-01 endpoint, so it binds every interface), Central Server's public port 80 is
 * nginx's, and this listener must only ever be reachable through nginx's proxy — see that key's javadoc for
 * why the bind address needs to be configurable, not fixed.
 */
@Component
@RequiredArgsConstructor
public class CentralServerAcmeConfig implements AcmeConfig {

    private final XRoadConfig config;

    /**
     * Member auth/sign-certificate-only concern; Central Server has no such flow, so this is never actually
     * read. Fixed {@code true} rather than a dedicated config key.
     */
    @Override
    public boolean isAcmeRenewalActive() {
        return true;
    }

    @Override
    public int getAcmeRenewalTimeBeforeExpirationDate() {
        return config.value(CsAdminServiceConfigKeys.ACME_RENEWAL_TIME_BEFORE_EXPIRATION_DATE);
    }

    @Override
    public int getAcmeKeypairRenewalTimeBeforeExpirationDate() {
        return config.value(CsAdminServiceConfigKeys.ACME_KEYPAIR_RENEWAL_TIME_BEFORE_EXPIRATION_DATE);
    }

    /**
     * Member auth/sign-certificate-only concern; Central Server has no such flow, so this is never actually
     * read. Fixed {@code false} rather than a dedicated config key.
     */
    @Override
    public boolean isAutomaticActivateAcmeSignCertificate() {
        return false;
    }

    @Override
    public int getAcmeAuthorizationWaitAttempts() {
        return config.value(CsAdminServiceConfigKeys.ACME_AUTHORIZATION_WAIT_ATTEMPTS);
    }

    @Override
    public int getAcmeAuthorizationWaitInterval() {
        return config.value(CsAdminServiceConfigKeys.ACME_AUTHORIZATION_WAIT_INTERVAL);
    }

    @Override
    public int getAcmeCertificateWaitAttempts() {
        return config.value(CsAdminServiceConfigKeys.ACME_CERTIFICATE_WAIT_ATTEMPTS);
    }

    @Override
    public int getAcmeCertificateWaitInterval() {
        return config.value(CsAdminServiceConfigKeys.ACME_CERTIFICATE_WAIT_INTERVAL);
    }

    @Override
    public int getAcmeCertificateAccountKeyPairExpiration() {
        return config.value(CsAdminServiceConfigKeys.ACME_CERTIFICATE_ACCOUNT_KEY_PAIR_EXPIRATION);
    }

    @Override
    public int getAcmeKeyLength() {
        return config.value(CsAdminServiceConfigKeys.ACME_KEY_LENGTH);
    }

    @Override
    public String getAcmeChallengePath() {
        return config.value(CsAdminServiceConfigKeys.ACME_CHALLENGE_PATH);
    }

    @Override
    public boolean isAcmeChallengePortEnabled() {
        return config.value(CsAdminServiceConfigKeys.ACME_CHALLENGE_PORT_ENABLED);
    }

    @Override
    public int getAcmeChallengePort() {
        return config.value(CsAdminServiceConfigKeys.ACME_CHALLENGE_PORT);
    }

    @Override
    public String getAcmeChallengeBindAddress() {
        return config.value(CsAdminServiceConfigKeys.ACME_CHALLENGE_BIND_ADDRESS);
    }

    @Override
    public int getAcmeRenewalRetryDelay() {
        return config.value(CsAdminServiceConfigKeys.ACME_RENEWAL_RETRY_DELAY);
    }

    @Override
    public int getAcmeRenewalInterval() {
        return config.value(CsAdminServiceConfigKeys.ACME_RENEWAL_INTERVAL);
    }
}
