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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class SharedParametersOlderVersionsOmitPublishedValuesTest {

    @ParameterizedTest
    @MethodSource("olderVersionMarshallers")
    void shouldNotLeakPublishedValuesIntoOlderVersions(SharedParametersMarshaller marshaller) {
        var xml = marshaller.marshall(sharedParametersWithPublishedValues());

        assertThat(xml)
                .contains("<serverCode>ss0</serverCode>", "<subsystemCode>TESTCLIENT</subsystemCode>")
                .doesNotContain("<did", "systemDid", "dspBaseUrl", "<securityServer id");
    }

    private static Stream<SharedParametersMarshaller> olderVersionMarshallers() {
        return Stream.of(
                new SharedParametersV2Marshaller(),
                new SharedParametersV3Marshaller(),
                new SharedParametersV4Marshaller(),
                new SharedParametersV5Marshaller(),
                new SharedParametersV6Marshaller()
        );
    }

    private static SharedParameters sharedParametersWithPublishedValues() {
        var memberId = ClientId.Conf.create("DEV", "COM", "222");
        var subsystemId = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");

        var member = new SharedParameters.Member();
        member.setMemberClass(new SharedParameters.MemberClass("COM", "Commercial"));
        member.setMemberCode("222");
        member.setName("Test member");
        member.setId(memberId);
        member.setSubsystems(List.of(new SharedParameters.Subsystem("TESTCLIENT", null, subsystemId)));
        member.setDids(List.of(new SharedParameters.MemberDid(SecurityServerId.Conf.create(memberId, "ss0"),
                "did:web:ss0.example.org%3A7183:v1:DEV:COM:222")));

        var server = new SharedParameters.SecurityServer();
        server.setOwner(memberId);
        server.setServerCode("ss0");
        server.setAddress("ss0.example.org");
        server.setAuthCertHashes(List.of(new CertHash("ss0-auth-cert".getBytes(UTF_8))));
        server.setClients(List.of(subsystemId));
        server.setMaintenanceMode(SharedParameters.MaintenanceMode.disabled());
        server.setDspBaseUrl("https://ss0.example.org:8183/api/dsp");
        server.setSystemDid("did:web:ss0.example.org%3A7183:v1:system");

        var configurationSource = new SharedParameters.ConfigurationSource();
        configurationSource.setAddress("cs");
        configurationSource.setInternalVerificationCerts(List.of("internal-conf-signing-cert".getBytes(UTF_8)));
        configurationSource.setExternalVerificationCerts(List.of("external-conf-signing-cert".getBytes(UTF_8)));

        return SharedParameters.builder()
                .instanceIdentifier("DEV")
                .sources(List.of(configurationSource))
                .members(List.of(member))
                .securityServers(List.of(server))
                .globalGroups(List.of())
                .approvedCAs(List.of())
                .approvedTSAs(List.of())
                .globalSettings(new SharedParameters.GlobalSettings(List.of(), 60))
                .build();
    }
}
