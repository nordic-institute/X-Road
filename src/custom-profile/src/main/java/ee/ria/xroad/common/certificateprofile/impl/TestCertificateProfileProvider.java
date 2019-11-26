/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.certificateprofile.impl;

import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider;
import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import java.security.cert.X509Certificate;

/**
 * Provider for testing custom provider dynamic loading
 */
public class TestCertificateProfileProvider implements CertificateProfileInfoProvider {
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

    /**
     * Constructor
     */
    private static class TestAuthCertificateProfileInfo
            extends AbstractCertificateProfileInfo
            implements AuthCertificateProfileInfo {
        TestAuthCertificateProfileInfo(Parameters params) {
            this(params.getServerId());
        }
        TestAuthCertificateProfileInfo(SecurityServerId securityServerId) {
            super(createTestDnFields(securityServerId));
        }
    }

    private static DnFieldDescription[] createTestDnFields(SecurityServerId securityServerId) {
        return new DnFieldDescription[] {
                // Serialnumber
                new DnFieldDescriptionImpl("serialNumber", "Serial number",
                        securityServerId.getXRoadInstance() + "/"
                                + securityServerId.getServerCode() + "/"
                                + securityServerId.getMemberClass()
                ).setReadOnly(true),

                // Dummy input field
                new DnFieldDescriptionImpl("dummy-input", "Just a dummy input field",
                        "foobar"
                ).setReadOnly(false) };
    }

    /**
     * Constructor
     */
    private static class TestSignCertificateProfileInfo
            extends AbstractCertificateProfileInfo
            implements SignCertificateProfileInfo {
        TestSignCertificateProfileInfo(Parameters params) {
            this(params.getServerId());
        }
        TestSignCertificateProfileInfo(SecurityServerId securityServerId) {
            super(createTestDnFields(securityServerId));
        }
        @Override
        public ClientId getSubjectIdentifier(X509Certificate certificate) throws Exception {
            return null;
        }
    }
}
