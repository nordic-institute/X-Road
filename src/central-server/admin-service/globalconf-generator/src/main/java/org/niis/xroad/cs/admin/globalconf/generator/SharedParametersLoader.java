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
package org.niis.xroad.cs.admin.globalconf.generator;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.domain.AuthCert;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType;
import org.niis.xroad.cs.admin.api.domain.FlattenedSecurityServerClientView;
import org.niis.xroad.cs.admin.api.domain.GlobalGroup;
import org.niis.xroad.cs.admin.api.domain.GlobalGroupMember;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.dto.CertificateAuthority;
import org.niis.xroad.cs.admin.api.dto.CertificationService;
import org.niis.xroad.cs.admin.api.dto.OcspResponder;
import org.niis.xroad.cs.admin.api.service.CertificationServicesService;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.api.service.ConfigurationService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupService;
import org.niis.xroad.cs.admin.api.service.MemberClassService;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.api.service.TimestampingServicesService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@Component
@RequiredArgsConstructor
@Slf4j
class SharedParametersLoader {
    private final SystemParameterService systemParameterService;
    private final CertificationServicesService certificationServicesService;
    private final TimestampingServicesService timestampingServicesService;
    private final ClientService clientService;
    private final SecurityServerService securityServerService;
    private final GlobalGroupService globalGroupService;
    private final GlobalGroupMemberService globalGroupMemberService;
    private final MemberClassService memberClassService;
    private final ConfigurationService configurationService;


    SharedParameters load() {
        var parameters = new SharedParameters();
        parameters.setSources(getSources());
        parameters.setInstanceIdentifier(systemParameterService.getInstanceIdentifier());
        parameters.setApprovedCAs(getApprovedCAs());
        parameters.setApprovedTSAs(getApprovedTSAs());
        parameters.setMembers(getMembers());
        parameters.setSecurityServers(getSecurityServers());
        parameters.setGlobalGroups(getGlobalGroups());
        parameters.setGlobalSettings(getGlobalSettings());
        return parameters;
    }

    private List<SharedParameters.ConfigurationSource> getSources() {
        return configurationService.getNodeAddressesWithOrderedConfigurationSigningKeys().entrySet().stream()
                .map(this::toSource)
                .toList();
    }

    private SharedParameters.ConfigurationSource toSource(
            Map.Entry<String, List<ConfigurationSigningKey>> addressWithConfigurationSigningKeys
    ) {
        var source = new SharedParameters.ConfigurationSource();
        source.setAddress(addressWithConfigurationSigningKeys.getKey());
        source.setInternalVerificationCerts(
                getSigningKeysByType(addressWithConfigurationSigningKeys.getValue(), ConfigurationSourceType.INTERNAL)
        );
        source.setExternalVerificationCerts(
                getSigningKeysByType(addressWithConfigurationSigningKeys.getValue(), ConfigurationSourceType.EXTERNAL)
        );
        return source;
    }

    private List<byte[]> getSigningKeysByType(
            List<ConfigurationSigningKey> signingKeys, ConfigurationSourceType configurationSourceType
    ) {
        return signingKeys.stream()
                .filter(key -> configurationSourceType.equals(key.getSourceType()))
                .map(ConfigurationSigningKey::getCert)
                .toList();
    }

    private List<SharedParameters.ApprovedCA> getApprovedCAs() {
        var approvedCas = certificationServicesService.findAll();
        return approvedCas.stream()
                .map(this::toApprovedCa)
                .toList();
    }

    private SharedParameters.ApprovedCA toApprovedCa(CertificationService ca) {
        var approvedCA = new SharedParameters.ApprovedCA();
        approvedCA.setName(ca.getName());
        approvedCA.setAuthenticationOnly(ca.getTlsAuth());
        approvedCA.setCertificateProfileInfo(ca.getCertificateProfileInfo());
        approvedCA.setTopCA(new SharedParameters.CaInfo(toOcspInfos(ca.getOcspResponders()), ca.getCertificate()));
        approvedCA.setIntermediateCAs(toCaInfos(ca.getIntermediateCas()));
        return approvedCA;
    }

    private List<SharedParameters.CaInfo> toCaInfos(List<CertificateAuthority> cas) {
        return cas.stream()
                .map(ca -> new SharedParameters.CaInfo(toOcspInfos(ca.getOcspResponders()), ca.getCaCertificate().getEncoded()))
                .toList();
    }

    private List<SharedParameters.OcspInfo> toOcspInfos(List<OcspResponder> ocspResponders) {
        return ocspResponders.stream()
                .map(this::toOcspInfo)
                .toList();
    }

