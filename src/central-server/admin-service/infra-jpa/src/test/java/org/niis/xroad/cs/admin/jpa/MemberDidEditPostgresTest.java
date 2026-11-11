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

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.exception.ErrorMessage;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.MemberService;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.dataspace.ParticipantValueValidator;
import org.niis.xroad.cs.admin.core.dataspace.RegisteredServer;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMemberMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerClientMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerMapper;
import org.niis.xroad.cs.admin.core.service.MemberServiceImpl;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.VALUE_DIFFERS;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.memberDid;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.newHost;
import static org.niis.xroad.ds.identity.ParticipantIdentifierScheme.MAX_DID_LENGTH;

/**
 * The Central Server edit of a member DID against Postgres: the stored DID is replaced or left as it was.
 */
@Import(MemberServiceImpl.class)
class MemberDidEditPostgresTest extends AbstractCentralServerPostgresTest {

    private record Served(CentralServerSeed.Server server, CentralServerSeed.ClientRef client, String storedDid) {
    }

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
    @Autowired
    private ParticipantValueStore store;

    @Test
    void replacesTheStoredDidAndLeavesNoOldOne() {
        var served = servedClientWithStoredDid();
        var newDid = otherPort(served.storedDid());

        edit(served, newDid);

        assertThat(storedDids(served.server().id())).containsExactly(newDid);
    }

    @Test
    void refusesADidOfAnotherSchemeVersionAndStoresNothing() {
        var served = servedClientWithStoredDid();

        assertRefused(served, served.storedDid().replace(":v1:", ":v2:"), ErrorMessage.DID_WRONG_SCHEME);
    }

    @Test
    void refusesAValueThatIsNotADidAndStoresNothing() {
        var served = servedClientWithStoredDid();

        assertRefused(served, "not-a-did", ErrorMessage.DID_WRONG_SCHEME);
    }

    @Test
    void refusesADidThatIsTooLongAndStoresNothing() {
        var served = servedClientWithStoredDid();

        assertRefused(served, served.storedDid() + "a".repeat(MAX_DID_LENGTH), ErrorMessage.DID_TOO_LONG);
    }

    @Test
    void refusesADidWithANonAsciiHostAndStoresNothing() {
        var served = servedClientWithStoredDid();

        assertRefused(served, memberDid("bücher.example", served.client().identifier()), ErrorMessage.DID_NON_ASCII_HOST);
    }

    @Test
    void refusesADidThatCannotBeDecodedAndStoresNothing() {
        var served = servedClientWithStoredDid();

        assertRefused(served, served.storedDid() + "%2", ErrorMessage.DID_UNDECODABLE);
    }

    @Test
    void refusesADidThatIsNotCanonicalAndStoresNothing() {
        var served = servedClientWithStoredDid();
        var upperCaseHost = memberDid(served.server().address().toUpperCase(Locale.ROOT), served.client().identifier());

        assertRefused(served, upperCaseHost, ErrorMessage.DID_NOT_CANONICAL);
    }

    @Test
    void refusesADidOfAnotherHostAndStoresNothing() {
        var served = servedClientWithStoredDid();

        assertRefused(served, memberDid(newHost(), served.client().identifier()), ErrorMessage.DID_HOST_DIFFERS);
    }

    @Test
    void refusesTheDidOfAnotherMemberAndStoresNothing() {
        var served = servedClientWithStoredDid();
        var ownersDid = memberDid(served.server().address(), served.server().owner().identifier());

        assertRefused(served, ownersDid, ErrorMessage.DID_OF_OTHER_MEMBER);
    }

