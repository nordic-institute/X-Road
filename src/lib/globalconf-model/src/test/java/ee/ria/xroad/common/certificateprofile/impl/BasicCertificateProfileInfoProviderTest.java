/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
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
import ee.ria.xroad.common.certificateprofile.DnFieldValue;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.bouncycastle.util.Arrays;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the Basic implementation of CertificateProfileInfoProvider.
 */
public class BasicCertificateProfileInfoProviderTest {

    /**
     * Tests whether getting expected subject fields succeeds as expected.
     */
    @Test
    public void signProfileSubjectFields() {
        DnFieldDescription[] expectedFields = {
                new EnumLocalizedFieldDescriptionImpl("CN", DnFieldLabelLocalizationKey.ORGANIZATION_NAME_CN,
                        "foobar"
                ).setReadOnly(true),
                new EnumLocalizedFieldDescriptionImpl("O", DnFieldLabelLocalizationKey.ORGANIZATION_NAME,
                        "foobar"
                ).setReadOnly(true),
                new EnumLocalizedFieldDescriptionImpl("businessCategory", DnFieldLabelLocalizationKey.MEMBER_CLASS_BC,
                        "bar"
                ).setReadOnly(true),
                new EnumLocalizedFieldDescriptionImpl("C", DnFieldLabelLocalizationKey.COUNTRY_CODE,
                        ""
                ).setReadOnly(false),
                new EnumLocalizedFieldDescriptionImpl("serialNumber", DnFieldLabelLocalizationKey.MEMBER_CODE_SN,
                        "baz"
                ).setReadOnly(true)
        };
        assertTrue(
                "Did not get expected fields",
                Arrays.areEqual(expectedFields, getSignProfile().getSubjectFields())
        );
    }

    /**
     * Tests whether validating correct subject field succeeds as expected.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void signProfileValidateFieldSuccessfully() throws Exception {
        getSignProfile().validateSubjectField(
                new DnFieldValueImpl("CN", "XX")
        );
    }

    /**
     * Tests whether validating unknown subject field fails as expected.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test(expected = Exception.class)
    public void signProfileFailToValidateUnknownField() throws Exception {
        getSignProfile().validateSubjectField(
                new DnFieldValueImpl("X", "foo")
        );
    }

    /**
     * Tests whether validating blank subject field of sign profile fails
     * as expected.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test(expected = Exception.class)
    public void signProfileFailToValidateBlankField() throws Exception {
        getSignProfile().validateSubjectField(
                new DnFieldValueImpl("serialNumber", "")
        );
    }

    /**
     * Tests whether creating subject Dn of sign profile succeeds as expected.
     */
    @Test
    public void signProfileCreateSubjectDn() {
        X500Principal x500PrincipalTest = new X500Principal("CN=XX, O=abc, serialNumber=baz");
        X500Principal x500PrincipalReal = getSignProfile().createSubjectDn(
                new DnFieldValue[]{
                        new DnFieldValueImpl("CN", "XX"),
                        new DnFieldValueImpl("O", "abc"),
                        new DnFieldValueImpl("serialNumber", "baz")
                }
        );
        assertEquals(x500PrincipalTest, x500PrincipalReal);
    }

    /**
     * Tests whether getting expected fields of auth profile succeeds
     * as expected.
     */
    @Test
    public void authProfileSubjectFields() {
        DnFieldDescription[] expectedFields = {
                new EnumLocalizedFieldDescriptionImpl("CN", DnFieldLabelLocalizationKey.SERVER_DNS_NAME,
                        ""
                ).setReadOnly(false),
                new EnumLocalizedFieldDescriptionImpl("serialNumber", DnFieldLabelLocalizationKey.MEMBER_CODE_SN,
                        "bar"
                ).setReadOnly(true),
                new EnumLocalizedFieldDescriptionImpl("C", DnFieldLabelLocalizationKey.COUNTRY_CODE,
                        ""
                ).setReadOnly(false),
                new EnumLocalizedFieldDescriptionImpl("O", DnFieldLabelLocalizationKey.ORGANIZATION_NAME,
                        "foobar"
                ).setReadOnly(true)
        };
        assertTrue(
                "Did not get expected fields",
                Arrays.areEqual(expectedFields, getAuthProfile().getSubjectFields())
        );
    }

    /**
     * Tests whether validating correct subject field of auth profile succeeds
     * as expected.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void authProfileValidateFieldSuccessfully() throws Exception {
        getAuthProfile().validateSubjectField(
                new DnFieldValueImpl("O", "bar")
        );
    }

    /**
     * Tests whether validating unknown subject field of auth profile fails
     * as expected.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test(expected = Exception.class)
    public void authProfileFailToValidateUnknownField() throws Exception {
        getAuthProfile().validateSubjectField(
                new DnFieldValueImpl("X", "foo")
        );
    }

    /**
     * Tests whether validating blank subject field of auth profile fails
     * as expected.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test(expected = Exception.class)
    public void authProfileFailToValidateBlankField() throws Exception {
        getAuthProfile().validateSubjectField(
                new DnFieldValueImpl("serialNumber", "")
        );
    }

    /**
     * Tests whether creating subject Dn of auth profile succeeds as expected.
     */
    @Test
    public void authProfileCreateSubjectDn() {
        assertEquals(
                new X500Principal("CN=server, serialNumber=foo, O=bar"),
                getAuthProfile().createSubjectDn(
                        new DnFieldValue[]{
                                new DnFieldValueImpl("CN", "server"),
                                new DnFieldValueImpl("serialNumber", "foo"),
                                new DnFieldValueImpl("O", "bar"),
                        }
                )
        );
    }

    // ------------------------------------------------------------------------

    private CertificateProfileInfoProvider provider() {
        return new BasicCertificateProfileInfoProvider();
    }

    private SignCertificateProfileInfo getSignProfile() {
        return provider().getSignCertProfile(new SignCertificateProfileInfo.Parameters() {
            @Override
            public ClientId getClientId() {
                return ClientId.Conf.create("XX", "bar", "baz");
            }

            @Override
            public String getMemberName() {
                return "foobar";
            }

            @Override
            public SecurityServerId getServerId() {
                return null;
            }
        });
    }

    private AuthCertificateProfileInfo getAuthProfile() {
        return provider().getAuthCertProfile(new AuthCertificateProfileInfo.Parameters() {
            @Override
            public SecurityServerId getServerId() {
                return SecurityServerId.Conf.create("XX", "foo", "bar", "server");
            }

            @Override
            public String getMemberName() {
                return "foobar";
            }
        });
    }
}
