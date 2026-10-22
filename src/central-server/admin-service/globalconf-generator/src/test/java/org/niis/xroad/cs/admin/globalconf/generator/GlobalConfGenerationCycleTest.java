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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CsAdminServiceConfigKeys;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType;
import org.niis.xroad.cs.admin.api.domain.DistributedFile;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.CertificationServicesService;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.api.service.ConfigurationService;
import org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerDidService;
import org.niis.xroad.cs.admin.api.service.DataspaceMemberDidService;
import org.niis.xroad.cs.admin.api.service.DsTlsCertificationAuthoritiesService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupService;
import org.niis.xroad.cs.admin.api.service.ManagementServiceTlsCertificateService;
import org.niis.xroad.cs.admin.api.service.MemberClassService;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.api.service.TimestampingServicesService;
import org.niis.xroad.cs.admin.api.service.TrustedAnchorService;
import org.niis.xroad.globalconf.impl.config.GlobalConfProperties;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA512;
import static ee.ria.xroad.common.crypto.identifier.SignMechanism.CKM_RSA_PKCS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService.SOURCE_TYPE_EXTERNAL;
import static org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService.SOURCE_TYPE_INTERNAL;
import static org.niis.xroad.cs.admin.globalconf.generator.GlobalConfGenerationEvent.FAILURE;
import static org.niis.xroad.cs.admin.globalconf.generator.GlobalConfGenerationEvent.SUCCESS;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS;

/**
 * Runs whole global configuration generation cycles on the same service instances: real generation service,
 * parts generators, loaders, marshallers and file writers. Services backed by the database are replaced
 * with in-memory fakes whose state survives between cycles.
 */
class GlobalConfGenerationCycleTest {
    private static final String INSTANCE = "XRD";
    private static final String LEGACY_PROTOCOL_DISABLED = "legacyProtocolDisabled";
    private static final String LEGACY_PROTOCOL_DISABLED_TRUE = "<legacyProtocolDisabled>true</legacyProtocolDisabled>";
    private static final int FIRST_VERSION = 2;
    private static final int CURRENT_VERSION = 7;
    private static final String STALE_DIRECTORY = "20200101000000000000000";

    @TempDir
    private Path tempDir;

    private final Map<String, String> systemParameters = new HashMap<>();
    private final Map<Integer, Map<String, DistributedFile>> storedParts = new HashMap<>();
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private Path generatedConfDir;

    @BeforeEach
    void setUp() {
        generatedConfDir = tempDir.resolve("generated");
    }

    @Test
    void versionSevenHasNoElementWhenNoValueIsStored() throws IOException {
        var service = createService(null);

        service.generate();

        verify(eventPublisher).publishEvent(SUCCESS);
        assertThat(sharedParameters(CURRENT_VERSION)).doesNotContain(LEGACY_PROTOCOL_DISABLED);
    }

    @Test
    void versionSevenHasNoElementWhenValueIsFalse() throws IOException {
        systemParameters.put(LEGACY_PROTOCOL_DISABLED, "false");
        var service = createService(null);

        service.generate();

        verify(eventPublisher).publishEvent(SUCCESS);
        assertThat(sharedParameters(CURRENT_VERSION)).doesNotContain(LEGACY_PROTOCOL_DISABLED);
    }

    @Test
    void valueChangesAreCarriedOnTheNextCycleOnTheSameInstances() throws IOException {
        var service = createService(null);

        service.generate();
        assertThat(sharedParameters(CURRENT_VERSION)).doesNotContain(LEGACY_PROTOCOL_DISABLED);

        systemParameters.put(LEGACY_PROTOCOL_DISABLED, "true");
        service.generate();
        assertThat(sharedParameters(CURRENT_VERSION)).contains(LEGACY_PROTOCOL_DISABLED_TRUE);

        systemParameters.remove(LEGACY_PROTOCOL_DISABLED);
        service.generate();
        assertThat(sharedParameters(CURRENT_VERSION)).doesNotContain(LEGACY_PROTOCOL_DISABLED);

        verify(eventPublisher, times(3)).publishEvent(SUCCESS);
        verify(eventPublisher, never()).publishEvent(FAILURE);
    }

    @Test
    void olderVersionsNeverContainTheElement() throws IOException {
        systemParameters.put(LEGACY_PROTOCOL_DISABLED, "true");
        var service = createService(null);

        service.generate();

        assertThat(sharedParameters(CURRENT_VERSION)).contains(LEGACY_PROTOCOL_DISABLED_TRUE);
        for (int version = FIRST_VERSION; version < CURRENT_VERSION; version++) {
            assertThat(sharedParameters(version)).as("V%d", version).doesNotContain(LEGACY_PROTOCOL_DISABLED);
        }
    }

