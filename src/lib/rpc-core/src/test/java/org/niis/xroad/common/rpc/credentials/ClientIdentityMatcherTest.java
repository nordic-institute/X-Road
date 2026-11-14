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
package org.niis.xroad.common.rpc.credentials;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.rpc.test.TestCa;

import javax.net.ssl.SSLEngine;
import javax.security.auth.x500.X500Principal;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ClientIdentityMatcherTest {

    private TestCa ca;

    private X509Certificate[] chain(String commonName, List<String> dnsNames, List<String> ipAddresses) throws Exception {
        if (ca == null) {
            ca = TestCa.selfSigned("Security Server CA");
        }
        return ca.issueLeaf(commonName, dnsNames, ipAddresses).chain();
    }

    @Test
    void commonNameMatches() throws Exception {
        var matcher = new ClientIdentityMatcher(List.of("proxy"));

        assertThat(matcher.matches(chain("proxy", List.of(), List.of()))).isTrue();
    }

    @Test
    void dnsSubjectAlternativeNameMatches() throws Exception {
        var matcher = new ClientIdentityMatcher(List.of("ui"));

        assertThat(matcher.matches(chain("other", List.of("ui"), List.of()))).isTrue();
    }

    @Test
    void matchingIsCaseInsensitiveForCommonNameAndDnsName() throws Exception {
        assertThat(new ClientIdentityMatcher(List.of("PROXY")).matches(chain("proxy", List.of(), List.of()))).isTrue();
        assertThat(new ClientIdentityMatcher(List.of("ui.Example.org"))
                .matches(chain("other", List.of("UI.example.ORG"), List.of()))).isTrue();
    }

    @Test
    void anyOfSeveralSubjectAlternativeNamesMatches() throws Exception {
        var matcher = new ClientIdentityMatcher(List.of("third"));

        assertThat(matcher.matches(chain("other", List.of("first", "second", "third"), List.of("10.0.0.1")))).isTrue();
    }

    @Test
    void nameThatIsOnlyPartOfAnEntryDoesNotMatch() throws Exception {
        var matcher = new ClientIdentityMatcher(List.of("proxy"));

        assertThat(matcher.matches(chain("proxy-2", List.of("my-proxy", "proxy.example.org"), List.of()))).isFalse();
    }

    @Test
    void certificateWithoutSubjectAlternativeNamesMatchesOnlyByCommonName() throws Exception {
        var matcher = new ClientIdentityMatcher(List.of("ui"));

        assertThat(matcher.matches(chain("proxy", List.of(), List.of()))).isFalse();
    }

    @Test
    void ipSubjectAlternativeNameNeverMatches() throws Exception {
        var matcher = new ClientIdentityMatcher(List.of("127.0.0.1"));

        assertThat(matcher.matches(chain("other", List.of(), List.of("127.0.0.1")))).isFalse();
    }

    @Test
    void ipShapedEntryMatchesCommonNameAndDnsNameEqualToIt() throws Exception {
        var matcher = new ClientIdentityMatcher(List.of("127.0.0.1"));

        assertThat(matcher.matches(chain("127.0.0.1", List.of(), List.of()))).isTrue();
        assertThat(matcher.matches(chain("other", List.of("127.0.0.1"), List.of()))).isTrue();
    }

    @Test
    void emptyListMatchesNothing() throws Exception {
        var matcher = new ClientIdentityMatcher(List.of());

        assertThat(matcher.matches(chain("proxy", List.of("proxy"), List.of()))).isFalse();
    }

    @Test
    void onlyTheLeafIsInspected() throws Exception {
        var matcher = new ClientIdentityMatcher(List.of("Security Server CA"));

        assertThat(matcher.matches(chain("proxy", List.of(), List.of()))).isFalse();
    }

    @Test
    void dnsNameLookupFailureIsSwallowedAndCommonNameStillMatches() throws Exception {
        X509Certificate leaf = mock(X509Certificate.class);
        doReturn(new X500Principal("CN=proxy")).when(leaf).getSubjectX500Principal();
        doThrow(new CertificateParsingException("bad SAN extension")).when(leaf).getSubjectAlternativeNames();

        var matcher = new ClientIdentityMatcher(List.of("proxy"));

        assertThat(matcher.matches(new X509Certificate[] {leaf})).isTrue();
    }

    @Test
    void missingChainDoesNotMatch() {
        var matcher = new ClientIdentityMatcher(List.of("proxy"));

        assertThat(matcher.matches(null)).isFalse();
        assertThat(matcher.matches(new X509Certificate[0])).isFalse();
    }

    @Test
    void verifierRejectsAnUnlistedClientAndAcceptsAListedOne() throws Exception {
        var matcher = new ClientIdentityMatcher(List.of("proxy"));
        var unlisted = chain("ui", List.of(), List.of());
        var listed = chain("proxy", List.of(), List.of());

        assertThatThrownBy(() -> matcher.verifyPeerCertificate(unlisted, "RSA", (Socket) null))
                .isInstanceOf(CertificateException.class);
        assertThatThrownBy(() -> matcher.verifyPeerCertificate(unlisted, "RSA", (SSLEngine) null))
                .isInstanceOf(CertificateException.class);
        assertThatCode(() -> matcher.verifyPeerCertificate(listed, "RSA", (Socket) null)).doesNotThrowAnyException();
        assertThatCode(() -> matcher.verifyPeerCertificate(listed, "RSA", (SSLEngine) null))
                .doesNotThrowAnyException();
    }
}
