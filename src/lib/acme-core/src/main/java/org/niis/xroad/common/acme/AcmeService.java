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
package org.niis.xroad.common.acme;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public final class AcmeService {

    private final AcmeClient acmeClient;

    public boolean isExternalAccountBindingRequired(String acmeServerDirectoryUrl) {
        return acmeClient.isExternalAccountBindingRequired(acmeServerDirectoryUrl);
    }

    public List<X509Certificate> orderCertificateFromACMEServer(String commonName,
                                                                String subjectAltName,
                                                                AcmeKeyPurpose keyUsage,
                                                                ApprovedCAInfo caInfo,
                                                                String memberId,
                                                                byte[] certRequest,
                                                                List<String> contacts) {
        return acmeClient.orderCertificate(commonName, subjectAltName, toAccountContext(memberId, caInfo, keyUsage, contacts),
                certRequest);
    }

    /**
     * The certificate profile id to send as the ACME {@code profile_id} header for a member-CA order, or
     * {@code null} if the CA needs none — the {@code keyUsage}-branched signing/authentication profile id.
     * <p>
     * Package-private for tests: proves this resolution in isolation, without needing a live ACME server to
     * observe the header it ultimately drives.
     */
    static String resolveCertificateProfileId(ApprovedCAInfo caInfo, AcmeKeyPurpose keyUsage) {
        return keyUsage == AcmeKeyPurpose.SIGNING
                ? caInfo.getSigningCertificateProfileId()
                : caInfo.getAuthenticationCertificateProfileId();
    }

    private AcmeAccountContext toAccountContext(String memberId, ApprovedCAInfo caInfo, AcmeKeyPurpose keyUsage,
                                                List<String> contacts) {
        return new AcmeAccountContext(memberId, caInfo.getName(), caInfo.getAcmeServerDirectoryUrl(),
                resolveCertificateProfileId(caInfo, keyUsage), keyUsage, contacts);
    }

    public void checkAccountKeyPairAndRenewIfNecessary(String memberId, ApprovedCAInfo caInfo, AcmeKeyPurpose keyUsage,
                                                        List<String> contacts) {
        acmeClient.checkAccountKeyPairAndRenewIfNecessary(toAccountContext(memberId, caInfo, keyUsage, contacts));
    }

    public boolean isRenewalRequired(String memberId, ApprovedCAInfo approvedCA, X509Certificate certificate,
                                     AcmeKeyPurpose keyUsage, List<String> contacts) {
        return acmeClient.isRenewalRequired(toAccountContext(memberId, approvedCA, keyUsage, contacts), certificate);
    }

    public Instant getNextRenewalTime(String memberId, ApprovedCAInfo approvedCA, X509Certificate x509Certificate,
                                      AcmeKeyPurpose keyUsage, List<String> contacts) {
        return acmeClient.getNextRenewalTime(toAccountContext(memberId, approvedCA, keyUsage, contacts), x509Certificate);
    }

    public boolean hasRenewalInfo(String memberId, ApprovedCAInfo approvedCA, AcmeKeyPurpose keyUsage, List<String> contacts) {
        return acmeClient.hasRenewalInfo(toAccountContext(memberId, approvedCA, keyUsage, contacts));
    }

    public List<X509Certificate> renew(String memberId, String subjectAltName, ApprovedCAInfo approvedCA,
                                       AcmeKeyPurpose keyUsage,
                                       X509Certificate oldCertificate, byte[] newCsr, List<String> contacts) {
        return acmeClient.renew(toAccountContext(memberId, approvedCA, keyUsage, contacts), subjectAltName, oldCertificate, newCsr);
    }
}
