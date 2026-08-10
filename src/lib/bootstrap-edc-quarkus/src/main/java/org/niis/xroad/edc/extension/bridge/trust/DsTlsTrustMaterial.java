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
package org.niis.xroad.edc.extension.bridge.trust;

import org.eclipse.edc.spi.EdcException;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;

/**
 * The dataspace TLS trust anchor set loaded from globalconf's {@code approvedDsTlsCa} list, paired
 * with a fingerprint over the certificates' encoded form so
 * {@link org.niis.xroad.common.vault.reload.PeriodicMaterialReloader} can skip re-applying material
 * that has not actually changed.
 */
record DsTlsTrustMaterial(List<X509Certificate> certificates, String fingerprint) {

    static final DsTlsTrustMaterial EMPTY = of(List.of());

    static DsTlsTrustMaterial of(List<X509Certificate> certificates) {
        return new DsTlsTrustMaterial(List.copyOf(certificates), fingerprintOf(certificates));
    }

    private static String fingerprintOf(List<X509Certificate> certificates) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var certificate : certificates) {
                digest.update(certificate.getEncoded());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (GeneralSecurityException e) {
            throw new EdcException("Failed to fingerprint the dataspace TLS trust anchors", e);
        }
    }
}
