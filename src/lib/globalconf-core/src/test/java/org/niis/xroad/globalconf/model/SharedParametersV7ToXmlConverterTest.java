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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.recursive.comparison.ComparingNormalizedFields;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.CostType;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.ObjectFactory;
import org.niis.xroad.globalconf.schema.sharedparameters.v7.SharedParametersTypeV7;

import java.io.StringWriter;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA256;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@Slf4j
class SharedParametersV7ToXmlConverterTest {

    private static final Map<String, String> FIELD_NAME_MAP = Map.ofEntries(
            entry("securityServer", "securityServers"),
            entry("source", "sources"),
            entry("internalVerificationCert", "internalVerificationCerts"),
            entry("externalVerificationCert", "externalVerificationCerts"),
            entry("approvedCA", "approvedCAs"),
            entry("approvedTSA", "approvedTSAs"),
            entry("approvedConnectorTlsCA", "approvedConnectorTlsCAs"),
            entry("member", "members"),
            entry("globalGroup", "globalGroups"),
            entry("intermediateCA", "intermediateCas"),
            entry("subsystem", "subsystems"),
            entry("client", "clients"),
            entry("memberClass", "memberClasses"),
            entry("authCertHash", "authCerts"),
            entry("groupMember", "groupMembers"),
            entry("inMaintenanceMode", "maintenanceMode")
    );

    @Test
    void shouldConvertAllFields() {
        var sharedParameters = getSharedParameters();
        var xmlType = SharedParametersV7ToXmlConverter.INSTANCE.convert(sharedParameters);

        var conf = RecursiveComparisonConfiguration.builder()
                .withIntrospectionStrategy(compareRenamedFields())
                .withIgnoredFields("securityServers.owner",
                        "securityServers.id",
                        "securityServers.clients",
                        "securityServers.authCerts",
                        "members.id",
                        "members.subsystems.id",
                        "members.did",
                        "centralService",
                        "any",
                        "credentialIssuer"
                )
                .withEqualsForFields((a, b) -> new BigInteger(a.toString()).compareTo(new BigInteger(b.toString())) == 0,
                        "globalSettings.ocspFreshnessSeconds")
                .build();

        assertThat(xmlType)
                .hasNoNullFieldsOrPropertiesExcept("centralService")
                .usingRecursiveComparison(conf)
                .isEqualTo(sharedParameters);

        assertThat(xmlType)
                .usingRecursiveAssertion()
                .ignoringFields("globalGroup.groupMember.id")
                .allFieldsSatisfy(Objects::nonNull);

        assertIdReferences(xmlType);
        assertMemberDidReferences(xmlType);
        assertThat(xmlType.getSecurityServer().getFirst().getAuthCertHash().getFirst())
                .isEqualTo(sharedParameters.getSecurityServers().getFirst().getAuthCertHashes().getFirst().getHash(SHA256));
    }

    @Test
    void shouldBeAbleToMarshall() throws JAXBException {
        var xmlType = SharedParametersV7ToXmlConverter.INSTANCE.convert(getSharedParameters());

        JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
        var writer = new StringWriter();
        var marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        var conf = new ObjectFactory().createConf(xmlType);

        assertThatNoException().isThrownBy(() -> marshaller.marshal(conf, writer));

        log.info(writer.toString());
    }

    @Test
    void shouldReturnNullWhenInputIsNull() {
        assertThat(SharedParametersV7ToXmlConverter.INSTANCE.convert((SharedParameters) null)).isNull();
    }

    @Test
    void shouldRoundTripConnectorTlsCaWithAcmeServerAndIntermediates() {
        var approvedConnectorTlsCA = getApprovedConnectorTlsCA();

        assertThat(roundTrip(List.of(approvedConnectorTlsCA)))
                .usingRecursiveComparison()
                .isEqualTo(List.of(approvedConnectorTlsCA));
    }

    @Test
    void shouldRoundTripConnectorTlsCaWithoutAcmeServer() {
        var approvedConnectorTlsCA = getApprovedConnectorTlsCA();
        approvedConnectorTlsCA.setAcmeServer(null);

        assertThat(roundTrip(List.of(approvedConnectorTlsCA)))
                .usingRecursiveComparison()
                .isEqualTo(List.of(approvedConnectorTlsCA));
    }

    @Test
    void shouldRoundTripConnectorTlsCaWithoutIntermediateCas() {
        var approvedConnectorTlsCA = getApprovedConnectorTlsCA();
        approvedConnectorTlsCA.setIntermediateCas(List.of());

        assertThat(roundTrip(List.of(approvedConnectorTlsCA)))
                .usingRecursiveComparison()
                .isEqualTo(List.of(approvedConnectorTlsCA));
    }

