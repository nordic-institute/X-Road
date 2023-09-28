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
package ee.ria.xroad.monitor;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingTimeWindowReservoir;

import java.util.concurrent.TimeUnit;

/**
 * Global access point for {@link MetricRegistry}
 */
public final class MetricRegistryHolder {

    private static final int MINUTES_IN_HOUR = 60;
    private static final MetricRegistryHolder INSTANCE = new MetricRegistryHolder();

    private MetricRegistry metrics;

    private MetricRegistryHolder() {
        metrics = new MetricRegistry();
    }

    /**
     * Get Singleton instance
     * @return instance
     */
    public static MetricRegistryHolder getInstance() {
        return INSTANCE;
    }

    /**
     * Get {@link MetricRegistry}
     * @return {@link MetricRegistry}
     */
    public MetricRegistry getMetrics() {
        return metrics;
    }

    /**
     * Set Singleton instance (for testing purposes)
     */
    public void setMetrics(MetricRegistry metricRegistry) {
        this.metrics = metricRegistry;
    }



    /**
     * Either registers a new sensor to metricRegistry, or reuses already registered one.
     */
    @SuppressWarnings("unchecked")
    public <T> SimpleSensor<T> getOrCreateSimpleSensor(String metricName) {
        final Gauge sensor = metrics.gauge(metricName, SimpleSensor::new);
        if (sensor instanceof SimpleSensor) {
            return (SimpleSensor<T>) sensor;
        }
        throw new IllegalArgumentException(metricName + " is already used for a different type of metric");
    }

    /**
     * Either registers a new default histogram to metricRegistry, or reuses already registered one.
     * throws an IllegalArgumentException if a metric with the same name but a different type exists
     */
    public Histogram getOrCreateHistogram(String metricName) {
        return metrics.histogram(metricName, this::createDefaultHistogram);
    }



    private Histogram createDefaultHistogram() {
        return new Histogram(new SlidingTimeWindowReservoir(MINUTES_IN_HOUR, TimeUnit.MINUTES));
    }
}
