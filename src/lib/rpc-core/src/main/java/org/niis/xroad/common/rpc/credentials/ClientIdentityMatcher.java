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
package org.niis.xroad.common.rpc.credentials;

import ee.ria.xroad.common.util.CertUtils;

import io.grpc.util.AdvancedTlsX509TrustManager.SslSocketAndEnginePeerVerifier;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLEngine;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Accepts a client certificate when its leaf's subject common name or one of its DNS subject alternative names
 * equals a listed name, ignoring case. IP subject alternative names and patterns never match.
 */
public final class ClientIdentityMatcher implements SslSocketAndEnginePeerVerifier {
    private static final String COMMON_NAME_TYPE = "CN";

    private final Set<String> allowedNames;

    public ClientIdentityMatcher(Collection<String> allowedNames) {
        this.allowedNames = allowedNames.stream().map(ClientIdentityMatcher::normalize).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @param chain the client certificate chain, leaf first
     * @return true when the leaf carries a listed name as its common name or as a DNS subject alternative name
     */
    public boolean matches(X509Certificate[] chain) {
        if (chain == null || chain.length == 0) {
            return false;
        }
        return identities(chain[0]).map(ClientIdentityMatcher::normalize).anyMatch(allowedNames::contains);
    }

    @Override
    public void verifyPeerCertificate(X509Certificate[] peerCertChain, String authType, Socket socket)
            throws CertificateException {
        verify(peerCertChain);
    }

    @Override
    public void verifyPeerCertificate(X509Certificate[] peerCertChain, String authType, SSLEngine engine)
            throws CertificateException {
        verify(peerCertChain);
    }

    private void verify(X509Certificate[] chain) throws CertificateException {
        if (!matches(chain)) {
            throw new CertificateException("The client certificate name is not in the list of allowed client names");
        }
    }

    private static Stream<String> identities(X509Certificate leaf) {
        return Stream.concat(commonNames(leaf), dnsNames(leaf));
    }

    private static Stream<String> commonNames(X509Certificate leaf) {
        try {
            var subject = new LdapName(leaf.getSubjectX500Principal().getName());
            return subject.getRdns().stream()
                    .filter(rdn -> COMMON_NAME_TYPE.equalsIgnoreCase(rdn.getType()))
                    .map(Rdn::getValue)
                    .map(String::valueOf);
        } catch (InvalidNameException _) {
            return Stream.empty();
        }
    }

    private static Stream<String> dnsNames(X509Certificate leaf) {
        try {
            return CertUtils.getDnsSubjectAlternativeNames(leaf).stream();
        } catch (CertificateParsingException _) {
            return Stream.empty();
        }
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
