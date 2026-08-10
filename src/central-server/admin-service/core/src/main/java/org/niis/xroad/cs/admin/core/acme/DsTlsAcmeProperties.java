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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Timing, retry and connector settings for the dataspace TLS certificate's ACME enrollment and renewal, including
 * the renewal kill-switch. Configurable under {@code [admin-service]} in {@code local.ini}, e.g.:
 * <pre>
 * [admin-service]
 * ds-tls-acme.renewal-active = false
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "xroad.admin-service.ds-tls-acme")
@Getter
@Setter
public class DsTlsAcmeProperties implements AcmeConfig {

    private static final int DEFAULT_RENEWAL_RETRY_DELAY = 60;
    private static final int DEFAULT_RENEWAL_INTERVAL = 3600;
    private static final int DEFAULT_RENEWAL_TIME_BEFORE_EXPIRATION_DATE = 14;
    private static final int DEFAULT_KEYPAIR_RENEWAL_TIME_BEFORE_EXPIRATION_DATE = 14;
    private static final int DEFAULT_AUTHORIZATION_WAIT_ATTEMPTS = 5;
    private static final int DEFAULT_AUTHORIZATION_WAIT_INTERVAL = 5;
    private static final int DEFAULT_CERTIFICATE_WAIT_ATTEMPTS = 5;
    private static final int DEFAULT_CERTIFICATE_WAIT_INTERVAL = 5;
    private static final int DEFAULT_CERTIFICATE_ACCOUNT_KEY_PAIR_EXPIRATION = 365;
    private static final int DEFAULT_CHALLENGE_PORT = 8180;
    private static final int DEFAULT_KEY_LENGTH = 2048;

    /**
     * Renewal kill-switch: set to {@code false} to stop the dataspace TLS ACME enrollment/renewal scheduler from
     * running at all. Defaults to active. Unlike the Security Server (whose only off switch is disabling the
     * DataSpace feature), the Central Server's DS stack is always installed and provisioned, so it gets this
     * explicit, independent toggle.
     */
    private boolean renewalActive = true;

    private int renewalRetryDelay = DEFAULT_RENEWAL_RETRY_DELAY;

    private int renewalInterval = DEFAULT_RENEWAL_INTERVAL;

    private int renewalTimeBeforeExpirationDate = DEFAULT_RENEWAL_TIME_BEFORE_EXPIRATION_DATE;

    private int keypairRenewalTimeBeforeExpirationDate = DEFAULT_KEYPAIR_RENEWAL_TIME_BEFORE_EXPIRATION_DATE;

    private int authorizationWaitAttempts = DEFAULT_AUTHORIZATION_WAIT_ATTEMPTS;

    private int authorizationWaitInterval = DEFAULT_AUTHORIZATION_WAIT_INTERVAL;

    private int certificateWaitAttempts = DEFAULT_CERTIFICATE_WAIT_ATTEMPTS;

    private int certificateWaitInterval = DEFAULT_CERTIFICATE_WAIT_INTERVAL;

    private int certificateAccountKeyPairExpiration = DEFAULT_CERTIFICATE_ACCOUNT_KEY_PAIR_EXPIRATION;

    /** Loopback-only port the HTTP-01 challenge responder listens on; nginx routes the challenge path to it. */
    private int challengePort = DEFAULT_CHALLENGE_PORT;

    private int keyLength = DEFAULT_KEY_LENGTH;

}