    @Test
    void shouldRoundTripEmptyConnectorTlsCaList() {
        assertThat(roundTrip(List.of())).isEmpty();
    }

    @Test
    void shouldRoundTripSingleIssuerDid() {
        assertThat(roundTripCredentialIssuerDids(List.of("did:web:cs1.example%3A443:issuer")))
                .containsExactly("did:web:cs1.example%3A443:issuer");
    }

    @Test
    void shouldRoundTripMultipleIssuerDids() {
        var credentialIssuerDids = List.of("did:web:cs1.example%3A443:issuer", "did:web:cs2.example%3A443:issuer");

        assertThat(roundTripCredentialIssuerDids(credentialIssuerDids)).containsExactlyElementsOf(credentialIssuerDids);
    }

    @Test
    void shouldRoundTripEmptyIssuerDidListAsNotDataspaceEnabled() {
        var xml = new SharedParametersV7Marshaller().marshall(minimalSharedParameters(List.of()));

        assertThat(xml).doesNotContain("credentialIssuer", "did");
        assertThat(roundTripCredentialIssuerDids(List.of())).isEmpty();
    }

    @Test
    void shouldWriteAndReadBackXrdAdr44Example() {
        var sharedParameters = xrdAdr44Example();
        var xmlType = SharedParametersV7ToXmlConverter.INSTANCE.convert(sharedParameters);

        var member = xmlType.getMember().getFirst();
        var subsystem = member.getSubsystem().getFirst();
        var server = xmlType.getSecurityServer().getFirst();
        assertThat(server.getOwner()).isSameAs(member);
        assertThat(server.getClient()).map(JAXBElement::getValue).containsExactly(subsystem);
        assertThat(member.getDid()).singleElement().satisfies(did -> assertThat(did.getSecurityServer()).isSameAs(server));
        assertThat(idIndex(server.getId())).isGreaterThan(idIndex(member.getId())).isGreaterThan(idIndex(subsystem.getId()));

        var xml = new SharedParametersV7Marshaller().marshall(sharedParameters);

        assertThat(xml)
                .contains("<dspBaseUrl>https://ss0.example.org:8183/api/dsp</dspBaseUrl>")
                .contains("<systemDid>did:web:ss0.example.org%3A7183:v1:system</systemDid>")
                .contains("did:web:ss0.example.org%3A7183:v1:DEV:COM:222</did>");
        assertThat(new SharedParametersV7(xml.getBytes(UTF_8)).getSharedParameters())
                .usingRecursiveComparison()
                .isEqualTo(sharedParameters);
    }

    @Test
    void shouldGiveEveryServerAnIdWhenNothingIsPublished() {
        var sharedParameters = xrdAdr44Example();
        var server = sharedParameters.getSecurityServers().getFirst();
        server.setSystemDid(null);
        server.setDspBaseUrl(null);
        sharedParameters.getMembers().getFirst().setDids(List.of());

        var xmlType = SharedParametersV7ToXmlConverter.INSTANCE.convert(sharedParameters);

        assertThat(xmlType.getSecurityServer()).singleElement().satisfies(ss -> assertThat(ss.getId()).isNotBlank());
        assertThat(xmlType.getMember().getFirst().getDid()).isEmpty();

        var xml = new SharedParametersV7Marshaller().marshall(sharedParameters);

        assertThat(xml).doesNotContain("<did securityServer", "systemDid", "dspBaseUrl");
        assertThat(new SharedParametersV7(xml.getBytes(UTF_8)).getSharedParameters())
                .usingRecursiveComparison()
                .isEqualTo(sharedParameters);
    }

    private static SharedParameters xrdAdr44Example() {
        var memberId = ClientId.Conf.create("DEV", "COM", "222");
        var subsystemId = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
        var serverId = SecurityServerId.Conf.create(memberId, "ss0");

        var member = new SharedParameters.Member();
        member.setMemberClass(new SharedParameters.MemberClass("COM", "Commercial"));
        member.setMemberCode("222");
        member.setName("Test member");
        member.setId(memberId);
        member.setSubsystems(List.of(new SharedParameters.Subsystem("TESTCLIENT", null, subsystemId)));
        member.setDids(List.of(new SharedParameters.MemberDid(serverId, "did:web:ss0.example.org%3A7183:v1:DEV:COM:222")));

        var server = new SharedParameters.SecurityServer();
        server.setOwner(memberId);
        server.setServerCode("ss0");
        server.setAddress("ss0.example.org");
        server.setAuthCertHashes(List.of(new CertHash(SHA256, "ss0-auth-cert-hash".getBytes(UTF_8))));
        server.setClients(List.of(subsystemId));
        server.setMaintenanceMode(SharedParameters.MaintenanceMode.disabled());
        server.setDspBaseUrl("https://ss0.example.org:8183/api/dsp");
        server.setSystemDid("did:web:ss0.example.org%3A7183:v1:system");

        return new SharedParameters("DEV", getConfigurationSources(), List.of(), List.of(), List.of(), List.of(),
                List.of(member), List.of(server), List.of(), new SharedParameters.GlobalSettings(List.of(), 60));
    }

