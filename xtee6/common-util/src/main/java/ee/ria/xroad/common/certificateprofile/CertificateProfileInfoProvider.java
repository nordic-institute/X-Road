package ee.ria.xroad.common.certificateprofile;

/**
 * Base interface for a certificate profile info.
 */
public interface CertificateProfileInfoProvider {

    /**
     * Returns the authentication certificate profile info.
     * @param params parameters for the authentication certificate profile info
     * @return the authentication certificate profile info
     */
    AuthCertificateProfileInfo getAuthCertProfile(
            AuthCertificateProfileInfo.Parameters params);

    /**
     * Returns the signing certificate profile info.
     * @param params parameters for the signing certificate profile info
     * @return the signing certificate profile info
     */
    SignCertificateProfileInfo getSignCertProfile(
            SignCertificateProfileInfo.Parameters params);

}
