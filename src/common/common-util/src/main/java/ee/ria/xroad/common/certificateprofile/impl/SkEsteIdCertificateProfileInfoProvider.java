/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider;
import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.DnFieldValue;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CertUtils;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;

import javax.security.auth.x500.X500Principal;

import java.security.cert.X509Certificate;

/**
 * Certificate profile for SK ESTEID.
 *
 * @deprecated
 * No longer used to the best of our knowledge, deprecated as of X-Road 7.2.0.
 * Will be removed in a future version.
 */
@Deprecated
public class SkEsteIdCertificateProfileInfoProvider
        implements CertificateProfileInfoProvider {

    @Override
    public AuthCertificateProfileInfo getAuthCertProfile(
            AuthCertificateProfileInfo.Parameters params) {
        return new SkEsteidAuthCertificateProfile();
    }

    @Override
    public SignCertificateProfileInfo getSignCertProfile(
            SignCertificateProfileInfo.Parameters params) {
        return new SkEsteidSignCertificateProfile(params);
    }

    private static class SkEsteidCertificateProfileInfo
            implements CertificateProfileInfo {

        @Override
        public DnFieldDescription[] getSubjectFields() {
            return new DnFieldDescription[] {};
        }

        @Override
        public X500Principal createSubjectDn(DnFieldValue[] values) {
            return null;
        }

        @Override
        public void validateSubjectField(DnFieldValue field) throws Exception {
        }

    }

    private static class SkEsteidAuthCertificateProfile
            extends SkEsteidCertificateProfileInfo
            implements AuthCertificateProfileInfo {
    }

    @RequiredArgsConstructor
    private static class SkEsteidSignCertificateProfile
            extends SkEsteidCertificateProfileInfo
            implements SignCertificateProfileInfo {

        private static final String PERSON = "PERSON";
        private final SignCertificateProfileInfo.Parameters params;

        @Override
        public ClientId.Conf getSubjectIdentifier(X509Certificate certificate)
                throws Exception {
            return getSubjectIdentifier(
                new X500Name(certificate.getSubjectX500Principal().getName())
            );
        }

        ClientId.Conf getSubjectIdentifier(X500Name x500name) throws Exception {
            String sn = CertUtils.getRDNValue(x500name, BCStyle.SERIALNUMBER);
            if (StringUtils.isEmpty(sn)) {
                throw new Exception(
                        "Subject name does not contain serial number");
            }

            return ClientId.Conf.create(
                params.getClientId().getXRoadInstance(),
                PERSON,
                sn
            );
        }
    }
}
