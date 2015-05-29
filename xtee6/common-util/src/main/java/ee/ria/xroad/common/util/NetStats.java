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
