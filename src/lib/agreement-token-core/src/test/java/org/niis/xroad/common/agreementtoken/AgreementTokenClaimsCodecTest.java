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
package org.niis.xroad.common.agreementtoken;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.CLAIM_SCOPE;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.decodeClient;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.decodeScope;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.decodeService;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.encodeClient;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.encodeScope;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.encodeService;

class AgreementTokenClaimsCodecTest {

    private static final ClientId SUBSYSTEM_CLIENT = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ClientId MEMBER_CLIENT = ClientId.Conf.create("DEV", "COM", "222");

    @Test
    void shouldRoundTripSubsystemClientId() {
        var encoded = encodeClient(SUBSYSTEM_CLIENT);

        assertThat(decodeClient(encoded)).isEqualTo(SUBSYSTEM_CLIENT);
    }

    @Test
    void shouldRoundTripMemberOnlyClientId() {
        var encoded = encodeClient(MEMBER_CLIENT);

        assertThat(decodeClient(encoded)).isEqualTo(MEMBER_CLIENT);
    }

    @Test
    void shouldRejectMalformedClientId() {
        assertThatThrownBy(() -> decodeClient("DEV:COM"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankClientId() {
        assertThatThrownBy(() -> decodeClient(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRoundTripServiceIdWithVersion() {
        var service = ServiceId.Conf.create(SUBSYSTEM_CLIENT, "getData", "v1");

        var encoded = encodeService(service);

        assertThat(decodeService(encoded)).isEqualTo(service);
    }

    @Test
    void shouldRoundTripServiceIdWithoutVersion() {
        var service = ServiceId.Conf.create(SUBSYSTEM_CLIENT, "getData");

        var encoded = encodeService(service);

        assertThat(decodeService(encoded)).isEqualTo(service);
    }

    @Test
    void shouldRejectMalformedServiceId() {
        assertThatThrownBy(() -> decodeService("not-a-service-id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankServiceId() {
        assertThatThrownBy(() -> decodeService(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRoundTripScope() {
        var scope = List.of(new AgreementTokenScope("GET", "/foo/*"), new AgreementTokenScope("POST", "/bar"));

        var claimsSet = new JWTClaimsSet.Builder().claim(CLAIM_SCOPE, encodeScope(scope)).build();

        assertThat(decodeScope(claimsSet)).isEqualTo(scope);
    }

    @Test
    void shouldRejectMissingScopeClaim() {
        var claimsSet = new JWTClaimsSet.Builder().build();

        assertThatThrownBy(() -> decodeScope(claimsSet))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectScopeEntryMissingPath() {
        var claimsSet = new JWTClaimsSet.Builder()
                .claim(CLAIM_SCOPE, List.of(Map.of("method", "GET")))
                .build();

        assertThatThrownBy(() -> decodeScope(claimsSet))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectScopeEntryThatIsNotAnObject() {
        var claimsSet = new JWTClaimsSet.Builder()
                .claim(CLAIM_SCOPE, List.of("not-an-object"))
                .build();

        assertThatThrownBy(() -> decodeScope(claimsSet))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
