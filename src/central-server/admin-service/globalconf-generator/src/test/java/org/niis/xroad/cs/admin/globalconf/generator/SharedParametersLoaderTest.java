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
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.CostType;
import org.niis.xroad.cs.admin.api.domain.ApprovedTsa;
import org.niis.xroad.cs.admin.api.domain.AuthCert;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.domain.FlattenedSecurityServerClientView;
import org.niis.xroad.cs.admin.api.domain.GlobalGroup;
import org.niis.xroad.cs.admin.api.domain.GlobalGroupMember;
import org.niis.xroad.cs.admin.api.domain.MemberClass;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.domain.StoredMemberDid;
import org.niis.xroad.cs.admin.api.domain.XRoadMember;
import org.niis.xroad.cs.admin.api.dto.CertificateAuthority;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.dto.CertificationService;
import org.niis.xroad.cs.admin.api.dto.DsTlsCertificationAuthority;
import org.niis.xroad.cs.admin.api.dto.DsTlsIntermediateCertificateAuthority;
import org.niis.xroad.cs.admin.api.dto.OcspResponder;
import org.niis.xroad.cs.admin.api.service.CertificationServicesService;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.api.service.ConfigurationService;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerDidService;
import org.niis.xroad.cs.admin.api.service.DataspaceMemberDidService;
import org.niis.xroad.cs.admin.api.service.DsTlsCertificationAuthoritiesService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupService;
import org.niis.xroad.cs.admin.api.service.MemberClassService;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.api.service.TimestampingServicesService;
import org.niis.xroad.globalconf.model.CertHash;
import org.niis.xroad.globalconf.model.CsrFormat;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.globalconf.model.SharedParameters;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static ee.ria.xroad.common.identifier.XRoadObjectType.MEMBER;
import static ee.ria.xroad.common.identifier.XRoadObjectType.SUBSYSTEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.EXTERNAL;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.INTERNAL;

@ExtendWith(MockitoExtension.class)
class SharedParametersLoaderTest {
    private static final String CA_NAME = "ca name";
    private static final byte[] CA_CERT = "ca cert".getBytes(UTF_8);
    private static final String CA_PROFILE_INFO = "profile-info";
    private static final String CA_OCSP_URL = "ca ocsp url";
    private static final byte[] CA_OCSP_CERT = "ca ocsp cert".getBytes(UTF_8);
    private static final String CA_ACME_SERVER_URL = "http://testca/acme";
    private static final String CA_ACME_SERVER_IP_ADDRESS = "1.2.3.4";
    private static final byte[] INTERMEDIATE_CA_CERT = "intermediate ca cert".getBytes(UTF_8);
    private static final String INTERMEDIATE_CA_OCSP_URL = "intermediate ca ocsp url";
    private static final byte[] INTERMEDIATE_CA_OCSP_CERT = "intermediate ca ocsp cert".getBytes(UTF_8);
    private static final String DS_TLS_CA_NAME = "Let's Encrypt";
    private static final byte[] DS_TLS_CA_CERT = "ds tls ca cert".getBytes(UTF_8);
    private static final byte[] DS_TLS_INTERMEDIATE_CA_CERT = "ds tls intermediate ca cert".getBytes(UTF_8);
    private static final String DS_TLS_CA_ACME_SERVER_URL = "https://acme-v02.api.letsencrypt.org/directory";
    private static final String DS_TLS_CERTIFICATE_PROFILE_ID = "xrd-ds-tls";
    private static final String ISSUER_DID_1 = "did:web:cs1.example%3A443:issuer";
    private static final String ISSUER_DID_2 = "did:web:cs2.example%3A443:issuer";
    private static final int SECOND_SERVER_ROW_ID = 2;
    private static final int ABSENT_SERVER_ROW_ID = 99;
    private static final SecurityServerId SERVER_1_ID = SecurityServerId.Conf.create(
            ClientId.Conf.create("XRD", "CLASS", "M1"), "SS1");
    private static final SecurityServerId SERVER_2_ID = SecurityServerId.Conf.create(
            ClientId.Conf.create("XRD", "CLASS", "M1"), "SS2");
    private static final String SYSTEM_DID = "did:web:security-server-address%3A7183:v1:system";
    private static final String DSP_BASE_URL = "https://security-server-address:8183/api/dsp";
    private static final String OWNER_DID = "did:web:security-server-address%3A7183:v1:XRD:CLASS:M1";
    private static final String CLIENT_DID = "did:web:security-server-address%3A7183:v1:XRD:CLASS:M2";
    private static final String OTHER_SERVER_CLIENT_DID = "did:web:other-server-address%3A7183:v1:XRD:CLASS:M2";
    private static final String TSA_NAME = "TSA name";
    private static final String TSA_URL = "TSA url";
    private static final byte[] TSA_CERT = "TSA cert".getBytes(UTF_8);
    private static final String XROAD_INSTANCE = "XRD";
    private static final String SECURITY_SERVER_ADDRESS = "security-server-address";
    private static final String SECURITY_SERVER_CODE = "SS1";
    private static final int SECURITY_SERVER_ID = 1;
    private static final CertHash SECURITY_SERVER_AUTH_CERT = new CertHash("auth cert".getBytes(UTF_8));
    private static final String GLOBAL_GROUP_CODE = "GG";
    private static final String GLOBAL_GROUP_DESCRIPTION = "GG description";
    private static final int GLOBAL_GROUP_ID = 2;
    private static final String MEMBER_CLASS_CODE = "MCLASS";
    private static final String MEMBER_CLASS_DESCRIPTION = "Member class description";
    private static final int OCSP_FRESHNESS_SECONDS = 333;
    private static final String CENTRAL_SERVICE = "cs";
    private static final byte[] CONFIGURATION_SIGNING_CERT = "conf signing cert".getBytes(UTF_8);

