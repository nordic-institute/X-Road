/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.commonui.OptionalConfPart;
import ee.ria.xroad.commonui.OptionalPartsConf;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType;
import org.niis.xroad.cs.admin.api.domain.DistributedFile;
import org.niis.xroad.cs.admin.api.dto.ConfigurationParts;
import org.niis.xroad.cs.admin.api.dto.File;
import org.niis.xroad.cs.admin.api.dto.GlobalConfDownloadUrl;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.service.ConfigurationService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSourceEntity;
import org.niis.xroad.cs.admin.core.entity.DistributedFileEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.DistributedFileMapper;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSigningKeyRepository;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSourceRepository;
import org.niis.xroad.cs.admin.core.repository.DistributedFileRepository;
import org.niis.xroad.cs.admin.core.validation.ConfigurationPartValidator;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS;
import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_UPLOAD_FILE_HASH_ALGORITHM;
import static java.util.stream.Collectors.toSet;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.EXTERNAL;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.INTERNAL;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CONFIGURATION_PART_FILE_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.UNKNOWN_CONFIGURATION_PART;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CONTENT_IDENTIFIER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.PART_FILE_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SOURCE_TYPE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.UPLOAD_FILE_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.UPLOAD_FILE_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.UPLOAD_FILE_NAME;

@Service
@Transactional
@RequiredArgsConstructor
public class ConfigurationServiceImpl implements ConfigurationService {

    private static final Set<String> NODE_LOCAL_CONTENT_IDS = Set.of(
            CONTENT_ID_PRIVATE_PARAMETERS,
            CONTENT_ID_SHARED_PARAMETERS);

    private final SystemParameterService systemParameterService;
    private final HAConfigStatus haConfigStatus;
    private final ConfigurationSourceRepository configurationSourceRepository;
    private final ConfigurationSigningKeyRepository configurationSigningKeyRepository;
    private final DistributedFileRepository distributedFileRepository;
    private final DistributedFileMapper distributedFileMapper;
    private final AuditDataHelper auditDataHelper;
    private final ConfigurationPartValidator configurationPartValidator;

    @Override
    public boolean hasSigningKeys(final ConfigurationSourceType sourceType) {
        return configurationSigningKeyRepository
                .countSigningKeysForSourceType(sourceType.name().toLowerCase(), haConfigStatus.getCurrentHaNodeName()) > 0;
    }

    @Override
    public Set<ConfigurationParts> getConfigurationParts(ConfigurationSourceType sourceType) {
        final var configurationSource = findConfigurationSourceBySourceType(sourceType);
        if (configurationSource.isEmpty()) {
            return Set.of();
        }
        final String haNodeName = configurationSource.get().getHaNodeName();

        Set<ConfigurationParts> configurationParts = new HashSet<>();
        if (sourceType.equals(EXTERNAL)) {
            configurationParts.addAll(getRequiredConfigurationParts(haNodeName, CONTENT_ID_SHARED_PARAMETERS));
        } else {
            configurationParts.addAll(getRequiredConfigurationParts(haNodeName,
                    CONTENT_ID_PRIVATE_PARAMETERS,
                    CONTENT_ID_SHARED_PARAMETERS));

            configurationParts.addAll(getOptionalParts(haNodeName));
        }

        return configurationParts;
    }

    private Set<ConfigurationParts> getOptionalParts(String haNodeName) {
        final List<OptionalConfPart> allParts = OptionalPartsConf.getOptionalPartsConf().getAllParts();
        final Set<ConfigurationParts> configurationParts = new HashSet<>();

        for (OptionalConfPart part : allParts) {
            final ConfigurationParts configurationPart = distributedFileRepository
                    .findFirstByContentIdentifierAndHaNodeName(part.getContentIdentifier(), haNodeName)
                    .map(file -> optionalConfigurationPart(part, file))
                    .orElse(optionalConfigurationPart(part));
            configurationParts.add(configurationPart);
        }
        return configurationParts;
    }

    private ConfigurationParts optionalConfigurationPart(OptionalConfPart part, DistributedFileEntity file) {
        return new ConfigurationParts(part.getContentIdentifier(), part.getFileName(), file.getVersion(), file.getFileUpdatedAt(), true);
    }

    private ConfigurationParts optionalConfigurationPart(OptionalConfPart part) {
        return new ConfigurationParts(part.getContentIdentifier(), part.getFileName(), null, null, true);
    }

    private Set<ConfigurationParts> getRequiredConfigurationParts(String haNode, String... contentIdentifiers) {
        Set<ConfigurationParts> configurationParts = new HashSet<>();
        for (String contentIdentifier : contentIdentifiers) {
            final Set<DistributedFileEntity> files = distributedFileRepository
                    .findAllByContentIdentifierAndHaNodeName(contentIdentifier, haNode);

            if (files.isEmpty()) {
                configurationParts.add(
                        new ConfigurationParts(contentIdentifier, resolveFileName(contentIdentifier), null, null, false)
                );
            } else {
                files.stream()
                        .map(file -> new ConfigurationParts(file.getContentIdentifier(), file.getFileName(), file.getVersion(),
                                file.getFileUpdatedAt(), false))
                        .forEach(configurationParts::add);
            }
        }
        return configurationParts;
    }

