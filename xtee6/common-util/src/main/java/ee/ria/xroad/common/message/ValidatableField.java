package ee.ria.xroad.common.message;

/**
 * Fields that can be validated during unmarshalling of the SOAP header must
 * implement this interface.
 */
public interface ValidatableField {

    /**
     * Subclasses must implement the validation logic.
     * @throws Exception if the validation does not pass
     */
    void validate() throws Exception;

}
