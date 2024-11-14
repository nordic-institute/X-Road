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
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.signer.SignerRpcClient;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.domain.DistributedFile;
import org.niis.xroad.cs.admin.api.dto.AlertInfo;
import org.niis.xroad.cs.admin.api.dto.OptionalConfPart;
import org.niis.xroad.cs.admin.api.dto.TokenInitStatus;
import org.niis.xroad.cs.admin.api.globalconf.OptionalPartsConf;
import org.niis.xroad.cs.admin.api.service.ConfigurationService;
import org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService;
import org.niis.xroad.cs.admin.api.service.GlobalConfGenerationService;
import org.niis.xroad.cs.admin.api.service.InitializationService;
import org.niis.xroad.cs.admin.api.service.NotificationService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService.SOURCE_TYPE_EXTERNAL;
import static org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService.SOURCE_TYPE_INTERNAL;
import static org.niis.xroad.cs.admin.globalconf.generator.GlobalConfApplier.getTmpExternalDirectory;
import static org.niis.xroad.cs.admin.globalconf.generator.GlobalConfApplier.getTmpInternalDirectory;
import static org.niis.xroad.cs.admin.globalconf.generator.GlobalConfGenerationEvent.FAILURE;
import static org.niis.xroad.cs.admin.globalconf.generator.GlobalConfGenerationEvent.SUCCESS;

@Component
@Slf4j
@RequiredArgsConstructor
public class GlobalConfGenerationServiceImpl implements GlobalConfGenerationService, InitializingBean, DisposableBean {

    private static final Set<String> EXTERNAL_SOURCE_CONTENT_IDENTIFIERS = Set.of(
            CONTENT_ID_SHARED_PARAMETERS);
    private static final Set<String> INTERNAL_SOURCE_REQUIRED_CONTENT_IDENTIFIERS = Set.of(
            CONTENT_ID_PRIVATE_PARAMETERS,
            CONTENT_ID_SHARED_PARAMETERS);

    private final SignerRpcClient signerRpcClient;
    private final SystemParameterService systemParameterService;
    private final ConfigurationService configurationService;
    private final ConfigurationSigningKeysService configurationSigningKeysService;
    private final ApplicationEventPublisher eventPublisher;
    private final List<ConfigurationPartsGenerator> configurationPartsGenerators;
    private final InitializationService initializationService;
    private final NotificationService notificationService;

    private final ScheduledExecutorService taskScheduler = Executors.newSingleThreadScheduledExecutor();

