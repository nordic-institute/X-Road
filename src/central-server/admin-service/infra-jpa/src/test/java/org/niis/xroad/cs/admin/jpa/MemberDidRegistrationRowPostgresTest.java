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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.identifiers.jpa.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Entry;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Result;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Status;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.api.domain.SystemDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.config.ManagementServiceConfigProperties;
import org.niis.xroad.cs.admin.core.entity.MemberDidRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.RequestEntity;
import org.niis.xroad.cs.admin.core.entity.SystemDidRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.converter.DidBatchJson;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.service.managementrequest.AuthCertServerCheck;
import org.niis.xroad.cs.admin.core.service.managementrequest.AuthCertServerCheck.CheckedServer;
import org.niis.xroad.cs.admin.core.service.managementrequest.MemberDidRegistrationRequestHandler;
import org.niis.xroad.cs.admin.core.service.managementrequest.MemberDidRegistrationServiceImpl;
import org.niis.xroad.cs.admin.core.service.managementrequest.SystemDidRegistrationRequestHandler;
import org.niis.xroad.cs.admin.jpa.CentralServerSeed.ClientRef;
import org.niis.xroad.cs.admin.jpa.CentralServerSeed.Server;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.dspBaseUrl;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.memberDid;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.systemDid;

/**
 * The columns {@code did_entries} and {@code did_results} of the request row: the text written to Postgres, the
 * entity read back through the repository, and the rows of other request types.
 */
class MemberDidRegistrationRowPostgresTest extends AbstractCentralServerPostgresTest {

    private static final int MAX_ENTRIES = 100;
    private static final int LONG_DID_LENGTH = 4000;

    @Autowired
    private ParticipantValueStore store;
    @Autowired
    private SecurityServerRepository servers;
    @Autowired
    private IdentifierRepository<SecurityServerIdEntity> serverIds;
    @Autowired
    private RequestRepository<MemberDidRegistrationRequestEntity> memberRequests;
    @Autowired
    private RequestRepository<SystemDidRegistrationRequestEntity> systemRequests;

    private final Set<String> unresolvableDids = new HashSet<>();

    private AuthCertServerCheck authCertServerCheck;
    private MemberDidRegistrationRequestHandler memberHandler;
    private MemberDidRegistrationServiceImpl service;

    @BeforeEach
    void buildServiceAndHandlers() {
        when(requestMapper.toDto(any(MemberDidRegistrationRequestEntity.class))).thenAnswer(inv -> {
            var dto = new MemberDidRegistrationRequest();
            dto.setId(inv.<RequestEntity>getArgument(0).getId());
            return dto;
        });
        when(requestMapper.toDto(any(SystemDidRegistrationRequestEntity.class))).thenAnswer(inv -> {
            var dto = new SystemDidRegistrationRequest();
            dto.setId(inv.<RequestEntity>getArgument(0).getId());
            return dto;
        });
        authCertServerCheck = mock(AuthCertServerCheck.class);
        memberHandler = new MemberDidRegistrationRequestHandler(serverIds, memberRequests, servers, store, requestMapper,
                mock(AuditDataHelper.class));

        var managementRequestService = mock(ManagementRequestService.class);
        when(managementRequestService.add(any(MemberDidRegistrationRequest.class)))
                .thenAnswer(inv -> transactions.execute(status -> memberHandler.add(inv.getArgument(0))));
        var configProperties = mock(ManagementServiceConfigProperties.class);
        when(configProperties.getMemberDidRegMaxEntries()).thenReturn(MAX_ENTRIES);
        service = new MemberDidRegistrationServiceImpl(dids -> dids.stream()
                .map(did -> unresolvableDids.contains(did)
                        ? DidResolution.notResolved(did, DidResolutionReason.NO_DOCUMENT)
                        : DidResolution.resolved(did))
                .toList(), store, servers, managementRequestService, configProperties);
    }

