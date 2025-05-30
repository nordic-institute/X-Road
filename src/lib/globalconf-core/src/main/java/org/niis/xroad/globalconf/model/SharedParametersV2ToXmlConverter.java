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
package org.niis.xroad.globalconf.model;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;

import jakarta.xml.bind.JAXBElement;
import lombok.SneakyThrows;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;
import org.niis.xroad.globalconf.schema.sharedparameters.v2.ApprovedCATypeV2;
import org.niis.xroad.globalconf.schema.sharedparameters.v2.GlobalGroupType;
import org.niis.xroad.globalconf.schema.sharedparameters.v2.GlobalSettingsType;
import org.niis.xroad.globalconf.schema.sharedparameters.v2.MemberType;
import org.niis.xroad.globalconf.schema.sharedparameters.v2.ObjectFactory;
import org.niis.xroad.globalconf.schema.sharedparameters.v2.SecurityServerType;
import org.niis.xroad.globalconf.schema.sharedparameters.v2.SharedParametersTypeV2;
import org.niis.xroad.globalconf.schema.sharedparameters.v2.SubsystemType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper(uses = {ObjectFactory.class, MappingUtils.class}, unmappedTargetPolicy = ReportingPolicy.ERROR)
abstract class SharedParametersV2ToXmlConverter {
    public static final SharedParametersV2ToXmlConverter INSTANCE = Mappers.getMapper(SharedParametersV2ToXmlConverter.class);
    protected static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();

    SharedParametersTypeV2 convert(SharedParameters sharedParameters) {
        return sharedParameters != null ? convert(sharedParameters, createClientIdMap(sharedParameters)) : null;
    }

    @Mapping(source = "approvedCAs", target = "approvedCA")
    @Mapping(source = "approvedTSAs", target = "approvedTSA")
    @Mapping(source = "members", target = "member")
    @Mapping(source = "securityServers", target = "securityServer")
    @Mapping(source = "globalGroups", target = "globalGroup")
    @Mapping(target = "centralService", ignore = true)
    abstract SharedParametersTypeV2 convert(SharedParameters sharedParameters, @Context Map<ClientId, Object> clientMap);

    @Mapping(source = "memberClasses", target = "memberClass")
    abstract GlobalSettingsType convert(SharedParameters.GlobalSettings globalSettings);

    @Mapping(source = "intermediateCas", target = "intermediateCA")
    abstract ApprovedCATypeV2 convert(SharedParameters.ApprovedCA approvedCa);

    @Mapping(source = "authCertHashes", target = "authCertHash", qualifiedByName = "toAuthCertHashes")
    @Mapping(source = ".", target = "client", qualifiedByName = "clientsById")
    @Mapping(target = "owner", qualifiedByName = "clientById")
    abstract SecurityServerType convert(SharedParameters.SecurityServer securityServer, @Context Map<ClientId, Object> clientMap);

    @Mapping(source = "groupMembers", target = "groupMember")
    abstract GlobalGroupType convert(SharedParameters.GlobalGroup globalGroup);

    @Mapping(target = "subsystem", ignore = true)
    @Mapping(source = "id", target = "id")
    abstract MemberType convertMember(SharedParameters.Member member, String id);

    @Mapping(source = "id", target = "id")
    abstract SubsystemType convertSubsystem(SharedParameters.Subsystem subsystem, String id);

    MemberType convertMember(SharedParameters.Member member, @Context Map<ClientId, Object> clientMap) {
        return (MemberType) clientMap.get(member.getId());
    }

    @Named("clientById")
    Object xmlClientId(ClientId value, @Context Map<ClientId, Object> clientMap) {
        return clientMap.get(value);
    }

    @Named("clientsById")
    List<JAXBElement<Object>> xmlClientIds(SharedParameters.SecurityServer securityServer, @Context Map<ClientId, Object> clientMap) {
        var clientIds = securityServer.getClients();

        if (clientIds == null) {
            return List.of();
        }
        return clientIds.stream()
                .filter(id -> id.isMember() || !securityServer.getMaintenanceMode().enabled())
                .map(clientId -> xmlClientId(clientId, clientMap))
                .map(OBJECT_FACTORY::createSecurityServerTypeClient)
                .toList();
    }

    @Named("toAuthCertHashes")
    protected List<byte[]> toAuthCertHashes(List<CertHash> authCerts) {
        return authCerts.stream()
                .map(this::toAuthCertHash)
                .toList();
    }

    @SneakyThrows
    private byte[] toAuthCertHash(CertHash authCert) {
        return authCert.getHash(DigestAlgorithm.SHA1);
    }

    private Map<ClientId, Object> createClientIdMap(SharedParameters sharedParameters) {
        if (sharedParameters.getMembers() == null) {
            return Map.of();
        }
        Map<ClientId, Object> clientMap = new HashMap<>();
        var sequence = new IdSequence();

        for (SharedParameters.Member member : sharedParameters.getMembers()) {
            var memberType = convertMember(member, sequence.nextValue());
            clientMap.put(member.getId(), memberType);
            for (SharedParameters.Subsystem subsystem : member.getSubsystems()) {
                var subsystemType = convertSubsystem(subsystem, sequence.nextValue());
                clientMap.put(subsystem.getId(), subsystemType);
                memberType.getSubsystem().add(subsystemType);
            }
        }
        return clientMap;
    }

    private static final class IdSequence {
        int nextId = 0;

        String nextValue() {
            return String.format("id%d", nextId++);
        }
    }
}
