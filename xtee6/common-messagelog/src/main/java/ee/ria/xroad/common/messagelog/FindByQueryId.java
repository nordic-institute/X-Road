package ee.ria.xroad.common.messagelog;

import java.io.Serializable;
import java.util.Date;

import lombok.Value;

/**
 * Message for finding a log record for a given message Query Id, start and end time.
 */
@Value
public class FindByQueryId implements Serializable {

    private final String queryId;
    private final Date startTime;
    private final Date endTime;
}
