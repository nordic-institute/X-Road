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

import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;

import java.util.List;

/**
 * Every product-specific concern the shared DS TLS ACME enrollment/renewal worker depends on, so that the
 * worker and its supporting classes stay free of any Security-Server- or Central-Server-specific config,
 * hostname source, or notification mechanism. Each product supplies its own implementation.
 */
public interface DsTlsAcmeHostContext {

    /**
     * @return this server's public DataSpace-facing hostname, used as both the CSR's subject CN and its DNS
     *     SAN, or {@code null} when DS TLS ACME enrollment is not currently enabled for this product — not a
     *     failure, the caller should skip this cycle. May throw {@link IllegalArgumentException} if the
     *     underlying configuration is present but malformed.
     */
    String getPublicHostname();

    /**
     * @return every DS TLS certification authority this product currently has approved/designated. The shared
     *     worker filters this list down to the ACME-capable entries (those with a non-blank ACME directory
     *     URL) and applies the zero/one/many designation rules itself — this method only supplies the raw,
     *     product-specific data source (globalconf distribution for the Security Server, a directly-read
     *     database table for the Central Server).
     */
    List<ApprovedDsTlsCaInfo> getDsTlsCertificationAuthorities();

    /**
     * @return the raw, possibly-unparseable configuration value {@link #getPublicHostname()} resolves from,
     *     used to identify the affected host in a failure notification when hostname resolution itself throws.
     *     Defaults to {@code null} (no better value available).
     */
    default String getConfiguredHostnameSource() {
        return null;
    }

    /**
     * @return the fixed, non-member ACME account/EAB alias this product's DS TLS certificate enrolls/renews
     *     under
     */
    String getEabAlias();

    /**
     * @return the ACME account contact addresses (e.g. {@code mailto:...}) to register with the CA, or an
     *     empty list if none are configured
     */
    default List<String> getAccountContacts() {
        return List.of();
    }

    /**
     * Called after a cycle successfully enrolls or renews the DS TLS certificate.
     */
    void notifyEnrollmentSuccess(String hostname, boolean isRenewal);

    /**
     * Called after a cycle's enrollment/renewal outcome changes to a failure.
     */
    void notifyEnrollmentFailure(String hostname, String errorDescription);
}
