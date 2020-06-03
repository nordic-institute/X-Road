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
package ee.ria.xroad.common.util;

import lombok.Value;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/**
 * Encapsulates network statistics such as number bytes received and transmitted.
 */
@Value
public class NetStats {
    private long bytesReceived;
    private long bytesTransmitted;

    /**
     * Computes the difference between two snapshots of network statistics.
     * @param current current network statistics snapshot
     * @param previous previous network statistics snapshot
     * @return network statistics snapshot containing the difference
     */
    public static NetStats diff(NetStats current, NetStats previous) {
        if (current == null || previous == null) {
            return null;
        }

        long diffReceived =
                current.getBytesReceived() - previous.getBytesReceived();
        long diffTransmitted =
                current.getBytesTransmitted() - previous.getBytesTransmitted();

        if (diffReceived < 0) {
            diffReceived = 0;
        }

        if (diffTransmitted < 0) {
            diffTransmitted = 0;
        }

        return new NetStats(diffReceived, diffTransmitted);
    }

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this).build();
    }
}
