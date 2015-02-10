package ee.cyber.sdsb.common.monitoring;

import java.io.Serializable;

import lombok.Data;

/**
 * Information about fault in proxy.
 */
@Data
public final class FaultInfo implements Serializable {

    private final MessageInfo message;
    private final String faultCode;
    private final String faultMessage;
}
