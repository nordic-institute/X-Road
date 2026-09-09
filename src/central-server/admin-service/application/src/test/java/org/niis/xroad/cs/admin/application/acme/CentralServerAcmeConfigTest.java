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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.CsAdminServiceConfigKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CentralServerAcmeConfigTest {

    @Mock
    private XRoadConfig config;

    private CentralServerAcmeConfig acmeConfig;

    @BeforeEach
    void setUp() {
        acmeConfig = new CentralServerAcmeConfig(config);
    }

    @Test
    void memberCertificateOnlyMethodsShouldBeFixedAndUnconfigurable() {
        assertThat(acmeConfig.isAcmeRenewalActive()).isTrue();
        assertThat(acmeConfig.isAutomaticActivateAcmeSignCertificate()).isFalse();
    }

    @Test
    void challengeListenerBindAddressShouldDelegateToXRoadConfig() {
        when(config.value(CsAdminServiceConfigKeys.ACME_CHALLENGE_BIND_ADDRESS)).thenReturn("127.0.0.1");

        assertThat(acmeConfig.getAcmeChallengeBindAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void schedulingAndChallengeSettingsShouldDelegateToXRoadConfig() {
        when(config.value(CsAdminServiceConfigKeys.ACME_CHALLENGE_PORT_ENABLED)).thenReturn(true);
        when(config.value(CsAdminServiceConfigKeys.ACME_CHALLENGE_PORT)).thenReturn(5987);
        when(config.value(CsAdminServiceConfigKeys.ACME_RENEWAL_RETRY_DELAY)).thenReturn(60);
        when(config.value(CsAdminServiceConfigKeys.ACME_RENEWAL_INTERVAL)).thenReturn(3600);

        assertThat(acmeConfig.isAcmeChallengePortEnabled()).isTrue();
        assertThat(acmeConfig.getAcmeChallengePort()).isEqualTo(5987);
        assertThat(acmeConfig.getAcmeRenewalRetryDelay()).isEqualTo(60);
        assertThat(acmeConfig.getAcmeRenewalInterval()).isEqualTo(3600);
    }

    @Test
    void enrollmentTimingSettingsShouldDelegateToXRoadConfig() {
        when(config.value(CsAdminServiceConfigKeys.ACME_RENEWAL_TIME_BEFORE_EXPIRATION_DATE)).thenReturn(14);
        when(config.value(CsAdminServiceConfigKeys.ACME_KEYPAIR_RENEWAL_TIME_BEFORE_EXPIRATION_DATE)).thenReturn(14);
        when(config.value(CsAdminServiceConfigKeys.ACME_AUTHORIZATION_WAIT_ATTEMPTS)).thenReturn(5);
        when(config.value(CsAdminServiceConfigKeys.ACME_AUTHORIZATION_WAIT_INTERVAL)).thenReturn(5);
        when(config.value(CsAdminServiceConfigKeys.ACME_CERTIFICATE_WAIT_ATTEMPTS)).thenReturn(5);
        when(config.value(CsAdminServiceConfigKeys.ACME_CERTIFICATE_WAIT_INTERVAL)).thenReturn(5);
        when(config.value(CsAdminServiceConfigKeys.ACME_CERTIFICATE_ACCOUNT_KEY_PAIR_EXPIRATION)).thenReturn(365);
        when(config.value(CsAdminServiceConfigKeys.ACME_KEY_LENGTH)).thenReturn(2048);
        when(config.value(CsAdminServiceConfigKeys.ACME_CHALLENGE_PATH)).thenReturn("/etc/xroad/acme-challenge");

        assertThat(acmeConfig.getAcmeRenewalTimeBeforeExpirationDate()).isEqualTo(14);
        assertThat(acmeConfig.getAcmeKeypairRenewalTimeBeforeExpirationDate()).isEqualTo(14);
        assertThat(acmeConfig.getAcmeAuthorizationWaitAttempts()).isEqualTo(5);
        assertThat(acmeConfig.getAcmeAuthorizationWaitInterval()).isEqualTo(5);
        assertThat(acmeConfig.getAcmeCertificateWaitAttempts()).isEqualTo(5);
        assertThat(acmeConfig.getAcmeCertificateWaitInterval()).isEqualTo(5);
        assertThat(acmeConfig.getAcmeCertificateAccountKeyPairExpiration()).isEqualTo(365);
        assertThat(acmeConfig.getAcmeKeyLength()).isEqualTo(2048);
        assertThat(acmeConfig.getAcmeChallengePath()).isEqualTo("/etc/xroad/acme-challenge");
    }
}
