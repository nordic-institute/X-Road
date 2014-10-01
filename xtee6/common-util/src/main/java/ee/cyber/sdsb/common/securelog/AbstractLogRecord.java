package ee.cyber.sdsb.common.securelog;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@EqualsAndHashCode
public abstract class AbstractLogRecord implements LogRecord {

    private Long no; // log record number

    @Getter
    @Setter
    private Long time; // time of the creation of the log record

    @Getter
    @Setter
    private boolean archived; // indicates, whether this log record is archived

    @Override
    public Long getNumber() {
        return no;
    }

    @Override
    public void setNumber(Long no) {
        this.no = no;
    }
}
