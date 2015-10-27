package ee.ria.xroad.common.certificateprofile.impl;

import java.security.cert.X509Certificate;

import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CertUtils;

/**
 * Default implementation of CertificateProfileInfoProvider.
 */
public class TestCertificateProfileInfoProvider
        implements CertificateProfileInfoProvider {

    @Override
    public AuthCertificateProfileInfo getAuthCertProfile(
            AuthCertificateProfileInfo.Parameters params) {
        return new TestAuthCertificateProfileInfo(params);
    }

    @Override
    public SignCertificateProfileInfo getSignCertProfile(
            SignCertificateProfileInfo.Parameters params) {
        return new TestSignCertificateProfileInfo(params);
    }

    static class TestAuthCertificateProfileInfo
            extends EjbcaAuthCertificateProfileInfo {

        public TestAuthCertificateProfileInfo(Parameters params) {
            super(params);
        }
    }

    static class TestSignCertificateProfileInfo
            extends EjbcaSignCertificateProfileInfo {

        public TestSignCertificateProfileInfo(Parameters params) {
            super(params);
        }

        @Override
        public ClientId getSubjectIdentifier(X509Certificate certificate) {
            return ClientId.create(
                params.getClientId().getXRoadInstance(),
                "BUSINESS",
                CertUtils.getSubjectCommonName(certificate)
            );
        }
    }
}
