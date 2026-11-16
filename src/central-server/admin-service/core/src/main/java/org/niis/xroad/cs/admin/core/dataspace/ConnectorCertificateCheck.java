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
package org.niis.xroad.cs.admin.core.dataspace;

import ee.ria.xroad.common.util.CertUtils;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.springframework.stereotype.Component;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;

/**
 * Whether the connector TLS certificate in the {@code tls/ds-https} OpenBao slot is ready for the issuer
 * host: present, unexpired, and covering that host as a DNS subject alternative name. Shared between set-up
 * (a prerequisite check) and the derived status (a status row), so both read the same certificate the same way.
 */
@Component
@RequiredArgsConstructor
public class ConnectorCertificateCheck {

    /** Fewest labels a wildcard name may have: {@code *} plus at least two more, so {@code *.org} never matches. */
    private static final int MIN_WILDCARD_LABELS = 3;

    private final DsTlsCertificateService dsTlsCertificateService;

    public boolean isReadyFor(String host) {
        X509Certificate certificate = dsTlsCertificateService.getStatus().certificate();
        return certificate != null && CertUtils.isValid(certificate) && hasDnsSubjectAlternativeName(certificate, host);
    }

    private static boolean hasDnsSubjectAlternativeName(X509Certificate certificate, String host) {
        try {
            return CertUtils.getDnsSubjectAlternativeNames(certificate).stream()
                    .anyMatch(san -> matchesHost(san, host));
        } catch (CertificateParsingException _) {
            return false;
        }
    }

    /**
     * Whether a DNS subject alternative name covers the given host: either an exact, case-insensitive match, or a
     * wildcard name per RFC 6125, section 6.4.3, restricted to the common safe form — the whole leftmost label is
     * {@code *}, and at least two labels follow it.
     */
    private static boolean matchesHost(String subjectAlternativeName, String host) {
        if (subjectAlternativeName.equalsIgnoreCase(host)) {
            return true;
        }
        String[] sanLabels = subjectAlternativeName.split("\\.", -1);
        if (sanLabels.length < MIN_WILDCARD_LABELS || !"*".equals(sanLabels[0])) {
            return false;
        }
        String[] hostLabels = host.split("\\.", -1);
        if (hostLabels.length != sanLabels.length) {
            return false;
        }
        for (int i = 1; i < sanLabels.length; i++) {
            if (!sanLabels[i].equalsIgnoreCase(hostLabels[i])) {
                return false;
            }
        }
        return true;
    }
}
