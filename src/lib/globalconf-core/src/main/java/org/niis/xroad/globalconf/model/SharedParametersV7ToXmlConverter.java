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
import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.xml.bind.JAXBElement;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.AcmeServer;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.ApprovedCATypeV4;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.ApprovedConnectorTlsCAType;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.ConfigurationSourceType;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.CredentialIssuerType;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.GlobalGroupType;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.GlobalSettingsType;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.MaintenanceMode;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.MemberDidType;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.MemberType;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.ObjectFactory;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.SecurityServerType;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.SharedParametersTypeV7;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.SubsystemType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper(uses = {ObjectFactory.class, MappingUtils.class}, unmappedTargetPolicy = ReportingPolicy.ERROR)
abstract class SharedParametersV7ToXmlConverter {
    public static final SharedParametersV7ToXmlConverter INSTANCE = Mappers.getMapper(SharedParametersV7ToXmlConverter.class);
    protected static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();

    SharedParametersTypeV7 convert(SharedParameters sharedParameters) {
        return sharedParameters == null ? null : convert(sharedParameters, createReferenceTargets(sharedParameters));
    }

    @Mapping(source = "sources", target = "source")
    @Mapping(source = "approvedCAs", target = "approvedCA")
    @Mapping(source = "approvedTSAs", target = "approvedTSA")
    @Mapping(source = "approvedConnectorTlsCAs", target = "approvedConnectorTlsCA")
    @Mapping(source = "credentialIssuerDids", target = "credentialIssuer")
    @Mapping(source = "members", target = "member")
    @Mapping(source = "securityServers", target = "securityServer")
    @Mapping(source = "globalGroups", target = "globalGroup")
    @Mapping(target = "centralService", ignore = true)
    @Mapping(target = "any", ignore = true)
    abstract SharedParametersTypeV7 convert(SharedParameters sharedParameters, @Context ReferenceTargets targets);

    @Mapping(source = "memberClasses", target = "memberClass")
    abstract GlobalSettingsType convert(SharedParameters.GlobalSettings globalSettings);

    @Mapping(source = "internalVerificationCerts", target = "internalVerificationCert")
    @Mapping(source = "externalVerificationCerts", target = "externalVerificationCert")
    abstract ConfigurationSourceType convert(SharedParameters.ConfigurationSource configurationSource);

    @Mapping(source = "intermediateCas", target = "intermediateCA")
    abstract ApprovedCATypeV4 convert(SharedParameters.ApprovedCA approvedCa);

    @Mapping(source = "intermediateCas", target = "intermediateCA")
    abstract ApprovedConnectorTlsCAType convert(SharedParameters.ApprovedConnectorTlsCA approvedConnectorTlsCA);

    abstract AcmeServer convert(SharedParameters.AcmeServer acmeServer);

    @Mapping(source = "securityServer.authCertHashes", target = "authCertHash", qualifiedByName = "toAuthCertHashes")
    @Mapping(source = "securityServer.clients", target = "client", qualifiedByName = "clientsById")
    @Mapping(source = "securityServer.owner", target = "owner", qualifiedByName = "clientById")
    @Mapping(source = "securityServer.maintenanceMode", target = "inMaintenanceMode")
    @Mapping(source = "id", target = "id")
    abstract SecurityServerType convertServer(SharedParameters.SecurityServer securityServer, String id,
                                              @Context ReferenceTargets targets);

    @Mapping(source = "groupMembers", target = "groupMember")
    abstract GlobalGroupType convert(SharedParameters.GlobalGroup globalGroup);

    @Mapping(target = "subsystem", ignore = true)
    @Mapping(target = "did", ignore = true)
    @Mapping(source = "id", target = "id")
    abstract MemberType convertMember(SharedParameters.Member member, String id);

    @Mapping(source = "id", target = "id")
    abstract SubsystemType convertSubsystem(SharedParameters.Subsystem subsystem, String id);

    MemberType convertMember(SharedParameters.Member member, @Context ReferenceTargets targets) {
        return (MemberType) targets.clients().get(member.getId());
    }

