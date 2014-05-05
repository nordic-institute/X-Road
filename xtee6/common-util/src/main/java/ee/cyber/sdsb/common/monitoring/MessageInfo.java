package ee.cyber.sdsb.common.monitoring;

import java.io.Serializable;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

/**
 * Monitoring info about a message processed by the proxy.
 */
public final class MessageInfo implements Serializable {

    private static final long serialVersionUID = -594491209518764424L;

    /** Where does the message originate from? */
    public enum Origin {
        CLIENT_PROXY,
        SERVER_PROXY
    }

    public final Origin origin;
    public final ClientId client;
    public final ServiceId service;
    public final String userId;
    public final String queryId;

    /** Construct the MessageInfo instance. */
    public MessageInfo(Origin origin, ClientId client, ServiceId service,
            String userId, String queryId) {
        this.origin = origin;
        this.client = client;
        this.service = service;
        this.userId = userId;
        this.queryId = queryId;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
