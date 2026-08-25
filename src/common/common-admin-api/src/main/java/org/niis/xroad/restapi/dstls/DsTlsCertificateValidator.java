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

import ee.ria.xroad.common.util.CryptoUtils;

import org.niis.xroad.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Collection;

import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_EXPIRED;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_NOT_YET_VALID;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_PARSE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_KEY_CERTIFICATE_MISMATCH;

/**
 * Shared validator for a manually uploaded DS TLS certificate chain, used by both Security Server and Central
 * Server admin services. The private key never crosses the admin API: the key is generated and kept server-side,
 * and an upload only ever carries a certificate chain whose leaf public key must match the stored key.
 * <p>
 * Checks that the chain parses, that the leaf certificate's validity window covers the current time, and that
 * the leaf certificate's public key equals the expected (stored) public key.
 * <p>
 * Deliberately does not verify that the chain is signed by a designated {@code approvedDsTlsCa} entry -
 * peers enforce that trust, the upload only needs to be internally consistent.
 */
@Component
public class DsTlsCertificateValidator {

    /**
     * Parses and validates an uploaded certificate chain against the expected public key.
     *
     * @param expectedPublicKey     the public key the leaf certificate must carry, i.e. the stored DS TLS key's
     *                              public key
     * @param certificateChainBytes PEM encoded certificate or certificate chain, leaf certificate first
     * @return the parsed certificate chain, leaf certificate first
     * @throws BadRequestException with an actionable {@link org.niis.xroad.common.core.exception.ErrorCode}
     *                              if the material is malformed, expired/not yet valid, or the leaf certificate's
     *                              public key does not match the expected key
     */
    public X509Certificate[] validate(PublicKey expectedPublicKey, byte[] certificateChainBytes) {
        X509Certificate[] chain = parseCertificateChain(certificateChainBytes);
        X509Certificate leaf = chain[0];

        checkValidityWindow(leaf);
        checkKeyMatches(expectedPublicKey, leaf.getPublicKey());

        return chain;
    }

    private X509Certificate[] parseCertificateChain(byte[] certificateChainBytes) {
        Collection<X509Certificate> certificates;
        try {
            certificates = CryptoUtils.readCertificates(certificateChainBytes);
        } catch (Exception e) {
            throw new BadRequestException(e, DS_TLS_CERTIFICATE_PARSE_FAILED.build());
        }
        if (certificates.isEmpty()) {
            throw new BadRequestException(DS_TLS_CERTIFICATE_PARSE_FAILED.build());
        }
        return certificates.toArray(X509Certificate[]::new);
    }

    private void checkValidityWindow(X509Certificate leaf) {
        try {
            leaf.checkValidity();
        } catch (CertificateExpiredException e) {
            throw new BadRequestException(e, DS_TLS_CERTIFICATE_EXPIRED.build());
        } catch (CertificateNotYetValidException e) {
            throw new BadRequestException(e, DS_TLS_CERTIFICATE_NOT_YET_VALID.build());
        }
    }

    private void checkKeyMatches(PublicKey expectedPublicKey, PublicKey leafPublicKey) {
        if (!expectedPublicKey.equals(leafPublicKey)) {
            throw new BadRequestException(DS_TLS_KEY_CERTIFICATE_MISMATCH.build());
        }
    }
}