    private String resolveFileName(String contentIdentifier) {
        switch (contentIdentifier) {
            case CONTENT_ID_PRIVATE_PARAMETERS:
                return FILE_NAME_PRIVATE_PARAMETERS;
            case CONTENT_ID_SHARED_PARAMETERS:
                return FILE_NAME_SHARED_PARAMETERS;
            default:
                throw new ServiceException(UNKNOWN_CONFIGURATION_PART);
        }
    }

    @Override
    public File getConfigurationPartFile(String contentIdentifier, int version) {
        return distributedFileRepository
                .findByContentIdAndVersion(contentIdentifier, version, getHaNodeName(contentIdentifier))
                .map(distributedFileMapper::toFile)
                .orElseThrow(() -> new NotFoundException(CONFIGURATION_PART_FILE_NOT_FOUND));
    }

    @Override
    public GlobalConfDownloadUrl getGlobalDownloadUrl(ConfigurationSourceType sourceType) {
        final String csAddress = systemParameterService.getCentralServerAddress();
        final String sourceDirectory = sourceType.equals(INTERNAL)
                ? SystemProperties.getCenterInternalDirectory()
                : SystemProperties.getCenterExternalDirectory();

        final String downloadUrl = "http://" + csAddress + "/" + sourceDirectory;

        return new GlobalConfDownloadUrl(downloadUrl);
    }

    @Override
    public void saveConfigurationPart(String contentIdentifier, String fileName, byte[] data, int version) {
        var distributedFileEntity = findOrCreate(contentIdentifier, version);
        distributedFileEntity.setFileName(fileName);
        distributedFileEntity.setFileData(data);
        distributedFileEntity.setFileUpdatedAt(Instant.now());
        distributedFileEntity.setHaNodeName(haConfigStatus.getCurrentHaNodeName());
        distributedFileRepository.save(distributedFileEntity);
    }

    @Override
    public Set<DistributedFile> getAllConfigurationFiles(int version) {
        return distributedFileRepository.findAllByVersion(version)
                .stream()
                .filter(this::isForCurrentNode)
                .map(distributedFileMapper::toTarget)
                .collect(toSet());
    }

    @Override
    public void uploadConfigurationPart(ConfigurationSourceType sourceType,
                                        String contentIdentifier, String originalFileName, byte[] data) {

        final OptionalPartsConf optionalPartsConf = OptionalPartsConf.getOptionalPartsConf();
        final String partFileName = optionalPartsConf.getPartFileName(contentIdentifier);

        auditDataHelper.put(SOURCE_TYPE, sourceType.name());
        auditDataHelper.put(CONTENT_IDENTIFIER, contentIdentifier);
        auditDataHelper.put(PART_FILE_NAME, partFileName);
        auditDataHelper.put(UPLOAD_FILE_NAME, originalFileName);

        if (sourceType == EXTERNAL && !contentIdentifier.equals(CONTENT_ID_SHARED_PARAMETERS)) {
            throw new ServiceException(UNKNOWN_CONFIGURATION_PART);
        }

        auditDataHelper.put(UPLOAD_FILE_HASH_ALGORITHM, DEFAULT_UPLOAD_FILE_HASH_ALGORITHM);
        auditDataHelper.put(UPLOAD_FILE_HASH, getFileHash(data));

        configurationPartValidator.validate(contentIdentifier, data);

        saveConfigurationPart(contentIdentifier, partFileName, data, 0);
    }

    @SneakyThrows
    private String getFileHash(byte[] data) {
        return CryptoUtils.hexDigest(DEFAULT_UPLOAD_FILE_HASH_ALGORITHM, data);
    }

    private boolean isForCurrentNode(DistributedFileEntity distributedFile) {
        if (haConfigStatus.isHaConfigured()
                && NODE_LOCAL_CONTENT_IDS.contains(distributedFile.getContentIdentifier())) {
            return haConfigStatus.getCurrentHaNodeName().equals(distributedFile.getHaNodeName());
        }
        return true;
    }

    private DistributedFileEntity findOrCreate(String contentIdentifier, int version) {
        String dfHaNodeName = getHaNodeName(contentIdentifier);
        return distributedFileRepository.findByContentIdAndVersion(contentIdentifier, version, dfHaNodeName)
                .orElseGet(() -> new DistributedFileEntity(contentIdentifier, version, dfHaNodeName));
    }

    private String getHaNodeName(String contentIdentifier) {
        return haConfigStatus.isHaConfigured() && isNodeLocalContentId(contentIdentifier)
                ? haConfigStatus.getCurrentHaNodeName()
                : null;
    }

    private boolean isNodeLocalContentId(@NonNull String contentId) {
        return NODE_LOCAL_CONTENT_IDS.contains(contentId);
    }

    private Optional<ConfigurationSourceEntity> findConfigurationSourceBySourceType(ConfigurationSourceType sourceType) {
        return configurationSourceRepository.findBySourceTypeAndHaNodeName(sourceType.name().toLowerCase(),
                haConfigStatus.getCurrentHaNodeName());
    }
}
