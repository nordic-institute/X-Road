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
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider;
import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CertUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;

import java.security.cert.X509Certificate;

/**
 * Certificate profile for SK Klass 3 certificates.
 */
@Slf4j
public class SkKlass3CertificateProfileInfoProvider implements CertificateProfileInfoProvider {

    @Override
    public AuthCertificateProfileInfo getAuthCertProfile(AuthCertificateProfileInfo.Parameters params) {
        return new SkAuthCertificateProfileInfo(params);
    }

    @Override
    public SignCertificateProfileInfo getSignCertProfile(SignCertificateProfileInfo.Parameters params) {
        return new SkSignCertificateProfileInfo(params);
    }

    private static class SkAuthCertificateProfileInfo extends AbstractCertificateProfileInfo
            implements AuthCertificateProfileInfo {
        SkAuthCertificateProfileInfo(AuthCertificateProfileInfo.Parameters params) {
            super(new DnFieldDescription[] {
                    new EnumLocalizedFieldDescriptionImpl("SN", DnFieldLabelLocalizationKey.SERIAL_NUMBER_SN,
                            params.getServerId().getMemberCode())
                            .setReadOnly(true),
                    new EnumLocalizedFieldDescriptionImpl("CN", DnFieldLabelLocalizationKey.COMMON_NAME,
                            params.getMemberName()).setReadOnly(true) }
            );
        }
    }

    private static class SkSignCertificateProfileInfo extends AbstractCertificateProfileInfo
            implements SignCertificateProfileInfo {
        private static final int SN_LENGTH = 8;

        private static final ASN1ObjectIdentifier ORGANIZATION_IDENTIFIER = new ASN1ObjectIdentifier("2.5.4.97")
                .intern();

        // Organization identifier prefix of Estonian National Business Register
        private static final String COM_PREFIX = "NTREE-";
        // Organization identifier prefix of Estonian Register of State and Local Government Organisations
        private static final String GOV_PREFIX = "GO:EE-";
        // Organization identifier prefix of Estonian Non-Profit Associations and Foundations Register
        private static final String NGO_PREFIX = "NP:EE-";

        private static final int PREFIX_LENGTH = 6;

        // Member class of Estonian National Business Register
        private static final String COM_MEMBER = "COM";
        // Member class of Estonian Register of State and Local Government Organisations
        private static final String GOV_MEMBER = "GOV";
        // Member class of Estonian Non-Profit Associations and Foundations Register
        private static final String NGO_MEMBER = "NGO";
        // Member class of others (Non-Estonian Enterprises)
        private static final String NEE_MEMBER = "NEE";

        private final String instanceIdentifier;

        SkSignCertificateProfileInfo(SignCertificateProfileInfo.Parameters params) {
            super(new DnFieldDescription[] {
                    new EnumLocalizedFieldDescriptionImpl("SN", DnFieldLabelLocalizationKey.SERIAL_NUMBER_SN,
                        params.getClientId().getMemberCode()
                    ).setReadOnly(true),
                    new EnumLocalizedFieldDescriptionImpl("CN", DnFieldLabelLocalizationKey.COMMON_NAME,
                        params.getMemberName()
                    ).setReadOnly(true) }
            );

            instanceIdentifier = params.getClientId().getXRoadInstance();
        }

        @Override
        public ClientId.Conf getSubjectIdentifier(X509Certificate certificate) throws Exception {
            X500Name x500Name = new X500Name(certificate.getSubjectX500Principal().getName());
            String organizationIdentifier = CertUtils.getRDNValue(x500Name, ORGANIZATION_IDENTIFIER);

            return StringUtils.isEmpty(organizationIdentifier)
                    ? getSubjectIdentifierBySerialNumber(x500Name)
                    : getSubjectIdentifierByOrgId(organizationIdentifier);
        }

        private ClientId.Conf getSubjectIdentifierBySerialNumber(X500Name x500Name) throws Exception {
            if (log.isTraceEnabled()) {
                log.trace("getSubjectIdentifierBySerialNumber {}", x500Name.toString());
            }

            String sn = CertUtils.getRDNValue(x500Name, BCStyle.SERIALNUMBER);

            if (StringUtils.isEmpty(sn)) {
                throw new Exception("Subject name does not contain serial number");
            }

            if (!StringUtils.isNumeric(sn)) {
                throw new Exception("Serial number is not an integer");
            }

            if (sn.length() != SN_LENGTH) {
                throw new Exception("Serial number must be " + SN_LENGTH + " digits long");
            }

            return ClientId.Conf.create(instanceIdentifier, getMemberClass(sn), sn);
        }

        private ClientId.Conf getSubjectIdentifierByOrgId(String orgId) {
            log.trace("getSubjectIdentifierByOrgId {}", orgId);

            if (orgId.startsWith(COM_PREFIX)) {
                return ClientId.Conf.create(instanceIdentifier, COM_MEMBER, getRegisterCode(orgId));
            } else if (orgId.startsWith(GOV_PREFIX)) {
                return ClientId.Conf.create(instanceIdentifier, GOV_MEMBER, getRegisterCode(orgId));
            } else if (orgId.startsWith(NGO_PREFIX)) {
                return ClientId.Conf.create(instanceIdentifier, NGO_MEMBER, getRegisterCode(orgId));
            } else {
                // In order to guarantee member code uniques, use full organization identifier here.
                return ClientId.Conf.create(instanceIdentifier, NEE_MEMBER, orgId);
            }
        }

        private static String getRegisterCode(String organizationIdentifier) {
            return organizationIdentifier.substring(PREFIX_LENGTH);
        }

        // Returns the hardcoded member class based on the first number in the serial number.
        private static String getMemberClass(String sn) throws Exception {
            switch (sn.charAt(0)) {
                case '1': // Fall through
                case '2': // Fall through
                case '3': // Fall through
                case '4': // Fall through
                case '5': // Fall through
                case '6':
                    return COM_MEMBER;
                case '7':
                    return GOV_MEMBER;
                case '8': // Fall through
                case '9':
                    return NGO_MEMBER;
                default:
                    throw new Exception("Malformed serial number: " + sn);
            }
        }
    }
}
