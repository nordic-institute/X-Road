/**
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

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider;
import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Test;
import org.mockito.Mockito;

import javax.security.auth.x500.X500Principal;

import java.security.cert.X509Certificate;

import static org.bouncycastle.util.Arrays.areEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the implementation of SkKlass3CertificateProfileInfoProvider.
 */
public class SkKlass3CertificateProfileInfoProviderTest {

    /**
     * Tests whether getting expected subject fields of auth and sing profile succeeds as expected.
     */
    @Test
    public void returnsCorrectSubjectFields() {
        DnFieldDescription[] expected = {
                new EnumLocalizedFieldDescriptionImpl(
                        "SN", DnFieldLabelLocalizationKey.SERIAL_NUMBER_SN, "bar").setReadOnly(true),
                new EnumLocalizedFieldDescriptionImpl(
                        "CN", DnFieldLabelLocalizationKey.COMMON_NAME, "foobar").setReadOnly(true)
        };

        assertTrue(areEqual(expected, getSignProfile().getSubjectFields()));
        assertTrue(areEqual(expected, getAuthProfile().getSubjectFields()));
    }

    /**
     * Tests whether subject name parts are read correctly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void getSubjectIdentifierSuccessfully() throws Exception {
        ClientId parts;

        parts = id("SERIALNUMBER=16543212");
        assertEquals("XX", parts.getXRoadInstance());
        assertEquals("COM", parts.getMemberClass());
        assertEquals("16543212", parts.getMemberCode());

        parts = id("SERIALNUMBER=26543212");
        assertEquals("XX", parts.getXRoadInstance());
        assertEquals("COM", parts.getMemberClass());
        assertEquals("26543212", parts.getMemberCode());

        parts = id("SERIALNUMBER=36543212");
        assertEquals("XX", parts.getXRoadInstance());
        assertEquals("COM", parts.getMemberClass());
        assertEquals("36543212", parts.getMemberCode());

        parts = id("SERIALNUMBER=46543212");
        assertEquals("XX", parts.getXRoadInstance());
        assertEquals("COM", parts.getMemberClass());
        assertEquals("46543212", parts.getMemberCode());

        parts = id("SERIALNUMBER=56543212");
        assertEquals("XX", parts.getXRoadInstance());
        assertEquals("COM", parts.getMemberClass());
        assertEquals("56543212", parts.getMemberCode());

        parts = id("SERIALNUMBER=66543212");
        assertEquals("XX", parts.getXRoadInstance());
        assertEquals("COM", parts.getMemberClass());
        assertEquals("66543212", parts.getMemberCode());

        parts = id("SERIALNUMBER=76543212");
        assertEquals("XX", parts.getXRoadInstance());
        assertEquals("GOV", parts.getMemberClass());
        assertEquals("76543212", parts.getMemberCode());

        parts = id("SERIALNUMBER=86543212");
        assertEquals("XX", parts.getXRoadInstance());
        assertEquals("NGO", parts.getMemberClass());
        assertEquals("86543212", parts.getMemberCode());

        parts = id("SERIALNUMBER=96543212");
        assertEquals("XX", parts.getXRoadInstance());
        assertEquals("NGO", parts.getMemberClass());
        assertEquals("96543212", parts.getMemberCode());

        parts = id("2.5.4.97=NTREE-16543212");
        assertEquals("XX", parts.getXRoadInstance());
        assertEquals("COM", parts.getMemberClass());
        assertEquals("16543212", parts.getMemberCode());

        parts = id("2.5.4.97=GO:EE-86543212");
        assertEquals("XX", parts.getXRoadInstance());
        assertEquals("GOV", parts.getMemberClass());
        assertEquals("86543212", parts.getMemberCode());

        parts = id("2.5.4.97=NP:EE-96543212");
        assertEquals("XX", parts.getXRoadInstance());
        assertEquals("NGO", parts.getMemberClass());
        assertEquals("96543212", parts.getMemberCode());

        parts = id("2.5.4.97=NP:FI-96543212");
        assertEquals("XX", parts.getXRoadInstance());
        assertEquals("NEE", parts.getMemberClass());
        assertEquals("NP:FI-96543212", parts.getMemberCode());
    }

    /**
     * Tests whether a invalid serial number length is detected.
     *
     * @throws Exception in case of an expected error
     */
    @Test(expected = Exception.class)
    public void invalidLength() throws Exception {
        id("SERIALNUMBER=123");
        fail();
    }

    /**
     * Tests whether a missing serial number is detected.
     *
     * @throws Exception in case of an expected error
     */
    @Test(expected = Exception.class)
    public void missingSerialNumber() throws Exception {
        id("S=7654321");
        fail();
    }

    /**
     * Tests whether an unknown serial number is detected.
     *
     * @throws Exception in case of an expected error
     */
    @Test(expected = Exception.class)
    public void unknownSerialNumber() throws Exception {
        id("SERIALNUMBER=s1234567");
        fail();
    }

    @Test
    public void getSubjectIdentifier() throws Exception {
        X509Certificate cert = TestCertUtil.getCert("/NTREE.pem");
        ClientId clientId = getSignProfile().getSubjectIdentifier(cert);

        assertEquals("XX", clientId.getXRoadInstance());
        assertEquals("COM", clientId.getMemberClass());
        assertEquals("10747013", clientId.getMemberCode());
    }

    // ------------------------------------------------------------------------

    private ClientId id(String name) throws Exception {
        X509Certificate mockCert = Mockito.mock(X509Certificate.class);

        Mockito.when(mockCert.getSubjectX500Principal()).thenReturn(new X500Principal(name));

        return getSignProfile().getSubjectIdentifier(mockCert);
    }

    private CertificateProfileInfoProvider provider() {
        return new SkKlass3CertificateProfileInfoProvider();
    }

    private SignCertificateProfileInfo getSignProfile() {
        return provider().getSignCertProfile(new SignCertificateProfileInfo.Parameters() {
            @Override
            public ClientId getClientId() {
                return ClientId.Conf.create("XX", "foo", "bar");
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
