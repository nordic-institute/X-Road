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
package ee.ria.xroad.common.cert;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Holds the certificate chain containing the trusted root certificate,
 * any intermediate certificates and end entity certificate.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class CertChain {

    /**
     * Holds the instanceIdentifier.
     */
    private final String instanceIdentifier;

    /**
     * Holds the end entity certificate.
     */
    private final X509Certificate endEntityCert;

    /**
     * Holds the trusted root certificate.
     */
    private final X509Certificate trustedRootCert;

    /**
     * Holds any additional certificates (intermediates).
     */
    private List<X509Certificate> additionalCerts = new ArrayList<>();

    /**
     * @return the complete chain used to create this instance,
     * starting with the end entity and ending with trusted root.
     */
    public List<X509Certificate> getAllCerts() {
        List<X509Certificate> allCerts = new ArrayList<>();
        allCerts.add(getEndEntityCert());
        allCerts.addAll(getAdditionalCerts());
        allCerts.add(getTrustedRootCert());

        return allCerts;
    }

    /**
     * @return the chain used to create this instance withouth the trusted root,
     * starting with the end entity followed by any additional certs.
     */
    public List<X509Certificate> getAllCertsWithoutTrustedRoot() {
        List<X509Certificate> allCerts = new ArrayList<>();
        allCerts.add(getEndEntityCert());
        allCerts.addAll(getAdditionalCerts());

        return allCerts;
    }

    /**
     * @return the minimum of the certificate chain notAfter values
     */
    public Date notAfter() {
        Date minNotAfter = endEntityCert.getNotAfter();
        Date tmp = trustedRootCert.getNotAfter();
        if (tmp.before(minNotAfter)) minNotAfter = tmp;

        for (X509Certificate c : additionalCerts) {
            tmp = c.getNotAfter();
            if (tmp.before(minNotAfter)) {
                minNotAfter = tmp;
            }
        }
        return minNotAfter;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Trusted root certificate:\n").append(trustedRootCert);
        sb.append("\n\n");
        sb.append("Intermediate certificates:\n");
        additionalCerts.forEach(c -> sb.append(c));
        sb.append("\n\n");
        sb.append("End entity certificate:\n").append(endEntityCert);
        return sb.toString();
    }
}
