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

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.cs.admin.api.domain.PublishedValueRejection;
import org.niis.xroad.cs.admin.core.dataspace.PublishedValueCandidate.MemberDid;
import org.niis.xroad.cs.admin.core.dataspace.PublishedValueCandidate.SystemPair;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.DID_TOO_LONG;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.DSP_BASE_URL_NOT_HTTPS;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.HOST_DIFFERS;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.MALFORMED_DSP_BASE_URL;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.NON_ASCII_HOST;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.NOT_CANONICAL;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.UNPARSEABLE_REGISTERED_ADDRESS;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.UNRELATED_MEMBER;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.WRONG_SCHEME;

class PublishedValueValidatorTest {

    private static final String ADDRESS = "ss0.example.org";
    private static final String SYSTEM_DID = "did:web:ss0.example.org%3A7183:v1:system";
    private static final String DSP_URL = "https://ss0.example.org:8183/api/dsp";
    private static final String OWNER_DID = "did:web:ss0.example.org%3A7183:v1:DEV:COM:222";
    private static final String MEMBER_DID_PREFIX = "did:web:ss0.example.org%3A7183:v1:DEV:COM:";
    private static final String IPV6_SYSTEM_DID = "did:web:%5B2001%3Adb8%3A%3A1%5D%3A7183:v1:system";
    private static final String IPV6_DSP_URL = "https://[2001:db8::1]:8183/api/dsp";

    private static final ClientId OWNER = member("222");
    private static final ClientId CLIENT = member("333");

    private final PublishedValueValidator validator = validatorFor(ADDRESS);

    @ParameterizedTest
    @CsvSource({
            "did:web:ss0.example.org%3A7183:v2:DEV:COM:222,    222, WRONG_SCHEME",
            "did:key:ss0.example.org%3A7183:v1:DEV:COM:222,    222, WRONG_SCHEME",
            "did:web:ss0.example.org%3A7183:DEV:COM:222,       222, WRONG_SCHEME",
            "did:web:SS0.example.org%3A7183:v1:DEV:COM:222,    222, NOT_CANONICAL",
            "did:web:ss0.example.org%3A443:v1:DEV:COM:222,     222, NOT_CANONICAL",
            "did:web:ss1.example.org%3A7183:v1:DEV:COM:222,    222, HOST_DIFFERS",
            "did:web:ss0.example.org%3A7183:v1:DEV:COM:999,    999, UNRELATED_MEMBER",
            "did:web:ss0.example.org%3A7183:v1:DEV:COM:222,    333, UNRELATED_MEMBER",
            "did:web:ss0.example.org%3A7183:v1:DEV:COM:22%2,   222, UNRELATED_MEMBER",
            "did:web:ss0.example.org%3a7183:v1:DEV:COM:222,    222, NOT_CANONICAL",
            "did:web:ss0.example.org%3A7183:v1:DEV:COM:%32%322, 222, NOT_CANONICAL",
            "did:web:ss0.example.org%3A7183:v1:DEV:COM:2%3a2,  222, NOT_CANONICAL",
            "did:web:ss0.example.org%3A7183:v1:DEV:COM:%41,    222, NOT_CANONICAL",
            "did:web:ss0.example.org%3A7183:v1:system,         222, UNRELATED_MEMBER",
            "did:web:ss0.example.org%3A7183:v1:DEV:COM:222,    222,"
    })
    void shouldValidateMemberDid(String did, String claimedCode, PublishedValueRejection expected) {
        assertThat(validator.validate(new MemberDid(member(claimedCode), did))).isEqualTo(Optional.ofNullable(expected));
    }

    @Test
    void shouldRejectMemberDidWithoutDid() {
        assertThat(validator.validate(new MemberDid(OWNER, null))).contains(WRONG_SCHEME);
    }

    @Test
    void shouldAcceptMemberDidOfRegisteredClient() {
        assertThat(validator.validate(new MemberDid(CLIENT, "did:web:ss0.example.org%3A7183:v1:DEV:COM:333"))).isEmpty();
    }

    @Test
    void shouldRejectMemberDidForMemberWithSubsystemClaim() {
        var withSubsystem = ClientId.Conf.create("DEV", "COM", "222", "sub");

        assertThat(validator.validate(new MemberDid(withSubsystem, OWNER_DID))).contains(UNRELATED_MEMBER);
    }

    @Test
    void shouldRejectNonAsciiDidHost() {
        var did = "did:web:bücher.example%3A7183:v1:DEV:COM:222";

        assertThat(validator.validate(new MemberDid(OWNER, did))).contains(NON_ASCII_HOST);
    }