    @Test
    void refusesAMemberThatIsNotAClientOfTheServerAndStoresNothing() {
        var served = servedClientWithStoredDid();
        var outsider = seed.member();

        assertThatThrownBy(() -> service.editMemberDid(served.server().identifier(), outsider.identifier(),
                memberDid(served.server().address(), outsider.identifier())))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorDeviation().code()).isEqualTo(ErrorMessage.DID_MEMBER_NOT_SERVED.code()));

        assertThat(storedDids(served.server().id())).containsExactly(served.storedDid());
    }

    @Test
    void refusesAnEditWhenTheRegisteredAddressCannotBeParsedAndStoresNothing() {
        var served = servedClientWithStoredDid();
        jdbc.update("update security_servers set address = 'a/b' where id = ?", served.server().id());

        assertRefused(served, otherPort(served.storedDid()), ErrorMessage.DID_UNPARSEABLE_SERVER_ADDRESS);
    }

    @Test
    void refusesADidStoredOnAnotherServerAndChangesNeitherServer() {
        var served = servedClientWithStoredDid();
        var otherServer = seed.server(seed.member(), served.server().address());
        seed.addClient(otherServer, served.client());
        var heldElsewhere = otherPort(served.storedDid());
        insertMemberDid(otherServer.id(), served.client().id(), heldElsewhere);

        assertRefused(served, heldElsewhere, ErrorMessage.DID_HELD_BY_ANOTHER_SERVER);

        assertThat(storedDids(otherServer.id())).containsExactly(heldElsewhere);
    }

    @ParameterizedTest(name = "DID host prefix [{0}]")
    @ValueSource(strings = {"", "moved."})
    void refusesAMemberWithNoStoredDidAsNotStoredWhateverTheHostAndStoresNothing(String hostPrefix) {
        var server = seed.server(seed.member());
        var client = seed.member();
        seed.addClient(server, client);

        assertThatThrownBy(() -> service.editMemberDid(
                server.identifier(), client.identifier(), memberDid(hostPrefix + server.address(), client.identifier())))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorDeviation().code()).isEqualTo(ErrorMessage.DID_NOT_STORED.code()));

        assertThat(storedDids(server.id())).isEmpty();
    }

    @Test
    void unknownMemberIsNotFound() {
        var served = servedClientWithStoredDid();
        var unknown = ClientId.Conf.create("DEV", "COM", "222");

        assertThatThrownBy(() -> service.editMemberDid(served.server().identifier(), unknown,
                memberDid(served.server().address(), unknown)))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getErrorDeviation().code()).isEqualTo(ErrorMessage.MEMBER_NOT_FOUND.code()));

        assertThat(storedDids(served.server().id())).containsExactly(served.storedDid());
    }

    @Test
    void equalDidSucceedsAndChangesNothing() {
        var served = servedClientWithStoredDid();
        var before = jdbc.queryForList("select * from member_dids where security_server_id = ?", served.server().id());

        assertThatCode(() -> edit(served, served.storedDid())).doesNotThrowAnyException();

        assertThat(jdbc.queryForList("select * from member_dids where security_server_id = ?", served.server().id()))
                .isEqualTo(before);
    }

    @Test
    void repeatingTheSameEditLeavesTheSameStateAndSucceeds() {
        var served = servedClientWithStoredDid();
        var newDid = otherPort(served.storedDid());

        edit(served, newDid);
        var afterFirst = storedDids(served.server().id());
        edit(served, newDid);

        assertThat(storedDids(served.server().id())).isEqualTo(afterFirst).containsExactly(newDid);
    }

    @Test
    void sameDidPutOnTwoServersConcurrentlyRefusesTheLoserAsHeldByAnotherServerAndStoresNothingForIt() throws Exception {
        var sharedHost = newHost();
        var client = seed.member();
        var winner = serverServing(client, sharedHost, 7183);
        var loser = serverServing(client, sharedHost, 7184);
        var contested = memberDid(sharedHost, client.identifier()).replace("%3A7183", "%3A7190");
        var loserBefore = storedDids(loser.server().id());
        var started = System.nanoTime();

        var racing = raceForLock(
                () -> service.editMemberDid(winner.server().identifier(), client.identifier(), contested),
                () -> service.editMemberDid(loser.server().identifier(), client.identifier(), contested));

        assertThatThrownBy(racing::get)
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(BadRequestException.class, e -> assertThat(e.getErrorDeviation().code())
                        .isEqualTo(ErrorMessage.DID_HELD_BY_ANOTHER_SERVER.code()));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(WAIT_SECONDS));
        assertThat(storedDids(winner.server().id())).containsExactly(contested);
        assertThat(storedDids(loser.server().id())).isEqualTo(loserBefore);
    }

    @Test
    void adminReplaceThenTheServersOwnSendOfThatDidIsAcceptedAsEqual() {
        var served = servedClientWithStoredDid();
        var newDid = otherPort(served.storedDid());
        edit(served, newDid);

        var results = store.writeMemberDidsOnce(served.server().id(), Map.of(served.client().identifier(), newDid));

        assertThat(results).containsEntry(served.client().identifier(), ParticipantValueResult.UNCHANGED);
        assertThat(storedDids(served.server().id())).containsExactly(newDid);
    }

    @Test
    void adminReplaceOfADidThatIsNotStaleThenTheServersResendOfItsOldDidIsRefused() {
        var served = servedClientWithStoredDid();
        var newDid = otherPort(served.storedDid());
        edit(served, newDid);

        var results = store.writeMemberDidsOnce(served.server().id(), Map.of(served.client().identifier(), served.storedDid()));

        assertThat(results).containsEntry(served.client().identifier(), ParticipantValueResult.rejected(VALUE_DIFFERS));
        assertThat(storedDids(served.server().id())).containsExactly(newDid);
    }

    @Test
    void addressChangeMakesTheStoredDidStaleAndAReplaceAtTheNewAddressClearsIt() {
        var served = servedClientWithStoredDid();
        assertThat(validatorAt(served, served.server().address()).isMemberDidStale(served.storedDid())).isFalse();
        var newAddress = newHost();
        jdbc.update("update security_servers set address = ? where id = ?", newAddress, served.server().id());
        assertThat(validatorAt(served, newAddress).isMemberDidStale(served.storedDid())).isTrue();
        var replacement = memberDid(newAddress, served.client().identifier());

        service.editMemberDid(served.server().identifier(), served.client().identifier(), replacement);

        assertThat(storedDids(served.server().id())).containsExactly(replacement);
        assertThat(validatorAt(served, newAddress).isMemberDidStale(replacement)).isFalse();
    }

    private Served servedClientWithStoredDid() {
        var server = seed.server(seed.member());
        return servedBy(server, seed.member(), server.address(), 7183);
    }

    private Served serverServing(CentralServerSeed.ClientRef client, String host, int port) {
        return servedBy(seed.server(seed.member(), host), client, host, port);
    }

    private Served servedBy(CentralServerSeed.Server server, CentralServerSeed.ClientRef client, String host, int port) {
        seed.addClient(server, client);
        var did = memberDid(host, client.identifier()).replace("%3A7183", "%3A" + port);
        insertMemberDid(server.id(), client.id(), did);
        return new Served(server, client, did);
    }

    private void edit(Served served, String did) {
        service.editMemberDid(served.server().identifier(), served.client().identifier(), did);
    }

    private void assertRefused(Served served, String did, ErrorMessage expected) {
        assertThatThrownBy(() -> edit(served, did))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorDeviation().code()).isEqualTo(expected.code()));

        assertThat(storedDids(served.server().id())).containsExactly(served.storedDid());
    }

    private static ParticipantValueValidator validatorAt(Served served, String address) {
        return new ParticipantValueValidator(new RegisteredServer(address, served.server().owner().identifier(), Set.of()));
    }

    private static String otherPort(String did) {
        return did.replace("%3A7183", "%3A7184");
    }

    private List<String> storedDids(int serverId) {
        return jdbc.queryForList("select did from member_dids where security_server_id = ? order by id", String.class, serverId);
    }
}