    private SharedParameters.OcspInfo toOcspInfo(OcspResponder ocsp) {
        return new SharedParameters.OcspInfo(ocsp.getUrl(), ocsp.getCertificate());
    }

    private List<SharedParameters.ApprovedTSA> getApprovedTSAs() {
        return timestampingServicesService.getTimestampingServices().stream()
                .map(tsa -> new SharedParameters.ApprovedTSA(tsa.getName(), tsa.getUrl(), tsa.getCertificate().getEncoded()))
                .toList();
    }

    private List<SharedParameters.Member> getMembers() {
        return new MemberMapper().map(clientService.findAll());

    }

    private List<SharedParameters.SecurityServer> getSecurityServers() {
        return securityServerService.findAll().stream()
                .map(this::toSecurityServer)
                .toList();
    }

    private SharedParameters.SecurityServer toSecurityServer(SecurityServer ss) {
        var result = new SharedParameters.SecurityServer();
        result.setOwner(ss.getOwner().getIdentifier());
        result.setAddress(ss.getAddress());
        result.setServerCode(ss.getServerCode());
        result.setClients(getSecurityServerClients(ss.getId()));
        result.setAuthCerts(ss.getAuthCerts().stream()
                .map(AuthCert::getCert)
                .toList());
        return result;
    }

    private List<ClientId> getSecurityServerClients(int id) {
        return clientService.find(new ClientService.SearchParameters()
                        .setSecurityServerId(id)
                        .setSecurityServerEnabled(true))
                .stream().map(SharedParametersLoader::toClientId).toList();

    }

    private static ClientId toClientId(FlattenedSecurityServerClientView client) {
        return ClientId.Conf.create(client.getXroadInstance(),
                client.getMemberClass().getCode(),
                client.getMemberCode(),
                client.getSubsystemCode());
    }

    private List<SharedParameters.GlobalGroup> getGlobalGroups() {
        return globalGroupService.findGlobalGroups().stream()
                .map(this::getGlobalGroup)
                .toList();
    }

    private SharedParameters.GlobalGroup getGlobalGroup(GlobalGroup globalGroup) {
        return new SharedParameters.GlobalGroup(
                globalGroup.getGroupCode(),
                globalGroup.getDescription(),
                getGroupMembers(globalGroup.getGroupCode()));
    }

    private List<ClientId> getGroupMembers(String groupCode) {
        return globalGroupMemberService.findByGroupCode(groupCode).stream()
                .map(GlobalGroupMember::getIdentifier)
                .collect(toList());
    }

    private SharedParameters.GlobalSettings getGlobalSettings() {
        var memberClasses = memberClassService.findAll().stream()
                .map(memberClass -> new SharedParameters.MemberClass(memberClass.getCode(), memberClass.getDescription()))
                .toList();

        return new SharedParameters.GlobalSettings(memberClasses, systemParameterService.getOcspFreshnessSeconds());
    }

    static class MemberMapper {
        private Map<ClientId, List<SharedParameters.Subsystem>> subsystems;

        List<SharedParameters.Member> map(List<FlattenedSecurityServerClientView> flattenedClients) {
            subsystems = new HashMap<>();
            var members = new ArrayList<SharedParameters.Member>();
            for (FlattenedSecurityServerClientView client : flattenedClients) {
                if (client.getSubsystemCode() == null) {
                    members.add(toMember(client));
                } else {
                    addSubSystem(client);
                }
            }
            return members;
        }

        private void addSubSystem(FlattenedSecurityServerClientView client) {
            var clientId = toClientId(client);
            getSubsystemList(toMemberId(clientId)).add(new SharedParameters.Subsystem(client.getSubsystemCode(), clientId));
        }

        private SharedParameters.Member toMember(FlattenedSecurityServerClientView client) {
            var clientId = toClientId(client);
            var member = new SharedParameters.Member();
            member.setId(clientId);
            member.setMemberClass(
                    new SharedParameters.MemberClass(client.getMemberClass().getCode(), client.getMemberClass().getDescription()));
            member.setMemberCode(client.getMemberCode());
            member.setName(client.getMemberName());
            member.setSubsystems(getSubsystemList(clientId));
            return member;
        }

        private List<SharedParameters.Subsystem> getSubsystemList(ClientId clientId) {
            return subsystems.computeIfAbsent(clientId, clId -> new ArrayList<>());
        }

        private ClientId toMemberId(ClientId clientId) {
            return ClientId.Conf.create(clientId.getXRoadInstance(), clientId.getMemberClass(), clientId.getMemberCode());
        }
    }
}