    @Test
    void shouldCheckDidLengthCap() {
        var code = "a".repeat(PublishedValueValidator.MAX_DID_LENGTH - MEMBER_DID_PREFIX.length());
        var atCap = new PublishedValueValidator(new PublishedValueContext(ADDRESS, OWNER, Set.of(member(code))));
        var didAtCap = MEMBER_DID_PREFIX + code;
        var didOverCap = didAtCap + "a";

        assertThat(didAtCap).hasSize(4000);
        assertThat(atCap.validate(new MemberDid(member(code), didAtCap))).isEmpty();
        assertThat(atCap.validate(new MemberDid(member(code + "a"), didOverCap))).contains(DID_TOO_LONG);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            SYSTEM_DID + "|" + DSP_URL + "|",
            "did:web:ss0.example.org%3A7183:v2:system|" + DSP_URL + "|WRONG_SCHEME",
            "did:web:SS0.example.org%3A7183:v1:system|" + DSP_URL + "|NOT_CANONICAL",
            "did:web:ss1.example.org%3A7183:v1:system|" + DSP_URL + "|HOST_DIFFERS",
            "did:web:ss0.example.org%3a7183:v1:system|" + DSP_URL + "|NOT_CANONICAL",
            "did:web:ss0.example.org%3A7183:v1:%73ystem|" + DSP_URL + "|NOT_CANONICAL",
            OWNER_DID + "|" + DSP_URL + "|FOREIGN_SYSTEM_DID",
            "did:web:ss0.example.org%3A7183:v1:system:extra|" + DSP_URL + "|FOREIGN_SYSTEM_DID",
            SYSTEM_DID + "|http://ss0.example.org:8183/api/dsp|DSP_BASE_URL_NOT_HTTPS",
            SYSTEM_DID + "|ss0.example.org:8183/api/dsp|DSP_BASE_URL_NOT_HTTPS",
            SYSTEM_DID + "|https://other.example.org:8183/api/dsp|HOST_DIFFERS",
            SYSTEM_DID + "|https://user@ss0.example.org:8183/api/dsp|MALFORMED_DSP_BASE_URL",
            SYSTEM_DID + "|https://ss0.example.org:8183/api/dsp?x=1|MALFORMED_DSP_BASE_URL",
            SYSTEM_DID + "|https://ss0.example.org:8183/api/dsp#top|MALFORMED_DSP_BASE_URL",
            SYSTEM_DID + "|https://ss0.example.org:0/api/dsp|MALFORMED_DSP_BASE_URL",
            SYSTEM_DID + "|https://ss0.example.org:70000/api/dsp|MALFORMED_DSP_BASE_URL",
            SYSTEM_DID + "|https://ss0.example.org:http/api/dsp|MALFORMED_DSP_BASE_URL",
            SYSTEM_DID + "|https:///api/dsp|MALFORMED_DSP_BASE_URL",
            SYSTEM_DID + "|https://2001:db8::1/api/dsp|MALFORMED_DSP_BASE_URL",
            SYSTEM_DID + "|https://bücher.example:8183/api/dsp|NON_ASCII_HOST",
            SYSTEM_DID + "|HTTPS://ss0.example.org:8183/api/dsp|NOT_CANONICAL",
            SYSTEM_DID + "|Https://ss0.example.org:8183/api/dsp|NOT_CANONICAL",
            SYSTEM_DID + "|https://SS0.Example.ORG:8183/api/dsp|NOT_CANONICAL",
            SYSTEM_DID + "|https://ss0.example.org:443/api/dsp|",
            SYSTEM_DID + "|https://other.example.org:443/api/dsp|HOST_DIFFERS",
            SYSTEM_DID + "|https://ss0.example.org:0443/api/dsp|NOT_CANONICAL",
            SYSTEM_DID + "|https://ss0.example.org:8183/api/ dsp|MALFORMED_DSP_BASE_URL",
            SYSTEM_DID + "|https://ss0.example.org:8183/api/%zz|MALFORMED_DSP_BASE_URL",
            SYSTEM_DID + "|https://ss0.example.org/api/dsp|",
            "did:web:bücher.example%3A7183:v1:system|" + DSP_URL + "|NON_ASCII_HOST"
    })
    void shouldValidateSystemPair(String did, String dspBaseUrl, PublishedValueRejection expected) {
        assertThat(validator.validate(new SystemPair(did, dspBaseUrl))).isEqualTo(Optional.ofNullable(expected));
    }

    @Test
    void shouldRejectSystemPairWithoutDspBaseUrl() {
        assertThat(validator.validate(new SystemPair(SYSTEM_DID, null))).contains(DSP_BASE_URL_NOT_HTTPS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ss0.example.org", "SS0.Example.ORG", "ss0.example.org:443", "ss0.example.org:7183"})
    void shouldAcceptRegisteredAddressInAnyNormalisation(String registered) {
        var normalised = validatorFor(registered);

        assertThat(normalised.validate(new SystemPair(SYSTEM_DID, DSP_URL))).isEmpty();
        assertThat(normalised.validate(new MemberDid(OWNER, OWNER_DID))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2001:db8::1", "[2001:db8::1]", "2001:DB8::1", "[2001:db8::1]:443"})
    void shouldAcceptIpv6RegisteredAddressInAnyNormalisation(String registered) {
        var ipv6 = validatorFor(registered);

        assertThat(ipv6.validate(new SystemPair(IPV6_SYSTEM_DID, IPV6_DSP_URL))).isEmpty();
        assertThat(ipv6.validate(new SystemPair(IPV6_SYSTEM_DID, "https://[2001:DB8::1]/api/dsp")))
                .contains(NOT_CANONICAL);
        assertThat(ipv6.validate(new SystemPair(IPV6_SYSTEM_DID, "https://[2001:db8::1]:443/api/dsp"))).isEmpty();
        assertThat(ipv6.validate(new SystemPair(SYSTEM_DID, DSP_URL))).contains(HOST_DIFFERS);
    }

    @Test
    void shouldRejectUnbracketedIpv6DspBaseUrlAndAcceptBracketedForm() {
        var ipv6 = validatorFor("2001:db8::1");

        assertThat(ipv6.validate(new SystemPair(IPV6_SYSTEM_DID, "https://2001:db8::1/api/dsp")))
                .contains(MALFORMED_DSP_BASE_URL);
        assertThat(ipv6.validate(new SystemPair(IPV6_SYSTEM_DID, "https://[2001:db8::1]/api/dsp"))).isEmpty();
    }

    @Test
    void shouldReportNonCanonicalPercentEncodingForIpv6Host() {
        var ipv6 = validatorFor("2001:db8::1");

        assertThat(ipv6.validate(new SystemPair("did:web:%5b2001%3adb8%3a%3a1%5d%3a7183:v1:system", IPV6_DSP_URL)))
                .contains(NOT_CANONICAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bücher.example", "例え.jp", "ss0.example.orgé"})
    void shouldRejectUnicodeRegisteredAddressWithoutFailing(String registered) {
        var unicode = validatorFor(registered);

        assertThat(unicode.validate(new SystemPair(SYSTEM_DID, DSP_URL))).contains(NON_ASCII_HOST);
        assertThat(unicode.validate(new MemberDid(OWNER, OWNER_DID))).contains(NON_ASCII_HOST);
    }

    @ParameterizedTest
    @MethodSource("unparseableRegisteredAddresses")
    void shouldRejectRegisteredAddressThatIsNotAHost(String registered) {
        var unparseable = validatorFor(registered);

        assertThat(unparseable.validate(new SystemPair(SYSTEM_DID, DSP_URL))).contains(UNPARSEABLE_REGISTERED_ADDRESS);
        assertThat(unparseable.validate(new MemberDid(OWNER, OWNER_DID))).contains(UNPARSEABLE_REGISTERED_ADDRESS);
    }

    static Stream<Arguments> unparseableRegisteredAddresses() {
        return Stream.of(null, "", "  ", ":7183", "[2001:db8::1", "ss0.example.org:http", "ss0.example.org:70000",
                "ss0.example.org ", "host:80:90", "a/b", "a%b", "[host:80]", "my_host", "[10.0.0.1]",
                "-a.org", "ss0.123")
                .map(Arguments::of);
    }

    @Test
    void shouldTreatValuesAsStaleAfterAddressChangeAndFreshAfterChangeBack() {
        var forA = validatorFor("a.example.org");
        var forB = validatorFor("b.example.org");
        var didForA = "did:web:a.example.org%3A7183:v1:DEV:COM:222";
        var systemDidForA = "did:web:a.example.org%3A7183:v1:system";
        var urlForA = "https://a.example.org:8183/api/dsp";

        assertThat(forA.isMemberDidStale(didForA)).isFalse();
        assertThat(forA.isSystemPairStale(systemDidForA, urlForA)).isFalse();
        assertThat(forB.isMemberDidStale(didForA)).isTrue();
        assertThat(forB.isSystemPairStale(systemDidForA, urlForA)).isTrue();
        assertThat(validatorFor("A.Example.ORG").isMemberDidStale(didForA)).isFalse();
        assertThat(validatorFor("a.example.org").isSystemPairStale(systemDidForA, urlForA)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "did:web:ss0.example.org%3A7183:v1:system,   https://other.example.org:8183/api/dsp",
            "did:web:other.example.org%3A7183:v1:system, https://ss0.example.org:8183/api/dsp",
            "did:web:ss0.example.org%3A7183:v1:system,   http://ss0.example.org:8183/api/dsp",
            "did:web:ss0.example.org%3A7183:v1:system,   https://",
            "not-a-did,                                  https://ss0.example.org:8183/api/dsp"
    })
    void shouldTreatSystemPairAsStaleWhenEitherHostDiffers(String did, String url) {
        assertThat(validator.isSystemPairStale(did, url)).isTrue();
    }

    @Test
    void shouldTreatUnreadableStoredValuesAndUnusableAddressAsStale() {
        assertThat(validator.isMemberDidStale("not-a-did")).isTrue();
        assertThat(validator.isMemberDidStale(null)).isTrue();
        assertThat(validator.isSystemPairStale(SYSTEM_DID, null)).isTrue();
        assertThat(validatorFor("bücher.example").isMemberDidStale(OWNER_DID)).isTrue();
        assertThat(validatorFor(null).isMemberDidStale(OWNER_DID)).isTrue();
    }

    private static PublishedValueValidator validatorFor(String registeredAddress) {
        return new PublishedValueValidator(new PublishedValueContext(registeredAddress, OWNER, Set.of(CLIENT)));
    }

    private static ClientId member(String code) {
        return ClientId.Conf.create("DEV", "COM", code);
    }
}
