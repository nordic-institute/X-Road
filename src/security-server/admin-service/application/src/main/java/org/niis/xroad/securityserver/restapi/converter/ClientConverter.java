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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.identifier.ClientId;

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerSignCertificates;
import org.niis.xroad.securityserver.restapi.converter.comparator.ClientSortingComparator;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeDto;
import org.niis.xroad.securityserver.restapi.util.ClientUtils;
import org.niis.xroad.serverconf.model.Client;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converter for Client related data between openapi and service domain classes
 */
@Component
@RequiredArgsConstructor
public class ClientConverter {

    private final GlobalConfProvider globalConfProvider;
    private final CurrentSecurityServerId securityServerOwner; // request scoped
    // request scoped contains all certificates of type sign
    private final CurrentSecurityServerSignCertificates currentSecurityServerSignCertificates;
    private final ClientSortingComparator clientSortingComparator;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    /**
     *
     * @param client
     * @return
     */
    public ClientDto convert(Client client) {
        ClientDto clientDto = new ClientDto();
        clientDto.setId(clientIdConverter.convertId(client.getIdentifier()));
        clientDto.setInstanceId(client.getIdentifier().getXRoadInstance());
        clientDto.setMemberClass(client.getIdentifier().getMemberClass());
        clientDto.setMemberCode(client.getIdentifier().getMemberCode());
        clientDto.setSubsystemCode(client.getIdentifier().getSubsystemCode());
        clientDto.setMemberName(globalConfProvider.getMemberName(client.getIdentifier()));
        clientDto.setOwner(client.getIdentifier().equals(securityServerOwner.getServerId().getOwner()));
        clientDto.setHasValidLocalSignCert(ClientUtils.hasValidLocalSignCert(client.getIdentifier(),
                currentSecurityServerSignCertificates.getSignCertificateInfos()));
        Optional<ClientStatusDto> status = ClientStatusMapping.map(client.getClientStatus());
        clientDto.setStatus(status.orElse(null));
        Optional<ConnectionTypeDto> connectionTypeEnum =
                ConnectionTypeMapping.map(client.getIsAuthentication());
        clientDto.setConnectionType(connectionTypeEnum.orElse(null));
        return clientDto;
    }

    /**
     * Convert a group of ClientType into a list of openapi ClientDto class
     * @param clientTypes
     * @return
     */
    public Set<ClientDto> convert(Iterable<Client> clientTypes) {
        return Streams.stream(clientTypes)
                .map(this::convert)
                .sorted(clientSortingComparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }


    /**
     * Convert MemberInfo into ClientDto
     * @param memberInfo
     * @return ClientDto
     */
    public ClientDto convertMemberInfoToClient(MemberInfo memberInfo) {
        ClientId clientId = memberInfo.id();
        ClientDto clientDto = new ClientDto();
        clientDto.setId(clientIdConverter.convertId(clientId));
        clientDto.setMemberClass(clientId.getMemberClass());
        clientDto.setMemberCode(clientId.getMemberCode());
        clientDto.setSubsystemCode(clientId.getSubsystemCode());
        clientDto.setMemberName(memberInfo.name());
        return clientDto;
    }

    /**
     * Convert MemberInfo list into ClientDto list
     * @param memberInfos
     * @return List of ClientDto
     */
    public List<ClientDto> convertMemberInfosToClients(List<MemberInfo> memberInfos) {
        return memberInfos.stream().map(this::convertMemberInfoToClient).collect(Collectors.toList());
    }

}
