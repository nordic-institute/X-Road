package ee.ria.xroad.common.certificateprofile;

/**
 * DistinguishedName field description for user interfaces.
 */
public interface DnFieldDescription {

    /**
     * Returns the identifier of the field (such as 'O', 'OU' etc).
     * @return the internal identifier of the field
     */
    String getId();

    /**
     * Returns the label of the field, used to display the field in
     * the user interface.
     * @return the label of the field
     */
    String getLabel();

    /**
     * Returns the default value of the field. Can be empty or null.
     * @return the value of the field
     */
    String getDefaultValue();

    /**
     * Must return true if the field is to be made read-only in the
     * user interface.
     * @return true, if this field is read-only
     */
    boolean isReadOnly();

    /**
     * Hint for user interface to indicate that this field is required to
     * be filled.
     * @return true, if this field is required to be filled
     */
    boolean isRequired();
}