    @Mock
    SystemParameterService systemParameterService;
    @Mock
    CertificationServicesService certificationServicesService;
    @Mock
    TimestampingServicesService timestampingServicesService;
    @Mock
    DsTlsCertificationAuthoritiesService dsTlsCertificationAuthoritiesService;
    @Mock
    DataspaceIssuerDidService dataspaceIssuerDidService;
    @Mock
    DataspaceMemberDidService dataspaceMemberDidService;
    @Mock
    ClientService clientService;
    @Mock
    SecurityServerService securityServerService;
    @Mock
    GlobalGroupService globalGroupService;
    @Mock
    GlobalGroupMemberService globalGroupMemberService;
    @Mock
    MemberClassService memberClassService;
    @Mock
    ConfigurationService configurationService;

    @InjectMocks
    SharedParametersLoader sharedParametersLoader;

    @Test
    void loadSharedParameters() {
        stubDependencies();
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of(ISSUER_DID_1, ISSUER_DID_2));

        var parameters = sharedParametersLoader.load();

        assertThat(parameters).isNotNull();
        assertThat(parameters.getInstanceIdentifier()).isEqualTo(XROAD_INSTANCE);
        assertNodeAddressesWithConfigurationSigningKeys(parameters);
        assertApprovedCa(parameters);
        assertApproveTsa(parameters);
        assertApprovedConnectorTlsCAs(parameters);
        assertThat(parameters.getCredentialIssuerDids()).containsExactlyInAnyOrder(ISSUER_DID_1, ISSUER_DID_2);
        assertSecurityServers(parameters);
        assertGlobalGroups(parameters);
        assertGlobalSettings(parameters);
    }

    @Test
    void loadSharedParametersWithSingleIssuerDid() {
        stubDependencies();
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of(ISSUER_DID_1));

        var parameters = sharedParametersLoader.load();

        assertThat(parameters.getCredentialIssuerDids()).containsExactly(ISSUER_DID_1);
    }

    @Test
    void loadSharedParametersOmitsIssuerDidsWhenRegistryIsEmpty() {
        stubDependencies();
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());

        var parameters = sharedParametersLoader.load();

        assertThat(parameters.getCredentialIssuerDids()).isEmpty();
    }

    @Test
    void loadSharedParametersCarriesLegacyProtocolDisabled() {
        stubDependencies();
        when(systemParameterService.isLegacyProtocolDisabled()).thenReturn(true);

        var parameters = sharedParametersLoader.load();

        assertThat(parameters.getGlobalSettings().isLegacyProtocolDisabled()).isTrue();
    }

    @Test
    void loadMapsSystemValuesOfServerThatPublishedThem() {
        stubDependencies();
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());
        when(securityServerService.findAll()).thenReturn(List.of(withSystemValues(getSecurityServers().getFirst(),
                SYSTEM_DID, DSP_BASE_URL)));

        var parameters = sharedParametersLoader.load();

        assertThat(parameters.getSecurityServers()).singleElement()
                .satisfies(ss -> assertThat(ss.getSystemValues()).isEqualTo(new ServerSystemValues(SYSTEM_DID, DSP_BASE_URL)));
    }

    @Test
    void loadLeavesSystemValuesAbsentForServerWithoutThem() {
        stubDependencies();
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());

        var parameters = sharedParametersLoader.load();

        assertThat(parameters.getSecurityServers()).singleElement()
                .satisfies(ss -> assertThat(ss.getSystemValues()).isNull());
    }

    @ParameterizedTest
    @CsvSource({
            "did:web:security-server-address%3A7183:v1:system, ''",
            "'', https://security-server-address:8183/api/dsp"})
    void loadLeavesSystemValuesAbsentForServerWithHalfPair(String systemDid, String dspBaseUrl) {
        stubDependencies();
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());
        when(securityServerService.findAll()).thenReturn(List.of(withSystemValues(getSecurityServers().getFirst(),
                systemDid, dspBaseUrl)));

        var parameters = sharedParametersLoader.load();

        assertThat(parameters.getSecurityServers()).singleElement()
                .satisfies(ss -> assertThat(ss.getSystemValues()).isNull());
    }

    @Test
    void loadSkipsMemberDidRowOfServerAbsentFromTheServerList() {
        stubDependencies();
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());
        when(dataspaceMemberDidService.findAll()).thenReturn(List.of(
                new StoredMemberDid(ABSENT_SERVER_ROW_ID, ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M1"), OWNER_DID),
                new StoredMemberDid(SECURITY_SERVER_ID, ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2"), CLIENT_DID)));

        var parameters = sharedParametersLoader.loadWithMemberDids();

        assertThat(didsByMember(parameters)).containsOnly(Map.entry("M2", Map.of(SERVER_1_ID, CLIENT_DID)));
    }

    @Test
    void loadMapsMemberDidsToTheServerIdentifierOfTheirServer() {
        stubDependencies();
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());
        when(dataspaceMemberDidService.findAll()).thenReturn(List.of(
                new StoredMemberDid(SECURITY_SERVER_ID, ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M1"), OWNER_DID),
                new StoredMemberDid(SECURITY_SERVER_ID, ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2"), CLIENT_DID)));

        var parameters = sharedParametersLoader.loadWithMemberDids();

        assertThat(didsByMember(parameters)).containsOnly(
                Map.entry("M1", Map.of(SERVER_1_ID, OWNER_DID)),
                Map.entry("M2", Map.of(SERVER_1_ID, CLIENT_DID)));
    }

    @Test
    void loadKeepsMemberDidWhenTheMembersOnlyClientOnTheServerIsDisabled() {
        stubDependencies(List.of());
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());
        when(dataspaceMemberDidService.findAll()).thenReturn(List.of(
                new StoredMemberDid(SECURITY_SERVER_ID, ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2"), CLIENT_DID)));

        var parameters = sharedParametersLoader.loadWithMemberDids();

        assertThat(parameters.getSecurityServers()).singleElement().satisfies(ss -> assertThat(ss.getClients()).isEmpty());
        assertThat(didsByMember(parameters)).containsOnly(Map.entry("M2", Map.of(SERVER_1_ID, CLIENT_DID)));
    }

    @Test
    void loadReadsMemberDidsOncePerCall() {
        stubDependencies();
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());
        when(dataspaceMemberDidService.findAll()).thenReturn(List.of(
                new StoredMemberDid(SECURITY_SERVER_ID, ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M1"), OWNER_DID),
                new StoredMemberDid(SECURITY_SERVER_ID, ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2"), CLIENT_DID)));

        sharedParametersLoader.loadWithMemberDids();

        verify(dataspaceMemberDidService, times(1)).findAll();
    }

    @Test
    void loadNoLongerCarriesMemberDidAfterItsRowIsDeleted() {
        stubDependencies();
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());
        var row = new StoredMemberDid(SECURITY_SERVER_ID, ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2"), CLIENT_DID);
        when(dataspaceMemberDidService.findAll()).thenReturn(List.of(row)).thenReturn(List.of());

        var before = sharedParametersLoader.loadWithMemberDids();
        var after = sharedParametersLoader.loadWithMemberDids();

        assertThat(didsByMember(before)).containsOnly(Map.entry("M2", Map.of(SERVER_1_ID, CLIENT_DID)));
        assertThat(didsByMember(after)).isEmpty();
    }

    @Test
    void loadLeavesMemberDidsOutAndNeverReadsThem() {
        stubDependencies();
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());

        var parameters = sharedParametersLoader.load();

        verifyNoInteractions(dataspaceMemberDidService);
        assertThat(didsByMember(parameters)).isEmpty();
        assertThat(parameters.getMembers()).extracting(SharedParameters.Member::getMemberCode).containsExactly("M1", "M2");
        assertThat(parameters.getSecurityServers()).hasSize(1);
    }

    @Test
    void loadTwiceOverUnchangedRowsGivesTheSameModelInStableOrder() {
        stubDependencies();
        stubSecondServer();
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());
        var m2 = ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2");
        when(dataspaceMemberDidService.findAll()).thenReturn(List.of(
                new StoredMemberDid(SECOND_SERVER_ROW_ID, m2, OTHER_SERVER_CLIENT_DID),
                new StoredMemberDid(SECURITY_SERVER_ID, m2, CLIENT_DID)));

        var first = sharedParametersLoader.loadWithMemberDids();
        var second = sharedParametersLoader.loadWithMemberDids();

        assertThat(first.getMembers()).extracting(SharedParameters.Member::getMemberCode).containsExactly("M1", "M2");
        assertThat(first.getMembers().getLast().getDids().entrySet()).extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactly(
                        tuple(SERVER_1_ID, CLIENT_DID),
                        tuple(SERVER_2_ID, OTHER_SERVER_CLIENT_DID));
        assertThat(second).usingRecursiveComparison().isEqualTo(first);
    }

    @ParameterizedTest
    @MethodSource("generators")
    void generatedSharedParametersCarryPublishedValuesOnlyInVersion7(
            String version, Function<SharedParametersLoader, String> generate) {
        stubDependencies();
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of(ISSUER_DID_1));
        when(securityServerService.findAll()).thenReturn(List.of(withSystemValues(getSecurityServers().getFirst(),
                SYSTEM_DID, DSP_BASE_URL)));
        boolean version7 = "7".equals(version);
        if (version7) {
            when(dataspaceMemberDidService.findAll()).thenReturn(List.of(
                    new StoredMemberDid(SECURITY_SERVER_ID, ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2"), CLIENT_DID)));
        }

        var xml = generate.apply(sharedParametersLoader);

        if (version7) {
            verify(dataspaceMemberDidService, times(1)).findAll();
            assertThat(xml).contains("<systemDid>" + SYSTEM_DID + "</systemDid>",
                    "<dspBaseUrl>" + DSP_BASE_URL + "</dspBaseUrl>", CLIENT_DID + "</did>");
        } else {
            verifyNoInteractions(dataspaceMemberDidService);
            assertThat(xml).contains("<serverCode>" + SECURITY_SERVER_CODE + "</serverCode>")
                    .doesNotContain("systemDid", "dspBaseUrl", CLIENT_DID);
        }
    }

    private static Stream<Arguments> generators() {
        return Stream.of(
                generator("2", loader -> new SharedParametersV2Generator(loader).generate()),
                generator("3", loader -> new SharedParametersV3Generator(loader).generate()),
                generator("4", loader -> new SharedParametersV4Generator(loader).generate()),
                generator("5", loader -> new SharedParametersV5Generator(loader).generate()),
                generator("6", loader -> new SharedParametersV6Generator(loader).generate()),
                generator("7", loader -> new SharedParametersV7Generator(loader).generate()));
    }

    private static Arguments generator(String version, Function<SharedParametersLoader, String> generate) {
        return Arguments.of(version, generate);
    }

    private static Map<String, Map<SecurityServerId, String>> didsByMember(SharedParameters parameters) {
        return parameters.getMembers().stream()
                .filter(member -> !member.getDids().isEmpty())
                .collect(toMap(SharedParameters.Member::getMemberCode,
                        SharedParameters.Member::getDids));
    }

    private static SecurityServer withSystemValues(SecurityServer server, String systemDid, String dspBaseUrl) {
        server.setSystemDid(systemDid);
        server.setDspBaseUrl(dspBaseUrl);
        return server;
    }

    private void stubSecondServer() {
        var owner = new XRoadMember("Member M1", ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M1"), getMemberClass());
        var second = new SecurityServer(owner, "SS2");
        second.setId(SECOND_SERVER_ROW_ID);
        second.setAddress("other-server-address");
        when(securityServerService.findAll()).thenReturn(List.of(getSecurityServers().getFirst(), second));
        stubEnabledServerClients(SECOND_SERVER_ROW_ID, List.of());
    }

    private void stubDependencies() {
        stubDependencies(List.of(getFlattenedSecurityServerClientView("M2", "S1", null)));
    }

    private void stubDependencies(List<FlattenedSecurityServerClientView> enabledServerClients) {
        when(systemParameterService.getInstanceIdentifier()).thenReturn(XROAD_INSTANCE);
        when(configurationService.getNodeAddressesWithOrderedConfigurationSigningKeys())
                .thenReturn(getNodeAddressesWithConfigurationSigningKeys());
        when(certificationServicesService.findAll()).thenReturn(List.of(getCertificationService()));

        when(timestampingServicesService.getTimestampingServices()).thenReturn(Set.of(getApprovedTsa()));
        when(dsTlsCertificationAuthoritiesService.findAll()).thenReturn(List.of(getDsTlsCertificationAuthority()));
        when(clientService.findAll()).thenReturn(getClients());

        when(securityServerService.findAll()).thenReturn(getSecurityServers());
        stubEnabledServerClients(SECURITY_SERVER_ID, enabledServerClients);

        when(globalGroupService.findGlobalGroups()).thenReturn(List.of(getGlobalGroup()));
        when(globalGroupMemberService.findByGroupCode(GLOBAL_GROUP_CODE)).thenReturn(List.of(
                new GlobalGroupMember(null, ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2", "S2"))));

        when(memberClassService.findAll()).thenReturn(List.of(new MemberClass(MEMBER_CLASS_CODE, MEMBER_CLASS_DESCRIPTION)));
        when(systemParameterService.getOcspFreshnessSeconds()).thenReturn(OCSP_FRESHNESS_SECONDS);
    }

    private void stubEnabledServerClients(int serverId, List<FlattenedSecurityServerClientView> enabledServerClients) {
        when(clientService.find(new ClientService.SearchParameters()
                .setSecurityServerId(serverId).setSecurityServerEnabled(true)))
                .thenReturn(enabledServerClients);
    }

    private void assertApprovedConnectorTlsCAs(SharedParameters parameters) {
        assertThat(parameters.getApprovedConnectorTlsCAs()).singleElement().satisfies(dsTlsCa -> {
            assertThat(dsTlsCa.getName()).isEqualTo(DS_TLS_CA_NAME);
            assertThat(dsTlsCa.getTopCA()).isNotNull();
            assertThat(dsTlsCa.getTopCA().getCert()).isEqualTo(DS_TLS_CA_CERT);
            assertThat(dsTlsCa.getIntermediateCas())
                    .singleElement()
                    .satisfies(caInfo -> assertThat(caInfo.getCert()).isEqualTo(DS_TLS_INTERMEDIATE_CA_CERT));
            assertThat(dsTlsCa.getAcmeServer()).isNotNull();
            assertThat(dsTlsCa.getAcmeServer().getDirectoryURL()).isEqualTo(DS_TLS_CA_ACME_SERVER_URL);
            assertThat(dsTlsCa.getAcmeServer().getConnectorTlsCertificateProfileId()).isEqualTo(DS_TLS_CERTIFICATE_PROFILE_ID);
            assertThat(dsTlsCa.getAcmeServer().getAuthenticationCertificateProfileId()).isNull();
            assertThat(dsTlsCa.getAcmeServer().getSigningCertificateProfileId()).isNull();
        });
    }

    private void assertGlobalSettings(SharedParameters parameters) {
        assertThat(parameters.getGlobalSettings()).isNotNull();
        assertThat(parameters.getGlobalSettings().getMemberClasses()).singleElement().isEqualTo(
                new SharedParameters.MemberClass(MEMBER_CLASS_CODE, MEMBER_CLASS_DESCRIPTION));

        assertThat(parameters.getGlobalSettings().getOcspFreshnessSeconds()).isEqualTo(OCSP_FRESHNESS_SECONDS);
        assertThat(parameters.getGlobalSettings().isLegacyProtocolDisabled()).isFalse();
    }

    private void assertGlobalGroups(SharedParameters parameters) {
        assertThat(parameters.getGlobalGroups()).singleElement().satisfies(gg -> {
            assertThat(gg.getGroupCode()).isEqualTo(GLOBAL_GROUP_CODE);
            assertThat(gg.getDescription()).isEqualTo(GLOBAL_GROUP_DESCRIPTION);
            assertThat(gg.getGroupMembers()).singleElement().isEqualTo(ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2", "S2"));
        });
    }

    private void assertSecurityServers(SharedParameters parameters) {
        assertThat(parameters.getSecurityServers()).singleElement().satisfies(ss -> {
            assertThat(ss.getOwner()).isEqualTo(ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M1"));
            assertThat(ss.getAddress()).isEqualTo(SECURITY_SERVER_ADDRESS);
            assertThat(ss.getServerCode()).isEqualTo(SECURITY_SERVER_CODE);
            assertThat(ss.getClients()).singleElement()
                    .isEqualTo(ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2", "S1"));
            assertThat(ss.getAuthCertHashes()).singleElement().isEqualTo(SECURITY_SERVER_AUTH_CERT);
        });
    }

    private void assertApproveTsa(SharedParameters parameters) {
        assertThat(parameters.getApprovedTSAs()).singleElement().satisfies(tsa -> {
            assertThat(tsa.getName()).isEqualTo(TSA_NAME);
            assertThat(tsa.getUrl()).isEqualTo(TSA_URL);
            assertThat(tsa.getCert()).isEqualTo(TSA_CERT);
            assertThat(tsa.getCostType()).isEqualTo(CostType.PAID);
        });
    }

    private void assertApprovedCa(SharedParameters parameters) {
        assertThat(parameters.getApprovedCAs()).singleElement().satisfies(approvedCA -> {
            assertThat(approvedCA.getName()).isEqualTo(CA_NAME);
            assertThat(approvedCA.getCertificateProfileInfo()).isEqualTo(CA_PROFILE_INFO);
            assertThat(approvedCA.getDefaultCsrFormat()).isEqualTo(CsrFormat.DER);
            assertThat(approvedCA.getAuthenticationOnly()).isTrue();
            assertThat(approvedCA.getTopCA()).isNotNull();
            assertThat(approvedCA.getTopCA().getCert()).isEqualTo(CA_CERT);
            assertThat(approvedCA.getTopCA().getOcsp()).singleElement().satisfies(ocsp -> {
                assertThat(ocsp.getUrl()).isEqualTo(CA_OCSP_URL);
                assertThat(ocsp.getCostType()).isEqualTo(CostType.FREE);
                assertThat(ocsp.getCert()).isEqualTo(CA_OCSP_CERT);
                assertThat(approvedCA.getAcmeServer().getDirectoryURL()).isEqualTo(CA_ACME_SERVER_URL);
                assertThat(approvedCA.getAcmeServer().getIpAddress()).isEqualTo(CA_ACME_SERVER_IP_ADDRESS);
            });

            assertThat(approvedCA.getIntermediateCas())
                    .isNotNull()
                    .singleElement()
                    .satisfies(caInfo -> {
                        assertThat(caInfo.getCert()).isEqualTo(INTERMEDIATE_CA_CERT);
                        assertThat(caInfo.getOcsp()).singleElement().satisfies(ocsp -> {
                            assertThat(ocsp.getUrl()).isEqualTo(INTERMEDIATE_CA_OCSP_URL);
                            assertThat(ocsp.getCert()).isEqualTo(INTERMEDIATE_CA_OCSP_CERT);
                        });
                    });
        });
    }

    private void assertNodeAddressesWithConfigurationSigningKeys(SharedParameters parameters) {
        assertThat(parameters.getSources()).singleElement().satisfies(src -> {
            assertThat(src.getAddress()).isEqualTo(CENTRAL_SERVICE);
            assertThat(src.getInternalVerificationCerts()).hasSize(1);
            assertThat(src.getInternalVerificationCerts().get(0)).isEqualTo(CONFIGURATION_SIGNING_CERT);
            assertThat(src.getExternalVerificationCerts()).hasSize(1);
            assertThat(src.getExternalVerificationCerts().get(0)).isEqualTo(CONFIGURATION_SIGNING_CERT);
        });
    }

    private Map<String, List<ConfigurationSigningKey>> getNodeAddressesWithConfigurationSigningKeys() {
        return Map.of(CENTRAL_SERVICE, List.of(
                new ConfigurationSigningKey().setSourceType(INTERNAL).setCert(CONFIGURATION_SIGNING_CERT),
                new ConfigurationSigningKey().setSourceType(EXTERNAL).setCert(CONFIGURATION_SIGNING_CERT)
        ));
    }

    private CertificationService getCertificationService() {
        var certificationService = new CertificationService();
        certificationService.setName(CA_NAME);
        certificationService.setCertificate(CA_CERT);
        certificationService.setCertificateProfileInfo(CA_PROFILE_INFO);
        certificationService.setTlsAuth(true);
        certificationService.setDefaultCsrFormat(CsrFormat.DER);
        certificationService.setOcspResponders(List.of(getOcspResponder(CA_OCSP_URL, CA_OCSP_CERT)));
        certificationService.setIntermediateCas(List.of(getCertificateAuthority()));
        certificationService.setAcmeServerDirectoryUrl(CA_ACME_SERVER_URL);
        certificationService.setAcmeServerIpAddress(CA_ACME_SERVER_IP_ADDRESS);
        return certificationService;

    }

    private CertificateAuthority getCertificateAuthority() {
        var certificateAuthority = new CertificateAuthority();
        certificateAuthority.setCaCertificate(new CertificateDetails().setEncoded(INTERMEDIATE_CA_CERT));
        certificateAuthority.setOcspResponders(List.of(
                getOcspResponder(INTERMEDIATE_CA_OCSP_URL, INTERMEDIATE_CA_OCSP_CERT)));
        return certificateAuthority;
    }

    private DsTlsCertificationAuthority getDsTlsCertificationAuthority() {
        var dsTlsCa = new DsTlsCertificationAuthority();
        dsTlsCa.setName(DS_TLS_CA_NAME);
        dsTlsCa.setCertificate(DS_TLS_CA_CERT);
        dsTlsCa.setAcmeServerDirectoryUrl(DS_TLS_CA_ACME_SERVER_URL);
        dsTlsCa.setDsTlsCertificateProfileId(DS_TLS_CERTIFICATE_PROFILE_ID);
        dsTlsCa.setIntermediateCas(List.of(getDsTlsIntermediateCertificateAuthority()));
        return dsTlsCa;
    }

    private DsTlsIntermediateCertificateAuthority getDsTlsIntermediateCertificateAuthority() {
        var intermediateCa = new DsTlsIntermediateCertificateAuthority();
        intermediateCa.setCaCertificate(new CertificateDetails().setEncoded(DS_TLS_INTERMEDIATE_CA_CERT));
        return intermediateCa;
    }

    private OcspResponder getOcspResponder(String caOcspUrl, byte[] caOcspCert) {
        var ocsp = new OcspResponder();
        ocsp.setUrl(caOcspUrl);
        ocsp.setCostType(CostType.FREE);
        ocsp.setCertificate(caOcspCert);
        return ocsp;
    }

    private ApprovedTsa getApprovedTsa() {
        var approvedTsa = new ApprovedTsa();
        approvedTsa.setName(TSA_NAME);
        approvedTsa.setUrl(TSA_URL);
        approvedTsa.setCostType(CostType.PAID);
        approvedTsa.setCertificate(new CertificateDetails().setEncoded(TSA_CERT));
        return approvedTsa;
    }


    @Test
    void shouldMapMembers() {
        var clients = getClients();
        var members = new SharedParametersLoader.MemberMapper().map(clients);

        assertThat(members)
                .hasSize(2)
                .satisfiesExactly(
                        member -> {
                            assertThat(member.getMemberCode()).isEqualTo("M1");
                            assertThat(member.getId()).isEqualTo(ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M1"));

                            assertThat(member.getSubsystems())
                                    .containsOnly(new SharedParameters.Subsystem("S1", null,
                                            ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M1", "S1")));
                        },
                        member -> {
                            assertThat(member.getMemberCode()).isEqualTo("M2");
                            assertThat(member.getId()).isEqualTo(ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2"));
                            assertThat(member.getSubsystems())
                                    .containsOnly(
                                            new SharedParameters.Subsystem("S1", null,
                                                    ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2", "S1")),
                                            new SharedParameters.Subsystem("S2", "Name of S2",
                                                    ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2", "S2")));
                        }
                );
    }

    private List<FlattenedSecurityServerClientView> getClients() {
        return List.of(
                getFlattenedSecurityServerClientView("M1", null, null),
                getFlattenedSecurityServerClientView("M1", "S1", null),
                getFlattenedSecurityServerClientView("M2", "S1", null),
                getFlattenedSecurityServerClientView("M2", "S2", "Name of S2"),
                getFlattenedSecurityServerClientView("M2", null, null));
    }

    private FlattenedSecurityServerClientView getFlattenedSecurityServerClientView(String memberCode,
                                                                                   String subsystemCode,
                                                                                    String subsystemName) {
        var client = new FlattenedSecurityServerClientView();
        client.setXroadInstance(XROAD_INSTANCE);

        MemberClass memberClass = getMemberClass();
        client.setMemberClass(memberClass);

        client.setMemberCode(memberCode);
        client.setSubsystemCode(subsystemCode);
        client.setSubsystemName(subsystemName);
        client.setType(subsystemCode == null ? MEMBER : SUBSYSTEM);
        client.setMemberName(String.format("Member %s%s", memberCode, subsystemCode != null ? "/" + subsystemCode : ""));
        return client;
    }

    private MemberClass getMemberClass() {
        var memberClass = new MemberClass();
        memberClass.setCode("CLASS");
        memberClass.setDescription("Class description");
        return memberClass;
    }

    private List<SecurityServer> getSecurityServers() {
        var owner = new XRoadMember("Member M1",
                ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M1"),
                getMemberClass());
        var securityServer = new SecurityServer(owner, SECURITY_SERVER_CODE);
        securityServer.setId(SECURITY_SERVER_ID);
        securityServer.setAddress(SECURITY_SERVER_ADDRESS);
        var authCert = new AuthCert();
        authCert.setCert("auth cert".getBytes(UTF_8));
        securityServer.setAuthCerts(Set.of(authCert));
        return List.of(securityServer);
    }

    private GlobalGroup getGlobalGroup() {
        var globalGroup = new GlobalGroup();
        globalGroup.setId(GLOBAL_GROUP_ID);
        globalGroup.setGroupCode(GLOBAL_GROUP_CODE);
        globalGroup.setDescription(GLOBAL_GROUP_DESCRIPTION);
        return globalGroup;
    }

}
