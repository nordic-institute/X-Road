/**
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
package ee.ria.xroad.proxymonitor.util;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.monitor.common.SystemMetricsRequest;
import ee.ria.xroad.monitor.common.SystemMetricsResponse;
import ee.ria.xroad.proxymonitor.message.MetricSetType;

import akka.actor.ActorSelection;
import akka.pattern.Patterns;
import akka.util.Timeout;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by hyoty on 25.9.2015.
 */
@Slf4j
public class MonitorClient {

    public static final int TIMEOUT_AWAIT = 10;
    public static final int TIMEOUT_REQUEST = 5;

    private final ActorSelection metricsProvider;

    public MonitorClient(ActorSelection metricsProvider) {
        this.metricsProvider = metricsProvider;
    }

    /**
     * Get monitoring metrics
     */
    public MetricSetType getMetrics(List<String> metricNames, boolean isOwner) {
        try {
            final Future<Object> response = Patterns.ask(metricsProvider,
                    new SystemMetricsRequest(metricNames, isOwner),
                    Timeout.apply(TIMEOUT_REQUEST, TimeUnit.SECONDS));
            Object obj = Await.result(response, Duration.apply(TIMEOUT_AWAIT, TimeUnit.SECONDS));
            if (obj instanceof SystemMetricsResponse) {
                final SystemMetricsResponse result = (SystemMetricsResponse) obj;
                return MetricTypes.of(result.getMetrics());
            } else {
                throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, "Unexpected response");
            }
        } catch (Exception e) {
            log.warn("Unable to read metrics", e);
            throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, "Unable to read metrics");
        }
    }


}