    @Test
    void minimumVersionSevenWritesOnlyVersionSevenAndLeavesOlderDirectoriesUntouched() throws IOException {
        for (int version = FIRST_VERSION; version < CURRENT_VERSION; version++) {
            var staleFile = generatedConfDir.resolve("V" + version).resolve(STALE_DIRECTORY).resolve("marker");
            Files.createDirectories(staleFile.getParent());
            Files.writeString(staleFile, "kept");
        }
        var service = createService(CURRENT_VERSION);

        service.generate();

        verify(eventPublisher).publishEvent(SUCCESS);
        assertDirectoryContentFilesWritten(CURRENT_VERSION);
        for (int version = FIRST_VERSION; version < CURRENT_VERSION; version++) {
            var versionDir = generatedConfDir.resolve("V" + version);
            assertThat(Files.readString(versionDir.resolve(STALE_DIRECTORY).resolve("marker"))).isEqualTo("kept");
            assertThat(children(versionDir)).containsExactly(STALE_DIRECTORY);
        }
    }

    @Test
    void defaultMinimumVersionWritesAllVersions() throws IOException {
        var service = createService(null);

        service.generate();

        verify(eventPublisher).publishEvent(SUCCESS);
        for (int version = FIRST_VERSION; version <= CURRENT_VERSION; version++) {
            assertDirectoryContentFilesWritten(version);
        }
    }

    private void assertDirectoryContentFilesWritten(int version) throws IOException {
        var versionDir = generatedConfDir.resolve("V" + version);
        assertThat(children(versionDir)).as("V%d", version).contains("internalconf", "externalconf");
        assertThat(Files.readString(versionDir.resolve("internalconf"))).contains("Version: " + version);
        assertThat(Files.readString(versionDir.resolve("externalconf"))).contains("Version: " + version);
    }

    private String sharedParameters(int version) throws IOException {
        var versionDir = generatedConfDir.resolve("V" + version);
        var latest = children(versionDir).stream()
                .filter(name -> name.matches("\\d+"))
                .max(String::compareTo)
                .orElseThrow();
        return Files.readString(versionDir.resolve(latest).resolve(FILE_NAME_SHARED_PARAMETERS), UTF_8);
    }

