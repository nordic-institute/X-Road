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
import ee.ria.xroad.common.util.CryptoUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.ApprovedTsa;
import org.niis.xroad.cs.admin.api.domain.AuthCert;
import org.niis.xroad.cs.admin.api.domain.FlattenedSecurityServerClientView;
import org.niis.xroad.cs.admin.api.domain.GlobalGroup;
import org.niis.xroad.cs.admin.api.domain.GlobalGroupMember;
import org.niis.xroad.cs.admin.api.domain.MemberClass;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.domain.XRoadMember;
import org.niis.xroad.cs.admin.api.dto.CertificateAuthority;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.dto.CertificationService;
import org.niis.xroad.cs.admin.api.dto.OcspResponder;
import org.niis.xroad.cs.admin.api.service.CertificationServicesService;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupService;
import org.niis.xroad.cs.admin.api.service.MemberClassService;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.api.service.TimestampingServicesService;

import java.util.List;
import java.util.Set;

import static ee.ria.xroad.common.identifier.XRoadObjectType.MEMBER;
import static ee.ria.xroad.common.identifier.XRoadObjectType.SUBSYSTEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedParametersLoaderTest {
    public static final String CA_NAME = "ca name";
    public static final byte[] CA_CERT = "ca cert".getBytes(UTF_8);
    public static final String CA_PROFILE_INFO = "profile-info";
    public static final String CA_OCSP_URL = "ca ocsp url";
    public static final byte[] CA_OCSP_CERT = "ca ocsp cert".getBytes(UTF_8);
    public static final byte[] INTERMEDIATE_CA_CERT = "intermediate ca cert".getBytes(UTF_8);
    public static final String INTERMEDIATE_CA_OCSP_URL = "intermediate ca ocsp url";
    public static final byte[] INTERMEDIATE_CA_OCSP_CERT = "intermediate ca ocsp cert".getBytes(UTF_8);
    public static final String TSA_NAME = "TSA name";
    public static final String TSA_URL = "TSA url";
    public static final byte[] TSA_CERT = "TSA cert".getBytes(UTF_8);
    public static final String XROAD_INSTANCE = "XRD";
    public static final String SECURITY_SERVER_ADDRESS = "security-server-address";
    public static final String SECURITY_SERVER_CODE = "SS1";
    public static final int SECURITY_SERVER_ID = 1;
    public static final byte[] SECURITY_SERVER_AUTH_CERT = "auth cert".getBytes(UTF_8);
    public static final String GLOBAL_GROUP_CODE = "GG";
    public static final String GLOBAL_GROUP_DESCRIPTION = "GG description";
    public static final int GLOBAL_GROUP_ID = 2;
    public static final String CENTRAL_SERVICE_CODE = "service-code";
    public static final String MEMBER_CLASS_CODE = "MCLASS";
    public static final String MEMBER_CLASS_DESCRIPTION = "Member class description";
    public static final int OCSP_FRESHNESS_SECONDS = 333;
    @Mock
    SystemParameterService systemParameterService;
    @Mock
    CertificationServicesService certificationServicesService;
    @Mock
    TimestampingServicesService timestampingServicesService;
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

    @InjectMocks
    SharedParametersLoader sharedParametersLoader;

    @Test
    void loadSharedParameters() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn(XROAD_INSTANCE);
        when(certificationServicesService.findAll()).thenReturn(List.of(getCertificationService()));

        when(timestampingServicesService.getTimestampingServices()).thenReturn(Set.of(getApprovedTsa()));
        when(clientService.findAll()).thenReturn(getClients());

        when(securityServerService.findAll()).thenReturn(getSecurityServers());
        when(clientService.find(new ClientService.SearchParameters().setSecurityServerId(SECURITY_SERVER_ID)))
                .thenReturn(List.of(getFlattenedSecurityServerClientView("M2", "S1")));

        when(globalGroupService.findGlobalGroups()).thenReturn(List.of(getGlobalGroup()));
        when(globalGroupMemberService.findByGroupCode(GLOBAL_GROUP_CODE)).thenReturn(List.of(
                new GlobalGroupMember(null, ClientId.Conf.create(XROAD_INSTANCE, "CLASS", "M2", "S2"))));

        when(memberClassService.findAll()).thenReturn(List.of(new MemberClass(MEMBER_CLASS_CODE, MEMBER_CLASS_DESCRIPTION)));
        when(systemParameterService.getOcspFreshnessSeconds()).thenReturn(OCSP_FRESHNESS_SECONDS);

        var parameters = sharedParametersLoader.load();

        assertThat(parameters).isNotNull();
        assertThat(parameters.getInstanceIdentifier()).isEqualTo(XROAD_INSTANCE);
        assertApprovedCa(parameters);
        assertApproveTsa(parameters);
        assertSecurityServers(parameters);
        assertGlobalGroups(parameters);
        assertGlobalSettings(parameters);
    }

    private void assertGlobalSettings(SharedParameters parameters) {
        assertThat(parameters.getGlobalSettings()).isNotNull();
        assertThat(parameters.getGlobalSettings().getMemberClasses()).singleElement().isEqualTo(
                new SharedParameters.MemberClass(MEMBER_CLASS_CODE, MEMBER_CLASS_DESCRIPTION));

        assertThat(parameters.getGlobalSettings().getOcspFreshnessSeconds()).isEqualTo(OCSP_FRESHNESS_SECONDS);
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
            assertThat(ss.getAuthCertHashes()).singleElement().isEqualTo(CryptoUtils.certHash(SECURITY_SERVER_AUTH_CERT));
        });
    }

    private void assertApproveTsa(SharedParameters parameters) {
        assertThat(parameters.getApprovedTSAs()).singleElement().satisfies(tsa -> {
            assertThat(tsa.getName()).isEqualTo(TSA_NAME);
            assertThat(tsa.getUrl()).isEqualTo(TSA_URL);
            assertThat(tsa.getCert()).isEqualTo(TSA_CERT);
        });
    }

    private void assertApprovedCa(SharedParameters parameters) {
        assertThat(parameters.getApprovedCAs()).singleElement().satisfies(approvedCA -> {
            assertThat(approvedCA.getName()).isEqualTo(CA_NAME);
            assertThat(approvedCA.getCertificateProfileInfo()).isEqualTo(CA_PROFILE_INFO);
            assertThat(approvedCA.getAuthenticationOnly()).isTrue();
            assertThat(approvedCA.getTopCA()).isNotNull();
            assertThat(approvedCA.getTopCA().getCert()).isEqualTo(CA_CERT);
            assertThat(approvedCA.getTopCA().getOcsp()).singleElement().satisfies(ocsp -> {
                assertThat(ocsp.getUrl()).isEqualTo(CA_OCSP_URL);
                assertThat(ocsp.getCert()).isEqualTo(CA_OCSP_CERT);
            });

            assertThat(approvedCA.getIntermediateCAs())
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


    private CertificationService getCertificationService() {
        var certificationService = new CertificationService();
        certificationService.setName(CA_NAME);
        certificationService.setCertificate(CA_CERT);
        certificationService.setCertificateProfileInfo(CA_PROFILE_INFO);
        certificationService.setTlsAuth(true);
        certificationService.setOcspResponders(List.of(getOcspResponder(CA_OCSP_URL, CA_OCSP_CERT)));
        certificationService.setIntermediateCas(List.of(getCertificateAuthority()));
        return certificationService;

    }

    private CertificateAuthority getCertificateAuthority() {
        var certificateAuthority = new CertificateAuthority();
        certificateAuthority.setCaCertificate(new CertificateDetails().setEncoded(INTERMEDIATE_CA_CERT));
        certificateAuthority.setOcspResponders(List.of(
                getOcspResponder(INTERMEDIATE_CA_OCSP_URL, INTERMEDIATE_CA_OCSP_CERT)));
        return certificateAuthority;
    }

    private OcspResponder getOcspResponder(String caOcspUrl, byte[] caOcspCert) {
        var ocsp = new OcspResponder();
        ocsp.setUrl(caOcspUrl);
        ocsp.setCertificate(caOcspCert);
        return ocsp;
    }

    private ApprovedTsa getApprovedTsa() {
        var approvedTsa = new ApprovedTsa();
        approvedTsa.setName(TSA_NAME);
        approvedTsa.setUrl(TSA_URL);
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
                            assertThat(member.getSubsystems()).map(SharedParameters.Subsystem::getSubsystemCode)
                                    .containsOnly("S1");
                        },
                        member -> {
                            assertThat(member.getMemberCode()).isEqualTo("M2");
                            assertThat(member.getSubsystems()).map(SharedParameters.Subsystem::getSubsystemCode)
                                    .containsOnly("S1", "S2");
                        }
                );
    }

    private List<FlattenedSecurityServerClientView> getClients() {
        return List.of(
                getFlattenedSecurityServerClientView("M1", null),
                getFlattenedSecurityServerClientView("M1", "S1"),
                getFlattenedSecurityServerClientView("M2", "S1"),
                getFlattenedSecurityServerClientView("M2", "S2"),
                getFlattenedSecurityServerClientView("M2", null));
    }

    private FlattenedSecurityServerClientView getFlattenedSecurityServerClientView(String memberCode, String subsystemCode) {
        var client = new FlattenedSecurityServerClientView();
        client.setXroadInstance(XROAD_INSTANCE);

        MemberClass memberClass = getMemberClass();
        client.setMemberClass(memberClass);

        client.setMemberCode(memberCode);
        client.setSubsystemCode(subsystemCode);
        client.setType(subsystemCode == null ? MEMBER : SUBSYSTEM);
        client.setMemberName(String.format("Member %s%s", memberCode, subsystemCode != null ? "/" + subsystemCode : ""));
        return client;
    }

    private MemberClass getMemberClass() {
        var memberClass = new MemberClass();
        memberClass.setCode("CLASS");
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
        authCert.setCert(SECURITY_SERVER_AUTH_CERT);
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
