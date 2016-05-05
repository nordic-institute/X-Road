/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.typesafe.config.ConfigFactory;
import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.monitor.common.SystemMetricsRequest;
import ee.ria.xroad.monitor.common.SystemMetricsResponse;
import ee.ria.xroad.proxymonitor.message.MetricSetType;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

/**
 * Created by hyoty on 25.9.2015.
 */
@Slf4j
public final class MonitorClient {

    private static final String CONFIG_FILENAME = "/etc/xroad/conf.d/monitor.ini";
    private static final String CONFIG_PROPERTY_PORT = "xroad.monitor.port";
    private static final String CONFIG_SECTION = "monitor";
    private static final int DEFAULT_PORT = 2552;

    private static final ActorSystem ACTOR_SYSTEM
            = ActorSystem.create("MonitorClient", ConfigFactory.load().getConfig("monitor-client"));
    public static final int TIMEOUT_AWAIT = 10;
    public static final int TIMEOUT_REQUEST = 5;

    private final ActorSelection metricsProvider =
            ACTOR_SYSTEM.actorSelection(getMonitorAddress() + "/user/MetricsProviderActor");

    /**
     * Program entry point
     */
    public static void main(String[] args) {
        log.debug("starting testing getMetrics");
        new MonitorClient().getMetrics();
        log.debug("starting testing getMetrics");
    }

    /**
     * Get monitoring metrics
     */
    public MetricSetType getMetrics() {
        try {
            final Future<Object> response = Patterns.ask(metricsProvider, new SystemMetricsRequest(),
                    Timeout.apply(TIMEOUT_REQUEST, TimeUnit.SECONDS));
            Object obj = Await.result(response, Duration.apply(TIMEOUT_AWAIT, TimeUnit.SECONDS));
            if (obj instanceof SystemMetricsResponse) {
                final SystemMetricsResponse result = (SystemMetricsResponse) obj;
                log.debug("monitorclient received metrics result: " + result.getMetrics());
                MetricSetType jaxb = MetricTypes.of(result.getMetrics());
                log.debug("converted to jaxb = " + jaxb);
                return jaxb;
            } else {
                throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, "Unexpected response");
            }
        } catch (Exception e) {
            log.warn("Unable to read metrics", e);
            throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, "Unable to read metrics");
        }
    }

    private String getMonitorAddress() {
        int port = DEFAULT_PORT;
        SystemPropertiesLoader.create().with(CONFIG_FILENAME, CONFIG_SECTION).load();
        try {
            port = Integer.parseUnsignedInt(System.getProperty(CONFIG_PROPERTY_PORT));
        } catch (NumberFormatException e) {
            log.warn(String.format("Could not load configuration property %s - using the default port",
                    CONFIG_PROPERTY_PORT));
        }
        return String.format("akka.tcp://xroad-monitor@127.0.0.1:%d", port);
    }
}
