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

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.recursive.comparison.ComparingNormalizedFields;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Test;
import org.niis.xroad.globalconf.schema.sharedparameters.v4.ObjectFactory;
import org.niis.xroad.globalconf.schema.sharedparameters.v4.SharedParametersTypeV4;

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
class SharedParametersV4ToXmlConverterTest {

    private static final Map<String, String> FIELD_NAME_MAP = Map.ofEntries(
            entry("securityServer", "securityServers"),
            entry("source", "sources"),
            entry("internalVerificationCert", "internalVerificationCerts"),
            entry("externalVerificationCert", "externalVerificationCerts"),
            entry("approvedCA", "approvedCAs"),
            entry("approvedTSA", "approvedTSAs"),
            entry("member", "members"),
            entry("globalGroup", "globalGroups"),
            entry("intermediateCA", "intermediateCas"),
            entry("subsystem", "subsystems"),
            entry("client", "clients"),
            entry("memberClass", "memberClasses"),
            entry("authCertHash", "authCerts"),
            entry("groupMember", "groupMembers")
    );

    @Test
    void shouldConvertAllFields() {
        var sharedParameters = getSharedParameters();
        var xmlType = SharedParametersV4ToXmlConverter.INSTANCE.convert(sharedParameters);

        var conf = RecursiveComparisonConfiguration.builder()
                .withIntrospectionStrategy(compareRenamedFields())
                .withIgnoredFields("securityServers.owner",
                        "securityServers.clients",
                        "securityServers.authCerts",
                        "members.id",
                        "members.subsystems.id",
                        "centralService",
                        "any"
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
        assertThat(xmlType.getSecurityServer().getFirst().getAuthCertHash().getFirst())
                .isEqualTo(sharedParameters.getSecurityServers().getFirst().getAuthCertHashes().getFirst().getHash(SHA256));
    }

    @Test
    void shouldBeAbleToMarshall() throws JAXBException {
        var xmlType = SharedParametersV4ToXmlConverter.INSTANCE.convert(getSharedParameters());

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
        assertThat(SharedParametersV4ToXmlConverter.INSTANCE.convert((SharedParameters) null)).isNull();
    }

    private static void assertIdReferences(SharedParametersTypeV4 xmlType) {
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
                List.of(new SharedParameters.ApprovedTSA("tsa-name", "tsa-url", "tsa cert".getBytes(UTF_8))),
                getMembers(), List.of(getSecurityServer()), List.of(new SharedParameters.GlobalGroup("group-code",
                "group-description", List.of(subsystemId(memberId(), "SUB1")))),
                new SharedParameters.GlobalSettings(List.of(getMemberClass()), 333));
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
        approvedCA.setAcmeServer(new SharedParameters.AcmeServer("http://testca.com/acme", "192.99.88.7", "1", "2"));
        return approvedCA;
    }

    private static SharedParameters.CaInfo getCaInfo() {
        return new SharedParameters.CaInfo("ca-cert".getBytes(UTF_8), List.of(
                new SharedParameters.OcspInfo("ocsp:url", "ocsp-cert".getBytes(UTF_8))));
    }

    private static List<SharedParameters.Member> getMembers() {
        SharedParameters.Member member = new SharedParameters.Member();
        member.setMemberCode("M1");
        member.setMemberClass(getMemberClass());
        member.setName("Member1");
        var clientId = memberId();
        member.setId(clientId);
        member.setSubsystems(List.of(subsystem(clientId, "SUB1")));
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
        securityServer.setMaintenanceMode(SharedParameters.MaintenanceMode.disabled());
        return securityServer;
    }

    private static SharedParameters.MemberClass getMemberClass() {
        return new SharedParameters.MemberClass("CLASS1", "member class description");
    }

    private static SharedParameters.Subsystem subsystem(ClientId.Conf clientId, String subsystemCode) {
        return new SharedParameters.Subsystem(subsystemCode, null, subsystemId(clientId, subsystemCode));
    }

    private static ClientId.Conf subsystemId(ClientId.Conf clientId, String subsystemCode) {
        return ClientId.Conf.create(clientId.getXRoadInstance(),
                clientId.getXRoadInstance(),
                clientId.getMemberCode(),
                subsystemCode);
    }

}
