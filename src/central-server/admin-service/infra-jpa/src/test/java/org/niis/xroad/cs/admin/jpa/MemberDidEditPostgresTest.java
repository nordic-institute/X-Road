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
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.MemberService;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMemberMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerClientMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerMapper;
import org.niis.xroad.cs.admin.core.service.MemberServiceImpl;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.memberDid;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.newHost;

/**
 * The Central Server edit of a member DID against Postgres: the stored DID is replaced or left as it was.
 */
@Import(MemberServiceImpl.class)
class MemberDidEditPostgresTest extends AbstractCentralServerPostgresTest {

    @MockitoBean
    private GlobalGroupMemberService globalGroupMemberService;
    @MockitoBean
    private GlobalGroupMemberMapper globalGroupMemberMapper;
    @MockitoBean
    private SecurityServerMapper securityServerMapper;
    @MockitoBean
    private SecurityServerClientMapper securityServerClientMapper;
    @MockitoBean
    private AuditDataHelper auditDataHelper;

    @Autowired
    private MemberService service;

    @Test
    void replacesTheStoredDidAndLeavesNoOldOne() {
        var owner = seed.member();
        var server = seed.server(owner);
        var client = seed.member();
        seed.addClient(server, client);
        insertMemberDid(server.id(), client.id(), memberDid(server.address(), client.identifier()));
        var newDid = memberDid(server.address(), client.identifier()).replace("%3A7183", "%3A7184");

        service.editMemberDid(server.identifier(), client.identifier(), newDid);

        assertThat(storedDids(server.id())).containsExactly(newDid);
    }

    @Test
    void refusedDidStoresNothing() {
        var owner = seed.member();
        var server = seed.server(owner);
        var client = seed.member();
        seed.addClient(server, client);
        var storedDid = memberDid(server.address(), client.identifier());
        insertMemberDid(server.id(), client.id(), storedDid);
        var didOfAnotherHost = memberDid(newHost(), client.identifier());

        assertThatThrownBy(() -> service.editMemberDid(server.identifier(), client.identifier(), didOfAnotherHost))
                .isInstanceOf(BadRequestException.class);

        assertThat(storedDids(server.id())).containsExactly(storedDid);
    }

    @Test
    void memberWithNoStoredDidStaysWithoutOne() {
        var server = seed.server(seed.member());
        var client = seed.member();
        seed.addClient(server, client);

        assertThatThrownBy(() -> service.editMemberDid(
                server.identifier(), client.identifier(), memberDid(server.address(), client.identifier())))
                .isInstanceOf(BadRequestException.class);

        assertThat(storedDids(server.id())).isEmpty();
    }

    @Test
    void repeatingTheSameEditLeavesTheSameStateAndSucceeds() {
        var server = seed.server(seed.member());
        var client = seed.member();
        seed.addClient(server, client);
        insertMemberDid(server.id(), client.id(), memberDid(server.address(), client.identifier()));
        var newDid = memberDid(server.address(), client.identifier()).replace("%3A7183", "%3A7184");

        service.editMemberDid(server.identifier(), client.identifier(), newDid);
        var afterFirst = storedDids(server.id());
        service.editMemberDid(server.identifier(), client.identifier(), newDid);

        assertThat(storedDids(server.id())).isEqualTo(afterFirst).containsExactly(newDid);
    }

    private List<String> storedDids(int serverId) {
        return jdbc.queryForList("select did from member_dids where security_server_id = ? order by id", String.class, serverId);
    }
}
