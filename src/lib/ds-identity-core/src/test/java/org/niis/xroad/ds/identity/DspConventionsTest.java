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
package org.niis.xroad.ds.identity;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DspConventionsTest {

    private static final int DID_PORT = 7183;
    private static final String DID_AUTHORITY = "ss0.example.org:" + DID_PORT;

    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "222");

    @Test
    void shouldDeriveCredentialServiceEndpoint() {
        assertThat(DspConventions.credentialServiceId("DEV:COM:222")).isEqualTo("DEV:COM:222-credential-service");
        assertThat(DspConventions.CREDENTIAL_SERVICE_TYPE).isEqualTo("CredentialService");
        assertThat(DspConventions.credentialServiceUrl("ss0.example.org", 7185, "DEV:COM:222"))
                .isEqualTo("https://ss0.example.org:7185/api/credentials/v1/participants/DEV:COM:222");
    }

    @Test
    void shouldPercentEncodeCredentialServiceParticipantSegment() {
        assertThat(DspConventions.credentialServiceUrl("ss0.example.org", 7185, "TEST:ORG:222%2BA"))
                .endsWith("/api/credentials/v1/participants/TEST:ORG:222%252BA");
        assertThat(DspConventions.credentialServiceUrl("ss0.example.org", 7185, "a b\u00e4"))
                .endsWith("/api/credentials/v1/participants/a%20b%C3%A4");
    }

    @Test
    void shouldBracketIpv6CredentialServiceHost() {
        assertThat(DspConventions.credentialServiceUrl("2001:db8::8", 7185, "system"))
                .isEqualTo("https://[2001:db8::8]:7185/api/credentials/v1/participants/system");
        assertThat(DspConventions.credentialServiceUrl("[2001:db8::8]", 7185, "system"))
                .isEqualTo("https://[2001:db8::8]:7185/api/credentials/v1/participants/system");
    }

    @Test
    void shouldDeriveDidAuthorityUnderConfiguredDidPort() {
        assertThat(DspConventions.didAuthority("ss0.example.org", 9183)).isEqualTo("ss0.example.org:9183");
        assertThat(DspConventions.didAuthority("2001:db8::8", 9183)).isEqualTo("[2001:db8::8]:9183");
        assertThat(DspConventions.didAuthority("[2001:db8::8]", DID_PORT)).isEqualTo("[2001:db8::8]:7183");
    }

    @Test
    void participantPathSegmentEncodesOnlyThePercentSign() {
        assertThat(DspConventions.participantPathSegment("DEV:COM:A%2BB")).isEqualTo("DEV:COM:A%252BB");
        assertThat(DspConventions.participantPathSegment("DEV:COM:a-b.c_d")).isEqualTo("DEV:COM:a-b.c_d");
    }

    @ParameterizedTest
    @ValueSource(chars = {'\'', '(', ')', '+', ',', '=', '?'})
    void credentialServiceUrlParticipantSegmentEqualsTheCounterPartyPathSegment(char special) {
        var ctxId = ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "COM", "A" + special + "B"));

        var url = DspConventions.credentialServiceUrl("ss0.example.org", 7185, ctxId);

        assertThat(url).endsWith("/api/credentials/v1/participants/" + DspConventions.participantPathSegment(ctxId));
    }

    @ParameterizedTest
    @CsvSource({
            "ss0.example.org,    8183, https://ss0.example.org:8183/api/dsp",
            "ss0.example.org,    9183, https://ss0.example.org:9183/api/dsp",
            "ss0.example.org,    443,  https://ss0.example.org:443/api/dsp",
            "SS0.Example.ORG,    8183, https://ss0.example.org:8183/api/dsp",
            "2001:db8::1,        8183, https://[2001:db8::1]:8183/api/dsp",
            "[2001:db8::1],      8183, https://[2001:db8::1]:8183/api/dsp",
            "xrd-ss0.lxd,        8183, https://xrd-ss0.lxd:8183/api/dsp",
            "127.0.0.1,          8183, https://127.0.0.1:8183/api/dsp",
            "ss0.example.org,    1,    https://ss0.example.org:1/api/dsp",
            "ss0.example.org,    65535, https://ss0.example.org:65535/api/dsp"
    })
    void shouldBuildDspBaseUrl(String ssAddress, int dspPort, String expected) {
        assertThat(DspConventions.dspBaseUrl(ssAddress, dspPort)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"b\u00fccher.example", "ss0.example.org\u00e9", "\u4f8b\u3048.jp"})
    void shouldRefuseNonAsciiDspAddressNamingIt(String ssAddress) {
        assertThatThrownBy(() -> DspConventions.dspBaseUrl(ssAddress, 8183))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("'" + ssAddress + "'");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 65536, -1})
    void shouldRefuseOutOfRangeDspPortNamingIt(int dspPort) {
        assertThatThrownBy(() -> DspConventions.dspBaseUrl("ss0.example.org", dspPort))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("'" + dspPort + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void shouldRefuseBlankDspAddress(String ssAddress) {
        assertThatThrownBy(() -> DspConventions.dspBaseUrl(ssAddress, 8183))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void shouldBuildDspTargetFromPublishedBaseUrlAndMemberDid() {
        assertThat(DspConventions.dspTarget("https://ss0.example.org:8183/api/dsp", memberDid(MEMBER)))
                .isEqualTo("https://ss0.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1");
    }

    @Test
    void shouldEscapePercentSignInDspTargetContextSegment() {
        var member = ClientId.Conf.create("DEV", "COM", "A+B");

        assertThat(DspConventions.dspTarget("https://ss0.example.org:8183/api/dsp", memberDid(member)))
                .isEqualTo("https://ss0.example.org:8183/api/dsp/DEV:COM:A%252BB/http-dsp-profile-2025-1");
    }

    @Test
    void shouldBuildDspTargetFromPublishedSystemDid() {
        var systemDid = ParticipantIdentifierScheme.systemDid(DID_AUTHORITY).toString();

        assertThat(DspConventions.dspTarget("https://ss0.example.org:9183/custom", systemDid))
                .isEqualTo("https://ss0.example.org:9183/custom/system/http-dsp-profile-2025-1");
    }

    @Test
    void shouldStripTrailingSlashOfPublishedBaseUrlFromDspTarget() {
        assertThat(DspConventions.dspTarget("https://ss0.example.org:8183/api/dsp/", memberDid(MEMBER)))
                .isEqualTo("https://ss0.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1");
    }

    @Test
    void shouldBuildTheSameDspTargetFromADecodedParticipantAsFromItsDid() {
        var did = memberDid(MEMBER);

        assertThat(DspConventions.dspTarget("https://ss0.example.org:8183/api/dsp/", ParticipantIdentifierScheme.decodeDid(did)))
                .isEqualTo(DspConventions.dspTarget("https://ss0.example.org:8183/api/dsp/", did))
                .isEqualTo("https://ss0.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1");
    }

    @Test
    void shouldRefuseDspTargetOfMalformedDid() {
        assertThatThrownBy(() -> DspConventions.dspTarget("https://ss0.example.org:8183/api/dsp", "not-a-did"))
                .isInstanceOf(XrdRuntimeException.class);
    }

    private static String memberDid(ClientId member) {
        return ParticipantIdentifierScheme.memberDid(member, DID_AUTHORITY).toString();
    }
}
