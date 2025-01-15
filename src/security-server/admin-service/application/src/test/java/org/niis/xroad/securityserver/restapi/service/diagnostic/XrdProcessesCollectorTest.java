/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.service.diagnostic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.monitor.common.Metrics;
import org.niis.xroad.monitor.common.MetricsGroup;
import org.niis.xroad.monitor.common.SingleMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XrdProcessesCollectorTest {
    private static final String PROC1 = "java arg arg";
    private static final String PROC2 = "java arg2 arg2";

    @Mock
    private MonitorClient monitorClient;

    @InjectMocks
    private XrdProcessesCollector collector;

    @Test
    void collect() {
        MetricsGroup metrics = MetricsGroup.newBuilder()
                .addMetrics(Metrics.newBuilder().setMetricsGroup(
                        MetricsGroup.newBuilder()
                                .addMetrics(Metrics.newBuilder().setMetricsGroup(buildProcess(PROC1)))
                                .addMetrics(Metrics.newBuilder().setMetricsGroup(buildProcess(PROC2)))
                ))
                .build();
        when(monitorClient.getMetrics("Xroad Processes")).thenReturn(metrics);

        assertThat(collector.collect())
                .containsExactly(new XrdProcessesCollector.Process(PROC1), new XrdProcessesCollector.Process(PROC2));
    }


    private MetricsGroup.Builder buildProcess(String command) {
        return MetricsGroup.newBuilder()
                .addMetrics(
                        Metrics.newBuilder().setSingleMetrics(
                                SingleMetrics.newBuilder()
                                        .setName("command")
                                        .setValue(command)
                        ));
    }


}
