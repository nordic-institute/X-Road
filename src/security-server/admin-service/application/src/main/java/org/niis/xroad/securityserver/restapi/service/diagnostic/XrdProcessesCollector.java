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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.monitor.common.Metrics;
import org.niis.xroad.monitor.common.MetricsGroup;
import org.niis.xroad.monitor.common.SingleMetrics;

import java.util.List;


@RequiredArgsConstructor
public class XrdProcessesCollector implements DiagnosticCollector<List<XrdProcessesCollector.Process>> {

    public static final String PROCESSES = "Xroad Processes";
    public static final String COMMAND = "command";

    private final MonitorClient monitorClient;

    @Override
    public String name() {
        return "JAVA Processes";
    }

    @Override
    public List<Process> collect() {
        return monitorClient.getMetrics(PROCESSES).getMetricsList().stream()
                .filter(Metrics::hasMetricsGroup)
                .map(Metrics::getMetricsGroup)
                .map(MetricsGroup::getMetricsList)
                .flatMap(List::stream)
                .filter(Metrics::hasMetricsGroup)
                .map(Metrics::getMetricsGroup)
                .map(MetricsGroup::getMetricsList)
                .flatMap(List::stream)
                .filter(Metrics::hasSingleMetrics)
                .map(Metrics::getSingleMetrics)
                .filter(mts -> COMMAND.equals(mts.getName()))
                .map(SingleMetrics::getValue)
                .map(Process::new)
                .toList();
    }

    public record Process(String command) {
    }
}
