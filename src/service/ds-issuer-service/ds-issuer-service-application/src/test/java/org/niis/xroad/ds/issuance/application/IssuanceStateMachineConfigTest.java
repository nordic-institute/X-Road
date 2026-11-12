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
package org.niis.xroad.ds.issuance.application;

import io.smallrye.config.source.yaml.YamlConfigSource;
import org.eclipse.edc.identityhub.statemachine.IdentityHubStateMachineConfiguration;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class IssuanceStateMachineConfigTest {

    private static final String PREFIX = "edc.issuer.issuance.";
    private static final String LEASE_DURATION_KEY = PREFIX + "state-machine.lease-duration-millis";
    private static final String BATCH_SIZE_KEY = PREFIX + "state-machine.batch-size";
    private static final String CALL_TIMEOUT_KEY = PREFIX + "send.call-timeout.ms";
    private static final String CONNECT_TIMEOUT_KEY = PREFIX + "send.connect-timeout.ms";

    private static final String[] SHIPPED_KEYS = {LEASE_DURATION_KEY, BATCH_SIZE_KEY, CALL_TIMEOUT_KEY, CONNECT_TIMEOUT_KEY};

    private static YamlConfigSource shipped;

    @BeforeAll
    static void loadShippedConfig() throws IOException {
        shipped = new YamlConfigSource(IssuanceStateMachineConfigTest.class.getResource("/application.yaml"));
    }

    @Test
    void shippedKeysAreDeclaredByEdc() {
        var declared = edcDeclaredKeys();
        for (var key : SHIPPED_KEYS) {
            assertThat(declared)
                    .as("%s must match a setting key declared by IdentityHubStateMachineConfiguration, "
                            + "otherwise EDC ignores it and uses its default", key)
                    .contains(key.substring(PREFIX.length()));
        }
    }

    @Test
    void shippedValuesArePositive() {
        for (var key : SHIPPED_KEYS) {
            assertThat(shippedNumber(key)).as("%s must be greater than 0", key).isPositive();
        }
    }

    @Test
    void fullBatchOfDeliveryCallsFitsInsideTheLease() {
        var leaseMillis = shippedNumber(LEASE_DURATION_KEY);
        var batchSize = shippedNumber(BATCH_SIZE_KEY);
        var callTimeoutMillis = shippedNumber(CALL_TIMEOUT_KEY);

        assertThat(batchSize * callTimeoutMillis)
                .as("%s x %s must stay below %s: a node that stalls past its lease lets another node "
                        + "take the same requests and deliver a credential twice",
                        BATCH_SIZE_KEY, CALL_TIMEOUT_KEY, LEASE_DURATION_KEY)
                .isLessThan(leaseMillis);
    }

    private static long shippedNumber(String key) {
        var value = shipped.getValue(key);
        assertThat(value).as("%s must be set in application.yaml", key).isNotNull();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new AssertionError("%s = %s must be a number".formatted(key, value), e);
        }
    }

    private static Set<String> edcDeclaredKeys() {
        return Arrays.stream(IdentityHubStateMachineConfiguration.class.getDeclaredFields())
                .map(field -> field.getAnnotation(Setting.class))
                .filter(setting -> setting != null)
                .map(Setting::key)
                .collect(Collectors.toSet());
    }
}
