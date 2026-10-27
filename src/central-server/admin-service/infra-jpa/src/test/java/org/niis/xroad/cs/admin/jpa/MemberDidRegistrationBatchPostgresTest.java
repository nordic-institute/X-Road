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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.identifiers.jpa.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Entry;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Status;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.api.service.DataspaceMemberDidService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.SubsystemService;
import org.niis.xroad.cs.admin.core.config.ManagementServiceConfigProperties;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.PageConverter;
import org.niis.xroad.cs.admin.core.converter.PageRequestDtoConverter;
import org.niis.xroad.cs.admin.core.dataspace.DataspaceMemberDidServiceImpl;
import org.niis.xroad.cs.admin.core.entity.MemberDidRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ClientIdMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.FlattenedSecurityServerClientViewMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerClientMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.ServerClientMapperImpl;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.paging.StableSortHelper;
import org.niis.xroad.cs.admin.core.service.ClientServiceImpl;
import org.niis.xroad.cs.admin.core.service.SecurityServerServiceImpl;
import org.niis.xroad.cs.admin.core.service.managementrequest.MemberDidRegistrationRequestHandler;
import org.niis.xroad.cs.admin.core.service.managementrequest.MemberDidRegistrationServiceImpl;
import org.niis.xroad.cs.admin.globalconf.generator.SharedParametersV7Fixture;
import org.niis.xroad.cs.admin.jpa.CentralServerSeed.ClientRef;
import org.niis.xroad.cs.admin.jpa.CentralServerSeed.Server;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.dspBaseUrl;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.memberDid;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.systemDid;

/**
 * A full batch of member DIDs, resolved at the latency of a real Issuer Service, is stored within the admin client
 * timeout and reaches the version 7 shared parameters. Runs the pre-transaction service, the request handler and the
 * store on Postgres.
 */
@Import({ClientServiceImpl.class, SecurityServerServiceImpl.class, DataspaceMemberDidServiceImpl.class,
        SecurityServerMapperImpl.class, SecurityServerClientMapperImpl.class, ClientIdMapperImpl.class,
        ServerClientMapperImpl.class, FlattenedSecurityServerClientViewMapperImpl.class, StableSortHelper.class,
        PageRequestDtoConverter.class, PageConverter.class})
class MemberDidRegistrationBatchPostgresTest extends AbstractCentralServerPostgresTest {

    private static final int BATCH_SIZE = 100;
    private static final int PARALLEL_FETCHES = 16;
    private static final Duration FETCH_LATENCY = Duration.ofMillis(200);
    private static final Duration ADMIN_CLIENT_TIMEOUT = Duration.ofSeconds(5);
    private static final int WRITE_STATEMENTS_PER_ENTRY = 2;
    private static final int DRY_RUN_STATEMENT_BOUND = 4;
    private static final int WRITE_STATEMENT_BOUND = WRITE_STATEMENTS_PER_ENTRY * BATCH_SIZE + 6;
    private static final int RESEND_STATEMENT_BOUND = 8;

    @MockitoBean
    private ManagementRequestService managementRequestService;
    @MockitoBean
    private GlobalGroupMemberService globalGroupMemberService;
    @MockitoBean
    private SubsystemService subsystemService;
    @MockitoBean
    private CertificateConverter certificateConverter;
    @MockitoBean
    private AuditDataHelper auditDataHelper;

    @Autowired
    private ParticipantValueStore store;
    @Autowired
    private SecurityServerRepository servers;
    @Autowired
    private IdentifierRepository<SecurityServerIdEntity> serverIds;
    @Autowired
    private RequestRepository<MemberDidRegistrationRequestEntity> memberRequests;
    @Autowired
    private ClientService clientService;
    @Autowired
    private SecurityServerService securityServerService;
    @Autowired
    private DataspaceMemberDidService memberDidService;

    private final AtomicInteger fetches = new AtomicInteger();
    private final AtomicInteger fetchesInFlight = new AtomicInteger();
    private final AtomicInteger peakFetchesInFlight = new AtomicInteger();

    private MemberDidRegistrationServiceImpl service;

    @BeforeEach
    void buildService() {
        var handler = new MemberDidRegistrationRequestHandler(serverIds, memberRequests, servers, store, requestMapper, auditDataHelper);
        when(requestMapper.toDto(any(MemberDidRegistrationRequestEntity.class))).thenAnswer(inv -> {
            var dto = new MemberDidRegistrationRequest();
            dto.setId(inv.<MemberDidRegistrationRequestEntity>getArgument(0).getId());
            return dto;
        });
        when(managementRequestService.add(any(MemberDidRegistrationRequest.class)))
                .thenAnswer(inv -> transactions.execute(status -> handler.add(inv.getArgument(0))));
        var configProperties = mock(ManagementServiceConfigProperties.class);
        when(configProperties.getMemberDidRegMaxEntries()).thenReturn(BATCH_SIZE);
        service = new MemberDidRegistrationServiceImpl(this::resolveAtIssuerLatency, store, servers, managementRequestService,
                configProperties);
    }

