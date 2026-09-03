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
package org.niis.xroad.edc.trust;

import org.niis.xroad.edc.reload.PeriodicMaterialReloader;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Builds the DataSpace outbound trust manager from globalconf's {@code approvedDsTlsCa} list — the member
 * {@code approvedCA} list is never consulted, so trust is separated by construction. Two outcomes are
 * deliberately distinguished: globalconf answering successfully with an empty list is a <em>successful</em>
 * load whose material is an explicit {@link RejectAllTrustManager} (a valid, running, fail-closed state);
 * globalconf being unreadable is reported by letting the underlying exception propagate, so the caller's
 * reload seam keeps serving the last-known-good trust manager and retries — this loader never decides on its
 * own to fail closed on a transient read failure the way it does for a confirmed-empty list.
 */
public final class DsTlsCaTrustManagerLoader implements PeriodicMaterialReloader.MaterialLoader<X509ExtendedTrustManager> {

    /**
     * Shared by every successful load whose result is reject-all, whether the list came back genuinely empty or
     * the initial boot-time load failed and fell back to reject-all: the resulting trust posture is identical,
     * so reusing one fingerprint avoids a spurious "reloaded" cycle the next time the list is confirmed empty.
     */
    public static final String REJECT_ALL_FINGERPRINT = "REJECT-ALL";

    private static final String FINGERPRINT_ALGORITHM = "SHA-256";

    private final GlobalConfProvider globalConfProvider;

    public DsTlsCaTrustManagerLoader(GlobalConfProvider globalConfProvider) {
        this.globalConfProvider = globalConfProvider;
    }

    @Override
    public PeriodicMaterialReloader.Loaded<X509ExtendedTrustManager> load() {
        var instanceIdentifier = globalConfProvider.getInstanceIdentifier();
        var cas = globalConfProvider.getApprovedDsTlsCas(instanceIdentifier)
                .stream()
                .sorted(Comparator.comparing(ApprovedDsTlsCaInfo::getName))
                .toList();

        if (cas.isEmpty()) {
            return new PeriodicMaterialReloader.Loaded<>(RejectAllTrustManager.INSTANCE, REJECT_ALL_FINGERPRINT);
        }
        return new PeriodicMaterialReloader.Loaded<>(buildTrustManager(cas), fingerprint(cas));
    }

    private static X509ExtendedTrustManager buildTrustManager(List<ApprovedDsTlsCaInfo> cas) {
        try {
            var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            var index = 0;
            for (var ca : cas) {
                keyStore.setCertificateEntry("ds-tls-ca-" + index + "-top", ca.getTopCaCert());
                var intermediateIndex = 0;
                for (var intermediate : ca.getIntermediateCaCerts()) {
                    keyStore.setCertificateEntry("ds-tls-ca-" + index + "-intermediate-" + intermediateIndex++, intermediate);
                }
                index++;
            }

            var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            return Arrays.stream(trustManagerFactory.getTrustManagers())
                    .filter(X509ExtendedTrustManager.class::isInstance)
                    .map(X509ExtendedTrustManager.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "The JVM's default TrustManagerFactory did not produce an X509ExtendedTrustManager "
                                    + "for the DataSpace TLS CA list"));
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to build the DataSpace TLS CA trust manager from globalconf", e);
        }
    }

    private static String fingerprint(List<ApprovedDsTlsCaInfo> cas) {
        try {
            var digest = MessageDigest.getInstance(FINGERPRINT_ALGORITHM);
            for (var ca : cas) {
                digest.update(ca.getTopCaCert().getEncoded());
                for (var intermediate : ca.getIntermediateCaCerts()) {
                    digest.update(intermediate.getEncoded());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new IllegalStateException("Failed to fingerprint the DataSpace TLS CA list", e);
        }
    }
}
