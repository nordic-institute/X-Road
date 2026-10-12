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

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERT61String;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.DERUniversalString;
import org.bouncycastle.asn1.DERVisibleString;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.security.auth.x500.X500Principal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks that {@link X500Names#fromPrincipal(X500Principal)} keeps the attributes of the principal in reversed RDN
 * order without re-encoding them, and that the values read from the result equal those of parsing
 * {@link X500Principal#getName()} with the default BouncyCastle style.
 */
class X500NamesFromPrincipalEncodingTest {
    private static final ASN1ObjectIdentifier ORGANIZATION_IDENTIFIER = new ASN1ObjectIdentifier("2.5.4.97");
    private static final ASN1ObjectIdentifier UNKNOWN_OID = new ASN1ObjectIdentifier("1.3.6.1.4.1.99999.1");

    static Stream<Arguments> cases() {
        return Stream.of(
                row("serialNumber UTF8",
                        rdn(BCStyle.C, ps("FI")), rdn(BCStyle.SERIALNUMBER, utf8("DEV/ss1/COM")), rdn(BCStyle.CN, utf8("222"))),
                row("serialNumber Printable",
                        rdn(BCStyle.C, ps("FI")), rdn(BCStyle.SERIALNUMBER, ps("DEV/ss1/COM")), rdn(BCStyle.CN, utf8("222"))),
                row("organizationIdentifier Printable",
                        rdn(BCStyle.C, ps("EE")), rdn(ORGANIZATION_IDENTIFIER, ps("NTREE-10000000")), rdn(BCStyle.CN, utf8("Org"))),
                row("organizationIdentifier UTF8",
                        rdn(BCStyle.C, ps("EE")), rdn(ORGANIZATION_IDENTIFIER, utf8("NTREE-10000000")), rdn(BCStyle.CN, utf8("Org"))),
                row("emailAddress UTF8",
                        rdn(BCStyle.CN, utf8("host")), rdn(BCStyle.EmailAddress, utf8("ops@example.org"))),
                row("emailAddress IA5",
                        rdn(BCStyle.CN, utf8("host")), rdn(BCStyle.EmailAddress, ia5("ops@example.org"))),
                row("surname Printable",
                        rdn(BCStyle.C, ps("EE")), rdn(BCStyle.SURNAME, ps("Tester")), rdn(BCStyle.CN, utf8("Tester"))),
                row("givenName Printable",
                        rdn(BCStyle.C, ps("EE")), rdn(BCStyle.GIVENNAME, ps("Test")), rdn(BCStyle.CN, utf8("Test"))),
                row("title Printable",
                        rdn(BCStyle.T, ps("Officer")), rdn(BCStyle.CN, utf8("Test"))),
                row("unknown OID Printable",
                        rdn(UNKNOWN_OID, ps("value")), rdn(BCStyle.CN, utf8("Test"))),
                row("unknown OID UTF8",
                        rdn(UNKNOWN_OID, utf8("value")), rdn(BCStyle.CN, utf8("Test"))),
                row("businessCategory Printable",
                        rdn(BCStyle.BUSINESS_CATEGORY, ps("COM")), rdn(BCStyle.SERIALNUMBER, ps("222")),
                        rdn(BCStyle.CN, utf8("Test"))),
                row("CN VisibleString",
                        rdn(BCStyle.C, ps("FI")), rdn(BCStyle.CN, new DERVisibleString("host"))),
                row("CN UniversalString",
                        rdn(BCStyle.C, ps("FI")), rdn(BCStyle.CN, universal("host"))),
                row("uniqueIdentifier BitString",
                        rdn(BCStyle.UNIQUE_IDENTIFIER, new DERBitString(new byte[] {0x01, 0x02})),
                        rdn(BCStyle.CN, utf8("Test"))),
                row("CN Printable",
                        rdn(BCStyle.C, ps("FI")), rdn(BCStyle.CN, ps("host"))),
                row("CN T61 and O BMP",
                        rdn(BCStyle.C, ps("FI")), rdn(BCStyle.O, new DERBMPString("Org")), rdn(BCStyle.CN, new DERT61String("host"))),
                row("O UTF8 non-ASCII",
                        rdn(BCStyle.C, ps("EE")), rdn(BCStyle.O, utf8("Ääre OÜ")), rdn(BCStyle.CN, utf8("Õun"))),
                row("O UTF8 special characters",
                        rdn(BCStyle.C, ps("FI")), rdn(BCStyle.O, utf8("#A, B + \"C\" <D>; E\\F ")),
                        rdn(BCStyle.CN, utf8("host"))),
                row("multi-valued CN+serialNumber",
                        rdn(BCStyle.C, ps("FI")),
                        new RDN(new AttributeTypeAndValue[] {
                            new AttributeTypeAndValue(BCStyle.CN, utf8("222")),
                            new AttributeTypeAndValue(BCStyle.SERIALNUMBER, ps("DEV/ss1/COM"))})),
                row("profile FI",
                        rdn(BCStyle.C, ps("FI")), rdn(BCStyle.O, utf8("Test Org")),
                        rdn(BCStyle.SERIALNUMBER, ps("DEV/ss1/COM")), rdn(BCStyle.CN, utf8("222"))),
                row("profile FI legacy",
                        rdn(BCStyle.C, ps("FI")), rdn(BCStyle.O, utf8("DEV")), rdn(BCStyle.OU, utf8("COM")),
                        rdn(BCStyle.CN, utf8("222"))),
                row("profile SK EsteID",
                        rdn(BCStyle.C, ps("EE")), rdn(BCStyle.CN, utf8("TESTER,TEST,10101010005")),
                        rdn(BCStyle.SURNAME, utf8("TESTER")), rdn(BCStyle.GIVENNAME, utf8("TEST")),
                        rdn(BCStyle.SERIALNUMBER, ps("PNOEE-10101010005"))),
                row("profile SK Klass3 orgId UTF8",
                        rdn(BCStyle.C, ps("EE")), rdn(BCStyle.O, utf8("Test Org OÜ")),
                        rdn(ORGANIZATION_IDENTIFIER, utf8("NTREE-10000000")), rdn(BCStyle.CN, utf8("Test Org OÜ"))),
                row("profile SK Klass3 serialNumber",
                        rdn(BCStyle.C, ps("EE")), rdn(BCStyle.O, utf8("Test Org")),
                        rdn(BCStyle.SERIALNUMBER, ps("10000000")), rdn(BCStyle.CN, utf8("Test Org"))),
                row("profile IS",
                        rdn(BCStyle.C, ps("IS")), rdn(BCStyle.O, utf8("DEV")), rdn(BCStyle.OU, utf8("COM")),
                        rdn(BCStyle.CN, utf8("222")), rdn(BCStyle.SERIALNUMBER, ps("ss1"))),
                row("profile FO",
                        rdn(BCStyle.C, ps("FO")), rdn(BCStyle.O, utf8("DEV")), rdn(BCStyle.OU, utf8("COM")),
                        rdn(BCStyle.CN, utf8("222")), rdn(BCStyle.SERIALNUMBER, ps("ss1"))),
                row("profile EJBCA C=DE",
                        rdn(BCStyle.C, ps("DE")), rdn(BCStyle.O, utf8("COM")), rdn(BCStyle.CN, utf8("222"))),
                row("profile EJBCA C=DEV",
                        rdn(BCStyle.C, ps("DEV")), rdn(BCStyle.O, utf8("COM")), rdn(BCStyle.CN, utf8("222"))),
                row("profile Basic",
                        rdn(BCStyle.C, ps("FI")), rdn(BCStyle.O, utf8("Test Org")),
                        rdn(BCStyle.BUSINESS_CATEGORY, utf8("COM")), rdn(BCStyle.SERIALNUMBER, ps("222")),
                        rdn(BCStyle.CN, utf8("ss1.example.org"))),
                row("profile ACME",
                        rdn(BCStyle.CN, utf8("ss1.example.org"))),
                row("CA issuer typical",
                        rdn(BCStyle.C, ps("FI")), rdn(BCStyle.O, utf8("Test CA Org")),
                        rdn(ORGANIZATION_IDENTIFIER, utf8("NTRFI-1234567-8")), rdn(BCStyle.CN, utf8("Test CA"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void keepsOriginalAttributesInReversedOrder(String label, X500Name certificateName) throws IOException {
        X500Principal principal = new X500Principal(certificateName.getEncoded());
        RDN[] principalRdns = X500Name.getInstance(principal.getEncoded()).getRDNs();

        RDN[] actual = X500Names.fromPrincipal(principal).getRDNs();

        assertEquals(principalRdns.length, actual.length, label + ": RDN count");
        for (int i = 0; i < actual.length; i++) {
            AttributeTypeAndValue[] expectedAttributes = principalRdns[principalRdns.length - 1 - i].getTypesAndValues();
            AttributeTypeAndValue[] actualAttributes = actual[i].getTypesAndValues();
            assertEquals(expectedAttributes.length, actualAttributes.length, label + ": attribute count in RDN " + i);
            for (int j = 0; j < actualAttributes.length; j++) {
                assertArrayEquals(derOf(expectedAttributes[j]), derOf(actualAttributes[j]), label + ": RDN " + i + "." + j);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void readsSameValuesAsParsingPrincipalName(String label, X500Name certificateName) throws IOException {
        X500Principal principal = new X500Principal(certificateName.getEncoded());
        X500Name parsed;
        try {
            parsed = new X500Name(principal.getName());
        } catch (IllegalArgumentException e) {
            assumeTrue(false, label + ": principal name does not parse");
            return;
        }

        assertValuesEqual(label, certificateName, parsed, X500Names.fromPrincipal(principal));
    }

    @Test
    void readsUniversalStringCommonNameAsParsingPrincipalNameDoes() throws IOException {
        X500Name certificateName = new X500Name(new RDN[] {
            rdn(BCStyle.C, ps("FI")), rdn(BCStyle.CN, universal("host"))});
        X500Principal principal = new X500Principal(certificateName.getEncoded());
        X500Name parsed = new X500Name(principal.getName());
        X500Name actual = X500Names.fromPrincipal(principal);

        assertEquals(CertUtils.getRDNValue(parsed, BCStyle.CN), CertUtils.getRDNValue(actual, BCStyle.CN));
        assertEquals(parsed.toString(), actual.toString());
    }

    @Test
    void readsCountryLongerThanTwoCharacters() throws IOException {
        X500Principal principal = new X500Principal(
                new X500Name(new RDN[] {rdn(BCStyle.C, ps("DEV")), rdn(BCStyle.O, utf8("COM")), rdn(BCStyle.CN, utf8("222"))})
                        .getEncoded());

        X500Name actual = X500Names.fromPrincipal(principal);

        assertEquals("DEV", CertUtils.getRDNValue(actual, BCStyle.C));
        assertEquals("COM", CertUtils.getRDNValue(actual, BCStyle.O));
        assertEquals("222", CertUtils.getRDNValue(actual, BCStyle.CN));
    }

    private static void assertValuesEqual(String label, X500Name certificateName, X500Name expected, X500Name actual) {
        Set<ASN1ObjectIdentifier> types = new LinkedHashSet<>();
        for (RDN rdn : certificateName.getRDNs()) {
            for (AttributeTypeAndValue attribute : rdn.getTypesAndValues()) {
                types.add(attribute.getType());
            }
        }
        for (ASN1ObjectIdentifier type : types) {
            assertEquals(CertUtils.getRDNValue(expected, type), CertUtils.getRDNValue(actual, type),
                    label + ": value of " + type.getId());
        }
    }

    private static byte[] derOf(AttributeTypeAndValue attribute) {
        try {
            return attribute.getEncoded(ASN1Encoding.DER);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Arguments row(String label, RDN... rdns) {
        return Arguments.of(label, new X500Name(rdns));
    }

    private static RDN rdn(ASN1ObjectIdentifier type, ASN1Encodable value) {
        return new RDN(type, value);
    }

    private static DERPrintableString ps(String value) {
        return new DERPrintableString(value);
    }

    private static DERUTF8String utf8(String value) {
        return new DERUTF8String(value);
    }

    private static DERIA5String ia5(String value) {
        return new DERIA5String(value);
    }

    private static DERUniversalString universal(String value) {
        byte[] ucs4 = new byte[value.length() * 4];
        for (int i = 0; i < value.length(); i++) {
            ucs4[i * 4 + 3] = (byte) value.charAt(i);
        }
        return new DERUniversalString(ucs4);
    }
}
