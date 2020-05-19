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
package ee.ria.xroad.monitor.common.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by hyoty on 24.9.2015.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString
public final class MetricSetDto extends MetricDto {
    private final Set<MetricDto> metrics;

    private MetricSetDto(String name, Set<MetricDto> metrics) {
        super(name);
        this.metrics = metrics;
    }

    /**
     * Builder for {@link MetricSetDto}
     */
    public static class Builder {
        private Set<MetricDto> metrics = new LinkedHashSet<>();
        private String name;

        public Builder(String name) {
            this.name = name;
        }
        public Builder withMetric(MetricDto metric) {
            metrics.add(metric);
            return this;
        }

        public <T extends Serializable> Builder withSimpleMetric(String metricName, T value) {
            metrics.add(new SimpleMetricDto<>(metricName, value));
            return this;
        }

        /**
         * Build instance
         */
        public MetricSetDto build() {
            Set<MetricDto> tmp = metrics;
            metrics = null;
            return new MetricSetDto(name, tmp);
        }
    }
}
