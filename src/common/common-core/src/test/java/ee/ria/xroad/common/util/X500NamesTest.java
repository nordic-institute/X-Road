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
package ee.ria.xroad.common.util;

import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;

import java.security.KeyPairGenerator;

import static ee.ria.xroad.common.DerSubjectCertUtil.derName;
import static ee.ria.xroad.common.DerSubjectCertUtil.selfSignedCertificate;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class X500NamesTest {
    private static final String LONG_COMMON_NAME = "x".repeat(65);

    @Test
    void fromStringAcceptsCountryThatIsNotTwoCharacters() throws Exception {
        X500Name name = X500Names.fromString("C=DEV, O=COM, CN=222");
        assertEquals("DEV", CertUtils.getRDNValue(name, BCStyle.C));
        assertArrayEquals(
                Hex.decode("302a310c300a06035504061303444556310c300a060355040a0c03434f4d310c300a06035504030c03323232"),
                name.getEncoded());
    }

    @Test
    void fromStringAcceptsCommonNameOverSixtyFourCharacters() throws Exception {
        X500Name name = X500Names.fromString("CN=" + LONG_COMMON_NAME);
        assertEquals(LONG_COMMON_NAME, CertUtils.getRDNValue(name, BCStyle.CN));
        assertArrayEquals(derName("CN", LONG_COMMON_NAME).getEncoded(), name.getEncoded());
    }

    @Test
    void fromStringEncodesValidCountryAsBefore() throws Exception {
        X500Name name = X500Names.fromString("C=FI, O=Org, CN=host.example.org, serialNumber=123");
        assertArrayEquals(
                Hex.decode("3044310b3009060355040613024649310c300a060355040a0c034f72673119301706035504030c10686f"
                        + "73742e6578616d706c652e6f7267310c300a06035504051303313233"),
                name.getEncoded());
    }

    @Test
    void fromPrincipalReadsEncodedNameWithoutValidation() throws Exception {
        X500Name original = derName("C", "DEV", "O", "COM", "CN", LONG_COMMON_NAME);
        X500Name name = X500Names.fromPrincipal(new X500Principal(original.getEncoded()));
        assertEquals("DEV", CertUtils.getRDNValue(name, BCStyle.C));
        assertEquals(LONG_COMMON_NAME, CertUtils.getRDNValue(name, BCStyle.CN));
        assertArrayEquals(derName("CN", LONG_COMMON_NAME, "O", "COM", "C", "DEV").getEncoded(), name.getEncoded());
    }

    @Test
    void fromPrincipalReturnsMostSpecificValueOfRepeatedAttributeFirst() throws Exception {
        X500Name original = derName("C", "FI", "O", "Org", "OU", "outerUnit", "OU", "innerUnit",
                "CN", "outer", "CN", "inner");
        X500Name name = X500Names.fromPrincipal(new X500Principal(original.getEncoded()));
        assertEquals("inner", CertUtils.getRDNValue(name, BCStyle.CN));
        assertEquals("innerUnit", CertUtils.getRDNValue(name, BCStyle.OU));
        assertEquals("CN=inner,CN=outer,OU=innerUnit,OU=outerUnit,O=Org,C=FI", name.toString());
    }

    @Test
    void fromPrincipalKeepsRepeatedAttributeSubjectInReversedOrder() throws Exception {
        X500Name original = derName("C", "FI", "O", "Org", "CN", "outer", "CN", "inner");
        X500Name name = X500Names.fromPrincipal(new X500Principal(original.getEncoded()));
        assertArrayEquals(
                Hex.decode("303b310e300c06035504030c05696e6e6572310e300c06035504030c056f75746572"
                        + "310c300a060355040a0c034f7267310b3009060355040613024649"),
                name.getEncoded());
    }

    @Test
    void fromPrincipalKeepsOriginalStringTypes() throws Exception {
        X500NameBuilder builder = new X500NameBuilder(BCStyle.INSTANCE);
        builder.addRDN(BCStyle.C, new DERPrintableString("FI"));
        builder.addRDN(BCStyle.O, new DERPrintableString("Org"));
        builder.addRDN(BCStyle.CN, new DERPrintableString("host"));
        X500Name name = X500Names.fromPrincipal(new X500Principal(builder.build().getEncoded()));

        X500NameBuilder reversed = new X500NameBuilder(BCStyle.INSTANCE);
        reversed.addRDN(BCStyle.CN, new DERPrintableString("host"));
        reversed.addRDN(BCStyle.O, new DERPrintableString("Org"));
        reversed.addRDN(BCStyle.C, new DERPrintableString("FI"));
        assertArrayEquals(reversed.build().getEncoded(), name.getEncoded());
    }

    @Test
    void certUtilsReadSubjectOfCertificateWithCountryDevAndLongCommonName() {
        var cert = selfSignedCertificate(derName("C", "DEV", "O", "COM", "CN", LONG_COMMON_NAME, "serialNumber", "222"));
        assertEquals(LONG_COMMON_NAME, CertUtils.getSubjectCommonName(cert));
        assertEquals(LONG_COMMON_NAME, CertUtils.getIssuerCommonName(cert));
        assertEquals("222", CertUtils.getSubjectSerialNumber(cert));
    }

    @Test
    void certUtilsGetCommonNameParsesSubjectStringWithLongCommonName() {
        assertEquals(LONG_COMMON_NAME, CertUtils.getCommonName("C=DEV, O=COM, CN=" + LONG_COMMON_NAME));
    }

    @Test
    void createSelfSignedCertificateAcceptsLongCommonName() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("RSA");
        keyPair.initialize(2048);
        var chain = CertUtils.createSelfSignedCertificate(LONG_COMMON_NAME, keyPair.generateKeyPair(), 1);
        assertEquals(LONG_COMMON_NAME, CertUtils.getSubjectCommonName(chain[0]));
    }
}
