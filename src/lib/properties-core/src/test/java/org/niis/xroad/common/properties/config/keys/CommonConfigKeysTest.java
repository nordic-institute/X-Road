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
package org.niis.xroad.common.properties.config.keys;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonConfigKeysTest {
    private static final String VAULT_RETRY_BUDGET_KEY = "xroad.common.vault-retry.budget";
    private static final Duration MAX_BUDGET = Duration.ofMinutes(5);
    private static final String VAULT_TOKEN_RENEWAL_INTERVAL_KEY = "xroad.common.vault-token-renewal.interval";

    @Test
    void vaultRetryBudgetIsExposedUnderItsKeyAndNotInTheUi() {
        assertThat(CommonConfigKeys.VAULT_RETRY_BUDGET.key()).isEqualTo(VAULT_RETRY_BUDGET_KEY);
        assertThat(CommonConfigKeys.VAULT_RETRY_BUDGET.exposedInUi()).isFalse();
    }

    @Test
    void vaultRetryBudgetAcceptsZeroUpToFiveMinutesAndRejectsTheRest() {
        for (Duration budget : new Duration[] {Duration.ZERO, Duration.ofSeconds(30), MAX_BUDGET}) {
            assertThat(CommonConfigKeys.VAULT_RETRY_BUDGET.validate(budget).valid()).isTrue();
        }
        assertThat(CommonConfigKeys.VAULT_RETRY_BUDGET.validate(Duration.ofSeconds(-1)).valid()).isFalse();
        assertThat(CommonConfigKeys.VAULT_RETRY_BUDGET.validate(MAX_BUDGET.plusMillis(1)).valid()).isFalse();
        assertThat(CommonConfigKeys.VAULT_RETRY_BUDGET.validate(null).valid()).isFalse();
    }

    @Test
    void vaultTokenRenewalIntervalAcceptsTenMinutesUpToSevenDays() {
        assertThat(interval("10m")).isEqualTo(Duration.ofMinutes(10));
        assertThat(interval("24h")).isEqualTo(Duration.ofHours(24));
        assertThat(interval("7d")).isEqualTo(Duration.ofDays(7));
        assertThat(interval("30m")).isEqualTo(Duration.ofMinutes(30));
        assertThat(interval("PT30M")).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void vaultTokenRenewalIntervalOutsideTheRangeOrUnparseableStopsStartup() {
        for (String value : new String[] {"PT0S", "9m", "-30m", "8d", "not-a-duration"}) {
            assertThatThrownBy(() -> interval(value))
                    .as(value)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(VAULT_TOKEN_RENEWAL_INTERVAL_KEY);
        }
    }

    private static Duration interval(String stored) {
        XRoadConfig config = XRoadConfigBuilder.create()
                .register(CommonConfigKeys.instance())
                .overrides(Map.of(VAULT_TOKEN_RENEWAL_INTERVAL_KEY, stored))
                .build();
        return config.value(CommonConfigKeys.VAULT_TOKEN_RENEWAL_INTERVAL);
    }
}
