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
package org.niis.xroad.globalconf.model;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.util.EncoderUtils.decodeBase64;

/**
 * Describes a configuration location where configuration can be downloaded.
 */
@RequiredArgsConstructor
@Getter
@Slf4j
public class ConfigurationLocation {

    private final String instanceIdentifier;

    private final String downloadURL;

    private final List<byte[]> verificationCerts;

    /**
     * @param certHashBase64 the base64 encoded certificate hash
     * @param hashAlgoUri    the hash algorithm id
     * @return verification certificate for a given certificate hash or null
     * if not found
     */
    public X509Certificate getVerificationCert(String certHashBase64,
                                               DigestAlgorithm hashAlgoUri) {
        byte[] certHash = decodeBase64(certHashBase64);
        for (byte[] certBytes : verificationCerts) {
            try {
                log.trace("Calculating certificate hash using algorithm {}",
                        hashAlgoUri);

                if (Arrays.equals(certHash, hash(hashAlgoUri, certBytes))) {
                    return CryptoUtils.readCertificate(certBytes);
                }
            } catch (Exception e) {
                log.error("Failed to calculate certificate hash using "
                        + "algorithm identifier " + hashAlgoUri, e);
            }
        }

        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ConfigurationLocation that)) {
            return false;
        }

        if (!this.getDownloadURL().equals(that.getDownloadURL())) {
            return false;
        }

        List<byte[]> thisCerts = this.verificationCerts;
        List<byte[]> thatCerts = that.verificationCerts;
        if (thisCerts.size() != thatCerts.size()) {
            return false;
        }

        for (int i = 0; i < thisCerts.size(); i++) {
            if (!Arrays.equals(thisCerts.get(i), thatCerts.get(i))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    private static byte[] hash(DigestAlgorithm hashAlgoUri, byte[] data) throws Exception {
        return calculateDigest(hashAlgoUri, data);
    }

    @Override
    public String toString() {
        return downloadURL;
    }
}