    private static int idIndex(String id) {
        return Integer.parseInt(id.substring("id".length()));
    }

    private static List<SharedParameters.ApprovedConnectorTlsCA> roundTrip(
            List<SharedParameters.ApprovedConnectorTlsCA> approvedConnectorTlsCAs) {
        var sharedParameters = minimalSharedParameters(approvedConnectorTlsCAs);
        var xml = new SharedParametersV7Marshaller().marshall(sharedParameters);
        var afterMarshalling = new SharedParametersV7(xml.getBytes(UTF_8)).getSharedParameters();
        return afterMarshalling.getApprovedConnectorTlsCAs();
    }

    private static List<String> roundTripCredentialIssuerDids(List<String> credentialIssuerDids) {
        var sharedParamsBuilder = SharedParameters.builder();
        sharedParamsBuilder.instanceIdentifier("CS");
        sharedParamsBuilder.sources(getConfigurationSources());
        sharedParamsBuilder.globalSettings(new SharedParameters.GlobalSettings(null, 60));
        sharedParamsBuilder.credentialIssuerDids(credentialIssuerDids);
        var xml = new SharedParametersV7Marshaller().marshall(sharedParamsBuilder.build());
        var afterMarshalling = new SharedParametersV7(xml.getBytes(UTF_8)).getSharedParameters();
        return afterMarshalling.getCredentialIssuerDids();
    }

    private static SharedParameters minimalSharedParameters(
            List<SharedParameters.ApprovedConnectorTlsCA> approvedConnectorTlsCAs) {
        var sharedParamsBuilder = SharedParameters.builder();
        sharedParamsBuilder.instanceIdentifier("CS");
        sharedParamsBuilder.sources(getConfigurationSources());
        sharedParamsBuilder.globalSettings(new SharedParameters.GlobalSettings(null, 60));
        sharedParamsBuilder.approvedConnectorTlsCAs(approvedConnectorTlsCAs);
        return sharedParamsBuilder.build();
    }

    private static void assertIdReferences(SharedParametersTypeV7 xmlType) {
        var ownerMember = xmlType.getMember().getFirst();
        var client = ownerMember.getSubsystem().getFirst();

        assertThat(ownerMember).isNotNull();
        assertThat(client).isNotNull();

        assertThat(xmlType.getSecurityServer()).singleElement()
                .satisfies(ss -> {
                    assertThat(ss.getOwner()).isSameAs(ownerMember);
                    assertThat(ss.getClient())
                            .map(JAXBElement::getValue)
                            .singleElement().isSameAs(client);
                });
    }

    private static void assertMemberDidReferences(SharedParametersTypeV7 xmlType) {
        var member = xmlType.getMember().getFirst();
        var server = xmlType.getSecurityServer().getFirst();

        assertThat(member.getDid()).singleElement().satisfies(did -> {
            assertThat(did.getValue()).isEqualTo("did:web:security-server-address%3A7183:v1:CLASS1:M1");
            assertThat(did.getSecurityServer()).isSameAs(server);
        });
        assertThat(server.getId()).isNotBlank();
    }

    private static ComparingNormalizedFields compareRenamedFields() {
        return new ComparingNormalizedFields() {
            @Override
            protected String normalizeFieldName(String fieldName) {
                return FIELD_NAME_MAP.getOrDefault(fieldName, fieldName);
            }
        };
    }

    private static SharedParameters getSharedParameters() {
        return new SharedParameters("INSTANCE", getConfigurationSources(), List.of(getApprovedCA()),
                List.of(new SharedParameters.ApprovedTSA("tsa-name", "tsa-url", "tsa cert".getBytes(UTF_8), CostType.PAID)),
                List.of(getApprovedConnectorTlsCA()), getCredentialIssuerDids(), getMembers(), List.of(getSecurityServer()),
                List.of(new SharedParameters.GlobalGroup("group-code",
                "group-description", List.of(subsystemId(memberId(), "SUB1")))),
                new SharedParameters.GlobalSettings(List.of(getMemberClass()), 333));
    }

