package ee.ria.xroad.common.certificateprofile;

/**
 * Represents a value of a field entered in the user interface.
 */
public interface DnFieldValue {

    /**
     * @return the identifier of the field (such as 'O', 'OU' etc).
     */
    String getId();

    /**
     * @return the value of the field as entered in the user interface.
     */
    String getValue();
}
