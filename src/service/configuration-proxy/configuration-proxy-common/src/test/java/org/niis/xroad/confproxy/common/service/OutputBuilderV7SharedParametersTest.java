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
package org.niis.xroad.confproxy.common.service;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.globalconf.model.ConfigurationConstants;
import org.niis.xroad.globalconf.model.ConfigurationDirectory;
import org.niis.xroad.globalconf.model.ConfigurationPartMetadata;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.globalconf.model.SharedParametersV7;
import org.niis.xroad.globalconf.model.VersionedConfigurationDirectory;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The configuration proxy re-writes the version 7 shared parameters of the main instance. Ids are
 * reassigned on re-write, so the re-written document is compared with the source by model values, not by bytes.
 */
@ExtendWith(MockitoExtension.class)
class OutputBuilderV7SharedParametersTest {

    private static final String GOOD_CONF_DIR = "../../../lib/globalconf-core/src/test/resources/globalconf_good_v7";
    private static final String PROXY_INSTANCE = "PROXY1";
    private static final String MAIN_INSTANCE = "EE";
    private static final String SHARED_PARAMETERS_FILE = "shared-params.xml";
    private static final String SIGNER_UNREACHABLE = "Signer is unreachable";

    @TempDir
    Path tempBase;

    @Mock
    SignerRpcClient signerRpcClient;
    @Mock
    SignerSignClient signerSignClient;
    @Mock
    ConfProxyInstance proxyInstance;

    private byte[] sourceBytes;
    private SharedParameters source;

    @BeforeEach
    void setUp() throws Exception {
        sourceBytes = Files.readAllBytes(Path.of(GOOD_CONF_DIR, MAIN_INSTANCE, SHARED_PARAMETERS_FILE));
        source = new SharedParametersV7(sourceBytes).getSharedParameters();

        when(proxyInstance.getInstance()).thenReturn(PROXY_INSTANCE);
        when(proxyInstance.getValidityIntervalSeconds()).thenReturn(600);
        when(proxyInstance.getVerificationCerts()).thenReturn(source.getSources().getFirst().getInternalVerificationCerts());
        when(signerRpcClient.getSignMechanism(any())).thenThrow(XrdRuntimeException.systemInternalError(SIGNER_UNREACHABLE));
    }

    @Test
    void rewrittenSharedParametersCarryPublishedValues() throws Exception {
        var rewritten = rewriteMainInstanceSharedParameters();

        assertThat(rewritten.getSecurityServers()).hasSameSizeAs(source.getSecurityServers());
        assertThat(rewritten.getMembers()).hasSameSizeAs(source.getMembers());
        assertThat(rewritten.getSecurityServers()).usingRecursiveComparison().isEqualTo(source.getSecurityServers());
        assertThat(rewritten.getMembers()).usingRecursiveComparison().isEqualTo(source.getMembers());
        assertThat(rewritten.getSecurityServers().stream().map(SharedParameters.SecurityServer::getSystemDid).toList())
                .isEqualTo(source.getSecurityServers().stream().map(SharedParameters.SecurityServer::getSystemDid).toList());
        assertThat(rewritten.getSecurityServers().stream().map(SharedParameters.SecurityServer::getDspBaseUrl).toList())
                .isEqualTo(source.getSecurityServers().stream().map(SharedParameters.SecurityServer::getDspBaseUrl).toList());
        assertThat(rewritten.getMembers().stream().map(SharedParameters.Member::getDids).toList())
                .isEqualTo(source.getMembers().stream().map(SharedParameters.Member::getDids).toList());
    }

    @Test
    void didWithSchemeVersionV2PassesThroughUnchanged() throws Exception {
        var rewritten = rewriteMainInstanceSharedParameters();

        assertThat(didsOf(rewritten, "foo")).extracting(SharedParameters.MemberDid::did)
                .containsExactly("did:web:foo.bar.baz%3A7183:v2:EE:BUSINESS:foo");
    }

    @Test
    void serversWithoutSystemValuesStayWithoutThemAfterRewrite() throws Exception {
        var rewritten = rewriteMainInstanceSharedParameters();

        var withoutValues = rewritten.getSecurityServers().stream()
                .filter(s -> s.getSystemDid() == null && s.getDspBaseUrl() == null)
                .map(SharedParameters.SecurityServer::getServerCode)
                .toList();
        assertThat(withoutValues).containsExactlyInAnyOrder("consumerServerCode", "fooServerCode");
        assertThat(rewritten.getSecurityServers()).hasSize(5);
    }

    private static List<SharedParameters.MemberDid> didsOf(SharedParameters parameters, String memberCode) {
        return parameters.getMembers().stream()
                .filter(m -> memberCode.equals(m.getMemberCode()))
                .flatMap(m -> m.getDids().stream())
                .toList();
    }

    private VersionedConfigurationDirectory confDirWithSourceSharedParameters() throws Exception {
        Path confRoot = tempBase.resolve("conf");
        ConfigurationDirectory.saveInstanceIdentifier(confRoot.toString(), MAIN_INSTANCE);

        var metadata = new ConfigurationPartMetadata();
        metadata.setContentIdentifier(ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS);
        metadata.setInstanceIdentifier(MAIN_INSTANCE);
        metadata.setContentLocation("/" + SHARED_PARAMETERS_FILE);
        metadata.setExpirationDate(OffsetDateTime.now().plusHours(1));
        metadata.setConfigurationVersion("7");
        ConfigurationDirectory.save(confRoot.resolve(MAIN_INSTANCE).resolve(SHARED_PARAMETERS_FILE), sourceBytes, metadata);

        return new VersionedConfigurationDirectory(confRoot.toString());
    }

    private SharedParameters rewriteMainInstanceSharedParameters() throws Exception {
        var confDir = confDirWithSourceSharedParameters();

        try (var output = OutputBuilder.build(confDir, 7, proxyInstance, "address",
                DigestAlgorithm.SHA512, DigestAlgorithm.SHA512, tempBase.toString())) {
            assertThatThrownBy(() -> output.buildSignedDirectory(signerRpcClient, signerSignClient))
                    .isInstanceOf(XrdRuntimeException.class)
                    .hasMessageContaining(SIGNER_UNREACHABLE);

            try (Stream<Path> files = Files.walk(tempBase.resolve(PROXY_INSTANCE))) {
                var written = files
                        .filter(p -> SHARED_PARAMETERS_FILE.equals(p.getFileName().toString()))
                        .filter(p -> MAIN_INSTANCE.equals(p.getParent().getFileName().toString()))
                        .toList();
                assertThat(written).hasSize(1);
                return new SharedParametersV7(Files.readAllBytes(written.getFirst())).getSharedParameters();
            }
        }
    }
}
