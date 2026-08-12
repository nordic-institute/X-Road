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

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Collection;

/**
 * Builds an {@link X509ExtendedTrustManager} trusting exactly a given set of trust anchors - no JVM
 * default trust anchors are ever mixed in, and neither is anything from the member
 * {@code approvedCA} list.
 */
final class TrustManagers {

    private TrustManagers() {
    }

    static X509ExtendedTrustManager fromTrustAnchors(Collection<X509Certificate> trustAnchors)
            throws IOException, GeneralSecurityException {
        if (trustAnchors.isEmpty()) {
            // java.security.cert.PKIXParameters(Set) throws InvalidAlgorithmParameterException for an
            // empty trust-anchor set - a code path some TrustManagerFactory providers reach from
            // init(KeyStore) too when the keystore is empty (verified vendor/version-dependent
            // behaviour, not guaranteed uniform across every JDK). Special-case it explicitly so an
            // empty DS TLS CA list fails closed the same way everywhere, rather than depending on
            // whatever a given provider happens to do with zero trust anchors.
            return RejectAllTrustManager.INSTANCE;
        }

        var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        var index = 0;
        for (var trustAnchor : trustAnchors) {
            keyStore.setCertificateEntry("ds-tls-ca-" + index++, trustAnchor);
        }

        var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        for (var trustManager : trustManagerFactory.getTrustManagers()) {
            if (trustManager instanceof X509ExtendedTrustManager extended) {
                return extended;
            }
        }
        throw new KeyStoreException("The default TrustManagerFactory algorithm did not yield an X509ExtendedTrustManager");
    }
}
