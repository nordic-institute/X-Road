package ee.cyber.sdsb.common.monitoring;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

@Data
public class SuccessfulMessage implements Serializable {

    private final MessageInfo message;
    private final Date startTime;
    private final Date endTime;
}
