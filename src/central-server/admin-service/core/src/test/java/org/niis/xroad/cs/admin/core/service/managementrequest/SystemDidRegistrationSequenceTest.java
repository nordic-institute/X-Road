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

package org.niis.xroad.cs.admin.core.service.managementrequest;

import ee.ria.xroad.common.identifier.ClientId;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.ErrorDeviation;
import org.niis.xroad.common.exception.ClientErrorException;
import org.niis.xroad.common.identifiers.jpa.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.SystemDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.SystemPairCheck;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.core.dataspace.PublishedValueStoreImpl;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SystemDidRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.MemberDidRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.niis.xroad.cs.admin.core.service.managementrequest.AuthCertServerCheck.CheckedServer;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.HOST_DIFFERS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_FIXABLE_CONFLICT;

/**
 * The pre-transaction service, the request handler and the published-value store run together over one
 * Security Server record, so a registration step changes what the next step sees.
 */
class SystemDidRegistrationSequenceTest {
    private static final int SERVER_ROW_ID = 7;
    private static final String ADDRESS_A = "ss0.example.org";
    private static final String ADDRESS_B = "ss1.example.org";

    private static final String DID_A = "did:web:ss0.example.org%3A7183:v1:system";
    private static final String URL_A = "https://ss0.example.org:8183/api/dsp";
    private static final String DID_A_OTHER_PORT = "did:web:ss0.example.org%3A7184:v1:system";
    private static final String URL_A_OTHER_PORT = "https://ss0.example.org:8184/api/dsp";
    private static final String DID_B = "did:web:ss1.example.org%3A7183:v1:system";
    private static final String URL_B = "https://ss1.example.org:8183/api/dsp";

    private static final byte[] CERTIFICATE = {1, 2, 3};
    private static final SecurityServerId SERVER_ID = SecurityServerId.create("DEV", "COM", "222", "SS0");

    private final List<String> resolvedDids = new ArrayList<>();
    private final List<SystemDidRegistrationRequestEntity> savedRows = new ArrayList<>();
    private int pairWrites;
    private String databaseAddress;
    private Runnable beforeLockedWrite = () -> { };

    private SecurityServerEntity server;
    private PublishedValueStoreImpl store;
    private SystemDidRegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        var owner = ClientId.Conf.create("DEV", "COM", "222");
        var ownerEntity = new XRoadMemberEntity("Member 222", owner, new MemberClassEntity("COM", "Companies"));
        ReflectionTestUtils.setField(ownerEntity, "id", 1L);
        server = new SecurityServerEntity(ownerEntity, "SS0");
        ReflectionTestUtils.setField(server, "id", SERVER_ROW_ID);
        changeRegisteredAddress(ADDRESS_A);

        var serverRepository = mock(SecurityServerRepository.class);
        when(serverRepository.lockForUpdateById(SERVER_ROW_ID)).thenReturn(Optional.of(SERVER_ROW_ID));
        when(serverRepository.findById(SERVER_ROW_ID)).thenReturn(Optional.of(server));
        when(serverRepository.existsBySystemDidAndIdNot(anyString(), anyInt())).thenReturn(false);
        when(serverRepository.saveAndFlush(any())).thenAnswer(inv -> {
            pairWrites++;
            return inv.getArgument(0);
        });
        var entityManager = mock(EntityManager.class);
        doAnswer(inv -> {
            server.setAddress(databaseAddress);
            return null;
        }).when(entityManager).refresh(server);
        store = new PublishedValueStoreImpl(serverRepository, mock(MemberDidRepository.class), mock(XRoadMemberRepository.class),
                mock(AuditEventLoggingFacade.class), entityManager);

        var authCertServerCheck = mock(AuthCertServerCheck.class);
        when(authCertServerCheck.requireServer(any(), any()))
                .thenAnswer(inv -> new CheckedServer(SERVER_ROW_ID, server.getAddress()));

