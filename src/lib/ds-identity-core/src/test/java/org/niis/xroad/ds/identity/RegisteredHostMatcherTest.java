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
package org.niis.xroad.ds.identity;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegisteredHostMatcherTest {

    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "222");
    private static final String ADDRESS = "ss0.example.org";
    private static final String DSP_BASE_URL = "https://ss0.example.org:7181/dsp";

    private final RegisteredHostMatcher matcher = RegisteredHostMatcher.of(ADDRESS);

    @Test
    void shouldMatchTheDidHostOfTheRegisteredAddress() {
        assertThat(matcher.isRegisteredAddressUsable()).isTrue();
        assertThat(matcher.didHostMatches(memberDid("ss0.example.org:7183"))).isTrue();
        assertThat(matcher.didHostMatches(ParticipantIdentifierScheme.systemDid("ss0.example.org:7183").toString()))
                .isTrue();
    }

    @Test
    void shouldUseARegisteredAddressWithAPortAsItsHost() {
        var withPort = RegisteredHostMatcher.of("ss0.example.org:7183");

        assertThat(withPort.isRegisteredAddressUsable()).isTrue();
        assertThat(withPort.didHostMatches(memberDid("ss0.example.org:8443"))).isTrue();
        assertThat(withPort.didHostMatches(memberDid("ss1.example.org:7183"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ss0.example.org", "ss0.example.org:7183", "ss0.example.org:8443"})
    void shouldNotCompareTheDidPort(String authority) {
        assertThat(matcher.didHostMatches(memberDid(authority))).isTrue();
    }

    @Test
    void shouldCompareHostsInCanonicalForm() {
        assertThat(RegisteredHostMatcher.of("SS0.Example.ORG").didHostMatches(memberDid("ss0.example.org:7183")))
                .isTrue();
        assertThat(matcher.didHostMatches(memberDid("SS0.EXAMPLE.ORG:7183"))).isTrue();
        assertThat(RegisteredHostMatcher.of("xn--bcher-kva.example").didHostMatches(memberDid("XN--BCHER-KVA.example")))
                .isTrue();
        assertThat(RegisteredHostMatcher.of("::1").didHostMatches(memberDid("[::1]:7183"))).isTrue();
        assertThat(RegisteredHostMatcher.of("[::1]").didHostMatches(memberDid("[::1]"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ss0.example.org:443", "SS0.Example.ORG"})
    void shouldUseARegisteredAddressInAnyNormalisation(String registered) {
        var normalised = RegisteredHostMatcher.of(registered);

        assertThat(normalised.didHostMatches(memberDid("ss0.example.org:7183"))).isTrue();
        assertThat(normalised.dspBaseUrlHostMatches(DSP_BASE_URL)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2001:db8::1", "[2001:db8::1]", "2001:DB8::1", "[2001:db8::1]:443"})
    void shouldUseAnIpv6RegisteredAddressInAnyNormalisation(String registered) {
        var ipv6 = RegisteredHostMatcher.of(registered);

        assertThat(ipv6.isRegisteredAddressUsable()).isTrue();
        assertThat(ipv6.didHostMatches(memberDid("[2001:db8::1]:7183"))).isTrue();
        assertThat(ipv6.dspBaseUrlHostMatches("https://[2001:db8::1]:443/api/dsp")).isTrue();
        assertThat(ipv6.dspBaseUrlHostMatches("https://[2001:DB8::1]/api/dsp")).isTrue();
        assertThat(ipv6.dspBaseUrlHostMatches(DSP_BASE_URL)).isFalse();
    }

    @Test
    void shouldNotMatchAnotherHost() {
        assertThat(matcher.didHostMatches(memberDid("ss1.example.org:7183"))).isFalse();
        assertThat(matcher.didHostMatches(memberDid("ss0.example.org.evil.test:7183"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-did", "did:web:ss0.example.org", "did:web:ss0.example.org:v9:DEV:COM:222",
            "did:web:ss0.example.org%3A7183:v1:DEV"})
    @NullAndEmptySource
    void shouldNotMatchADidThatCannotBeDecoded(String did) {
        assertThat(matcher.didHostMatches(did)).isFalse();
        assertThat(matcher.isMemberDidStale(did)).isTrue();
    }

    @Test
    void shouldMatchTheDspBaseUrlHostOfTheRegisteredAddress() {
        assertThat(matcher.dspBaseUrlHostMatches(DSP_BASE_URL)).isTrue();
        assertThat(matcher.dspBaseUrlHostMatches("https://ss0.example.org/dsp")).isTrue();
        assertThat(matcher.dspBaseUrlHostMatches("https://ss0.example.org")).isTrue();
        assertThat(matcher.dspBaseUrlHostMatches("HTTPS://SS0.example.org:443/dsp")).isTrue();
        assertThat(RegisteredHostMatcher.of("::1").dspBaseUrlHostMatches("https://[::1]:7181/dsp")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://ss1.example.org:7181/dsp", "http://ss0.example.org:7181/dsp",
            "ftp://ss0.example.org/dsp", "ss0.example.org:7181/dsp", "https://", "https://:7181/dsp"})
    @NullAndEmptySource
    void shouldNotMatchADspBaseUrlOfAnotherHostOrWithoutAnHttpsHost(String url) {
        assertThat(matcher.dspBaseUrlHostMatches(url)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://user@ss0.example.org/dsp", "https://ss0.example.org:0/dsp",
            "https://ss0.example.org:70000/dsp", "https://ss0.example.org:http/dsp", "https://ss0.example.orgé/dsp",
            "https://ss0.example.org:/dsp"})
    void shouldNotMatchADspBaseUrlWithAMalformedAuthority(String url) {
        var systemDid = ParticipantIdentifierScheme.systemDid("ss0.example.org:7183").toString();

        assertThat(matcher.dspBaseUrlHostMatches(url)).isFalse();
        assertThat(matcher.isSystemPairStale(systemDid, url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://ss0.example.org?x=1", "https://ss0.example.org#top",
            "https://ss0.example.org:7181?x=1", "https://ss0.example.org/dsp?x=1#top"})
    void shouldEndTheDspBaseUrlAuthorityAtAQueryOrFragment(String url) {
        assertThat(matcher.dspBaseUrlHostMatches(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"did:web:ss0.example.org%2:v1:DEV:COM:222", "did:web:ss0.example.org:v1:system:extra",
            "did:web:ss0.example.org%3a7183:v1:DEV:COM:222", "did:web:ss0.example.org%41:v1:DEV:COM:222",
            "did:web:ss0.example.org:v1:DEV:COM:%41BC", "did:web:ss0.example.orgé:v1:system",
            "did:web:ss0.example.org:v1:DEV:COM"})
    void shouldCallAnUndecodableDidStale(String did) {
        assertThat(matcher.didHostMatches(did)).isFalse();
        assertThat(matcher.isMemberDidStale(did)).isTrue();
    }

    @Test
    void shouldNotMatchAnotherIpv6Host() {
        var ipv6 = RegisteredHostMatcher.of("2001:db8::1");

        assertThat(ipv6.didHostMatches(memberDid("[2001:db8::2]:7183"))).isFalse();
        assertThat(ipv6.dspBaseUrlHostMatches("https://[2001:db8::2]:7181/dsp")).isFalse();
        assertThat(ipv6.isMemberDidStale(memberDid("[2001:db8::2]:7183"))).isTrue();
        assertThat(ipv6.didHostMatches(memberDid("[2001:db8::1]:7183"))).isTrue();
    }

    @Test
    void shouldMatchAnAuthorityByItsHost() {
        assertThat(matcher.authorityHostMatches("ss0.example.org")).isTrue();
        assertThat(matcher.authorityHostMatches("SS0.Example.org:8443")).isTrue();
        assertThat(matcher.authorityHostMatches("ss1.example.org")).isFalse();
        assertThat(matcher.authorityHostMatches("user@ss0.example.org")).isFalse();
        assertThat(matcher.authorityHostMatches("")).isFalse();
        assertThat(matcher.authorityHostMatches(null)).isFalse();
        assertThat(RegisteredHostMatcher.of(null).authorityHostMatches("ss0.example.org")).isFalse();
    }

    @Test
    void shouldCallAMemberDidStaleOnlyWhenItsHostDiffers() {
        assertThat(matcher.isMemberDidStale(memberDid("ss0.example.org:7183"))).isFalse();
        assertThat(matcher.isMemberDidStale(memberDid("ss1.example.org:7183"))).isTrue();
    }

    @Test
    void shouldCallTheSystemPairStaleWhenEitherHostDiffers() {
        var systemDid = ParticipantIdentifierScheme.systemDid("ss0.example.org:7183").toString();
        var otherSystemDid = ParticipantIdentifierScheme.systemDid("ss1.example.org:7183").toString();

        assertThat(matcher.isSystemPairStale(systemDid, DSP_BASE_URL)).isFalse();
        assertThat(matcher.isSystemPairStale(otherSystemDid, DSP_BASE_URL)).isTrue();
        assertThat(matcher.isSystemPairStale(systemDid, "https://ss1.example.org:7181/dsp")).isTrue();
        assertThat(matcher.isSystemPairStale(otherSystemDid, "https://ss1.example.org:7181/dsp")).isTrue();
        assertThat(matcher.isSystemPairStale(null, DSP_BASE_URL)).isTrue();
        assertThat(matcher.isSystemPairStale(systemDid, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"bücher.example", "例え.jp", "ss0.example.orgé", "bad host", "ss0.example.org:notaport", " ",
            ":7183", "[2001:db8::1", "ss0.example.org:http", "ss0.example.org:70000", "ss0.example.org ",
            "host:80:90", "a/b", "a%b", "[host:80]", "my_host", "[10.0.0.1]", "-a.org", "ss0.123"})
    @NullAndEmptySource
    void shouldCallEveryValueStaleWhenTheRegisteredAddressCannotBeUsed(String registeredAddress) {
        var unusable = RegisteredHostMatcher.of(registeredAddress);
        var systemDid = ParticipantIdentifierScheme.systemDid("ss0.example.org:7183").toString();

        assertThat(unusable.didHostMatches(memberDid("ss0.example.org:7183"))).isFalse();
        assertThat(unusable.isRegisteredAddressUsable()).isFalse();
        assertThat(unusable.dspBaseUrlHostMatches(DSP_BASE_URL)).isFalse();
        assertThat(unusable.isMemberDidStale(memberDid("ss0.example.org:7183"))).isTrue();
        assertThat(unusable.isSystemPairStale(systemDid, DSP_BASE_URL)).isTrue();
    }

    private static String memberDid(String authority) {
        return ParticipantIdentifierScheme.memberDid(MEMBER, authority).toString();
    }
}
