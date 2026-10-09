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
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;

import javax.security.auth.x500.X500Principal;

/**
 * Builds BouncyCastle {@link X500Name} instances without BouncyCastle's per-attribute length validation,
 * which rejects a country value that is not 2 characters (X-Road uses the instance identifier as C)
 * and a common name over 64 characters.
 */
public final class X500Names {
    private static final LenientBcStyle LENIENT_STYLE = new LenientBcStyle();

    private X500Names() {
    }

    /**
     * Parses a distinguished name string. Values are encoded the same way as {@link BCStyle} does,
     * only the length checks are skipped.
     */
    public static X500Name fromString(String distinguishedName) {
        return new X500Name(LENIENT_STYLE, distinguishedName);
    }

    /**
     * Reads the name from its DER encoding, so no string parsing or validation takes place.
     * The RDNs are ordered as in {@link X500Principal#getName()}, the most specific one first, and string values are
     * encoded as {@link #fromString(String)} encodes them.
     */
    public static X500Name fromPrincipal(X500Principal principal) {
        RDN[] derOrder = X500Name.getInstance(principal.getEncoded()).getRDNs();
        RDN[] reversed = new RDN[derOrder.length];
        for (int i = 0; i < derOrder.length; i++) {
            reversed[derOrder.length - 1 - i] = reencode(derOrder[i]);
        }
        return new X500Name(LENIENT_STYLE, reversed);
    }

    private static RDN reencode(RDN rdn) {
        AttributeTypeAndValue[] attributes = rdn.getTypesAndValues();
        for (int i = 0; i < attributes.length; i++) {
            if (attributes[i].getValue() instanceof ASN1String value) {
                attributes[i] = new AttributeTypeAndValue(attributes[i].getType(),
                        LENIENT_STYLE.encodeStringValue(attributes[i].getType(), value.getString()));
            }
        }
        return new RDN(attributes);
    }

    private static final class LenientBcStyle extends BCStyle {
        @Override
        protected ASN1Encodable encodeStringValue(ASN1ObjectIdentifier oid, String value) {
            if (oid.equals(EmailAddress) || oid.equals(DC)) {
                return new DERIA5String(value);
            }

            if (oid.equals(DATE_OF_BIRTH)) {
                return new ASN1GeneralizedTime(value);
            }

            if (oid.equals(C) || oid.equals(SERIALNUMBER) || oid.equals(DN_QUALIFIER)
                    || oid.equals(TELEPHONE_NUMBER) || oid.equals(JURISDICTION_C)) {
                return new DERPrintableString(value);
            }

            return new DERUTF8String(value);
        }
    }
}
