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

package org.niis.xroad.ds.controlplane.application;

import io.smallrye.config.source.yaml.YamlConfigSource;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.virtual.controlplane.tasks.executor.TaskPollConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SendRetryLimitsTest {

    private static final String POLLER_MAX_RETRY_KEY = "edc.tasks.poll.max-retry";

    @ParameterizedTest
    @ValueSource(strings = {"edc.negotiation.send.retry.limit", "edc.transfer.send.retry.limit"})
    void shippedSendRetryLimitStaysWithinTaskPollerCap(String limitKey) throws IOException {
        var shipped = new YamlConfigSource(SendRetryLimitsTest.class.getResource("/application.yaml")).getValue(limitKey);
        var cap = taskPollerDefaultMaxRetry();

        assertThat(shipped)
                .as("%s must be set in application.yaml", limitKey)
                .isNotNull();
        assertThat(Integer.parseInt(shipped))
                .as("%s = %s must not exceed the task poller's %s default of %d. A send retry limit of L gives L+1 "
                        + "attempts, and the poller drops a task after max-retry + 1 attempts, so a higher limit "
                        + "lets the poller drop a failed record before EDC ends it.",
                        limitKey, shipped, POLLER_MAX_RETRY_KEY, cap)
                .isLessThanOrEqualTo(cap);
    }

    private static int taskPollerDefaultMaxRetry() {
        return Arrays.stream(TaskPollConfig.class.getDeclaredFields())
                .map(field -> field.getAnnotation(Setting.class))
                .filter(setting -> setting != null && POLLER_MAX_RETRY_KEY.equals(setting.key()))
                .map(setting -> Integer.parseInt(setting.defaultValue()))
                .findFirst()
                .orElseThrow();
    }
}
