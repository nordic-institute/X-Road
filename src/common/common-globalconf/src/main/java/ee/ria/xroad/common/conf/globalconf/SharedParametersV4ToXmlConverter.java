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
package ee.ria.xroad.common.conf.globalconf;


import ee.ria.xroad.common.conf.globalconf.sharedparameters.v4.ApprovedCATypeV3;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v4.ConfigurationSourceType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v4.DataspaceSettingsType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v4.GlobalGroupType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v4.GlobalSettingsType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v4.MemberType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v4.ObjectFactory;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v4.SecurityServerType;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v4.SharedParametersTypeV4;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v4.SubsystemType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CryptoUtils;

import jakarta.xml.bind.JAXBElement;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(uses = {ObjectFactory.class, MappingUtils.class}, unmappedTargetPolicy = ReportingPolicy.ERROR)

abstract class SharedParametersV4ToXmlConverter {
    public static final SharedParametersV4ToXmlConverter INSTANCE = Mappers.getMapper(SharedParametersV4ToXmlConverter.class);
    protected static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();

    SharedParametersTypeV4 convert(SharedParameters sharedParameters) {
        return sharedParameters != null ? convert(sharedParameters, createClientIdMap(sharedParameters)) : null;
    }

    @Mapping(source = "sources", target = "source")
    @Mapping(source = "approvedCAs", target = "approvedCA")
    @Mapping(source = "approvedTSAs", target = "approvedTSA")
    @Mapping(source = "members", target = "member")
    @Mapping(source = "securityServers", target = "securityServer")
    @Mapping(source = "globalGroups", target = "globalGroup")
    @Mapping(target = "centralService", ignore = true)
    @Mapping(target = "dataspacesSettings", source = "sharedParameters", qualifiedByName = "mapDataspacesSettings")
    abstract SharedParametersTypeV4 convert(SharedParameters sharedParameters,
                                            @Context Map<ClientId, Object> clientMap);

    @Mapping(source = "memberClasses", target = "memberClass")
    abstract GlobalSettingsType convert(SharedParameters.GlobalSettings globalSettings);

    @Mapping(source = "internalVerificationCerts", target = "internalVerificationCert")
    @Mapping(source = "externalVerificationCerts", target = "externalVerificationCert")
    abstract ConfigurationSourceType convert(SharedParameters.ConfigurationSource configurationSource);

    @Mapping(source = "intermediateCas", target = "intermediateCA")
    abstract ApprovedCATypeV3 convert(SharedParameters.ApprovedCA approvedCa);

    @Mapping(source = "authCertHashes", target = "authCertHash", qualifiedByName = "toAuthCertHashes")
    @Mapping(source = "clients", target = "client", qualifiedByName = "clientsById")
    @Mapping(source = "serverAddress.address", target = "address")
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
    List<JAXBElement<Object>> xmlClientIds(List<ClientId> clientIds, @Context Map<ClientId, Object> clientMap) {
        if (clientIds == null) {
            return List.of();
        }
        return clientIds.stream()
                .map(clientId -> OBJECT_FACTORY.createSecurityServerTypeClient(xmlClientId(clientId, clientMap)))
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
        return authCert.getHash(CryptoUtils.SHA256_ID);
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

    @Named("mapDataspacesSettings")
    protected DataspaceSettingsType mapDataspacesSettings(SharedParameters sharedParameters,
                                                          @Context Map<ClientId, Object> clientMap) {
        DataspaceSettingsType result = new DataspaceSettingsType();

        if (sharedParameters.getSecurityServers() != null) {
            var servers = sharedParameters.getSecurityServers().stream()
                    .filter(securityServer -> securityServer.getServerAddress().isDsSupported())
                    .map(s -> {
                        var securityServer = new DataspaceSettingsType.SecurityServers.SecurityServer();
                        securityServer.setServerId(SecurityServerId.Conf.create(s.getOwner(), s.getServerCode()).asEncodedId());
                        securityServer.setProtocolUrl(s.getServerAddress().dsProtocolUrl());
                        return securityServer;
                    })
                    .collect(Collectors.toSet());

            if (!servers.isEmpty()) {
                if (result.getSecurityServers() == null) {
                    result.setSecurityServers(new DataspaceSettingsType.SecurityServers());
                }
                result.setSecurityServers(new DataspaceSettingsType.SecurityServers());
                result.getSecurityServers().getSecurityServer().addAll(servers);
            }
        }

        if (sharedParameters.getMembers() != null) {
            for (SharedParameters.Member member : sharedParameters.getMembers()) {
                if (StringUtils.isNotBlank(member.getDid())) {
                    var xmlMember = new DataspaceSettingsType.Members.Member();
                    xmlMember.setMember(clientMap.get(member.getId()));
                    xmlMember.setDid(member.getDid());

                    if (result.getMembers() == null) {
                        result.setMembers(new DataspaceSettingsType.Members());
                    }
                    result.getMembers().getMember().add(xmlMember);
                }
            }
        }

        if (result.getSecurityServers() != null || result.getMembers() != null) {
            return result;
        }
        return null;
    }

    private static final class IdSequence {
        int nextId = 0;

        String nextValue() {
            return String.format("id%d", nextId++);
        }
    }
}
