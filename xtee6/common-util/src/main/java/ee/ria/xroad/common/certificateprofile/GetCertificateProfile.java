package ee.ria.xroad.common.certificateprofile;

import lombok.RequiredArgsConstructor;
import ee.ria.xroad.common.CodedException;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * Utility class for getting the certificate profile instance.
 */
@RequiredArgsConstructor
public class GetCertificateProfile {

    private final String className;

    /**
     * Returns the instance of the certificate profile provider class name.
     * Checks that the class is in classpath and that the class implements
     * the {@link ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider} interface.
     * @return the instance of the certificate profile
     * @throws Exception if an error occurs while instantiating
     */
    public CertificateProfileInfoProvider instance() throws Exception {
        try {
            return klass().newInstance();
        } catch (InstantiationException e) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not instantiate %s: %s", className, e.getMessage());
        } catch (IllegalAccessException e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }
    }

    /**
     * Returns the class that implements the
     * {@link ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider} interface.
     * @return the class
     * @throws Exception if the class cannot be found in the classpath or
     * if the class does not implement the interface
     */
    @SuppressWarnings("unchecked")
    public Class<CertificateProfileInfoProvider> klass() throws Exception {
        try {
            Class<?> clazz = Class.forName(className);
            if (CertificateProfileInfoProvider.class.isAssignableFrom(clazz)) {
                return (Class<CertificateProfileInfoProvider>) clazz;
            } else {
                throw new CodedException(X_INTERNAL_ERROR,
                        "%s must implement %s", className,
                        CertificateProfileInfoProvider.class);
            }
        } catch (ClassNotFoundException e) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "%s could not be found in classpath", className);
        }
    }
}
