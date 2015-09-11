package ee.ria.xroad.common.messagelog;

import java.io.Serializable;

import lombok.Value;

/**
 * Message for timestamping an existing message record.
 */
@Value
public class TimestampMessage implements Serializable {
    private final Long messageRecordId;
}
