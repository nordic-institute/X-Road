package ee.cyber.sdsb.common.monitoring;

import java.io.Serializable;

import lombok.Data;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

/**
 * Monitoring info about a message processed by the proxy.
 */
@Data
public final class MessageInfo implements Serializable {

    /** Where does the message originate from? */
    public enum Origin {
        CLIENT_PROXY,
        SERVER_PROXY
    }

    private final Origin origin;
    private final ClientId client;
    private final ServiceId service;
    private final String userId;
    private final String queryId;
}
