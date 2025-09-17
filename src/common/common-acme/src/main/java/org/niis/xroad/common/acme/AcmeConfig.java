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

package org.niis.xroad.common.acme;

public interface AcmeConfig {

    // raw usage with default in org.niis.xroad.securityserver.restapi.config.AcmeBeanConfig.IsAcmeCertRenewalJobsActive
    boolean isAcmeRenewalActive();

    // ACME certificate renewal retry delay in seconds
    int getAcmeRenewalRetryDelay();

    // ACME certificate renewal job interval in seconds
    int getAcmeRenewalInterval();

    // when to trigger automatic renewal subtracted as days from the expiration date of the certificate.
    // Used when it's not possible to receive the ACME renewal information from the ACME server.
    int getAcmeRenewalTimeBeforeExpirationDate();

    // when to trigger automatic acme account keypair renewal subtracted as days from the expiration date of the certificate.
    int getAcmeKeypairRenewalTimeBeforeExpirationDate();

    // whether to automatically activate new signing certificates after they are ordered with ACME.
    boolean isAutomaticActivateAcmeSignCertificate();

    // the number of attempts to check whether the acme authorizations have completed
    int getAcmeAuthorizationWaitAttempts();

    // the amount of seconds to wait between acme authorization completion check attempts
    int getAcmeAuthorizationWaitInterval();

    // number of attempts to check whether the acme certificate is ready
    int getAcmeCertificateWaitAttempts();

    // amount of seconds to wait between acme certificate completion check attempts
    int getAcmeCertificateWaitInterval();

    // the amount of days the ACME server account's self-signed certificate is valid
    int getAcmeCertificateAccountKeyPairExpiration();

    // whether the service should listen on acme challenge port (default 80) for incoming requests
    boolean isAcmeChallengePortEnabled();

    int getAcmeChallengePort();
}
