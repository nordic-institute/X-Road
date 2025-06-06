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
package org.niis.xroad.globalconf.model;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;

import jakarta.xml.bind.JAXBElement;
import org.niis.xroad.globalconf.schema.sharedparameters.v5.AcmeServer;
import org.niis.xroad.globalconf.schema.sharedparameters.v5.ApprovedCATypeV3;
import org.niis.xroad.globalconf.schema.sharedparameters.v5.ApprovedTSAType;
import org.niis.xroad.globalconf.schema.sharedparameters.v5.CaInfoType;
import org.niis.xroad.globalconf.schema.sharedparameters.v5.ConfigurationSourceType;
import org.niis.xroad.globalconf.schema.sharedparameters.v5.GlobalGroupType;
import org.niis.xroad.globalconf.schema.sharedparameters.v5.GlobalSettingsType;
import org.niis.xroad.globalconf.schema.sharedparameters.v5.MemberClassType;
import org.niis.xroad.globalconf.schema.sharedparameters.v5.MemberType;
import org.niis.xroad.globalconf.schema.sharedparameters.v5.OcspInfoType;
import org.niis.xroad.globalconf.schema.sharedparameters.v5.SecurityServerType;
import org.niis.xroad.globalconf.schema.sharedparameters.v5.SharedParametersTypeV5;
import org.niis.xroad.globalconf.schema.sharedparameters.v5.SubsystemType;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SharedParametersV5Converter {

    SharedParameters convert(SharedParametersTypeV5 source) throws CertificateEncodingException, IOException {
        String instanceIdentifier = source.getInstanceIdentifier();
        List<SharedParameters.ConfigurationSource> configurationSources = getConfigurationSources(source.getSource());
        List<SharedParameters.ApprovedCA> approvedCAs = getApprovedCAs(source.getApprovedCA());
        List<SharedParameters.ApprovedTSA> approvedTSAs = getApprovedTSAs(source.getApprovedTSA());
        List<SharedParameters.Member> members = getMembers(instanceIdentifier, source.getMember());
        List<SharedParameters.SecurityServer> securityServers = getSecurityServers(source);
        List<SharedParameters.GlobalGroup> globalGroups = getGlobalGroups(source.getGlobalGroup());
        SharedParameters.GlobalSettings globalSettings = getGlobalSettings(source.getGlobalSettings());
        return new SharedParameters(instanceIdentifier, configurationSources, approvedCAs, approvedTSAs,
                members, securityServers, globalGroups, globalSettings);
    }

    private List<SharedParameters.ConfigurationSource> getConfigurationSources(List<ConfigurationSourceType> sources) {
        List<SharedParameters.ConfigurationSource> configurationSources = new ArrayList<>();
        if (sources != null) {
            configurationSources.addAll(sources.stream().map(this::toConfigurationSource).toList());
        }
        return configurationSources;
    }

    private List<SharedParameters.ApprovedCA> getApprovedCAs(List<ApprovedCATypeV3> approvedCATypes) {
        List<SharedParameters.ApprovedCA> approvedCAs = new ArrayList<>();
        if (approvedCATypes != null) {
            approvedCAs.addAll(approvedCATypes.stream().map(this::toApprovedCa).toList());
        }
        return approvedCAs;
    }


    private List<SharedParameters.ApprovedTSA> getApprovedTSAs(List<ApprovedTSAType> approvedTSATypes) {
        List<SharedParameters.ApprovedTSA> approvedTSAs = new ArrayList<>();
        if (approvedTSATypes != null) {
            approvedTSAs.addAll(approvedTSATypes.stream().map(this::toApprovedTsa).toList());
        }
        return approvedTSAs;
    }

    private List<SharedParameters.Member> getMembers(String instanceIdentifier, List<MemberType> memberTypes) {
        if (memberTypes != null) {
            return memberTypes.stream().map(source -> toMember(instanceIdentifier, source)).toList();
        }
        return List.of();
    }

    private List<SharedParameters.SecurityServer> getSecurityServers(SharedParametersTypeV5 source) {
        List<SharedParameters.SecurityServer> securityServers = new ArrayList<>();
        if (source.getSecurityServer() != null) {
            Map<String, ClientId> clientIds = getClientIds(source);
            securityServers.addAll(
                    source.getSecurityServer().stream()
                            .map(s -> toSecurityServer(clientIds, s, source.getInstanceIdentifier()))
                            .toList()
            );
        }
        return securityServers;
    }

    private Map<String, ClientId> getClientIds(SharedParametersTypeV5 source) {
        Map<String, ClientId> ret = new HashMap<>();
        source.getMember().forEach(member -> {
            ret.put(member.getId(), toClientId(source.getInstanceIdentifier(), member));
            member.getSubsystem().forEach(subsystem -> {
                ret.put(subsystem.getId(), toClientId(source.getInstanceIdentifier(), member, subsystem));
            });
        });
        return ret;
    }

    private List<SharedParameters.GlobalGroup> getGlobalGroups(List<GlobalGroupType> globalGroupTypes) {
        List<SharedParameters.GlobalGroup> globalGroups = new ArrayList<>();
        if (globalGroupTypes != null) {
            globalGroups.addAll(globalGroupTypes.stream().map(this::toGlobalGroup).toList());
        }
        return globalGroups;
    }

    private SharedParameters.GlobalSettings getGlobalSettings(GlobalSettingsType globalSettingsType) {
        return globalSettingsType != null ? toGlobalSettings(globalSettingsType) : null;
    }

    private SharedParameters.ConfigurationSource toConfigurationSource(ConfigurationSourceType source) {
        var target = new SharedParameters.ConfigurationSource();
        target.setAddress(source.getAddress());
        target.setInternalVerificationCerts(source.getInternalVerificationCert());
        target.setExternalVerificationCerts(source.getExternalVerificationCert());
        return target;
    }

    private SharedParameters.ApprovedCA toApprovedCa(ApprovedCATypeV3 source) {
        var target = new SharedParameters.ApprovedCA();
        target.setName(source.getName());
        target.setAuthenticationOnly(source.isAuthenticationOnly());
        if (source.getTopCA() != null) {
            target.setTopCA(toCaInfo(source.getTopCA()));
        }
        if (source.getIntermediateCA() != null) {
            target.setIntermediateCas(source.getIntermediateCA().stream().map(this::toCaInfo).toList());
        }
        target.setCertificateProfileInfo(source.getCertificateProfileInfo());
        if (source.getAcmeServer() != null) {
            target.setAcmeServer(toAcmeServer(source.getAcmeServer()));
        }
        return target;
    }

    private SharedParameters.AcmeServer toAcmeServer(AcmeServer source) {
        var acmeServer = new SharedParameters.AcmeServer();
        acmeServer.setDirectoryURL(source.getDirectoryURL());
        acmeServer.setIpAddress(source.getIpAddress());
        acmeServer.setAuthenticationCertificateProfileId(source.getAuthenticationCertificateProfileId());
        acmeServer.setSigningCertificateProfileId(source.getSigningCertificateProfileId());
        return acmeServer;
    }

    private SharedParameters.CaInfo toCaInfo(CaInfoType source) {
        var caInfo = new SharedParameters.CaInfo();
        caInfo.setCert(source.getCert());
        if (source.getOcsp() != null) {
            caInfo.setOcsp(source.getOcsp().stream().map(this::toOcspInfo).toList());
        }
        return caInfo;
    }

    private SharedParameters.OcspInfo toOcspInfo(OcspInfoType source) {
        var ocspInfo = new SharedParameters.OcspInfo();
        ocspInfo.setUrl(source.getUrl());
        ocspInfo.setCert(source.getCert());
        return ocspInfo;
    }

    private SharedParameters.ApprovedTSA toApprovedTsa(ApprovedTSAType source) {
        var target = new SharedParameters.ApprovedTSA();
        target.setName(source.getName());
        target.setUrl(source.getUrl());
        target.setCert(source.getCert());
        return target;
    }

    private SharedParameters.Member toMember(String instanceIdentifier, MemberType source) {
        var target = new SharedParameters.Member();
        target.setMemberClass(toMemberClass(source.getMemberClass()));
        target.setMemberCode(source.getMemberCode());
        target.setName(source.getName());
        target.setId(toClientId(instanceIdentifier, source));
        if (source.getSubsystem() != null) {
            target.setSubsystems(source.getSubsystem().stream().map(subsystem ->
                    toSubsystem(instanceIdentifier, source, subsystem)).toList());
        }
        return target;
    }

    private SharedParameters.MemberClass toMemberClass(MemberClassType source) {
        var target = new SharedParameters.MemberClass();
        target.setCode(source.getCode());
        target.setDescription(source.getDescription());
        return target;
    }

    private SharedParameters.Subsystem toSubsystem(String instanceIdentifier, MemberType memberType, SubsystemType source) {
        return new SharedParameters.Subsystem(source.getSubsystemCode(),
                source.getSubsystemName(),
                toClientId(instanceIdentifier, memberType, source));
    }

    private SharedParameters.SecurityServer toSecurityServer(
            Map<String, ClientId> clientIds, SecurityServerType source, String instanceIdentifier) {
        var target = new SharedParameters.SecurityServer();
        target.setOwner(toClientId(instanceIdentifier, (MemberType) source.getOwner()));
        target.setServerCode(source.getServerCode());
        target.setAddress(source.getAddress());
        target.setAuthCertHashes(source.getAuthCertHash().stream().map(hash -> new CertHash(DigestAlgorithm.SHA256, hash)).toList());

        if (source.getClient() != null) {
            List<ClientId> clients = new ArrayList<>();
            for (JAXBElement<?> client : source.getClient()) {
                if (client.getValue() instanceof MemberType) {
                    clients.add(toClientId(instanceIdentifier, (MemberType) client.getValue()));
                } else if (client.getValue() instanceof SubsystemType) {
                    clients.add(clientIds.get(((SubsystemType) client.getValue()).getId()));
                }
            }
            target.setClients(clients);
        }
        Optional.ofNullable(source.getInMaintenanceMode())
                .map(mode -> SharedParameters.MaintenanceMode.enabled(mode.getMessage()))
                .or(() -> Optional.of(SharedParameters.MaintenanceMode.disabled()))
                .ifPresent(target::setMaintenanceMode);
        return target;
    }

    private ClientId toClientId(String instanceIdentifier, MemberType source) {
        return ClientId.Conf.create(instanceIdentifier, source.getMemberClass().getCode(), source.getMemberCode());
    }

    private ClientId toClientId(String instanceIdentifier, MemberType member, SubsystemType subsystem) {
        return ClientId.Conf.create(
                instanceIdentifier, member.getMemberClass().getCode(), member.getMemberCode(), subsystem.getSubsystemCode()
        );
    }

    private SharedParameters.GlobalGroup toGlobalGroup(GlobalGroupType source) {
        var target = new SharedParameters.GlobalGroup();
        target.setGroupCode(source.getGroupCode());
        target.setDescription(source.getDescription());

        if (source.getGroupMember() != null) {
            target.setGroupMembers(source.getGroupMember().stream().map(ClientId.class::cast).toList());
        }
        return target;
    }

    private SharedParameters.GlobalSettings toGlobalSettings(GlobalSettingsType source) {
        var target = new SharedParameters.GlobalSettings();
        target.setOcspFreshnessSeconds(source.getOcspFreshnessSeconds().intValue());
        if (source.getMemberClass() != null) {
            target.setMemberClasses(source.getMemberClass().stream().map(this::toMemberClass).toList());
        }
        return target;
    }

}
