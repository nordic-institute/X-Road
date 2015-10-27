package ee.ria.xroad.common.certificateprofile;

/**
 * This interface describes the certificate profile information.
 */
public interface CertificateProfileInfo {

    /**
     * Returns the DN fields that will be displayed in the user interface for
     * the specific certificate type.
     * @return the DN fields
     */
    DnFieldDescription[] getSubjectFields();

    /**
     * Creates the DistiguishedName object from the specified fields for the
     * specific certificate type.
     * filled in the user interface.
     * @param values the field values
     * @return the DistiguishedName
     */
    javax.security.auth.x500.X500Principal createSubjectDn(DnFieldValue[] values);

    /**
     * Called when the user interface validates the field value. The
     * validation logic is implementation specific. Should throw an exception
     * if the field is invalid.
     * @param field the field value
     * @throws Exception if validation fails
     */
    void validateSubjectField(DnFieldValue field) throws Exception;
}
