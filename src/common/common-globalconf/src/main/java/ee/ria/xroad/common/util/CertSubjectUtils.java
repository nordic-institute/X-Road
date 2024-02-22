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

import lombok.experimental.UtilityClass;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;

import javax.security.auth.x500.X500Principal;

import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.util.CertUtils.getRDNValue;

@UtilityClass
public class CertSubjectUtils {

    /**
     * @param cert certificate from which to construct the client ID
     * @return a fully constructed Client identifier from DN of the certificate.
     */
    public static ClientId.Conf getSubjectClientId(X509Certificate cert) {
        X500Principal principal = cert.getSubjectX500Principal();
        X500Name x500name = new X500Name(principal.getName());

        String c = getRDNValue(x500name, BCStyle.C);

        if (c == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain country code");
        }

        String o = getRDNValue(x500name, BCStyle.O);

        if (o == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain organization");
        }

        String cn = getRDNValue(x500name, BCStyle.CN);

        if (cn == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain common name");
        }

        return ClientId.Conf.create(c, o, cn);
    }
}
