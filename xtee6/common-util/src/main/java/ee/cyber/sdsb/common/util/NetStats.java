package ee.cyber.sdsb.common.util;

import lombok.Value;

@Value
public class NetStats {
    private long bytesReceived;
    private long bytesTransmitted;

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
}