        IdentifierRepository<SecurityServerIdEntity> serverIds = mock(IdentifierRepository.class);
        when(serverIds.findOne(any())).thenReturn(mock(SecurityServerIdEntity.class));
        RequestRepository<SystemDidRegistrationRequestEntity> requests = mock(RequestRepository.class);
        when(requests.save(any())).thenAnswer(inv -> {
            savedRows.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        var handler = new SystemDidRegistrationRequestHandler(serverIds, requests, authCertServerCheck, store,
                mock(RequestMapper.class));
        var managementRequestService = mock(ManagementRequestService.class);
        when(managementRequestService.add(any(SystemDidRegistrationRequest.class)))
                .thenAnswer(inv -> {
                    beforeLockedWrite.run();
                    return handler.add(inv.getArgument(0));
                });

        service = new SystemDidRegistrationServiceImpl(authCertServerCheck, store, dids -> {
            resolvedDids.addAll(dids);
            return dids.stream().map(DidResolution::resolved).toList();
        }, managementRequestService);
    }

    @Test
    void firstRegistrationThenEqualResendStoresWithoutResolvingAndKeepsThePair() {
        assertThat(register(DID_A, URL_A, SystemPairCheck.ABSENT)).isEmpty();
        assertStoredPair(DID_A, URL_A);
        assertCounts(1, 1, 1);

        assertThat(register(DID_A, URL_A, SystemPairCheck.EQUAL)).isEmpty();

        assertStoredPair(DID_A, URL_A);
        assertCounts(1, 1, 2);
    }

    @Test
    void addressChangeThenValuesForTheNewAddressReplacesTheStalePair() {
        register(DID_A, URL_A, SystemPairCheck.ABSENT);
        changeRegisteredAddress(ADDRESS_B);

        assertThat(register(DID_B, URL_B, SystemPairCheck.STALE_REPLACEABLE)).isEmpty();

        assertStoredPair(DID_B, URL_B);
        assertThat(resolvedDids).containsExactly(DID_A, DID_B);
        assertCounts(2, 2, 2);
        assertThat(register(DID_B, URL_B, SystemPairCheck.EQUAL)).isEmpty();
        assertCounts(2, 2, 3);
    }

    @ParameterizedTest
    @MethodSource("differingPairs")
    void differingValueWithoutAnAddressChangeIsFixableAndKeepsThePair(String did, String url) {
        register(DID_A, URL_A, SystemPairCheck.ABSENT);

        var answer = register(did, url, SystemPairCheck.VALUE_DIFFERS);

        assertThat(answer).contains(MR_DID_FIXABLE_CONFLICT.build("VALUE_DIFFERS"));
        assertStoredPair(DID_A, URL_A);
        assertCounts(1, 1, 1);
    }

    @Test
    void addressChangeThenTheOldValuesResentIsFixableAndTheStalePairStaysUntilNewValuesArrive() {
        register(DID_A, URL_A, SystemPairCheck.ABSENT);
        changeRegisteredAddress(ADDRESS_B);

        var answer = register(DID_A, URL_A, SystemPairCheck.rejected(HOST_DIFFERS));

        assertThat(answer).contains(MR_DID_FIXABLE_CONFLICT.build("HOST_DIFFERS"));
        assertStoredPair(DID_A, URL_A);
        assertCounts(1, 1, 1);

        assertThat(register(DID_B, URL_B, SystemPairCheck.STALE_REPLACEABLE)).isEmpty();
        assertStoredPair(DID_B, URL_B);
        assertCounts(2, 2, 2);
    }

    @Test
    void returningToTheFirstAddressMakesTheUnreplacedPairCurrentAgain() {
        register(DID_A, URL_A, SystemPairCheck.ABSENT);
        changeRegisteredAddress(ADDRESS_B);
        assertThat(store.checkSystemPair(SERVER_ROW_ID, DID_A, URL_A)).isEqualTo(SystemPairCheck.rejected(HOST_DIFFERS));
        changeRegisteredAddress(ADDRESS_A);

        assertThat(register(DID_A, URL_A, SystemPairCheck.EQUAL)).isEmpty();

        assertStoredPair(DID_A, URL_A);
        assertCounts(1, 1, 2);
    }

    @Test
    void returningToTheFirstAddressAfterAReplacementReplacesThePairAgain() {
        register(DID_A, URL_A, SystemPairCheck.ABSENT);
        changeRegisteredAddress(ADDRESS_B);
        register(DID_B, URL_B, SystemPairCheck.STALE_REPLACEABLE);
        changeRegisteredAddress(ADDRESS_A);

        assertThat(register(DID_A, URL_A, SystemPairCheck.STALE_REPLACEABLE)).isEmpty();

        assertStoredPair(DID_A, URL_A);
        assertThat(resolvedDids).containsExactly(DID_A, DID_B, DID_A);
        assertCounts(3, 3, 3);
    }

    @Test
    void addressChangeCommittedBetweenThePreCheckAndTheLockedWriteIsFixableAndKeepsThePair() {
        register(DID_A, URL_A, SystemPairCheck.ABSENT);
        beforeLockedWrite = () -> databaseAddress = ADDRESS_B;

        var answer = register(DID_A, URL_A, SystemPairCheck.EQUAL);

        assertThat(answer).contains(MR_DID_FIXABLE_CONFLICT.build("HOST_DIFFERS"));
        assertThat(server.getAddress()).isEqualTo(ADDRESS_B);
        assertStoredPair(DID_A, URL_A);
        assertCounts(1, 1, 1);
    }

    private void changeRegisteredAddress(String address) {
        databaseAddress = address;
        server.setAddress(address);
    }

    private Optional<ErrorDeviation> register(String did, String url, SystemPairCheck expectedDryRun) {
        assertThat(store.checkSystemPair(SERVER_ROW_ID, did, url)).isEqualTo(expectedDryRun);
        try {
            service.add(new SystemDidRegistrationRequest(SECURITY_SERVER, SERVER_ID, did, url, CERTIFICATE));
            return Optional.empty();
        } catch (ClientErrorException e) {
            return Optional.of(e.getErrorDeviation());
        }
    }

    private void assertStoredPair(String did, String url) {
        assertThat(server.getSystemDid()).isEqualTo(did);
        assertThat(server.getDspBaseUrl()).isEqualTo(url);
    }

    private void assertCounts(int resolveCalls, int pairWriteCount, int requestRows) {
        assertThat(resolvedDids).hasSize(resolveCalls);
        assertThat(pairWrites).isEqualTo(pairWriteCount);
        assertThat(savedRows).hasSize(requestRows);
    }

    private static Stream<Arguments> differingPairs() {
        return Stream.of(
                Arguments.of(DID_A, URL_A_OTHER_PORT),
                Arguments.of(DID_A_OTHER_PORT, URL_A),
                Arguments.of(DID_A_OTHER_PORT, URL_A_OTHER_PORT));
    }
}
