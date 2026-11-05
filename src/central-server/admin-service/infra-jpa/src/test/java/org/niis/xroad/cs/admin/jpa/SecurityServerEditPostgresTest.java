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
import org.niis.xroad.cs.admin.api.domain.SystemPairEdit;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.SubsystemService;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.PageConverter;
import org.niis.xroad.cs.admin.core.converter.PageRequestDtoConverter;
import org.niis.xroad.cs.admin.core.entity.mapper.ClientIdMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.FlattenedSecurityServerClientViewMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerClientMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.ServerClientMapperImpl;
import org.niis.xroad.cs.admin.core.repository.paging.StableSortHelper;
import org.niis.xroad.cs.admin.core.service.SecurityServerServiceImpl;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.dspBaseUrl;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.newHost;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.systemDid;

/**
 * The Central Server edit of a Security Server against Postgres: the address and the SYSTEM pair are saved
 * together or not at all.
 */
@Import({SecurityServerServiceImpl.class, SecurityServerMapperImpl.class, SecurityServerClientMapperImpl.class,
        ClientIdMapperImpl.class, ServerClientMapperImpl.class, FlattenedSecurityServerClientViewMapperImpl.class,
        StableSortHelper.class, PageRequestDtoConverter.class, PageConverter.class})
class SecurityServerEditPostgresTest extends AbstractCentralServerPostgresTest {

    @MockitoBean
    private ManagementRequestService managementRequestService;
    @MockitoBean
    private ClientService clientService;
    @MockitoBean
    private GlobalGroupMemberService globalGroupMemberService;
    @MockitoBean
    private SubsystemService subsystemService;
    @MockitoBean
    private CertificateConverter certificateConverter;
    @MockitoBean
    private AuditDataHelper auditDataHelper;

    @Autowired
    private SecurityServerService service;

    @Test
    void storesTheNewAddressAndPairTogether() {
        var server = serverWithStoredPair();
        var newAddress = newHost();
        var pair = pairFor(newAddress, 7184);

        var edited = service.editSecurityServer(server.identifier(), newAddress, pair);

        assertThat(edited).isPresent();
        assertThat(stored(server.id())).isEqualTo(row(newAddress, pair));
    }

    @Test
    void refusedPairStoresNeitherThePairNorTheAddress() {
        var server = serverWithStoredPair();
        var before = stored(server.id());
        var newAddress = newHost();
        var pairForAnotherHost = pairFor(newHost(), 7184);

        assertThatThrownBy(() -> service.editSecurityServer(server.identifier(), newAddress, pairForAnotherHost))
                .isInstanceOf(BadRequestException.class);

        assertThat(stored(server.id())).isEqualTo(before);
    }

    @Test
    void pairForAServerWithNoStoredPairStoresNothing() {
        var server = seed.server(seed.member());
        var before = stored(server.id());
        var newAddress = newHost();

        assertThatThrownBy(() -> service.editSecurityServer(server.identifier(), newAddress, pairFor(newAddress, 7184)))
                .isInstanceOf(BadRequestException.class);

        assertThat(stored(server.id())).isEqualTo(before);
    }

    @Test
    void addressOnlyEditLeavesTheStoredPairAsItWas() {
        var server = serverWithStoredPair();
        var before = stored(server.id());
        var newAddress = newHost();

        service.editSecurityServer(server.identifier(), newAddress, null);

        var after = stored(server.id());
        assertThat(after).containsEntry("address", newAddress);
        assertThat(after).containsEntry("system_did", before.get("system_did"));
        assertThat(after).containsEntry("dsp_base_url", before.get("dsp_base_url"));
    }

    @Test
    void repeatingTheSameEditLeavesTheSameStateAndSucceeds() {
        var server = serverWithStoredPair();
        var newAddress = newHost();
        var pair = pairFor(newAddress, 7184);

        service.editSecurityServer(server.identifier(), newAddress, pair);
        var afterFirst = stored(server.id());
        var second = service.editSecurityServer(server.identifier(), newAddress, pair);

        assertThat(second).isPresent();
        assertThat(stored(server.id())).isEqualTo(afterFirst);
    }

    private CentralServerSeed.Server serverWithStoredPair() {
        var server = seed.server(seed.member());
        jdbc.update("update security_servers set system_did = ?, dsp_base_url = ? where id = ?",
                systemDid(server.address(), 7183), dspBaseUrl(server.address()), server.id());
        return server;
    }

    private static SystemPairEdit pairFor(String host, int port) {
        return new SystemPairEdit(systemDid(host, port), dspBaseUrl(host));
    }

    private static Map<String, Object> row(String address, SystemPairEdit pair) {
        return Map.of("address", address, "system_did", pair.systemDid(), "dsp_base_url", pair.dspBaseUrl());
    }

    private Map<String, Object> stored(int serverId) {
        return jdbc.queryForMap("select address, system_did, dsp_base_url from security_servers where id = ?", serverId);
    }
}