    SecurityServerType convert(SharedParameters.SecurityServer securityServer, @Context ReferenceTargets targets) {
        return targets.servers().get(serverId(securityServer));
    }

    CredentialIssuerType toCredentialIssuer(String did) {
        var credentialIssuer = OBJECT_FACTORY.createCredentialIssuerType();
        credentialIssuer.setDid(did);
        return credentialIssuer;
    }

    MaintenanceMode convertMaintenanceMode(SharedParameters.MaintenanceMode mode) {
        if (mode != null && mode.enabled()) {
            var maintenanceMode = OBJECT_FACTORY.createMaintenanceMode();
            maintenanceMode.setMessage(mode.message());
            maintenanceMode.setMessage(mode.message());
            return maintenanceMode;
        }
        return null;
    }

    @Named("clientById")
    Object xmlClientId(ClientId value, @Context ReferenceTargets targets) {
        return targets.clients().get(value);
    }

    @Named("clientsById")
    List<JAXBElement<Object>> xmlClientIds(List<ClientId> clientIds, @Context ReferenceTargets targets) {
        if (clientIds == null) {
            return List.of();
        }
        return clientIds.stream()
                .map(clientId -> OBJECT_FACTORY.createOriginalSecurityServerTypeClient(xmlClientId(clientId, targets)))
                .toList();
    }

    @Named("toAuthCertHashes")
    protected List<byte[]> toAuthCertHashes(List<CertHash> authCerts) {
        return authCerts.stream()
                .map(this::toAuthCertHash)
                .toList();
    }

    private byte[] toAuthCertHash(CertHash authCert) {
        return authCert.getHash(DigestAlgorithm.SHA256);
    }

    private static MemberDidType toMemberDid(ClientId memberId, SharedParameters.MemberDid memberDid,
                                            ReferenceTargets targets) {
        var server = targets.servers().get(memberDid.serverId());
        if (server == null) {
            throw XrdRuntimeException.systemInternalError("Member %s has a did for security server %s, which is not in the server list"
                    .formatted(memberId, memberDid.serverId()));
        }
        var memberDidType = OBJECT_FACTORY.createMemberDidType();
        memberDidType.setValue(memberDid.did());
        memberDidType.setSecurityServer(server);
        return memberDidType;
    }

    private static SecurityServerId serverId(SharedParameters.SecurityServer securityServer) {
        return SecurityServerId.Conf.create(securityServer.getOwner(), securityServer.getServerCode());
    }

    private ReferenceTargets createReferenceTargets(SharedParameters sharedParameters) {
        var targets = new ReferenceTargets(new HashMap<>(), new HashMap<>());
        var sequence = new IdSequence();

        if (sharedParameters.getMembers() != null) {
            for (SharedParameters.Member member : sharedParameters.getMembers()) {
                var memberType = convertMember(member, sequence.nextValue());
                targets.clients().put(member.getId(), memberType);
                for (SharedParameters.Subsystem subsystem : member.getSubsystems()) {
                    var subsystemType = convertSubsystem(subsystem, sequence.nextValue());
                    targets.clients().put(subsystem.getId(), subsystemType);
                    memberType.getSubsystem().add(subsystemType);
                }
            }
        }
        if (sharedParameters.getSecurityServers() != null) {
            for (SharedParameters.SecurityServer securityServer : sharedParameters.getSecurityServers()) {
                targets.servers().put(serverId(securityServer),
                        convertServer(securityServer, sequence.nextValue(), targets));
            }
        }
        if (sharedParameters.getMembers() != null) {
            for (SharedParameters.Member member : sharedParameters.getMembers()) {
                var memberType = (MemberType) targets.clients().get(member.getId());
                member.getDids().forEach(memberDid -> memberType.getDid().add(toMemberDid(member.getId(), memberDid, targets)));
            }
        }
        return targets;
    }

    record ReferenceTargets(Map<ClientId, Object> clients, Map<SecurityServerId, SecurityServerType> servers) {
    }

    private static final class IdSequence {
        int nextId = 0;

        String nextValue() {
            return String.format("id%d", nextId++);
        }
    }
}
