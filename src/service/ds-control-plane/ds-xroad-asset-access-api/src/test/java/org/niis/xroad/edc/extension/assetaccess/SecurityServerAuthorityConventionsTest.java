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

package org.niis.xroad.edc.extension.assetaccess;

import ee.ria.xroad.common.identifier.ClientId;

import com.apicatalog.did.Did;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;
import org.niis.xroad.ds.identity.DidAuthority;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.edc.protocol.assetaccess.SecurityServerAuthority;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityServerAuthorityConventionsTest {

    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "222");
    private static final int DID_PORT = Integer.parseInt(DspConfigKeys.DID_PORT.defaultValue());
    private static final int DSP_PORT = Integer.parseInt(DspConfigKeys.DSP_PORT.defaultValue());

    @ParameterizedTest
    @ValueSource(strings = {"SS0.Example.ORG", "xn--bcher-kva.example", "10.0.0.1", "xrd-ss0.lxd", "2001:DB8::8", "[2001:db8::8]"})
    void theKeyOfAConventionAddressIsTheCanonicalHostAndTheDspPort(String host) {
        var expected = DidAuthority.canonicalHost(host) + ":" + DSP_PORT;

        assertThat(SecurityServerAuthority.of(dspTarget(host, ParticipantIdentifierScheme.memberDid(MEMBER, didAuthority(host)))))
                .isEqualTo(expected);
        assertThat(SecurityServerAuthority.of(dspTarget(host, ParticipantIdentifierScheme.systemDid(didAuthority(host)))))
                .isEqualTo(expected);
    }

    @Test
    void theKeyOfAnInternationalHostIsItsCanonicalAsciiHostAndPort() {
        assertThat(SecurityServerAuthority.of("https://bücher.example:" + DSP_PORT + "/api/dsp"))
                .isEqualTo(DidAuthority.canonicalHost("bücher.example") + ":" + DSP_PORT);
    }

    @Test
    void anUncompressedIpv6HostKeepsItsSpellingInTheDidButHasTheCompressedKey() {
        var uncompressed = "2001:db8:0:0:0:0:0:1";

        assertThat(DidAuthority.canonicalHost(uncompressed)).isEqualTo("[" + uncompressed + "]");
        assertThat(SecurityServerAuthority.of(dspTarget(uncompressed, ParticipantIdentifierScheme.systemDid(didAuthority(uncompressed)))))
                .isEqualTo("[2001:db8::1]:" + DSP_PORT);
    }

    private static String didAuthority(String host) {
        return DspConventions.didAuthority(host, DID_PORT);
    }

    private static String dspTarget(String host, Did did) {
        return DspConventions.dspTarget(DspConventions.dspBaseUrl(host, DSP_PORT), did.toString());
    }
}
