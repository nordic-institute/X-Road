package ee.cyber.sdsb.common.securelog;

import java.io.Serializable;

public interface LogRecord extends Serializable {

    Long getNumber();

    void setNumber(Long nr);

    void setTime(Long time);

    Long getTime();

    boolean isArchived();

    void setArchived(boolean isArchived);

    Object[] getLinkingInfoFields();
}
