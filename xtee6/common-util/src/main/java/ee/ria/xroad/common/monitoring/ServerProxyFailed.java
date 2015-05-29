package ee.ria.xroad.common.monitoring;

import java.io.Serializable;

import lombok.Data;

/**
 * Serializable message denoting server proxy failure.
 */
@Data
public class ServerProxyFailed implements Serializable {

    private final MessageInfo message;
}