    private static List<String> getCredentialIssuerDids() {
        return List.of("did:web:cs1.example%3A443:issuer", "did:web:cs2.example%3A443:issuer");
    }

    private static List<SharedParameters.ConfigurationSource> getConfigurationSources() {
        var configurationSource = new SharedParameters.ConfigurationSource();
        configurationSource.setAddress("cs");
        configurationSource.setInternalVerificationCerts(List.of("internal-conf-singing-cert".getBytes(UTF_8)));
        configurationSource.setExternalVerificationCerts(List.of("external-conf-singing-cert".getBytes(UTF_8)));
        return List.of(configurationSource);
    }

    private static SharedParameters.ApprovedCA getApprovedCA() {
        var approvedCA = new SharedParameters.ApprovedCA();
        approvedCA.setName("approved ca name");
        approvedCA.setAuthenticationOnly(true);
        approvedCA.setTopCA(getCaInfo());
        approvedCA.setCertificateProfileInfo("certificateProfileInfo");
        approvedCA.setIntermediateCas(List.of(getCaInfo()));
        approvedCA.setDefaultCsrFormat(CsrFormat.PEM);
        approvedCA.setAcmeServer(new SharedParameters.AcmeServer("http://testca.com/acme", "192.99.88.7", "1", "2", "3"));
        return approvedCA;
    }

    private static SharedParameters.ApprovedConnectorTlsCA getApprovedConnectorTlsCA() {
        var approvedConnectorTlsCA = new SharedParameters.ApprovedConnectorTlsCA();
        approvedConnectorTlsCA.setName("approved ds tls ca name");
        approvedConnectorTlsCA.setTopCA(getCaInfo());
        approvedConnectorTlsCA.setIntermediateCas(List.of(getCaInfo()));
        approvedConnectorTlsCA.setAcmeServer(new SharedParameters.AcmeServer("http://testca.com/acme", "192.99.88.7", "1", "2",
                "ds-tls-profile"));
        return approvedConnectorTlsCA;
    }

    private static SharedParameters.CaInfo getCaInfo() {
        return new SharedParameters.CaInfo("ca-cert".getBytes(UTF_8), List.of(
                new SharedParameters.OcspInfo("ocsp:url", "ocsp-cert".getBytes(UTF_8), CostType.FREE)));
    }

    private static List<SharedParameters.Member> getMembers() {
        SharedParameters.Member member = new SharedParameters.Member();
        member.setMemberCode("M1");
        member.setMemberClass(getMemberClass());
        member.setName("Member1");
        var clientId = memberId();
        member.setId(clientId);
        member.setSubsystems(List.of(
                subsystem(clientId, "SUB1", "Name1"),
                subsystem(clientId, "SUB2", "Name2")
        ));
        member.setDids(List.of(new SharedParameters.MemberDid(
                SecurityServerId.Conf.create(clientId, "security-server-code"),
                "did:web:security-server-address%3A7183:v1:CLASS1:M1")));
        return List.of(member);
    }

    private static ClientId.Conf memberId() {
        return ClientId.Conf.create("INSTANCE", "CLASS1", "M1");
    }

    private static SharedParameters.SecurityServer getSecurityServer() {
        var securityServer = new SharedParameters.SecurityServer();
        securityServer.setOwner(memberId());
        securityServer.setServerCode("security-server-code");
        securityServer.setAddress("security-server-address");
        securityServer.setClients(List.of(subsystemId(memberId(), "SUB1")));
        securityServer.setAuthCertHashes(List.of(new CertHash("ss-auth-cert".getBytes(UTF_8))));
        securityServer.setMaintenanceMode(new SharedParameters.MaintenanceMode(true, "maintenance message"));
        securityServer.setSystemDid("did:web:security-server-address%3A7183:v1:system");
        securityServer.setDspBaseUrl("https://security-server-address:8183/api/dsp");
        return securityServer;
    }

    private static SharedParameters.MemberClass getMemberClass() {
        return new SharedParameters.MemberClass("CLASS1", "member class description");
    }

    private static SharedParameters.Subsystem subsystem(ClientId.Conf clientId, String subsystemCode, String subsystemName) {
        return new SharedParameters.Subsystem(subsystemCode, subsystemName, subsystemId(clientId, subsystemCode));
    }

    private static ClientId.Conf subsystemId(ClientId.Conf clientId, String subsystemCode) {
        return ClientId.Conf.create(clientId.getXRoadInstance(),
                clientId.getXRoadInstance(),
                clientId.getMemberCode(),
                subsystemCode);
    }

}
