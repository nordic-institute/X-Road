package ee.cyber.sdsb.common.monitoring;

import java.io.Serializable;

import org.apache.commons.lang3.builder.ToStringBuilder;

public class ServerProxyFailed implements Serializable {

    private static final long serialVersionUID = -8736686484751095539L;

    public final MessageInfo message;

    public ServerProxyFailed(MessageInfo message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
