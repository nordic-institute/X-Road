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

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class SharedParametersMarshallRoundTripTest {

    private static final String V7_FIXTURE = "src/test/resources/globalconf_good_v7/EE/shared-params.xml";
    private static final String UNKNOWN_SCHEME_VERSION_DID = "did:web:foo.bar.baz%3A7183:v2:EE:BUSINESS:foo";

    @ParameterizedTest
    @ValueSource(strings = {
            "src/test/resources/globalconf_good_v7/EE/shared-params.xml",
            "src/test/resources/globalconf_good_v6/EE/shared-params.xml",
            "src/test/resources/globalconf_good_v5/EE/shared-params.xml",
            "src/test/resources/globalconf_good_v4/EE/shared-params.xml",
            "src/test/resources/globalconf_good_v3/EE/shared-params.xml",
            "src/test/resources/globalconf_good_v2/EE/shared-params.xml"
    })
    void shouldEqualAfterXmlMarshalling(String sharedParamsPath) throws Exception {
        var path = Paths.get(sharedParamsPath);
        var version = VersionedConfigurationDirectory.getVersion(path);
        var content = FileUtils.readFileToByteArray(path.toFile());
        var parametersProviderFactory = ParametersProviderFactory.forGlobalConfVersion(version);
        var sharedParametersProvider = parametersProviderFactory.sharedParametersProvider(content);

        var initial = sharedParametersProvider.getSharedParameters();
        var xml = sharedParametersProvider.getMarshaller().marshall(initial);
        var afterMarshalling = parametersProviderFactory.sharedParametersProvider(xml.getBytes(UTF_8)).getSharedParameters();

        assertThat(initial)
                .usingRecursiveComparison()
                .isEqualTo(afterMarshalling);

    }

    @Test
    void shouldCarryPublishedValuesThroughMarshalling() throws Exception {
        var initial = readV7(FileUtils.readFileToByteArray(Paths.get(V7_FIXTURE).toFile()));
        assertPublishedValuesOfFixture(initial);

        var xml = new SharedParametersV7Marshaller().marshall(initial);
        var afterMarshalling = readV7(xml.getBytes(UTF_8));

        assertPublishedValuesOfFixture(afterMarshalling);
        assertThat(afterMarshalling).usingRecursiveComparison().isEqualTo(initial);
    }

    @Test
    void shouldReadVersion7WithoutPublishedValues() throws Exception {
        var withoutPublishedValues = FileUtils.readFileToString(Paths.get(V7_FIXTURE).toFile(), UTF_8)
                .replaceAll("\\s*<did securityServer=\"[^\"]*\">[^<]*</did>", "")
                .replaceAll("\\s*<dspBaseUrl>[^<]*</dspBaseUrl>", "")
                .replaceAll("\\s*<systemDid>[^<]*</systemDid>", "")
                .replaceAll("<securityServer id=\"[^\"]*\">", "<securityServer>");
        assertThat(withoutPublishedValues).doesNotContain("<did securityServer", "dspBaseUrl", "systemDid", "<securityServer id");

        var parameters = readV7(withoutPublishedValues.getBytes(UTF_8));

        assertThat(parameters.getMembers()).isNotEmpty().allSatisfy(member -> assertThat(member.getDids()).isEmpty());
        assertThat(parameters.getSecurityServers()).isNotEmpty().allSatisfy(server -> {
            assertThat(server.getSystemDid()).isNull();
            assertThat(server.getDspBaseUrl()).isNull();
        });

        var xml = new SharedParametersV7Marshaller().marshall(parameters);

        assertThat(readV7(xml.getBytes(UTF_8))).usingRecursiveComparison().isEqualTo(parameters);
    }

    private static SharedParameters readV7(byte[] content) throws IOException {
        return new SharedParametersV7(content).getSharedParameters();
    }

    private static void assertPublishedValuesOfFixture(SharedParameters parameters) {
        Map<String, SharedParameters.Member> membersByCode = parameters.getMembers().stream()
                .collect(Collectors.toMap(SharedParameters.Member::getMemberCode, Function.identity()));
        Map<String, SharedParameters.SecurityServer> serversByCode = parameters.getSecurityServers().stream()
                .collect(Collectors.toMap(SharedParameters.SecurityServer::getServerCode, Function.identity()));

        assertThat(membersByCode.get("producer").getDids()).extracting(SharedParameters.MemberDid::did)
                .containsExactly("did:web:127.0.0.1%3A7183:v1:EE:BUSINESS:producer",
                        "did:web:127.0.0.1%3A7283:v1:EE:BUSINESS:producer");
        assertThat(membersByCode.get("producer").getDids()).extracting(did -> did.serverId().getServerCode())
                .containsExactly("producerServerCode", "producerServerCode2");
        assertThat(membersByCode.get("foo").getDids()).singleElement().satisfies(did -> {
            assertThat(did.did()).isEqualTo(UNKNOWN_SCHEME_VERSION_DID);
            assertThat(did.serverId().getServerCode()).isEqualTo("FooBarServerCode");
        });

        assertThat(serversByCode.get("producerServerCode").getSystemDid())
                .isEqualTo("did:web:127.0.0.1%3A7183:v1:system");
        assertThat(serversByCode.get("producerServerCode").getDspBaseUrl())
                .isEqualTo("https://127.0.0.1:8183/api/dsp");
        assertThat(serversByCode.get("producerServerCode2").getDspBaseUrl())
                .isEqualTo("https://127.0.0.1:8283/api/dsp");
        assertThat(serversByCode.get("consumerServerCode").getSystemDid()).isNull();
        assertThat(serversByCode.get("consumerServerCode").getDspBaseUrl()).isNull();
        assertThat(List.of(serversByCode.get("producerServerCode"), serversByCode.get("producerServerCode2")))
                .extracting(SharedParameters.SecurityServer::getAddress).containsOnly("127.0.0.1");
    }
}
