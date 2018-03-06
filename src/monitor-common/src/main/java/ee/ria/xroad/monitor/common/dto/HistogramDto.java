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
package ee.ria.xroad.monitor.common.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;

/**
 * Created by hyoty on 24.9.2015.
 */

@Getter
@EqualsAndHashCode(callSuper = true)
public class HistogramDto extends MetricDto {
    /**
     * The date/time when data was last updated
     */
    private final Instant updateDateTime;
    private final double distribution75thPercentile;
    private final double distribution95thPercentile;
    private final double distribution98thPercentile;
    private final double distribution99thPercentile;
    private final double distribution999thPercentile;
    private final double max;
    private final double mean;
    private final double median;
    private final double min;
    private final double stdDev;

    /**
     * Constructor
     */
    public HistogramDto(String name,
                        double distribution75thPercentile,
                        double distribution95thPercentile,
                        double distribution98thPercentile,
                        double distribution99thPercentile,
                        double distribution999thPercentile,
                        double max,
                        double mean,
                        double median,
                        double min,
                        double stdDev) {
        super(name);
        updateDateTime = Instant.now();
        this.distribution75thPercentile = distribution75thPercentile;
        this.distribution95thPercentile = distribution95thPercentile;
        this.distribution98thPercentile = distribution98thPercentile;
        this.distribution99thPercentile = distribution99thPercentile;
        this.distribution999thPercentile = distribution999thPercentile;
        this.min = min;
        this.max = max;
        this.mean = mean;
        this.median = median;
        this.stdDev = stdDev;
    }
}