    @Test
    void mixedBatchIsStoredAsSnakeCaseJsonInRequestOrderAndReadsBackThroughTheRepository() {
        var owner = seed.member();
        var server = seed.server(owner);
        var storedMember = servedClient(server);
        var duplicated = servedClient(server);
        var twoDids = servedClient(server);
        var claimant = servedClient(server);
        var unresolved = servedClient(server);
        var notServed = seed.member();
        storeSystemPair(server);
        var storedDid = didOf(server, storedMember);
        var duplicatedDid = didOf(server, duplicated);
        var unresolvedDid = didOf(server, unresolved);
        unresolvableDids.add(unresolvedDid);
        var entries = List.of(
                entry(duplicated, duplicatedDid),
                entry(storedMember, storedDid),
                entry(duplicated, duplicatedDid),
                entry(twoDids, didOf(server, twoDids)),
                entry(claimant, storedDid),
                entry(twoDids, didOf(server, claimant)),
                entry(notServed, didOf(server, notServed)),
                entry(unresolved, unresolvedDid));

        var answer = service.add(new MemberDidRegistrationRequest(SECURITY_SERVER, server.identifier(), entries));

        var expectedEntries = "[" + String.join(",", entries.stream()
                .map(entry -> "{\"member_id\":\"" + entry.memberId().asEncodedId() + "\",\"did\":\"" + entry.did() + "\"}")
                .toList()) + "]";
        var expectedResults = "["
                + resultJson(duplicatedDid, "STORED", null) + ","
                + resultJson(storedDid, "STORED", null) + ","
                + resultJson(duplicatedDid, "STORED", null) + ","
                + resultJson(didOf(server, twoDids), "FINAL_CONFLICT", "CONFLICTING_DUPLICATE") + ","
                + resultJson(storedDid, "FINAL_CONFLICT", "DID_OF_OTHER_MEMBER") + ","
                + resultJson(didOf(server, claimant), "FINAL_CONFLICT", "CONFLICTING_DUPLICATE") + ","
                + resultJson(didOf(server, notServed), "FIXABLE_CONFLICT", "MEMBER_NOT_SERVED") + ","
                + resultJson(unresolvedDid, "NOT_RESOLVED", "NO_DOCUMENT")
                + "]";
        var columns = rawColumns(answer.getId());
        assertThat((String) columns.get("did_entries")).isEqualTo(expectedEntries);
        assertThat((String) columns.get("did_results")).isEqualTo(expectedResults);

        var statuses = answer.getResults().stream().map(Result::status).toList();
        assertThat(statuses).containsExactly(Status.STORED, Status.STORED, Status.STORED, Status.FINAL_CONFLICT,
                Status.FINAL_CONFLICT, Status.FINAL_CONFLICT, Status.FIXABLE_CONFLICT, Status.NOT_RESOLVED);
        assertThat(answer.getId()).isPositive();

        entityManager.clear();
        var row = transactions.execute(status -> memberRequests.findById(answer.getId()).orElseThrow());
        assertThat(row.getDidEntries()).containsExactlyElementsOf(entries.stream()
                .map(entry -> new DidBatchJson.Entry(entry.memberId().asEncodedId(), entry.did())).toList());
        assertThat(row.getDidResults()).containsExactlyElementsOf(IntStream.range(0, entries.size())
                .mapToObj(i -> new DidBatchJson.Result(entries.get(i).did(), answer.getResults().get(i).status(),
                        answer.getResults().get(i).reason()))
                .toList());
    }

    @Test
    void systemDidRegistrationRowLeavesBothColumnsNull() {
        var server = seed.server(seed.member());
        when(authCertServerCheck.requireServer(any(), any())).thenReturn(new CheckedServer(server.id(), server.address()));
        var systemHandler = new SystemDidRegistrationRequestHandler(serverIds, systemRequests, servers, authCertServerCheck, store,
                requestMapper);

        var added = transactions.execute(status -> systemHandler.add(new SystemDidRegistrationRequest(Origin.SECURITY_SERVER,
                server.identifier(), systemDid(server.address(), 7183), dspBaseUrl(server.address()), new byte[] {1})));

        var columns = rawColumns(added.getId());
        assertThat(columns).containsEntry("did_entries", null).containsEntry("did_results", null);
    }

    @Test
    void fullBatchOfMaximumLengthDidsIsStoredAndReadsBackIntact() {
        var server = seed.server(seed.member());
        var entries = new ArrayList<Entry>();
        var settled = new LinkedHashMap<Integer, Result>();
        for (int i = 0; i < MAX_ENTRIES; i++) {
            var member = seed.member();
            var did = ("did:web:long" + i + ".test:" + "x".repeat(LONG_DID_LENGTH)).substring(0, LONG_DID_LENGTH);
            entries.add(entry(member, did));
            settled.put(i, new Result(member.identifier(), Status.FINAL_CONFLICT, "WRONG_SCHEME"));
        }
        var request = new MemberDidRegistrationRequest(SECURITY_SERVER, server.identifier(), entries);
        request.setSettledResults(settled);

        var added = transactions.execute(status -> memberHandler.add(request));

        entityManager.clear();
        var row = transactions.execute(status -> memberRequests.findById(added.getId()).orElseThrow());
        assertThat(row.getDidEntries()).hasSize(MAX_ENTRIES);
        assertThat(row.getDidResults()).hasSize(MAX_ENTRIES);
        for (int i = 0; i < MAX_ENTRIES; i++) {
            var did = entries.get(i).did();
            assertThat(did).hasSize(LONG_DID_LENGTH);
            assertThat(row.getDidEntries().get(i)).isEqualTo(new DidBatchJson.Entry(entries.get(i).memberId().asEncodedId(), did));
            assertThat(row.getDidResults().get(i)).isEqualTo(new DidBatchJson.Result(did, Status.FINAL_CONFLICT, "WRONG_SCHEME"));
        }
        assertThat((String) rawColumns(added.getId()).get("did_entries")).hasSizeGreaterThan(MAX_ENTRIES * LONG_DID_LENGTH);
    }

    private ClientRef servedClient(Server server) {
        var client = seed.member();
        seed.addClient(server, client);
        return client;
    }

    private void storeSystemPair(Server server) {
        jdbc.update("update security_servers set system_did = ?, dsp_base_url = ? where id = ?",
                systemDid(server.address(), 7183), dspBaseUrl(server.address()), server.id());
    }

    private static Entry entry(ClientRef member, String did) {
        return new Entry(member.identifier(), did);
    }

    private static String didOf(Server server, ClientRef member) {
        return memberDid(server.address(), member.identifier());
    }

    private static String resultJson(String did, String status, String reason) {
        return "{\"did\":\"" + did + "\",\"status\":\"" + status + "\""
                + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
    }

    private Map<String, Object> rawColumns(int requestId) {
        return jdbc.queryForMap("select did_entries, did_results from requests where id = ?", requestId);
    }
}
