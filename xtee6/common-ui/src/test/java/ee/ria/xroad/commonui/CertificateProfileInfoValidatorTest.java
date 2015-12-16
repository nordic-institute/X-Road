package ee.ria.xroad.commonui;

import org.junit.Test;

/**
 * Testis for CertificateProfileInfoValidator.
 */
public class CertificateProfileInfoValidatorTest {

    /**
     * Test to ensure validation of correct class succeeds.
     * @throws ClassNotFoundException in case validated class not found
     */
    @Test
    public void passWhenClassNameCorrect() throws ClassNotFoundException {
        CertificateProfileInfoValidator.validate(
                "ee.ria.xroad.common.certificateprofile.impl."
                        + "EjbcaCertificateProfileInfoProvider");
    }

    /**
     * Test to ensure validation fails if class does not implement the
     * CertificateProfileInfo class.
     * @throws ClassNotFoundException in case validated class not found
     */
    @Test(expected = RuntimeException.class)
    public void failWhenClassDoesNotImplementProfileInfoInterface()
            throws ClassNotFoundException {
        CertificateProfileInfoValidator.validate("java.lang.String");
    }

    /**
     * Test to ensure validation fails if class does not exists.
     * @throws ClassNotFoundException in case validated class not found
     */
    @Test(expected = RuntimeException.class)
    public void failWhenClassDoesNotExist() throws ClassNotFoundException {
        CertificateProfileInfoValidator.validate("a.b.C");
    }
}
