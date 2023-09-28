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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider;
import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CertUtils;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;

import javax.security.auth.x500.X500Principal;

import java.security.cert.X509Certificate;

/**
 * Basic certificate profile
 */
public class BasicCertificateProfileInfoProvider
        implements CertificateProfileInfoProvider {

    @Override
    public AuthCertificateProfileInfo getAuthCertProfile(AuthCertificateProfileInfo.Parameters params) {
        return new BasicAuthCertificateProfileInfo(params);
    }

    @Override
    public SignCertificateProfileInfo getSignCertProfile(SignCertificateProfileInfo.Parameters params) {
        return new BasicSignCertificateProfileInfo(params);
    }

    /**
     * Auth cert
     *
     * CN = server DNS
     * C = country
     * serialNumber = memberCode
     * O = memberName
     */
    private static class BasicAuthCertificateProfileInfo extends AbstractCertificateProfileInfo
            implements AuthCertificateProfileInfo {

        BasicAuthCertificateProfileInfo(AuthCertificateProfileInfo.Parameters params) {
            super(new DnFieldDescription[] {
                new EnumLocalizedFieldDescriptionImpl("CN", DnFieldLabelLocalizationKey.SERVER_DNS_NAME,
                        "")
                        .setReadOnly(false),
                new EnumLocalizedFieldDescriptionImpl("serialNumber", DnFieldLabelLocalizationKey.MEMBER_CODE_SN,
                        params.getServerId().getMemberCode())
                        .setReadOnly(true),
                new EnumLocalizedFieldDescriptionImpl("C", DnFieldLabelLocalizationKey.COUNTRY_CODE,
                        "")
                        .setReadOnly(false),
                new EnumLocalizedFieldDescriptionImpl("O", DnFieldLabelLocalizationKey.ORGANIZATION_NAME,
                        params.getMemberName())
                        .setReadOnly(true)}
            );
        }
    }
    /**
     * Sign cert
     *
     * CN = memberName
     * O = memberName
     * businessCategory = memberClass
     * C = country
     * serialNumber = memberCode
     */
    private static class BasicSignCertificateProfileInfo extends AbstractCertificateProfileInfo
            implements SignCertificateProfileInfo {

        private final String instanceIdentifier;

        BasicSignCertificateProfileInfo(SignCertificateProfileInfo.Parameters params) {
            super(new DnFieldDescription[] {

                new EnumLocalizedFieldDescriptionImpl("CN", DnFieldLabelLocalizationKey.ORGANIZATION_NAME_CN,
                        params.getMemberName())
                        .setReadOnly(true),
                new EnumLocalizedFieldDescriptionImpl("O", DnFieldLabelLocalizationKey.ORGANIZATION_NAME,
                        params.getMemberName())
                        .setReadOnly(true),
                new EnumLocalizedFieldDescriptionImpl("businessCategory", DnFieldLabelLocalizationKey.MEMBER_CLASS_BC,
                        params.getClientId().getMemberClass())
                        .setReadOnly(true),
                new EnumLocalizedFieldDescriptionImpl("C", DnFieldLabelLocalizationKey.COUNTRY_CODE,
                        "")
                        .setReadOnly(false),
                new EnumLocalizedFieldDescriptionImpl("serialNumber", DnFieldLabelLocalizationKey.MEMBER_CODE_SN,
                        params.getClientId().getMemberCode())
                        .setReadOnly(true) }
            );

            instanceIdentifier = params.getClientId().getXRoadInstance();
        }

        @Override
        public ClientId.Conf getSubjectIdentifier(X509Certificate cert) {

            X500Principal principal = cert.getSubjectX500Principal();
            X500Name x500name = new X500Name(principal.getName());

            String memberClass = CertUtils.getRDNValue(x500name, BCStyle.BUSINESS_CATEGORY);
            if (memberClass == null) {
                throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain business category");
            }

            String memberCode = CertUtils.getRDNValue(x500name, BCStyle.SERIALNUMBER);
            if (memberCode == null) {
                throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain serial number");
            }

            return ClientId.Conf.create(instanceIdentifier, memberClass, memberCode);

        }

    }

}
