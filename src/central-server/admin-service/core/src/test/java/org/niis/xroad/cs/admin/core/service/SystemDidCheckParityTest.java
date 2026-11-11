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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.cs.admin.api.domain.DidCheck;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.core.dataspace.DidBatchResolution;
import org.niis.xroad.cs.admin.core.dataspace.ParticipantValueStoreImpl;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.repository.MemberDidRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_HELD_BY_ANOTHER_SERVER;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_TOO_LONG;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.FOREIGN_SYSTEM_DID;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.HOST_DIFFERS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.NON_ASCII_HOST;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.NOT_CANONICAL;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.NO_STORED_VALUE;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.UNDECODABLE_DID;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.UNPARSEABLE_REGISTERED_ADDRESS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.WRONG_SCHEME;

/**
 * The SYSTEM DID check and the SYSTEM pair administrator replacement are two implementations of one refusal
 * decision. Both run here over the same inputs and must reach the same verdict before the check resolves the DID.
 */
class SystemDidCheckParityTest {

    private static final int SERVER_ROW_ID = 7;
    private static final String ADDRESS = "ss0.example.org";
    private static final String SYSTEM_DID = "did:web:ss0.example.org%3A7183:v1:system";
    private static final String OLD_SYSTEM_DID = "did:web:old.example.org%3A7183:v1:system";
    private static final String OLD_DSP_URL = "https://old.example.org:8183/api/dsp";

    private final SecurityServerId serverId = SecurityServerId.Conf.create("DEV", "COM", "222", "SS0");
    private final SecurityServerRepository securityServerRepository = mock(SecurityServerRepository.class);

    private SecurityServerEntity server;
    private ParticipantValueStoreImpl store;
    private SystemDidCheckServiceImpl checkService;

    @BeforeEach
    void setUp() {
        var owner = new XRoadMemberEntity("Member 222", ClientId.Conf.create("DEV", "COM", "222"),
                new MemberClassEntity("COM", "Companies"));
        ReflectionTestUtils.setField(owner, "id", 1L);
        server = new SecurityServerEntity(owner, "SS0");
        ReflectionTestUtils.setField(server, "id", SERVER_ROW_ID);

        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.of(server));
        when(securityServerRepository.lockForUpdateById(SERVER_ROW_ID)).thenReturn(Optional.of(SERVER_ROW_ID));
        when(securityServerRepository.findById(SERVER_ROW_ID)).thenReturn(Optional.of(server));

        store = new ParticipantValueStoreImpl(securityServerRepository, mock(MemberDidRepository.class),
                mock(AuditEventLoggingFacade.class), mock(EntityManager.class));
        checkService = new SystemDidCheckServiceImpl(securityServerRepository,
                new DidBatchResolution(dids -> dids.stream().map(DidResolution::resolved).toList()));
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("a server with no stored pair", false, false, ADDRESS, SYSTEM_DID, NO_STORED_VALUE),
                Arguments.of("no stored pair and an invalid DID", false, false, ADDRESS, "did:example:system", NO_STORED_VALUE),
                Arguments.of("a DID of another scheme", true, false, ADDRESS, "did:example:system", WRONG_SCHEME),
                Arguments.of("a DID that is too long", true, false, ADDRESS,
                        "did:web:ss0.example.org%3A7183:v1:" + "a".repeat(4000), DID_TOO_LONG),
                Arguments.of("a DID with a non-ASCII host", true, false, ADDRESS,
                        "did:web:ssä0.example.org%3A7183:v1:system", NON_ASCII_HOST),
                Arguments.of("a DID with a host that is not canonical", true, false, ADDRESS,
                        "did:web:SS0.EXAMPLE.ORG%3A7183:v1:system", NOT_CANONICAL),
                Arguments.of("a DID whose host is not the proposed address", true, false, "other.example.org", SYSTEM_DID,
                        HOST_DIFFERS),
                Arguments.of("a member DID", true, false, ADDRESS, "did:web:ss0.example.org%3A7183:v1:DEV:COM:222",
                        FOREIGN_SYSTEM_DID),
                Arguments.of("a DID that cannot be decoded", true, false, ADDRESS,
                        "did:web:ss0.example.org%3A7183:v1:system%2", UNDECODABLE_DID),
                Arguments.of("an unusable proposed address", true, false, "", SYSTEM_DID, UNPARSEABLE_REGISTERED_ADDRESS),
                Arguments.of("a non-ASCII proposed address", true, false, "ssä0.example.org", SYSTEM_DID, NON_ASCII_HOST),
                Arguments.of("a DID held by another server", true, true, ADDRESS, SYSTEM_DID, DID_HELD_BY_ANOTHER_SERVER),
                Arguments.of("an invalid DID held by another server", true, true, ADDRESS, "did:example:system", WRONG_SCHEME),
                Arguments.of("an accepted DID", true, false, ADDRESS, SYSTEM_DID, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void shouldReachTheSameVerdictBeforeResolution(String name, boolean hasStoredPair, boolean heldByAnotherServer,
                                                   String proposedAddress, String proposedDid,
                                                   ParticipantValueRejection expectedRefusal) {
        if (hasStoredPair) {
            server.setSystemDid(OLD_SYSTEM_DID);
            server.setDspBaseUrl(OLD_DSP_URL);
        }
        server.setAddress(proposedAddress);
        when(securityServerRepository.existsBySystemDidAndIdNot(anyString(), anyInt())).thenReturn(false);
        if (heldByAnotherServer) {
            when(securityServerRepository.existsBySystemDidAndIdNot(proposedDid, SERVER_ROW_ID)).thenReturn(true);
        }

        var checked = checkService.check(serverId, proposedAddress, proposedDid);
        var saved = store.replaceSystemPair(SERVER_ROW_ID, proposedDid, "https://" + proposedAddress + ":8183/api/dsp");

        if (expectedRefusal == null) {
            assertThat(saved.outcome()).isNotEqualTo(ParticipantValueResult.Outcome.REJECTED);
            assertThat(checked.status()).isNotEqualTo(DidCheck.Status.REFUSED);
        } else {
            assertThat(saved).isEqualTo(ParticipantValueResult.rejected(expectedRefusal));
            assertThat(checked).isEqualTo(DidCheck.refused(expectedRefusal));
        }
    }
}
