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

import org.eclipse.edc.spi.monitor.Monitor;
import org.niis.xroad.common.vault.reload.PeriodicMaterialReloader;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;

import javax.net.ssl.X509ExtendedTrustManager;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Owns the reload seam for the dataspace TLS trust set: periodically reloads the
 * {@code approvedDsTlsCa} chains from globalconf ({@link GlobalConfProvider#getApprovedDsTlsCas})
 * and applies them to a {@link DsTlsTrustManager}. This is a second, independent
 * {@link PeriodicMaterialReloader} instance from the one that reloads the DS TLS keystore
 * ({@code XRoadJettyExtension} in lib:edc-jetty-core) - both use the same reusable reload seam, but
 * neither shares state or a schedule with the other.
 *
 * <p>Globalconf being unready or unreachable never fails {@link #start()}: it is treated exactly
 * like an empty CA list, so a cold start or a transient globalconf failure degrades to rejecting all
 * dataspace TLS connections rather than aborting EDC boot - the trust set self-heals once globalconf
 * becomes available, on the next reload cycle.
 */
public final class DsTlsTrustSource {

    private final GlobalConfProvider globalConfProvider;
    private final Monitor monitor;
    private final DsTlsTrustManager trustManager;
    private final PeriodicMaterialReloader<DsTlsTrustMaterial> reloader;

    public DsTlsTrustSource(GlobalConfProvider globalConfProvider, Monitor monitor, VaultEndpoint vaultEndpoint,
                             List<X509Certificate> vaultCaCertificates, Duration reloadInterval) {
        this.globalConfProvider = globalConfProvider;
        this.monitor = monitor;
        this.trustManager = new DsTlsTrustManager(vaultEndpoint, vaultCaCertificates);
        this.reloader = PeriodicMaterialReloader.builder("DS TLS trust", this::load)
                .fingerprint(DsTlsTrustMaterial::fingerprint)
                .onChange(material -> trustManager.update(material.certificates()))
                .reloadInterval(reloadInterval)
                .build();
    }

    public X509ExtendedTrustManager trustManager() {
        return trustManager;
    }

    /**
     * Loads and applies the initial trust set, then starts the periodic reload cycle. Never throws:
     * an empty or unreachable DS TLS CA list is a valid, fail-closed running state, not a boot
     * failure - unlike the DS TLS keystore, whose own reload seam does fail boot on an empty vault
     * slot.
     */
    public void start() {
        try {
            reloader.loadInitial();
        } catch (Exception e) {
            // load() below never throws itself; this is a last-resort guard so a truly unexpected
            // failure still degrades to fail-closed trust instead of aborting EDC boot.
            monitor.warning("Failed to apply the initial dataspace TLS trust set - staying fail-closed: " + e.getMessage());
        }
        reloader.start();
    }

    public void shutdown() {
        reloader.shutdown();
    }

    private DsTlsTrustMaterial load() {
        try {
            globalConfProvider.verifyValidity();
            var approvedCas = globalConfProvider.getApprovedDsTlsCas(globalConfProvider.getInstanceIdentifier());
            return toMaterial(approvedCas);
        } catch (Exception e) {
            monitor.warning("DS TLS CA list unavailable - dataspace TLS trust is empty (fail-closed): " + e.getMessage());
            return DsTlsTrustMaterial.EMPTY;
        }
    }

    private static DsTlsTrustMaterial toMaterial(Collection<ApprovedDsTlsCaInfo> approvedCas) throws CertificateException {
        var certificateFactory = CertificateFactory.getInstance("X.509");
        var certificates = new ArrayList<X509Certificate>();
        for (var ca : approvedCas) {
            certificates.add(parse(certificateFactory, ca.getTopCaCert()));
            for (var intermediate : ca.getIntermediateCaCerts()) {
                certificates.add(parse(certificateFactory, intermediate));
            }
        }
        return DsTlsTrustMaterial.of(certificates);
    }

    private static X509Certificate parse(CertificateFactory factory, byte[] der) throws CertificateException {
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der));
    }
}
