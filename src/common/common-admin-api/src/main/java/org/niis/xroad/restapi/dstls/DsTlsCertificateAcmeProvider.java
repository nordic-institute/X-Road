/*
 * The MIT License
 *
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
package org.niis.xroad.restapi.dstls;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

/**
 * {@link org.niis.xroad.restapi.service.DsTlsCertificateService}'s seam into ACME ordering. Implemented by the
 * shared ACME Spring module on top of the DS TLS ACME engine and each product's own DS TLS ACME host context;
 * each product wires that implementation as a Spring bean.
 * <p>
 * The shared service resolves an implementation of this interface at call time (e.g. via {@code
 * ObjectProvider#getIfAvailable()}), never through its own constructor, so that an implementation is free to
 * depend on product-specific beans without ever forming a cycle back into the shared service. For that same
 * reason, an implementation of this interface must never depend on
 * {@link org.niis.xroad.restapi.service.DsTlsCertificateService}.
 * <p>
 * No bean of this type wired means ACME ordering is unavailable for this product/deployment: the shared service
 * reports {@link DsTlsAcmeAvailability#available()} as {@code false} and rejects an order with a documented 4xx.
 */
public interface DsTlsCertificateAcmeProvider {

    /**
     * @return whether ACME ordering is currently available, and if so, from which designated certification
     *     authorities and under which public hostname
     */
    DsTlsAcmeAvailability getAvailability();

    /**
     * Orders a DataSpace TLS certificate from {@code caName}'s ACME server for {@code distinguishedName} and
     * {@code subjectAltName}, reusing the given key. Orders a fresh certificate when {@code currentCertificate}
     * is {@code null}; otherwise references it so the CA's ACME Renewal Information can track the replacement.
     *
     * @param caName             name of a designated, ACME-capable DS TLS certification authority
     * @param distinguishedName  the CSR's subject, exactly as entered
     * @param subjectAltName     the CSR's single DNS subject alternative name, also the ACME order's identifier
     * @param privateKey         the stored DS TLS private key the CSR and resulting certificate are built for
     * @param publicKey          the public key matching {@code privateKey}
     * @param currentCertificate the currently stored DS TLS certificate, or {@code null} if none exists yet
     * @return the issued certificate chain and its next ACME renewal time
     * @throws IllegalArgumentException if {@code distinguishedName} cannot be parsed as an X.500 name
     * @throws org.niis.xroad.common.exception.BadRequestException if {@code caName} does not name a designated,
     *                                                              ACME-capable DS TLS certification authority
     */
    DsTlsAcmeOrderResult order(String caName, String distinguishedName, String subjectAltName,
                              PrivateKey privateKey, PublicKey publicKey, X509Certificate currentCertificate);
}
