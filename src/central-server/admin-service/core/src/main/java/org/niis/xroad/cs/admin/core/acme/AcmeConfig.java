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
package org.niis.xroad.cs.admin.core.acme;

import ee.ria.xroad.common.FilePaths;

import org.niis.xroad.common.acme.AcmeHttp01Support;

import java.nio.file.Path;

/**
 * Timing and connector settings for the dataspace TLS certificate's ACME enrollment and renewal, bound from
 * {@code xroad.admin-service.ds-tls-acme} by {@link DsTlsAcmeProperties}. An interface (rather than a plain
 * properties class) purely as a test seam, mirroring the Security Server's equivalent.
 */
public interface AcmeConfig {

    Path ACME_ACCOUNT_KEYSTORE_PATH = FilePaths.BASE_CONF_PATH.resolve("ssl/acme.p12");
    Path ACME_CHALLENGE_PATH = FilePaths.BASE_CONF_PATH.resolve("acme-challenge");

    /**
     * Validates an ACME HTTP-01 challenge token received from an (untrusted) ACME server before it is used
     * to build a file path under {@link #ACME_CHALLENGE_PATH}.
     * <p>
     * Rejects anything that is not a plain RFC 8555 base64url token, and, as defense in depth, verifies that
     * resolving the token under {@link #ACME_CHALLENGE_PATH} does not escape that directory.
     */
    static boolean isValidChallengeToken(String token) {
        return AcmeHttp01Support.isValidChallengeToken(ACME_CHALLENGE_PATH, token);
    }

    /**
     * The renewal kill-switch: whether the scheduler that drives dataspace TLS ACME enrollment/renewal is
     * scheduled at all. Defaults to active. Unlike the Security Server, the Central Server has no other way to
     * pause this background activity - the DS stack is always installed and provisioned.
     */
    boolean isRenewalActive();

    /**
     * Renewal retry delay in seconds, used after a failed enrollment/renewal cycle.
     */
    int getRenewalRetryDelay();

    /**
     * Renewal job interval in seconds, used when the previous cycle succeeded.
     */
    int getRenewalInterval();

    /**
     * When to trigger automatic renewal, subtracted as days from the certificate's expiration date. Used when
     * the ACME server's renewal information (ARI) is unavailable.
     */
    int getRenewalTimeBeforeExpirationDate();

    /**
     * When to trigger automatic ACME account keypair renewal, subtracted as days from the expiration date of the
     * account keystore's wrapper certificate.
     */
    int getKeypairRenewalTimeBeforeExpirationDate();

    /**
     * The number of attempts to check whether the ACME authorization has completed.
     */
    int getAuthorizationWaitAttempts();

    /**
     * The number of seconds to wait between ACME authorization completion check attempts.
     */
    int getAuthorizationWaitInterval();

    /**
     * The number of attempts to check whether the ACME certificate is ready.
     */
    int getCertificateWaitAttempts();

    /**
     * The number of seconds to wait between ACME certificate completion check attempts.
     */
    int getCertificateWaitInterval();

    /**
     * The number of days the ACME account's self-signed wrapper certificate is valid for.
     */
    int getCertificateAccountKeyPairExpiration();

    /**
     * The loopback-only port the HTTP-01 challenge responder listens on. The native CS nginx configuration
     * routes {@code /.well-known/acme-challenge/} to it.
     */
    int getChallengePort();

    int getKeyLength();
}
