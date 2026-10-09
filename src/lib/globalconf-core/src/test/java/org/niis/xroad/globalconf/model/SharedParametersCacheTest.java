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
package org.niis.xroad.globalconf.model;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.Test;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class SharedParametersCacheTest {

    @Test
    void shouldNotCacheApprovedConnectorTlsCAData() throws Exception {
        var memberCaCert = TestCertUtil.getCaCert();

        var approvedCa = new SharedParameters.ApprovedCA();
        approvedCa.setName("Member CA");
        approvedCa.setCertificateProfileInfo("certificateProfileInfo");
        approvedCa.setTopCA(new SharedParameters.CaInfo(memberCaCert.getEncoded(), List.of()));
        approvedCa.setIntermediateCas(List.of());

        var connectorTlsCa = new SharedParameters.ApprovedConnectorTlsCA();
        connectorTlsCa.setName("DS TLS CA");
        connectorTlsCa.setTopCA(new SharedParameters.CaInfo("not a real certificate".getBytes(UTF_8), List.of()));
        connectorTlsCa.setIntermediateCas(List.of(
                new SharedParameters.CaInfo("not a real intermediate certificate either".getBytes(UTF_8), List.of())));

        var sharedParameters = SharedParameters.builder()
                .instanceIdentifier("CS")
                .approvedCAs(List.of(approvedCa))
                .approvedConnectorTlsCAs(List.of(connectorTlsCa))
                .securityServers(List.of())
                .build();

        var cache = new SharedParametersCache(sharedParameters);

        assertThat(cache.getVerificationCaCerts()).containsExactly(memberCaCert);
        assertThat(cache.getCaCertsAndApprovedCAData()).containsOnlyKeys(memberCaCert);
        assertThat(cache.getCaCertsAndOcspData()).containsOnlyKeys(memberCaCert);
        assertThat(cache.getSubjectsAndCaCerts()).containsValue(memberCaCert);
    }

    @Test
    void shouldKeepSeparateSystemValuesAndMemberDidsForServersSharingOneAddress() {
        var owner = ClientId.Conf.create("DEV", "COM", "222");
        var serverOne = SecurityServerId.Conf.create(owner, "ss1");
        var serverTwo = SecurityServerId.Conf.create(owner, "ss2");

        var cache = new SharedParametersCache(SharedParameters.builder()
                .instanceIdentifier("DEV")
                .approvedCAs(List.of())
                .members(List.of(member(owner,
                        new SharedParameters.MemberDid(serverOne, "did:web:ss0.example.org%3A7183:v1:DEV:COM:222"),
                        new SharedParameters.MemberDid(serverTwo, "did:web:ss0.example.org%3A7283:v1:DEV:COM:222"))))
                .securityServers(List.of(
                        server(owner, "ss1", "ss0.example.org", "did:web:ss0.example.org%3A7183:v1:system",
                                "https://ss0.example.org:8183/api/dsp"),
                        server(owner, "ss2", "ss0.example.org", "did:web:ss0.example.org%3A7283:v1:system",
                                "https://ss0.example.org:8283/api/dsp")))
                .build());

        assertThat(cache.getSystemValuesByServerId())
                .containsEntry(serverOne, new ServerSystemValues("did:web:ss0.example.org%3A7183:v1:system",
                        "https://ss0.example.org:8183/api/dsp"))
                .containsEntry(serverTwo, new ServerSystemValues("did:web:ss0.example.org%3A7283:v1:system",
                        "https://ss0.example.org:8283/api/dsp"));
        assertThat(cache.getMemberDids().get(owner))
                .containsEntry(serverOne, "did:web:ss0.example.org%3A7183:v1:DEV:COM:222")
                .containsEntry(serverTwo, "did:web:ss0.example.org%3A7283:v1:DEV:COM:222");
    }

    @Test
    void shouldResolveMemberDidWhenMemberIsNotInServerClientList() {
        var owner = ClientId.Conf.create("DEV", "COM", "222");
        var otherMember = ClientId.Conf.create("DEV", "COM", "333");
        var server = SecurityServerId.Conf.create(owner, "ss1");

        var cache = new SharedParametersCache(SharedParameters.builder()
                .instanceIdentifier("DEV")
                .approvedCAs(List.of())
                .members(List.of(member(owner),
                        member(otherMember, new SharedParameters.MemberDid(server, "did:web:ss0.example.org:v1"))))
                .securityServers(List.of(server(owner, "ss1", "ss0.example.org", null, null)))
                .build());

        assertThat(cache.getSecurityServerClients().get(server)).containsExactly(owner);
        assertThat(cache.getMemberDids().get(otherMember)).containsEntry(server, "did:web:ss0.example.org:v1");
    }

    @Test
    void shouldNotCacheSystemValuesOfServerWithoutBothValues() {
        var owner = ClientId.Conf.create("DEV", "COM", "222");

        var cache = new SharedParametersCache(SharedParameters.builder()
                .instanceIdentifier("DEV")
                .approvedCAs(List.of())
                .securityServers(List.of(
                        server(owner, "ss1", "ss0.example.org", null, null),
                        server(owner, "ss2", "ss1.example.org", "did:web:ss1.example.org:v1:system", null),
                        server(owner, "ss3", "ss2.example.org", null, "https://ss2.example.org/api/dsp")))
                .build());

        assertThat(cache.getSystemValuesByServerId()).isEmpty();
    }

    private static SharedParameters.Member member(ClientId id, SharedParameters.MemberDid... dids) {
        var member = new SharedParameters.Member();
        member.setId(id);
        member.setMemberClass(new SharedParameters.MemberClass(id.getMemberClass(), id.getMemberClass()));
        member.setMemberCode(id.getMemberCode());
        member.setDids(List.of(dids));
        return member;
    }

    private static SharedParameters.SecurityServer server(ClientId owner, String serverCode, String address,
                                                          String systemDid, String dspBaseUrl) {
        var server = new SharedParameters.SecurityServer();
        server.setOwner(owner);
        server.setServerCode(serverCode);
        server.setAddress(address);
        server.setAuthCertHashes(List.of());
        server.setClients(List.of());
        server.setSystemDid(systemDid);
        server.setDspBaseUrl(dspBaseUrl);
        return server;
    }
}
