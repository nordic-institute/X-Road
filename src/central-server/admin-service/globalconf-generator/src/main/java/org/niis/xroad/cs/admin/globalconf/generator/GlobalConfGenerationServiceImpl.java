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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.commonui.OptionalConfPart;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.domain.DistributedFile;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.ConfigurationService;
import org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService;
import org.niis.xroad.cs.admin.api.service.GlobalConfGenerationService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static ee.ria.xroad.common.SystemProperties.getCenterExternalDirectory;
import static ee.ria.xroad.common.SystemProperties.getCenterInternalDirectory;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS;
import static ee.ria.xroad.commonui.OptionalPartsConf.getOptionalPartsConf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService.SOURCE_TYPE_EXTERNAL;
import static org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService.SOURCE_TYPE_INTERNAL;
import static org.niis.xroad.cs.admin.globalconf.generator.GlobalConfGenerationEvent.FAILURE;
import static org.niis.xroad.cs.admin.globalconf.generator.GlobalConfGenerationEvent.SUCCESS;

@Component
@Slf4j
@RequiredArgsConstructor
public class GlobalConfGenerationServiceImpl implements GlobalConfGenerationService {
    private static final int OLD_CONF_PRESERVING_SECONDS = 600;

    private static final Set<String> EXTERNAL_SOURCE_CONTENT_IDENTIFIERS = Set.of(
            CONTENT_ID_SHARED_PARAMETERS);
    private static final Set<String> INTERNAL_SOURCE_REQUIRED_CONTENT_IDENTIFIERS = Set.of(
            CONTENT_ID_PRIVATE_PARAMETERS,
            CONTENT_ID_SHARED_PARAMETERS);

    private final SignerProxyFacade signerProxyFacade;
    private final SystemParameterService systemParameterService;
    private final ConfigurationService configurationService;
    private final ConfigurationSigningKeysService configurationSigningKeysService;
    private final ApplicationEventPublisher eventPublisher;

    private final List<ConfigurationPartsGenerator> configurationPartsGenerators;

