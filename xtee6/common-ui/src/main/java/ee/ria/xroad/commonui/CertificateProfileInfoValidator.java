package ee.ria.xroad.commonui;

import lombok.extern.slf4j.Slf4j;
import ee.ria.xroad.common.certificateprofile.GetCertificateProfile;

/**
 * Checks if correct class for certificate profile info validating is used.
 *
 * Valid class must implement CertificateProfileInfo interface.
 */
@Slf4j
public class CertificateProfileInfoValidator {

    public static void validate(String className) {
        try {
            new GetCertificateProfile(className).klass();
        } catch (Exception e) {
            log.error("Error getting profile info for class '{}'",
                    className, e);
            throw new RuntimeException("Certificate profile with name '"
                    + className + "' does not exist.");
        }
    }
}
