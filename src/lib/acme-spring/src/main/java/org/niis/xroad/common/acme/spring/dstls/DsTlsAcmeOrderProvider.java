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
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;
import org.niis.xroad.restapi.dstls.DsTlsAcmeAvailability;
import org.niis.xroad.restapi.dstls.DsTlsAcmeOrderResult;
import org.niis.xroad.restapi.dstls.DsTlsCertificateAcmeProvider;
import org.niis.xroad.restapi.dstls.DsTlsCsrBuilder;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CA_NOT_FOUND;

/**
 * Implements {@link DsTlsCertificateAcmeProvider} on top of the existing DS TLS ACME engine ({@link
 * DsTlsAcmeService}) and this product's {@link DsTlsAcmeHostContext}. Deliberately does not depend on {@code
 * DsTlsCertificateService} — the shared key, current certificate and next-renewal bookkeeping all cross this
 * boundary as plain parameters/return values instead, so that {@code DsTlsCertificateService} can resolve this
 * bean at call time without ever forming a construction cycle back into it.
 */
@Component
@RequiredArgsConstructor
class DsTlsAcmeOrderProvider implements DsTlsCertificateAcmeProvider {

    private final DsTlsAcmeHostContext hostContext;
    private final DsTlsAcmeService dsTlsAcmeService;

    @Override
    public DsTlsAcmeAvailability getAvailability() {
        List<String> caNames = acmeCapableCas().stream().map(ApprovedDsTlsCaInfo::getName).toList();
        String hostname = resolvePublicHostnameOrNull();
        return new DsTlsAcmeAvailability(!caNames.isEmpty() && hostname != null, caNames, hostname);
    }

    @Override
    public DsTlsAcmeOrderResult order(String caName, String distinguishedName, String subjectAltName,
                                      PrivateKey privateKey, PublicKey publicKey, X509Certificate currentCertificate) {
        ApprovedDsTlsCaInfo caInfo = acmeCapableCas().stream()
                .filter(ca -> ca.getName().equals(caName))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(DS_TLS_CA_NOT_FOUND.build(caName)));

        byte[] certRequest = DsTlsCsrBuilder.buildDer(privateKey, publicKey, distinguishedName, subjectAltName);

        List<X509Certificate> chain = currentCertificate == null
                ? dsTlsAcmeService.enroll(caInfo, subjectAltName, certRequest)
                : dsTlsAcmeService.renew(caInfo, subjectAltName, currentCertificate, certRequest);
        if (chain == null || chain.isEmpty()) {
            throw new IllegalStateException("The ACME server returned no certificate");
        }

        X509Certificate leaf = chain.get(0);
        return new DsTlsAcmeOrderResult(chain, dsTlsAcmeService.getNextRenewalTime(caInfo, leaf));
    }

    private List<ApprovedDsTlsCaInfo> acmeCapableCas() {
        return hostContext.getDsTlsCertificationAuthorities().stream()
                .filter(ca -> isNotBlank(ca.getAcmeServerDirectoryUrl()))
                .toList();
    }

    private String resolvePublicHostnameOrNull() {
        try {
            return hostContext.getPublicHostname();
        } catch (Exception e) {
            return null;
        }
    }
}