    @Test
    void fullBatchIsStoredWithinTheAdminClientTimeoutAndReachesTheSharedParametersOfVersion7() {
        var owner = seed.member();
        var server = seed.server(owner);
        var members = new ArrayList<ClientRef>();
        members.add(owner);
        for (int i = 1; i < BATCH_SIZE; i++) {
            var client = seed.member();
            seed.addClient(server, client);
            members.add(client);
        }
        jdbc.update("update security_servers set system_did = ?, dsp_base_url = ? where id = ?",
                systemDid(server.address(), 7183), dspBaseUrl(server.address()), server.id());
        var request = request(server, members);

        long started = System.nanoTime();
        var answer = service.add(request);
        var elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(elapsed).isLessThan(ADMIN_CLIENT_TIMEOUT);
        assertThat(answer.getResults()).hasSize(BATCH_SIZE).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo(Status.STORED);
            assertThat(result.reason()).isNull();
        });
        assertThat(fetches).hasValue(BATCH_SIZE);
        assertThat(peakFetchesInFlight.get()).isLessThanOrEqualTo(PARALLEL_FETCHES);
        assertThat(countMemberDids(server.id())).isEqualTo(BATCH_SIZE);

        var emitted = SharedParametersV7Fixture.emit(clientService, securityServerService, memberDidService);
        var byId = emitted.parameters().getMembers().stream()
                .collect(Collectors.toMap(SharedParameters.Member::getId, Function.identity()));
        members.forEach(member -> {
            var did = memberDid(server.address(), member.identifier());
            assertThat(byId.get(member.identifier()).getDids()).containsExactly(Map.entry(server.identifier(), did));
            assertThat(emitted.xml()).contains(did);
        });
        assertThat(byId.get(owner.identifier()).getDids()).containsKey(server.identifier());

        fetches.set(0);
        started = System.nanoTime();
        var resend = service.add(request(server, members));
        var resendElapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(resendElapsed).isLessThan(ADMIN_CLIENT_TIMEOUT);
        assertThat(resend.getResults()).hasSize(BATCH_SIZE).allSatisfy(result -> assertThat(result.status()).isEqualTo(Status.STORED));
        assertThat(resend.getId()).isZero();
        assertThat(fetches).hasValue(0);
    }

    @Test
    void statementCountsOfAFullBatchStayWithinTheirBounds() {
        var owner = seed.member();
        var server = seed.server(owner);
        var members = new ArrayList<ClientRef>();
        members.add(owner);
        for (int i = 1; i < BATCH_SIZE; i++) {
            var client = seed.member();
            seed.addClient(server, client);
            members.add(client);
        }
        var entries = new LinkedHashMap<ClientId, String>();
        members.forEach(member -> entries.put(member.identifier(), memberDid(server.address(), member.identifier())));

        var dryRun = statementsOf(() -> store.checkMemberDids(server.id(), entries));
        var write = statementsOf(() -> store.writeMemberDidsOnce(server.id(), entries));
        var resend = statementsOf(() -> {
            store.checkMemberDids(server.id(), entries);
            store.writeMemberDidsOnce(server.id(), entries);
        });

        assertThat(dryRun).as("dry run statements").isLessThanOrEqualTo(DRY_RUN_STATEMENT_BOUND);
        assertThat(write).as("write statements").isLessThanOrEqualTo(WRITE_STATEMENT_BOUND);
        assertThat(resend).as("equal resend statements").isLessThanOrEqualTo(RESEND_STATEMENT_BOUND);
    }

    private static int statementsOf(Runnable call) {
        StatementRecorder.start();
        call.run();
        return StatementRecorder.stop().size();
    }

    private List<DidResolution> resolveAtIssuerLatency(List<String> dids) {
        try (var executor = Executors.newFixedThreadPool(PARALLEL_FETCHES)) {
            try {
                var pending = dids.stream().<Callable<DidResolution>>map(did -> () -> fetch(did)).toList();
                var resolutions = new ArrayList<DidResolution>();
                for (var future : executor.invokeAll(pending)) {
                    resolutions.add(future.get());
                }
                return resolutions;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (ExecutionException e) {
                throw new IllegalStateException(e.getCause());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private DidResolution fetch(String did) throws InterruptedException {
        peakFetchesInFlight.accumulateAndGet(fetchesInFlight.incrementAndGet(), Math::max);
        try {
            Thread.sleep(FETCH_LATENCY);
            fetches.incrementAndGet();
            return DidResolution.resolved(did);
        } finally {
            fetchesInFlight.decrementAndGet();
        }
    }

    private static MemberDidRegistrationRequest request(Server server, List<ClientRef> members) {
        var entries = members.stream()
                .map(member -> new Entry(member.identifier(), memberDid(server.address(), member.identifier())))
                .toList();
        return new MemberDidRegistrationRequest(SECURITY_SERVER, server.identifier(), entries);
    }
}