    @Value("${xroad.admin-service.global-configuration-generation-rate-in-seconds}")
    private int globalConfigurationGenerationRateInSeconds;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Scheduling global conf generation every {} seconds", globalConfigurationGenerationRateInSeconds);
        taskScheduler.scheduleWithFixedDelay(this::generate, globalConfigurationGenerationRateInSeconds,
                globalConfigurationGenerationRateInSeconds, SECONDS);
    }

    @Override
    public void destroy() throws Exception {
        taskScheduler.shutdown();
    }

    @Override
    @Transactional
    public void generate() {
        if (!canGenerateGlobalConf()) {
            log.info("Server configuration is not complete, skipping global conf generation.");
            // publish the FAILURE to display the warning in the UI
            eventPublisher.publishEvent(FAILURE);
            return;
        }

        final var results = configurationPartsGenerators.stream()
                .map(this::generate)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        if (results.isEmpty()) {
            log.debug("No global conf generation was done");
            return;
        }

        if (results.stream().allMatch(Result::success)) {
            results.stream()
                    .map(Result::applier)
                    .forEach(GlobalConfApplier::apply);
            eventPublisher.publishEvent(SUCCESS);
        } else {
            results.stream()
                    .map(Result::applier)
                    .forEach(GlobalConfApplier::rollback);
            eventPublisher.publishEvent(FAILURE);
        }
    }

    private Optional<Result> generate(ConfigurationPartsGenerator generator) {
        int confVersion = generator.getConfigurationVersion();
        if (confVersion < SystemProperties.getMinimumCentralServerGlobalConfigurationVersion()) {
            return Optional.empty();
        }

        var configGenerationTime = TimeUtils.now();
        var generatedConfDir = Path.of(SystemProperties.getCenterGeneratedConfDir());
        var configDistributor = new ConfigurationDistributor(generatedConfDir, confVersion, configGenerationTime);
        var globalConfApplier = new GlobalConfApplier(confVersion, configDistributor, systemParameterService);

        try {
            log.debug("Starting global conf V{} generation", confVersion);

            var configurationParts = generator.generateConfigurationParts();
            configurationParts.forEach(gp -> configurationService
                    .saveConfigurationPart(gp.getContentIdentifier(), gp.getFilename(), gp.getData(), confVersion));

            var allConfigurationParts = toConfigurationParts(configurationService.getAllConfigurationFiles(confVersion));
            globalConfApplier.addConfigurationParts(allConfigurationParts);

            var internalConfigurationParts = internalConfigurationParts(allConfigurationParts);
            var externalConfigurationParts = externalConfigurationParts(allConfigurationParts);

            configDistributor.initConfLocation();
            configDistributor.writeConfigurationFiles(allConfigurationParts);

            var internalSigningKey = configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_INTERNAL).orElseThrow();
            var externalSigningKey = configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_EXTERNAL).orElseThrow();

            writeDirectoryContentFile(configDistributor, internalConfigurationParts, internalSigningKey, getTmpInternalDirectory());
            writeDirectoryContentFile(configDistributor, externalConfigurationParts, externalSigningKey, getTmpExternalDirectory());

            log.debug("Global conf generated");
            return Optional.of(new Result(true, globalConfApplier));
        } catch (Exception e) {
            log.error("Global conf generation failed", e);
            return Optional.of(new Result(false, globalConfApplier));
        }
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
                signerRpcClient,
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

    private DigestAlgorithm getConfSignCertHashAlgoId() {
        return systemParameterService.getConfSignCertHashAlgoUri();
    }

    private DigestAlgorithm getConfHashAlgoId() {
        return systemParameterService.getConfHashAlgoUri();
    }

    private static Set<String> getInternalSourceContentIdentifiers() {
        return concat(INTERNAL_SOURCE_REQUIRED_CONTENT_IDENTIFIERS.stream(),
                OptionalPartsConf.getOptionalPartsConf().getAllParts().stream()
                        .map(OptionalConfPart::contentIdentifier))
                .collect(toSet());
    }

    private boolean canGenerateGlobalConf() {
        var status = initializationService.getInitializationStatus();

        boolean canGenerate =
                // check server is initialized (similar check on the UI side, system.ts:isServerInitialized())
                StringUtils.isNotBlank(status.getInstanceIdentifier())
                        && StringUtils.isNotBlank(status.getCentralServerAddress())
                        && status.getSoftwareTokenInitStatus() != TokenInitStatus.NOT_INITIALIZED
                        // check management service is set
                        && StringUtils.isNotBlank(systemParameterService.getAuthCertRegUrl())
                        && systemParameterService.getManagementServiceProviderId() != null;

        if (!canGenerate) {
            log.debug("Server is not initialized (instance identifier: '{}', server address: '{}', "
                            + "token status: '{}', auth cert reg url: '{}', management service: '{}')",
                    status.getInstanceIdentifier(), status.getCentralServerAddress(),
                    status.getSoftwareTokenInitStatus(), systemParameterService.getAuthCertRegUrl(),
                    systemParameterService.getManagementServiceProviderId());
            return false;
        }
        try {
            List<TokenInfo> tokens = signerRpcClient.getTokens();
            Set<AlertInfo> internalKeyAlerts = notificationService.checkConfigurationSigningKey(SOURCE_TYPE_INTERNAL, tokens);
            if (!internalKeyAlerts.isEmpty()) {
                log.debug("Internal signing keys alerts: {}", errorCodes(internalKeyAlerts));
                return false;
            }
            Set<AlertInfo> externalKeyAlerts = notificationService.checkConfigurationSigningKey(SOURCE_TYPE_EXTERNAL, tokens);
            if (!externalKeyAlerts.isEmpty()) {
                log.debug("External signing keys alerts: {}", errorCodes(externalKeyAlerts));
                return false;
            }
        } catch (Exception e) {
            log.debug("Failed to get tokens", e);
            return false;
        }

        return true;
    }

    private String errorCodes(Set<AlertInfo> alertInfos) {
        return alertInfos.stream().map(AlertInfo::getErrorCode).collect(joining(", "));
    }

    private record Result(boolean success, GlobalConfApplier applier) {
    }

}
