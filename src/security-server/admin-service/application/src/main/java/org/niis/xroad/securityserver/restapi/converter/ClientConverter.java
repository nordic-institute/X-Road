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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerSignCertificates;
import org.niis.xroad.securityserver.restapi.converter.comparator.ClientSortingComparator;
import org.niis.xroad.securityserver.restapi.facade.GlobalConfFacade;
import org.niis.xroad.securityserver.restapi.openapi.model.Client;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionType;
import org.niis.xroad.securityserver.restapi.util.ClientUtils;
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

    private final GlobalConfFacade globalConfFacade;
    private final CurrentSecurityServerId securityServerOwner; // request scoped
    // request scoped contains all certificates of type sign
    private final CurrentSecurityServerSignCertificates currentSecurityServerSignCertificates;
    private final ClientSortingComparator clientSortingComparator;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    /**
     *
     * @param clientType
     * @return
     */
    public Client convert(ClientType clientType) {
        Client client = new Client();
        client.setId(clientIdConverter.convertId(clientType.getIdentifier()));
        client.setInstanceId(clientType.getIdentifier().getXRoadInstance());
        client.setMemberClass(clientType.getIdentifier().getMemberClass());
        client.setMemberCode(clientType.getIdentifier().getMemberCode());
        client.setSubsystemCode(clientType.getIdentifier().getSubsystemCode());
        client.setMemberName(globalConfFacade.getMemberName(clientType.getIdentifier()));
        client.setOwner(clientType.getIdentifier().equals(securityServerOwner.getServerId().getOwner()));
        client.setHasValidLocalSignCert(ClientUtils.hasValidLocalSignCert(clientType.getIdentifier(),
                currentSecurityServerSignCertificates.getSignCertificateInfos()));
        Optional<ClientStatus> status = ClientStatusMapping.map(clientType.getClientStatus());
        client.setStatus(status.orElse(null));
        Optional<ConnectionType> connectionTypeEnum =
                ConnectionTypeMapping.map(clientType.getIsAuthentication());
        client.setConnectionType(connectionTypeEnum.orElse(null));
        return client;
    }

    /**
     * Convert a group of ClientType into a list of openapi Client class
     * @param clientTypes
     * @return
     */
    public Set<Client> convert(Iterable<ClientType> clientTypes) {
        return Streams.stream(clientTypes)
                .map(this::convert)
                .sorted(clientSortingComparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }



    /**
     * Convert MemberInfo into Client
     * @param memberInfo
     * @return Client
     */
    public Client convertMemberInfoToClient(MemberInfo memberInfo) {
        ClientId clientId = memberInfo.getId();
        Client client = new Client();
        client.setId(clientIdConverter.convertId(clientId));
        client.setMemberClass(clientId.getMemberClass());
        client.setMemberCode(clientId.getMemberCode());
        client.setSubsystemCode(clientId.getSubsystemCode());
        client.setMemberName(memberInfo.getName());
        return client;
    }

    /**
     * Convert MemberInfo list into Client list
     * @param memberInfos
     * @return List of Clients
     */
    public List<Client> convertMemberInfosToClients(List<MemberInfo> memberInfos) {
        return memberInfos.stream().map(this::convertMemberInfoToClient).collect(Collectors.toList());
    }

}
