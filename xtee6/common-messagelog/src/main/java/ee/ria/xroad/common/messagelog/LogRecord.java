package ee.ria.xroad.common.messagelog;

import java.io.Serializable;

/**
 * Declares methods that a log record should have.
 */
public interface LogRecord extends Serializable {

    /**
     * @return ID of the log record
     */
    Long getId();

    /**
     * Sets the ID of the log record.
     * @param nr the ID of the log record
     */
    void setId(Long nr);

    /**
     * Sets the timestamp of the log record's creation.
     * @param time the timestamp
     */
    void setTime(Long time);

    /**
     * @return the timestamp of the log record's creation
     */
    Long getTime();

    /**
     * @return true if the log record is archived
     */
    boolean isArchived();

    /**
     * Sets whether this log record is archived.
     * @param isArchived whether this log record is archived
     */
    void setArchived(boolean isArchived);

    /**
     * @return the log record linking info fields
     */
    Object[] getLinkingInfoFields();
}
