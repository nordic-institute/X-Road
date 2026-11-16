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
package org.niis.xroad.common.vault;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * Renews the shared secret store token once per call. The renewal runs through {@link ResilientVaultCall}, so a
 * retryable failure is retried within the budget. A failure that outlasts the budget is logged as a warning and
 * never thrown, so a scheduler keeps running later renewals.
 */
@Slf4j
public final class VaultTokenKeepalive {

    private static final String OPERATION = "renew-self";
    private static final String PATH = "auth/token/renew-self";

    private final ResilientVaultCall resilientCall;
    private final Duration budget;
    private final Runnable renewSelf;

    public VaultTokenKeepalive(Predicate<Throwable> retryable, Duration budget, Runnable renewSelf) {
        this.resilientCall = new ResilientVaultCall(retryable, budget);
        this.budget = budget;
        this.renewSelf = renewSelf;
    }

    public void renewOnce() {
        try {
            resilientCall.run(OPERATION, PATH, renewSelf);
            log.info("Secret store token renewed");
        } catch (RuntimeException e) {
            log.warn("Secret store token renewal failed (retry budget {}), the next renewal is still scheduled: {}",
                    ResilientVaultCall.formatBudget(budget), e.toString());
        }
    }
}