    private static List<String> children(Path dir) throws IOException {
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.map(path -> path.getFileName().toString()).toList();
        }
    }

    private GlobalConfGenerationServiceImpl createService(Integer minimumVersion) {
        var overrides = new HashMap<String, String>();
        overrides.put(CsAdminServiceConfigKeys.GLOBAL_CONF_GENERATOR_GENERATED_CONF_DIR.key(), generatedConfDir.toString());
        if (minimumVersion != null) {
            overrides.put(CsAdminServiceConfigKeys.GLOBAL_CONF_GENERATOR_MINIMUM_GLOBAL_CONFIGURATION_VERSION.key(),
                    String.valueOf(minimumVersion));
        }
        XRoadConfig xRoadConfig = XRoadConfigBuilder.create()
                .register(CsAdminServiceConfigKeys.instance())
                .overrides(overrides)
                .build();

        var systemParameterService = mapBackedSystemParameterService();
        var configurationService = inMemoryConfigurationService();
        var sharedParametersLoader = new SharedParametersLoader(systemParameterService,
                mock(CertificationServicesService.class), mock(TimestampingServicesService.class),
                mock(DsTlsCertificationAuthoritiesService.class), mock(DataspaceIssuerDidService.class),
                mock(DataspaceMemberDidService.class),
                mock(ClientService.class), mock(SecurityServerService.class), mock(GlobalGroupService.class),
                mock(GlobalGroupMemberService.class), mock(MemberClassService.class), configurationService);
        var privateParametersLoader = new PrivateParametersLoader(systemParameterService,
                mock(TrustedAnchorService.class), managementServiceTlsCertificateService());
        var privateParametersV2Generator = new PrivateParametersV2Generator(privateParametersLoader);
        var privateParametersV3Generator = new PrivateParametersV3Generator(privateParametersLoader);

        var generators = List.<ConfigurationPartsGenerator>of(
                new V2ConfigurationPartsGenerator(privateParametersV2Generator,
                        new SharedParametersV2Generator(sharedParametersLoader)),
                new V3ConfigurationsPartsGenerator(privateParametersV3Generator,
                        new SharedParametersV3Generator(sharedParametersLoader)),
                new V4ConfigurationsPartsGenerator(privateParametersV3Generator,
                        new SharedParametersV4Generator(sharedParametersLoader)),
                new V5ConfigurationsPartsGenerator(privateParametersV3Generator,
                        new SharedParametersV5Generator(sharedParametersLoader)),
                new V6ConfigurationsPartsGenerator(privateParametersV3Generator,
                        new SharedParametersV6Generator(sharedParametersLoader)),
                new V7ConfigurationsPartsGenerator(privateParametersV3Generator,
                        new SharedParametersV7Generator(sharedParametersLoader)));

        return new GlobalConfGenerationServiceImpl(signerProxyFacade(), systemParameterService, configurationService,
                signingKeysService(), eventPublisher, new GlobalConfGenerationProperties(xRoadConfig),
                globalConfProperties(), generators);
    }

    private SystemParameterService mapBackedSystemParameterService() {
        var service = mock(SystemParameterService.class);
        when(service.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(service.getConfSignCertHashAlgoUri()).thenReturn(SHA512);
        when(service.getConfSignDigestAlgoId()).thenReturn(SHA512);
        when(service.getConfHashAlgoUri()).thenReturn(SHA512);
        when(service.getConfExpireIntervalSeconds()).thenReturn(600);
        when(service.getTimeStampingIntervalSeconds()).thenReturn(60);
        when(service.getOcspFreshnessSeconds()).thenReturn(3600);
        when(service.getAuthCertRegUrl()).thenReturn("https://cs.example:4001/managementservice/");
        when(service.getManagementServiceProviderId()).thenReturn(ClientId.Conf.create(INSTANCE, "GOV", "MGMT"));
        when(service.isLegacyProtocolDisabled())
                .thenAnswer(invocation -> Boolean.parseBoolean(systemParameters.get(LEGACY_PROTOCOL_DISABLED)));
        return service;
    }

    private ConfigurationService inMemoryConfigurationService() {
        var service = mock(ConfigurationService.class);
        var signingKeys = List.of(
                signingKey(ConfigurationSourceType.INTERNAL),
                signingKey(ConfigurationSourceType.EXTERNAL));
        when(service.getNodeAddressesWithOrderedConfigurationSigningKeys()).thenReturn(Map.of("cs", signingKeys));
        doAnswer(invocation -> {
            var part = new DistributedFile();
            part.setContentIdentifier(invocation.getArgument(0));
            part.setFileName(invocation.getArgument(1));
            part.setFileData(invocation.getArgument(2));
            part.setVersion(invocation.getArgument(3));
            storedParts.computeIfAbsent(part.getVersion(), version -> new HashMap<>()).put(part.getContentIdentifier(), part);
            return null;
        }).when(service).saveConfigurationPart(anyString(), anyString(), any(byte[].class), anyInt());
        when(service.getAllConfigurationFiles(anyInt())).thenAnswer(invocation ->
                Set.copyOf(storedParts.getOrDefault(invocation.<Integer>getArgument(0), Map.of()).values()));
        return service;
    }

    private static ConfigurationSigningKey signingKey(ConfigurationSourceType sourceType) {
        return new ConfigurationSigningKey()
                .setKeyIdentifier("key-" + sourceType)
                .setCert(("cert-" + sourceType).getBytes(UTF_8))
                .setSourceType(sourceType)
                .setActiveSourceSigningKey(true);
    }

    private static ConfigurationSigningKeysService signingKeysService() {
        var service = mock(ConfigurationSigningKeysService.class);
        when(service.findActiveForSource(SOURCE_TYPE_INTERNAL))
                .thenReturn(Optional.of(signingKey(ConfigurationSourceType.INTERNAL)));
        when(service.findActiveForSource(SOURCE_TYPE_EXTERNAL))
                .thenReturn(Optional.of(signingKey(ConfigurationSourceType.EXTERNAL)));
        return service;
    }

    private static SignerProxyFacade signerProxyFacade() {
        var facade = mock(SignerProxyFacade.class);
        when(facade.getSignMechanism(anyString())).thenReturn(CKM_RSA_PKCS);
        when(facade.sign(anyString(), any(), any())).thenReturn("signature".getBytes(UTF_8));
        return facade;
    }

    private static ManagementServiceTlsCertificateService managementServiceTlsCertificateService() {
        try {
            var certificate = mock(X509Certificate.class);
            when(certificate.getEncoded()).thenReturn("tls-cert".getBytes(UTF_8));
            var service = mock(ManagementServiceTlsCertificateService.class);
            when(service.getTlsCertificate()).thenReturn(certificate);
            return service;
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private GlobalConfProperties globalConfProperties() {
        var localCopyDir = tempDir.resolve("globalconf").toString();
        return new GlobalConfProperties() {
            @Override
            public GlobalConfSource source() {
                return GlobalConfSource.FILESYSTEM;
            }

            @Override
            public Duration refreshRate() {
                return Duration.ofSeconds(60);
            }

            @Override
            public String configurationPath() {
                return localCopyDir;
            }
        };
    }
}
