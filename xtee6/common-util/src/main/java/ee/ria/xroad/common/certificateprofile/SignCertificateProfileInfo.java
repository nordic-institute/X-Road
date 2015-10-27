package ee.ria.xroad.common.certificateprofile;

import ee.ria.xroad.common.identifier.ClientId;

/**
 * Signing certificate profile information provider.
 */
public interface SignCertificateProfileInfo extends CertificateProfileInfo {

    /**
     * Parameters used to provide the instance of SignCertificateProfileInfo.
     */
    public interface Parameters {

        /**
         * @return the client identifier
         */
        ClientId getClientId();

        /**
         * @return the member name
         */
        String getMemberName();
    }

    /**
     * Reads the subject identifier from the specified certificate.
     * @param certificate the certificate
     * @return the subject identifier
     * @throws Exception if an error occurs while reading the identifier
     */
    ee.ria.xroad.common.identifier.ClientId getSubjectIdentifier(
            java.security.cert.X509Certificate certificate) throws Exception;
}
