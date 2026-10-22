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

package org.niis.xroad.cs.admin.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.dspBaseUrl;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.memberDid;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.newHost;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.systemDid;

/**
 * The record's schema as the real changelog creates it: the cascades and the constraints.
 */
class PublishedDidSchemaPostgresTest extends AbstractCentralServerPostgresTest {

    private static final int MAX_LENGTH = 4000;

    @Test
    void deletingServerRemovesItsSystemValuesAndAllItsMemberDidRowsOnly() {
        var owner = seed.member();
        var client = seed.member();
        var doomed = seed.server(owner);
        var surviving = seed.server(owner);
        seed.addClient(doomed, client);
        seed.addClient(surviving, client);
        var doomedHost = doomed.address();
        jdbc.update("update security_servers set system_did = ?, dsp_base_url = ? where id = ?",
                systemDid(doomedHost, 7183), dspBaseUrl(doomedHost), doomed.id());
        insertMemberDid(doomed.id(), client.id(), memberDid(doomedHost, client.identifier()));
        insertMemberDid(doomed.id(), owner.id(), memberDid(doomedHost, owner.identifier()));
        insertMemberDid(surviving.id(), client.id(), memberDid(surviving.address(), client.identifier()));

        jdbc.update("delete from security_servers where id = ?", doomed.id());

        assertThat(jdbc.queryForObject("select count(*) from security_servers where id = ?", Integer.class, doomed.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from security_servers where system_did = ?", Integer.class,
                systemDid(doomedHost, 7183))).isZero();
        assertThat(countMemberDids(doomed.id())).isZero();
        assertThat(countMemberDids(surviving.id())).isEqualTo(1);
    }

    @Test
    void deletingMemberRemovesItsDidRowsOnEveryServerAndTheRowsOfTheServersItOwns() {
        var doomedMember = seed.member();
        var other = seed.member();
        var otherOwner = seed.member();
        var ownedServer = seed.server(doomedMember);
        var foreignServer = seed.server(otherOwner);
        var otherServer = seed.server(otherOwner);
        seed.addClient(ownedServer, other);
        seed.addClient(foreignServer, doomedMember);
        seed.addClient(foreignServer, other);
        seed.addClient(otherServer, other);
        insertMemberDid(ownedServer.id(), other.id(), memberDid(ownedServer.address(), other.identifier()));
        insertMemberDid(foreignServer.id(), doomedMember.id(), memberDid(foreignServer.address(), doomedMember.identifier()));
        insertMemberDid(foreignServer.id(), other.id(), memberDid(foreignServer.address(), other.identifier()));
        insertMemberDid(otherServer.id(), other.id(), memberDid(otherServer.address(), other.identifier()));

        jdbc.update("delete from security_server_clients where id = ?", doomedMember.id());

        assertThat(jdbc.queryForObject("select count(*) from member_dids where member_id = ?", Integer.class,
                doomedMember.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from security_servers where id = ?", Integer.class,
                ownedServer.id())).isZero();
        assertThat(countMemberDids(ownedServer.id())).isZero();
        assertThat(countMemberDids(foreignServer.id())).isEqualTo(1);
        assertThat(countMemberDids(otherServer.id())).isEqualTo(1);
    }

    @Test
    void memberDidOfFourThousandCharactersIsStoredAndASecondInsertOfItIsRejected() {
        var firstOwner = seed.member();
        var secondOwner = seed.member();
        var first = seed.server(firstOwner);
        var second = seed.server(secondOwner);
        var did = "did:web:" + incompressibleSuffix(MAX_LENGTH - "did:web:".length());
        assertThat(did).hasSize(MAX_LENGTH);

        insertMemberDid(first.id(), firstOwner.id(), did);

        assertThat(jdbc.queryForObject("select length(did) from member_dids where security_server_id = ?", Integer.class,
                first.id())).isEqualTo(MAX_LENGTH);
        assertThatThrownBy(() -> insertMemberDid(second.id(), secondOwner.id(), did))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("member_dids_did_excl");
        assertThat(countMemberDids(second.id())).isZero();
    }

    @Test
    void systemDidIsUniqueAcrossServers() {
        var first = seed.server(seed.member());
        var second = seed.server(seed.member());
        var did = systemDid(newHost(), 7183);
        jdbc.update("update security_servers set system_did = ?, dsp_base_url = ? where id = ?", did, "https://a.test/dsp", first.id());

        assertThatThrownBy(() -> jdbc.update("update security_servers set system_did = ?, dsp_base_url = ? where id = ?",
                did, "https://b.test/dsp", second.id()))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("security_servers_system_did_excl");
    }

    @Test
    void dspBaseUrlWithPathOverFourThousandCharactersIsStored() {
        var server = seed.server(seed.member());
        var url = "https://" + server.address() + ":8183/" + "p".repeat(MAX_LENGTH + 100);

        jdbc.update("update security_servers set system_did = ?, dsp_base_url = ? where id = ?",
                systemDid(server.address(), 7183), url, server.id());

        Map<String, Object> stored = jdbc.queryForMap("select dsp_base_url from security_servers where id = ?", server.id());
        assertThat(stored.get("dsp_base_url")).isEqualTo(url);
        assertThat(url.length()).isGreaterThan(MAX_LENGTH);
    }

    @Test
    void systemDidAndDspBaseUrlAreStoredBothOrNeither() {
        var server = seed.server(seed.member());

        assertThatThrownBy(() -> jdbc.update("update security_servers set system_did = ? where id = ?",
                systemDid(server.address(), 7183), server.id()))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("security_servers_system_values_check");
        assertThatThrownBy(() -> jdbc.update("update security_servers set dsp_base_url = ? where id = ?",
                dspBaseUrl(server.address()), server.id()))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("security_servers_system_values_check");
    }

    private static String incompressibleSuffix(int length) {
        var random = new Random(MAX_LENGTH);
        var alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        var suffix = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            suffix.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return suffix.toString();
    }
}
