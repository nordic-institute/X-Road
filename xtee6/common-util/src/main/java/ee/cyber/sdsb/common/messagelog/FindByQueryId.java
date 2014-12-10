package ee.cyber.sdsb.common.messagelog;

import java.io.Serializable;
import java.util.Date;

import lombok.Value;

@Value
public class FindByQueryId implements Serializable {

    private final String queryId;
    private final Date startTime;
    private final Date endTime;
}
