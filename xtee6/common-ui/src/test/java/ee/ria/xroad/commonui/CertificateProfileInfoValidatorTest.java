package ee.ria.xroad.commonui;

import org.junit.Test;

/**
 * Testis for CertificateProfileInfoValidator.
 */
public class CertificateProfileInfoValidatorTest {

    @Test
    public void passWhenClassNameCorrect() throws ClassNotFoundException {
        CertificateProfileInfoValidator.validate(
                "ee.ria.xroad.common.certificateprofile.impl."
                        + "EjbcaCertificateProfileInfoProvider");
    }

    @Test(expected = RuntimeException.class)
    public void failWhenClassDoesNotImplementProfileInfoInterface()
            throws ClassNotFoundException {
        CertificateProfileInfoValidator.validate("java.lang.String");
    }

    @Test(expected = RuntimeException.class)
    public void failWhenClassDoesNotExist() throws ClassNotFoundException {
        CertificateProfileInfoValidator.validate("a.b.C");
    }
}
