package ee.cyber.sdsb.common.monitoring;

import java.io.Serializable;

import lombok.Data;

@Data
public class ServerProxyFailed implements Serializable {

    private final MessageInfo message;
}
