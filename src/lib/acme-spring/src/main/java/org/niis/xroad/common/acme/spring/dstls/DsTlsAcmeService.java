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
package org.niis.xroad.common.acme.spring.dstls;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.acme.AcmeAccountContext;
import org.niis.xroad.common.acme.AcmeClient;
import org.niis.xroad.common.acme.AcmeKeyPurpose;
import org.niis.xroad.common.acme.AcmeService;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

/**
 * Thin DS TLS-specific wrapper around the shared {@link AcmeClient} engine (order, renew, ARI-aware renewal
 * timing) — no signer, no member id, no {@code KeyUsageInfo}.
 * <p>
 * Every call is made under a fixed, non-member ACME account alias supplied by {@link DsTlsAcmeHostContext},
 * reusing the existing per-CA/per-member EAB configuration map with a synthetic member slot rather than
 * extending its schema. That alias can never collide with a real encoded {@code ClientId}, because every
 * encoded {@code ClientId} contains a {@code :} separator and this alias does not.
 * <p>
 * This class is the only place that knows {@link ApprovedDsTlsCaInfo} exists — it's translated into a plain
 * {@link AcmeAccountContext} here before ever reaching {@link AcmeClient}, so the shared engine never needs to
 * know this CA-info shape exists at all. This class depends directly on {@link AcmeClient}, never on
 * {@link AcmeService} — that class is member auth/sign's own façade over the same shared engine.
 */
@Component
@RequiredArgsConstructor
class DsTlsAcmeService {

    private final AcmeClient acmeClient;
    private final DsTlsAcmeHostContext hostContext;

    /**
     * Orders a brand-new DS TLS certificate: no certificate exists yet for this CSR's key.
     */
    List<X509Certificate> enroll(ApprovedDsTlsCaInfo caInfo, String hostname, byte[] certRequest) {
        return acmeClient.orderCertificate(hostname, hostname, toAccountContext(caInfo), certRequest);
    }

    /**
     * Renews the currently stored DS TLS certificate, referencing it via ACME Renewal Information where the CA
     * supports it.
     */
    List<X509Certificate> renew(ApprovedDsTlsCaInfo caInfo, String hostname, X509Certificate currentCertificate, byte[] certRequest) {
        return acmeClient.renew(toAccountContext(caInfo), hostname, currentCertificate, certRequest);
    }

    /**
     * The time {@code certificate} is next due for ACME renewal: the CA's suggested ACME Renewal Information window
     * start where available, otherwise a fixed number of days before the certificate's own expiry.
     */
    Instant getNextRenewalTime(ApprovedDsTlsCaInfo caInfo, X509Certificate certificate) {
        return acmeClient.getNextRenewalTime(toAccountContext(caInfo), certificate);
    }

    private AcmeAccountContext toAccountContext(ApprovedDsTlsCaInfo caInfo) {
        return new AcmeAccountContext(hostContext.getEabAlias(), caInfo.getName(), caInfo.getAcmeServerDirectoryUrl(),
                caInfo.getDsTlsCertificateProfileId(), AcmeKeyPurpose.AUTHENTICATION, hostContext.getAccountContacts());
    }
}
