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
package org.niis.xroad.securityserver.restapi.dstls;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.acme.AcmeKeyPurpose;
import org.niis.xroad.common.acme.AcmeService;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.globalconf.model.DsTlsCaInfo;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNullElse;

/**
 * Thin DS TLS-specific wrapper around the shared {@link AcmeService} engine (order, renew, ARI-aware renewal
 * timing) — no signer, no member id, no {@code KeyUsageInfo}.
 * <p>
 * Every call is made under a fixed, non-member ACME account alias (see {@link #DS_TLS_ACME_ALIAS}), reusing the
 * existing per-CA/per-member EAB configuration map with a synthetic member slot rather than extending its schema.
 * The alias can never collide with a real encoded {@code ClientId}, because every encoded {@code ClientId} contains
 * a {@code :} separator and this alias does not.
 * <p>
 * {@link AcmeKeyPurpose#AUTHENTICATION} is passed to the shared engine for every call purely as a required, but
 * here inert, parameter: it only changes which EAB credential sub-fields ({@code auth-kid}/{@code sign-kid}) get
 * selected, and the DS TLS alias's EAB entry carries none of those, so the plain {@code kid}/{@code mac-key} pair
 * is always used regardless of which purpose value is passed.
 */
@Component
@RequiredArgsConstructor
class DsTlsAcmeService {

    static final String DS_TLS_ACME_ALIAS = "dataspace-tls";

    private static final AcmeKeyPurpose DS_TLS_ACME_KEY_PURPOSE = AcmeKeyPurpose.AUTHENTICATION;
    private static final List<String> NO_CONTACTS = List.of();

    private final AcmeService acmeService;
    private final AdminServiceProperties adminServiceProperties;

    /**
     * Orders a brand-new DS TLS certificate: no certificate exists yet for this CSR's key.
     */
    List<X509Certificate> enroll(DsTlsCaInfo caInfo, String hostname, byte[] certRequest) {
        return acmeService.orderCertificateFromACMEServer(hostname, hostname, DS_TLS_ACME_KEY_PURPOSE,
                toApprovedCaInfo(caInfo), DS_TLS_ACME_ALIAS, certRequest, resolveContacts());
    }

    /**
     * Renews the currently stored DS TLS certificate, referencing it via ACME Renewal Information where the CA
     * supports it.
     */
    List<X509Certificate> renew(DsTlsCaInfo caInfo, String hostname, X509Certificate currentCertificate, byte[] certRequest) {
        return acmeService.renew(DS_TLS_ACME_ALIAS, hostname, toApprovedCaInfo(caInfo), DS_TLS_ACME_KEY_PURPOSE,
                currentCertificate, certRequest, resolveContacts());
    }

    /**
     * The time {@code certificate} is next due for ACME renewal: the CA's suggested ACME Renewal Information window
     * start where available, otherwise a fixed number of days before the certificate's own expiry.
     */
    Instant getNextRenewalTime(DsTlsCaInfo caInfo, X509Certificate certificate) {
        return acmeService.getNextRenewalTime(DS_TLS_ACME_ALIAS, toApprovedCaInfo(caInfo), certificate,
                DS_TLS_ACME_KEY_PURPOSE, resolveContacts());
    }

    private List<String> resolveContacts() {
        return requireNonNullElse(adminServiceProperties.getDataspace().getTlsCertificateContacts(), NO_CONTACTS);
    }

    /**
     * Adapts a {@link DsTlsCaInfo} globalconf entry to the {@link ApprovedCAInfo} shape the shared ACME engine
     * expects. {@code authenticationCertificateProfileId} is deliberately left {@code null} regardless of whether
     * {@code caInfo} carries a {@code dsTlsCertificateProfileId}: that field only ever steers the engine's
     * profile-id ACME URI scheme, whose header-building step resolves the profile id by matching against
     * {@code GlobalConfProvider.getApprovedCAs()} (the member CA list) — DS TLS CAs live in a separate list that
     * mechanism was never wired to consult. Every DS TLS order therefore uses the plain ACME scheme, which covers
     * every CA this feature targets on day one (public DSP-only CAs need no profile id at all).
     */
    private static ApprovedCAInfo toApprovedCaInfo(DsTlsCaInfo caInfo) {
        return new ApprovedCAInfo(caInfo.getName(), null, null, null,
                caInfo.getAcmeServerDirectoryUrl(), caInfo.getAcmeServerIpAddress(), null, null);
    }
}
