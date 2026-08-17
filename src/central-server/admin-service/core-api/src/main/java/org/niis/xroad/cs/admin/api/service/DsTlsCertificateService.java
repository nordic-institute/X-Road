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
package org.niis.xroad.cs.admin.api.service;

import org.niis.xroad.cs.admin.api.dto.CertificateDetails;

/**
 * Manages the manually uploaded DataSpace TLS certificate stored in OpenBao at {@code tls/ds-https}.
 */
public interface DsTlsCertificateService {

    /**
     * @return the current DS TLS certificate's details, or {@code null} if none has been provisioned yet
     */
    CertificateDetails getTlsCertificateDetails();

    /**
     * @return the current DS TLS certificate packaged as a gzip compressed tar archive
     */
    byte[] getTlsCertificateTar();

    /**
     * Validates and stores an operator-provided private key and certificate chain.
     *
     * @param keyBytes              PEM encoded private key
     * @param certificateChainBytes PEM encoded certificate chain, leaf certificate first
     * @return the stored certificate's details
     */
    CertificateDetails uploadCertificate(byte[] keyBytes, byte[] certificateChainBytes);
}
