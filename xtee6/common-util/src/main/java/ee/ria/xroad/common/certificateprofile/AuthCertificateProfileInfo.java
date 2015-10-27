package ee.ria.xroad.common.certificateprofile;

import ee.ria.xroad.common.identifier.SecurityServerId;

/**
 * Authentication certificate profile information provider.
 */
public interface AuthCertificateProfileInfo extends CertificateProfileInfo {

    /**
     * Parameters used to provide the instance of AuthCertificateProfileInfo.
     */
    public interface Parameters {

        /**
         * @return the server identifier
         */
        SecurityServerId getServerId();

        /**
         * @return the member name
         */
        String getMemberName();
    }

}