    @SneakyThrows
    @Override
    @Transactional
    @Scheduled(fixedRateString = "${xroad.admin-service.global-configuration-generation-rate-in-seconds}", timeUnit = SECONDS)
    public void generate() {
        configurationPartsGenerators.forEach(configurationPartsGenerator -> {
            int confVersion = configurationPartsGenerator.getConfigurationVersion();
            if (confVersion < SystemProperties.getMinimumCentralServerGlobalConfigurationVersion()) {
                return;
            }

            var success = false;
            try {
                log.debug("Starting global conf V{} generation", confVersion);

                var configurationParts = configurationPartsGenerator.generateConfigurationParts();
                configurationParts.forEach(gp -> configurationService
                        .saveConfigurationPart(gp.getContentIdentifier(), gp.getFilename(), gp.getData(), confVersion));
                var configGenerationTime = TimeUtils.now();

                var allConfigurationParts = toConfigurationParts(configurationService.getAllConfigurationFiles(confVersion));
                var internalConfigurationParts = internalConfigurationParts(allConfigurationParts);
                var externalConfigurationParts = externalConfigurationParts(allConfigurationParts);

                var generatedConfDir = Path.of(SystemProperties.getCenterGeneratedConfDir());
                var configDistributor = new ConfigurationDistributor(generatedConfDir, confVersion, configGenerationTime);
                configDistributor.initConfLocation();
                configDistributor.writeConfigurationFiles(allConfigurationParts);

                var internalSigningKey = configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_INTERNAL).orElseThrow();
                var externalSigningKey = configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_EXTERNAL).orElseThrow();

                writeDirectoryContentFile(configDistributor, internalConfigurationParts, internalSigningKey, getTmpInternalDirectory());
                writeDirectoryContentFile(configDistributor, externalConfigurationParts, externalSigningKey, getTmpExternalDirectory());

                configDistributor.moveDirectoryContentFile(getTmpInternalDirectory(), getCenterInternalDirectory());
                configDistributor.moveDirectoryContentFile(getTmpExternalDirectory(), getCenterExternalDirectory());

                cleanUpOldConfigurations(generatedConfDir.resolve(configDistributor.getVersionSubPath()));

                writeLocalCopy(confVersion, allConfigurationParts);

                log.debug("Global conf generated");
                success = true;
            } finally {
                eventPublisher.publishEvent(success ? SUCCESS : FAILURE);
            }
        });
    }

    @SneakyThrows
    private static boolean isExpiredConfDir(Path dirPath) {
        return Files.isDirectory(dirPath)
                && dirPath.getFileName().toString().matches("\\A\\d+\\z")
                && Files.getLastModifiedTime(dirPath).toInstant().isBefore(
                TimeUtils.now().minusSeconds(OLD_CONF_PRESERVING_SECONDS));
    }

    @SneakyThrows
    private static void deleteExpiredConfigDir(Path dirPath) {
        log.trace("Deleting expired global configuration directory {}", dirPath);
        FileUtils.deleteDirectory(dirPath.toFile());
    }

    @SneakyThrows
    private void cleanUpOldConfigurations(Path versionDir) {
        try (var filesStream = Files.list(versionDir)) {
            filesStream
                    .filter(GlobalConfGenerationServiceImpl::isExpiredConfDir)
                    .forEach(GlobalConfGenerationServiceImpl::deleteExpiredConfigDir);
        }
    }

    private static String getTmpExternalDirectory() {
        return getCenterExternalDirectory() + ".tmp";
    }

    private static String getTmpInternalDirectory() {
        return getCenterInternalDirectory() + ".tmp";
    }

    private static Set<ConfigurationPart> internalConfigurationParts(Set<ConfigurationPart> configurationParts) {
        var contentIdentifiers = getInternalSourceContentIdentifiers();
        return configurationParts.stream()
                .filter(cp -> contentIdentifiers.contains(cp.getContentIdentifier()))
                .collect(toSet());
    }

    private static Set<ConfigurationPart> externalConfigurationParts(Set<ConfigurationPart> configurationParts) {
        return configurationParts.stream()
                .filter(cp -> EXTERNAL_SOURCE_CONTENT_IDENTIFIERS.contains(cp.getContentIdentifier()))
                .collect(toSet());
    }

    private void writeDirectoryContentFile(ConfigurationDistributor configDistributor,
                                           Set<ConfigurationPart> configurationParts,
                                           ConfigurationSigningKey signingKey,
                                           String fileName) {
        String signedDirectory = createSignedDirectory(configDistributor, configurationParts, signingKey);
        configDistributor.writeDirectoryContentFile(fileName, signedDirectory.getBytes(UTF_8));
    }

    private String createSignedDirectory(ConfigurationDistributor configDistributor, Set<ConfigurationPart> configurationParts,
                                         ConfigurationSigningKey signingKey) {
        var directoryContentBuilder = new DirectoryContentBuilder(
                getConfHashAlgoId(),
                TimeUtils.now().plusSeconds(systemParameterService.getConfExpireIntervalSeconds()),
                "/" + configDistributor.getSubPath().toString(),
                systemParameterService.getInstanceIdentifier(),
                configDistributor.getVersion())
                .contentParts(configurationParts);
        var directoryContent = directoryContentBuilder.build();

        var directoryContentSigner = new DirectoryContentSigner(
                signerProxyFacade,
                systemParameterService
                        .getConfSignDigestAlgoId(),
                getConfSignCertHashAlgoId());

        return directoryContentSigner.createSignedDirectory(directoryContent, signingKey.getKeyIdentifier(), signingKey.getCert());
    }

    private Set<ConfigurationPart> toConfigurationParts(Set<DistributedFile> configurationFiles) {
        return configurationFiles
                .stream().map(this::toConfigurationPart)
                .collect(toSet());
    }

    private ConfigurationPart toConfigurationPart(DistributedFile df) {
        return ConfigurationPart.builder().filename(df.getFileName())
                .contentIdentifier(df.getContentIdentifier())
                .data(df.getFileData())
                .build();
    }

    @SneakyThrows
    private String getConfSignCertHashAlgoId() {
        return CryptoUtils.getAlgorithmId(systemParameterService.getConfSignCertHashAlgoUri());
    }

    @SneakyThrows
    private String getConfHashAlgoId() {
        return CryptoUtils.getAlgorithmId(systemParameterService.getConfHashAlgoUri());
    }

    private void writeLocalCopy(int configurationVersion, Set<ConfigurationPart> allConfigurationParts) {
        new LocalCopyWriter(configurationVersion,
                systemParameterService.getInstanceIdentifier(),
                Path.of(SystemProperties.getConfigurationPath()),
                TimeUtils.now().plusSeconds(systemParameterService.getConfExpireIntervalSeconds())
        )
                .write(allConfigurationParts);
    }

    private static Set<String> getInternalSourceContentIdentifiers() {
        return concat(INTERNAL_SOURCE_REQUIRED_CONTENT_IDENTIFIERS.stream(),
                getOptionalPartsConf().getAllParts().stream()
                        .map(OptionalConfPart::getContentIdentifier))
                .collect(toSet());
    }

}
