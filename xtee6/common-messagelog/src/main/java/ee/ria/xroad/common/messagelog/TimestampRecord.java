package ee.ria.xroad.common.messagelog;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A timestamp log record.
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TimestampRecord extends AbstractLogRecord {

    @Getter
    @Setter
    private String timestamp; // base-64 encoded time-stamp DER

    @Getter
    @Setter
    private String hashChainResult; // time-stamp hash chain result

    @Override
    public Object[] getLinkingInfoFields() {
        return new Object[] {getId(), getTime(), timestamp};
    }
}
