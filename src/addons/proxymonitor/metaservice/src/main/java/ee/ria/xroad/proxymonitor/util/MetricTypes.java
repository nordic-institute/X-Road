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
package ee.ria.xroad.proxymonitor.util;

import ee.ria.xroad.proxymonitor.message.HistogramMetricType;
import ee.ria.xroad.proxymonitor.message.MetricSetType;
import ee.ria.xroad.proxymonitor.message.MetricType;
import ee.ria.xroad.proxymonitor.message.NumericMetricType;
import ee.ria.xroad.proxymonitor.message.StringMetricType;

import com.google.protobuf.util.Timestamps;
import org.apache.commons.lang3.math.NumberUtils;
import org.niis.xroad.monitor.common.HistogramMetrics;
import org.niis.xroad.monitor.common.Metrics;
import org.niis.xroad.monitor.common.MetricsGroup;
import org.niis.xroad.monitor.common.SingleMetrics;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;
import java.util.Optional;

/**
 * Created by hyoty on 25.9.2015.
 */
public final class MetricTypes {
    private MetricTypes() {
    }

    /**
     * MetricSetType factory
     */
    public static MetricSetType of(MetricsGroup metrics) {
        final MetricSetType metricSet = new MetricSetType();
        metricSet.setName(metrics.getName());
        for (Metrics metricDto : metrics.getMetricsList()) {
            if (metricDto.hasMetricsGroup()) {
                metricSet.getMetrics().add(of(metricDto.getMetricsGroup()));
            } else if (metricDto.hasSingleHistogram()) {
                metricSet.getMetrics().add(toMetricType(metricDto.getSingleHistogram()));
            } else if (metricDto.hasSingleMetrics()) {
                metricSet.getMetrics().add(toMetricType(metricDto.getSingleMetrics()));
            }
        }
        return metricSet;
    }


    private static MetricType toMetricType(SingleMetrics metricDto) {
        Optional<String> optValue = Optional.ofNullable(metricDto.hasValue() ? metricDto.getValue() : null);

        if (optValue.isPresent() && NumberUtils.isCreatable(optValue.get())) {
            final NumericMetricType metric = new NumericMetricType();
            metric.setName(metricDto.getName());
            metric.setValue(new BigDecimal(optValue.get()));
            return metric;
        }

        final StringMetricType metric = new StringMetricType();
        metric.setName(metricDto.getName());
        optValue.ifPresent(metric::setValue);

        return metric;
    }

    private static MetricType toMetricType(HistogramMetrics metricDto) {
        final HistogramMetricType metric = new HistogramMetricType();

        var dateUpdated = Instant.ofEpochMilli(Timestamps.toMillis(metricDto.getUpdateDateTime()));

        final GregorianCalendar cal =
                GregorianCalendar.from(ZonedDateTime.ofInstant(dateUpdated, ZoneId.of("UTC")));
        metric.setUpdated(DATATYPE_FACTORY.newXMLGregorianCalendar(cal));
        metric.setName(metricDto.getName());
        metric.setMax(BigDecimal.valueOf(metricDto.getMax()));
        metric.setMin(BigDecimal.valueOf(metricDto.getMin()));
        metric.setMean(BigDecimal.valueOf(metricDto.getMean()));
        metric.setStddev(BigDecimal.valueOf(metricDto.getStdDev()));
        metric.setMedian(BigDecimal.valueOf(metricDto.getMedian()));
        return metric;
    }

    private static final DatatypeFactory DATATYPE_FACTORY;

    static {
        try {
            DATATYPE_FACTORY = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}
