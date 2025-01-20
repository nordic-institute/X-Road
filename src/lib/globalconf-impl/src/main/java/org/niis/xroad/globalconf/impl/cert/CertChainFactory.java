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
package org.niis.xroad.globalconf.impl.cert;

import ee.ria.xroad.common.CodedException;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_CERT_PATH;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;

@RequiredArgsConstructor
public class CertChainFactory {
    private final GlobalConfProvider globalConfProvider;

    /**
     * Builds certificate chain form the given array of certificates
     * (ordered with the user's certificate first and the root
     * certificate authority last).
     *
     * @param instanceIdentifier the instance identifier
     * @param chain              the certificate chain
     * @return the certificate chain
     */
    public CertChain create(String instanceIdentifier,
                            X509Certificate[] chain) {
        if (chain.length < 2) {
            throw new CodedException(X_CANNOT_CREATE_CERT_PATH,
                    "Chain must have at least user's certificate "
                            + "and root certificate authority");
        }

        X509Certificate trustAnchor = chain[chain.length - 1];
        List<X509Certificate> additionalCerts = new ArrayList<>();
        if (chain.length > 2) {
            additionalCerts.addAll(Arrays.asList(
                    Arrays.copyOfRange(chain, 1, chain.length - 1)));
        }

        try {
            return new CertChain(instanceIdentifier, chain[0], trustAnchor,
                    additionalCerts);
        } catch (Exception ex) {
            throw translateWithPrefix(X_CANNOT_CREATE_CERT_PATH, ex);
        }
    }

    /**
     * Builds certificate chain from cert to trusted root.
     *
     * @param instanceIdentifier the instance identifier
     * @param cert               the end entity certificate
     * @param additionalCerts    additional certificates that can be used to
     *                           construct the cert chain.
     * @return the certificate chain
     */
    public CertChain create(String instanceIdentifier,
                            X509Certificate cert, List<X509Certificate> additionalCerts) {
        try {
            X509Certificate trustAnchor = globalConfProvider.getCaCert(instanceIdentifier, cert);
            return new CertChain(instanceIdentifier, cert, trustAnchor,
                    additionalCerts != null
                            ? additionalCerts : new ArrayList<X509Certificate>());
        } catch (Exception ex) {
            throw translateWithPrefix(X_CANNOT_CREATE_CERT_PATH, ex);
        }
    }
}
