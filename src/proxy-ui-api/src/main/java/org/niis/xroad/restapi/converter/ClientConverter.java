/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;

import com.google.common.collect.Streams;
import org.apache.commons.lang.StringUtils;
import org.niis.xroad.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.restapi.cache.CurrentSecurityServerSignCertificates;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.model.Client;
import org.niis.xroad.restapi.openapi.model.ClientStatus;
import org.niis.xroad.restapi.openapi.model.ConnectionType;
import org.niis.xroad.restapi.util.ClientUtils;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.converter.Converters.ENCODED_ID_SEPARATOR;

/**
 * Converter for Client related data between openapi and service domain classes
 */
@Component
public class ClientConverter {

    private final GlobalConfFacade globalConfFacade;
    private final CurrentSecurityServerId securityServerOwner; // request scoped
    // request scoped contains all certificates of type sign
    private final CurrentSecurityServerSignCertificates currentSecurityServerSignCertificates;

    public static final int INSTANCE_INDEX = 0;
    public static final int MEMBER_CLASS_INDEX = 1;
    public static final int MEMBER_CODE_INDEX = 2;
    public static final int SUBSYSTEM_CODE_INDEX = 3;

    @Autowired
    public ClientConverter(GlobalConfFacade globalConfFacade,
            CurrentSecurityServerId securityServerOwner,
            CurrentSecurityServerSignCertificates currentSecurityServerSignCertificates) {
        this.globalConfFacade = globalConfFacade;
        this.securityServerOwner = securityServerOwner;
        this.currentSecurityServerSignCertificates = currentSecurityServerSignCertificates;
    }

    /**
     *
     * @param clientType
     * @return
     */
    public Client convert(ClientType clientType) {
        Client client = new Client();
        client.setId(convertId(clientType.getIdentifier()));
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
     * convert a group of ClientType into a list of openapi Client class
     * @param clientTypes
     * @return
     */
    public List<Client> convert(Iterable<ClientType> clientTypes) {
        return Streams.stream(clientTypes)
                .map(this::convert)
                .collect(Collectors.toList());
    }

    /**
     * Convert ClientId into encoded member id
     * @return
     */
    public String convertId(ClientId clientId) {
        return convertId(clientId, false);
    }

    /**
     * Convert ClientId into encoded member id
     * @param clientId
     * @return
     */
    public String convertId(ClientId clientId, boolean includeType) {
        StringBuilder builder = new StringBuilder();
        if (includeType) {
            builder.append(clientId.getObjectType())
                    .append(ENCODED_ID_SEPARATOR);
        }
        builder.append(clientId.getXRoadInstance())
                .append(ENCODED_ID_SEPARATOR)
                .append(clientId.getMemberClass())
                .append(ENCODED_ID_SEPARATOR)
                .append(clientId.getMemberCode());
        if (StringUtils.isNotEmpty(clientId.getSubsystemCode())) {
            builder.append(ENCODED_ID_SEPARATOR)
                    .append(clientId.getSubsystemCode());
        }
        return builder.toString().trim();
    }

    /**
     * Convert encoded member id into ClientId
     * @param encodedId
     * @return ClientId
     * @throws BadRequestException if encoded id could not be decoded
     */
    public ClientId convertId(String encodedId) throws BadRequestException {
        if (!isEncodedClientId(encodedId)) {
            throw new BadRequestException("Invalid client id " + encodedId);
        }
        List<String> parts = Arrays.asList(encodedId.split(String.valueOf(ENCODED_ID_SEPARATOR)));
        String instance = parts.get(INSTANCE_INDEX);
        String memberClass = parts.get(MEMBER_CLASS_INDEX);
        String memberCode = parts.get(MEMBER_CODE_INDEX);
        String subsystemCode = null;
        if (parts.size() != (MEMBER_CODE_INDEX + 1)
                && parts.size() != (SUBSYSTEM_CODE_INDEX + 1)) {
            throw new BadRequestException("Invalid client id " + encodedId);
        }
        if (parts.size() == (SUBSYSTEM_CODE_INDEX + 1)) {
            subsystemCode = parts.get(SUBSYSTEM_CODE_INDEX);
        }
        return ClientId.create(instance, memberClass, memberCode, subsystemCode);
    }

    /**
     * Convert a list of encoded member ids to ClientIds
     * @param encodedIds
     * @return List of ClientIds
     * @throws BadRequestException if encoded id could not be decoded
     */
    public List<ClientId> convertIds(List<String> encodedIds) throws BadRequestException {
        return encodedIds.stream().map(this::convertId).collect(Collectors.toList());
    }

    /**
     * Convert MemberInfo into Client
     * @param memberInfo
     * @return Client
     */
    public Client convertMemberInfoToClient(MemberInfo memberInfo) {
        ClientId clientId = memberInfo.getId();
        Client client = new Client();
        client.setId(convertId(clientId));
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

    public boolean isEncodedSubsystemId(String encodedId) {
        int separators = FormatUtils.countOccurences(encodedId, Converters.ENCODED_ID_SEPARATOR);
        return separators == SUBSYSTEM_CODE_INDEX;
    }

    public boolean isEncodedMemberId(String encodedId) {
        int separators = FormatUtils.countOccurences(encodedId, Converters.ENCODED_ID_SEPARATOR);
        return separators == MEMBER_CODE_INDEX;
    }

    public boolean isEncodedClientId(String encodedId) {
        return isEncodedMemberId(encodedId) || isEncodedSubsystemId(encodedId);
    }
}
