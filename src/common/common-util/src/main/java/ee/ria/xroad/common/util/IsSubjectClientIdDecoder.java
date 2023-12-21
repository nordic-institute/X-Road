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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.identifier.ClientId;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;

import javax.security.auth.x500.X500Principal;

import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.util.CertUtils.getRDNValue;

/**
 * Helper class for decoding ClientId from Icelandic
 * X-Road instance signing certificates.
 */
public final class IsSubjectClientIdDecoder {

    private IsSubjectClientIdDecoder() {
        // utility class
    }

    /**
     * @param cert certificate from which to construct the client ID
     * @return a fully constructed Client identifier from DN of the certificate.
     */
    public static ClientId.Conf getSubjectClientId(X509Certificate cert) {
        X500Principal principal = cert.getSubjectX500Principal();
        X500Name x500name = new X500Name(principal.getName());

        if (getRDNValue(x500name, BCStyle.SERIALNUMBER) == null) {
            if (getRDNValue(x500name, BCStyle.OU) == null) {
                return CertUtils.getSubjectClientId(cert);
            }
        }
        return parseClientId(x500name);
    }

     /**
     * The encoding for clientID:
     *
     *  C  = IS (country code must be 'IS' when using this decoder)
     *  O  = instance identifier (must be present)
     *  OU = memberClass
     *  CN = memberCode
     *  serialNumber = serverId, not used
     */
    private static ClientId.Conf parseClientId(X500Name x500name) {

        // Country Code Identifier
        String memberCountry = getRDNValue(x500name, BCStyle.C);
        if (!"IS".equals(memberCountry)) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                "Certificate subject name does not contain valid country code");
        }

        // Instance Identifier
        String memberInstance = getRDNValue(x500name, BCStyle.O);
        if (memberInstance == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                "Certificate subject name does not contain organization");
        }

        // Member Class Identifier
        String memberClass = getRDNValue(x500name, BCStyle.OU);
        if (memberClass == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                "Certificate subject name does not contain organization unit");
        }

        // Member Class Identifier
        String memberCode = getRDNValue(x500name, BCStyle.CN);
        if (memberCode == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                "Certificate subject name does not contain common name");
        }

        // Check if the Serial Number is present
        if (getRDNValue(x500name, BCStyle.SERIALNUMBER) == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                "Certificate subject name does not contain serial number");
        }

        // Call factory method for creating a new ClientId.
        return ClientId.Conf.create(memberInstance, memberClass, memberCode);

    }

}
