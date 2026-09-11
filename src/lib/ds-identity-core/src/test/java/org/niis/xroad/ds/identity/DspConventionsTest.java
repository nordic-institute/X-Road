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
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DspConventionsTest {

    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "222");

    @Test
    void shouldDeriveDidAuthorityFromRegisteredAddress() {
        assertThat(DspConventions.didAuthority("ss0.example.org")).isEqualTo("ss0.example.org:7183");
    }

    @Test
    void shouldDeriveInterimHostAndManagementDids() {
        assertThat(DspConventions.hostDid("ss0.example.org")).isEqualTo("did:web:ss0.example.org%3A7183");
        assertThat(DspConventions.managementDid("ss0.example.org")).isEqualTo("did:web:ss0.example.org%3A7183:mgmt");
    }

    @Test
    void shouldDeriveMemberCounterPartyId() {
        assertThat(DspConventions.memberCounterPartyId(MEMBER, "ss0.example.org"))
                .isEqualTo("did:web:ss0.example.org%3A7183:v1:DEV:COM:222");
    }

    @Test
    void shouldDeriveMemberCounterPartyAddress() {
        assertThat(DspConventions.memberCounterPartyAddress(MEMBER, "ss0.example.org"))
                .isEqualTo("https://ss0.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1");
    }

    @ParameterizedTest
    @CsvSource({
            "xrd-ss0,     did:web:xrd-ss0%3A7183:v1:DEV:COM:222,     https://xrd-ss0:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1",
            "xrd-ss0.lxd, did:web:xrd-ss0.lxd%3A7183:v1:DEV:COM:222, https://xrd-ss0.lxd:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1",
            "ss0,         did:web:ss0%3A7183:v1:DEV:COM:222,         https://ss0:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1",
            "proxy.ss0,   did:web:proxy.ss0%3A7183:v1:DEV:COM:222,   https://proxy.ss0:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1"
    })
    void shouldDeriveCoordinatesForEachDevSubstrateAddress(String ssAddress, String expectedDid, String expectedAddress) {
        assertThat(DspConventions.memberCounterPartyId(MEMBER, ssAddress)).isEqualTo(expectedDid);
        assertThat(DspConventions.memberCounterPartyAddress(MEMBER, ssAddress)).isEqualTo(expectedAddress);
    }

    @Test
    void shouldBracketIpv6RegisteredAddress() {
        assertThat(DspConventions.didAuthority("2001:db8::8")).isEqualTo("[2001:db8::8]:7183");
        assertThat(DspConventions.hostDid("2001:db8::8")).isEqualTo("did:web:%5B2001%3Adb8%3A%3A8%5D%3A7183");
        assertThat(DspConventions.managementDid("2001:db8::8")).isEqualTo("did:web:%5B2001%3Adb8%3A%3A8%5D%3A7183:mgmt");
        assertThat(DspConventions.memberCounterPartyId(MEMBER, "2001:db8::8"))
                .isEqualTo("did:web:%5B2001%3Adb8%3A%3A8%5D%3A7183:v1:DEV:COM:222");
        assertThat(DspConventions.memberCounterPartyAddress(MEMBER, "2001:db8::8"))
                .isEqualTo("https://[2001:db8::8]:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1");
    }

    @Test
    void shouldNotDoubleBracketAlreadyBracketedIpv6Address() {
        assertThat(DspConventions.didAuthority("[2001:db8::8]")).isEqualTo("[2001:db8::8]:7183");
        assertThat(DspConventions.memberCounterPartyAddress(MEMBER, "[2001:db8::8]"))
                .isEqualTo("https://[2001:db8::8]:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1");
    }

    @Test
    void shouldEncodeMemberSegmentsInCounterPartyCoordinates() {
        var member = ClientId.Conf.create("DEV", "COM", "A+B");

        assertThat(DspConventions.memberCounterPartyId(member, "ss0.example.org"))
                .isEqualTo("did:web:ss0.example.org%3A7183:v1:DEV:COM:A%2BB");
        assertThat(DspConventions.memberCounterPartyAddress(member, "ss0.example.org"))
                .isEqualTo("https://ss0.example.org:8183/api/dsp/DEV:COM:A%252BB/http-dsp-profile-2025-1");
    }

    @Test
    void counterPartyAddressContextSegmentDecodesBackToTheRegisteredCtxId() {
        var member = ClientId.Conf.create("DEV", "COM", "A+B");

        var address = DspConventions.memberCounterPartyAddress(member, "ss0.example.org");
        var ctxIdSegment = address.split("/api/dsp/")[1].split("/")[0];

        assertThat(URLDecoder.decode(ctxIdSegment, StandardCharsets.UTF_8))
                .isEqualTo(ParticipantIdentifierScheme.memberCtxId(member));
    }

    @Test
    void shouldRejectSubsystemIdentifier() {
        var subsystem = ClientId.Conf.create("DEV", "COM", "222", "SUB");

        assertThatThrownBy(() -> DspConventions.memberCounterPartyId(subsystem, "ss0.example.org"))
                .isInstanceOf(XrdRuntimeException.class);
        assertThatThrownBy(() -> DspConventions.memberCounterPartyAddress(subsystem, "ss0.example.org"))
                .isInstanceOf(XrdRuntimeException.class);
    }
}
