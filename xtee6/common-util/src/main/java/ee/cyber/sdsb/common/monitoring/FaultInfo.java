package ee.cyber.sdsb.common.monitoring;

import java.io.Serializable;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Information about fault in proxy.
 */
public final class FaultInfo implements Serializable {

    private static final long serialVersionUID = -7844399580607682370L;

    public final MessageInfo message;
    public final String faultCode;
    public final String faultMessage;

    public FaultInfo(MessageInfo message, String faultCode,
            String faultMessage) {
        this.message = message;
        this.faultCode = faultCode;
        this.faultMessage = faultMessage;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
