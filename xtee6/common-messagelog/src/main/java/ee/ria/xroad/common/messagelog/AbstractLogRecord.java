package ee.ria.xroad.common.messagelog;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Encapsulates information about a log record.
 */
@ToString
@EqualsAndHashCode
public abstract class AbstractLogRecord implements LogRecord {

    @Getter
    @Setter
    private Long id; // log record id

    @Getter
    @Setter
    private Long time; // time of the creation of the log record

    @Getter
    @Setter
    private boolean archived; // indicates, whether this log record is archived
}
