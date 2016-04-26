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

import ee.ria.xroad.monitor.common.dto.*;
import ee.ria.xroad.proxymonitor.message.*;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;

/**
 * Created by hyoty on 25.9.2015.
 */
public final class MetricTypes {
    private MetricTypes() { }

    /**
     * MetricSetType factory
     */
    public static MetricSetType of(MetricSetDto metrics) {
        final MetricSetType metricSet = new MetricSetType();
        metricSet.setName(metrics.getName());
        for (MetricDto metricDto : metrics.getMetrics()) {
            if (metricDto instanceof MetricSetDto) {
                metricSet.getMetrics().add(of((MetricSetDto) metricDto));
            } else if (metricDto instanceof HistogramDto) {
                metricSet.getMetrics().add(toMetricType((HistogramDto) metricDto));
            } else if (metricDto instanceof SimpleMetricDto) {
                metricSet.getMetrics().add(toMetricType((SimpleMetricDto<?>) metricDto));
            }
        }
        return metricSet;
    }

    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal) return (BigDecimal)n;
        if (n instanceof Integer || n instanceof Long) return BigDecimal.valueOf(n.longValue());
        return BigDecimal.valueOf(n.doubleValue());

    }

    private static MetricType toMetricType(SimpleMetricDto<?> metricDto) {
        Object value = metricDto.getValue();
        if (value instanceof Number) {
            final NumericMetricType metric = new NumericMetricType();
            metric.setName(metricDto.getName());
            metric.setValue(toBigDecimal((Number) value));
            return metric;
        }
        final StringMetricType metric = new StringMetricType();
        metric.setName(metricDto.getName());
        metric.setValue((metricDto.getValue() == null ? null : metricDto.getValue().toString()));
        return metric;
    }

    private static MetricType toMetricType(HistogramDto metricDto) {
        final HistogramMetricType metric = new HistogramMetricType();
        final GregorianCalendar cal =
                GregorianCalendar.from(ZonedDateTime.ofInstant(metricDto.getUpdateDateTime(), ZoneId.of("UTC")));
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
