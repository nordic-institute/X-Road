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
import java.util.regex.Pattern;

import static ee.ria.xroad.common.util.CertUtils.getRDNValue;

/**
 * Helper class for decoding ClientId from Finnish X-Road instance signing certificates.
 */
public final class FISubjectClientIdDecoder {

    public static final int NUM_COMPONENTS = 3;

    private FISubjectClientIdDecoder() {
        //utility class
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
            return parseClientIdFromLegacyName(x500name);
        }
        return parseClientId(x500name);
    }

    /*
     * The encoding for clientID:
     * <ul>
     *  <li>C = FI (country code must be 'FI' when using this decoder)</li>
     *  <li>O = organization (must be present)
     *  <li>CN = memberCode (business code without "Y" prefix)</li>
     *  <li>serialNumber = instanceIdentifier;serverCode;memberClass
     * </ul>
     */

    private static final Pattern SPLIT_PATTERN = Pattern.compile("/");

    private static ClientId.Conf parseClientId(X500Name x500name) {
        String c = getRDNValue(x500name, BCStyle.C);
        if (!"FI".equals(c)) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain valid country code");
        }

        if (getRDNValue(x500name, BCStyle.O) == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain organization");
        }

        String memberCode = getRDNValue(x500name, BCStyle.CN);
        if (memberCode == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain common name");
        }

        String serialNumber = getRDNValue(x500name, BCStyle.SERIALNUMBER);
        if (serialNumber == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain serial number");
        }

        final String[] components = SPLIT_PATTERN.split(serialNumber);
        if (components.length != NUM_COMPONENTS) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name's attribute serialNumber has invalid value");
        }

        // Note. components[1] = serverCode, unused
        return ClientId.Conf.create(
                components[0], // instanceId
                components[2], // memberClass
                memberCode);

    }


    /*
     * The legacy encoding for clientID:
     * <ul>
     *  <li>C = FI (country code must be 'FI' when using this decoder)</li>
     *  <li>O = instanceId</li>
     *  <li>OU = memberClass</li>
     *  <li>CN = memberCode (business code without "Y" prefix)</li>
     * </ul>
     */
    private static ClientId.Conf parseClientIdFromLegacyName(X500Name x500name) {
        String c = getRDNValue(x500name, BCStyle.C);
        if (!"FI".equals(c)) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain valid country code");
        }

        String instanceId = getRDNValue(x500name, BCStyle.O);
        if (instanceId == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain organization");
        }

        String memberClass = getRDNValue(x500name, BCStyle.OU);
        if (memberClass == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain organization unit");
        }

        String memberCode = getRDNValue(x500name, BCStyle.CN);
        if (memberCode == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain common name");
        }

        return ClientId.Conf.create(instanceId, memberClass, memberCode);
    }
}
