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
package ee.ria.xroad.common.util;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;

import javax.security.auth.x500.X500Principal;

import java.security.cert.X509Certificate;

/**
 * Name extractor for SK certificates.
 */
public final class SkCprKlass3 {

    private static final int SN_LENGTH = 8;

    private SkCprKlass3() {
    }

    /**
     * Extracts subject identifier from a certificate.
     * @param cert the certificate
     * @return String array containing member class and serial number
     * @throws Exception if any errors occur
     */
    public static String[] getSubjectIdentifier(X509Certificate cert)
            throws Exception {
        X500Principal p = cert.getSubjectX500Principal();
        return getSubjectIdentifier(new X500Name(p.getName()));
    }

    static String[] getSubjectIdentifier(X500Name x500name) throws Exception {
        String sn = CertUtils.getRDNValue(x500name, BCStyle.SERIALNUMBER);
        if (sn == null) {
            throw new Exception("Subject name does not contain serial number");
        }

        if (sn.length() != SN_LENGTH) {
            throw new Exception("Serial number length must be " + SN_LENGTH);
        }

        return new String[] {getMemberClass(sn), sn};
    }

    // Returns the hardcoded member class based on the first number in
    // the serial number.
    private static String getMemberClass(String sn) throws Exception {
        switch (sn.charAt(0)) {
            case '1': // Fall through
            case '2': // Fall through
            case '3': // Fall through
            case '4': // Fall through
            case '5': // Fall through
            case '6':
                return "COM";
            case '7':
                return "GOV";
            case '8': // Fall through
            case '9':
                return "NGO";
            default:
                throw new Exception("Certificate does not match policy: "
                        + "registry code must start with 1, 7, 8 or 9");
        }
    }
}
