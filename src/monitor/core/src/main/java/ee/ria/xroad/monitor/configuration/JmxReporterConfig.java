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

package ee.ria.xroad.monitor.configuration;

import ee.ria.xroad.monitor.MetricRegistryHolder;
import ee.ria.xroad.monitor.common.SystemMetricNames;

import com.codahale.metrics.jmx.JmxReporter;
import com.google.common.collect.Lists;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class JmxReporterConfig {

    @Bean
    JmxReporterWrapper jmxReporterWrapper() {
        return new JmxReporterWrapper();
    }

    static class JmxReporterWrapper {
        private static JmxReporter jmxReporter;

        JmxReporterWrapper() {
            jmxReporter = JmxReporter.forRegistry(MetricRegistryHolder.getInstance().getMetrics())
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .filter((name, metric) -> !Lists.newArrayList(SystemMetricNames.PROCESSES,
                            SystemMetricNames.PACKAGES, SystemMetricNames.CERTIFICATES).contains(name))
                    .build();
        }

        @PostConstruct
        public void afterPropertiesSet() {
            jmxReporter.start();
        }

        @PreDestroy
        public void destroy() {
            log.trace("stopReporter()");

            if (jmxReporter != null) {
                jmxReporter.stop();
            }
        }
    }
}
