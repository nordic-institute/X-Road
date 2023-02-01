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
import ee.ria.xroad.common.conf.globalconf.privateparameters.v2.ConfigurationAnchorType;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v2.ObjectFactory;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.commonui.OptionalConfPart;
import ee.ria.xroad.commonui.OptionalPartsConf;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType;
import org.niis.xroad.cs.admin.api.domain.DistributedFile;
import org.niis.xroad.cs.admin.api.dto.ConfigurationAnchor;
import org.niis.xroad.cs.admin.api.dto.ConfigurationParts;
import org.niis.xroad.cs.admin.api.dto.File;
import org.niis.xroad.cs.admin.api.dto.GlobalConfDownloadUrl;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.exception.ConfigurationPartException;
import org.niis.xroad.cs.admin.api.exception.ConfigurationSourceException;
import org.niis.xroad.cs.admin.api.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.service.ConfigurationService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSigningKeyEntity;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSourceEntity;
import org.niis.xroad.cs.admin.core.entity.DistributedFileEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.DistributedFileMapper;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSourceRepository;
import org.niis.xroad.cs.admin.core.repository.DistributedFileRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.transaction.Transactional;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;
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
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CONFIGURATION_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CONFIGURATION_PART_FILE_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.ERROR_RECREATING_ANCHOR;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INSTANCE_IDENTIFIER_NOT_SET;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.NO_CONFIGURATION_SIGNING_KEYS_CONFIGURED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.UNKNOWN_CONFIGURATION_PART;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.RE_CREATE_EXTERNAL_CONFIGURATION_ANCHOR;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.RE_CREATE_INTERNAL_CONFIGURATION_ANCHOR;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CONTENT_IDENTIFIER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.PART_FILE_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SOURCE_TYPE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.UPLOAD_FILE_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.UPLOAD_FILE_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.UPLOAD_FILE_NAME;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ConfigurationServiceImpl implements ConfigurationService {
    private static final JAXBContext JAXB_CTX;

    static {
        try {
            JAXB_CTX = JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new RuntimeException("Failed to initialize JAXB context", e);
        }
    }

    private static final String INTERNAL_CONFIGURATION = "INTERNAL";
    private static final Set<String> NODE_LOCAL_CONTENT_IDS = Set.of(
            CONTENT_ID_PRIVATE_PARAMETERS,
            CONTENT_ID_SHARED_PARAMETERS);

    private final SystemParameterService systemParameterService;
    private final HAConfigStatus haConfigStatus;
    private final ConfigurationSourceRepository configurationSourceRepository;
    private final DistributedFileRepository distributedFileRepository;
    private final DistributedFileMapper distributedFileMapper;
    private final AuditEventHelper auditEventHelper;
    private final AuditDataHelper auditDataHelper;

    @Override
    public Set<ConfigurationParts> getConfigurationParts(ConfigurationSourceType sourceType) {
        final ConfigurationSourceEntity configurationSource = findConfigurationSourceBySourceType(
                sourceType.name().toLowerCase());
        final String haNodeName = configurationSource.getHaNodeName();

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
            final Instant updatedAt = distributedFileRepository
                    .findFirstByContentIdentifierAndHaNodeName(part.getContentIdentifier(), haNodeName)
                    .map(DistributedFileEntity::getFileUpdatedAt)
                    .orElse(null);

            configurationParts.add(
                    optionalConfigurationPart(part.getContentIdentifier(), part.getFileName(), updatedAt));

        }
        return configurationParts;
    }

    private ConfigurationParts optionalConfigurationPart(String contentIdentifier, String fileName, Instant updatedAt) {
        return new ConfigurationParts(contentIdentifier, fileName, null, updatedAt, true);
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
                throw new ConfigurationPartException(UNKNOWN_CONFIGURATION_PART);
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
    public ConfigurationAnchor getConfigurationAnchor(String sourceType) {
        final ConfigurationSourceEntity configurationSource = findConfigurationSourceBySourceType(
                sourceType.toLowerCase());

        return new ConfigurationAnchor(configurationSource.getAnchorFileHash(), configurationSource.getAnchorGeneratedAt());
    }

    @Override
    public ConfigurationAnchor recreateAnchor(String configurationType) {
        auditEventHelper.changeRequestScopedEvent(configurationType.equals(INTERNAL_CONFIGURATION)
                ? RE_CREATE_INTERNAL_CONFIGURATION_ANCHOR
                : RE_CREATE_EXTERNAL_CONFIGURATION_ANCHOR);

        final var instanceIdentifier = Optional.ofNullable(systemParameterService.getInstanceIdentifier())
                .filter(StringUtils::isNotEmpty)
                .orElseThrow(() -> new ConfigurationSourceException(INSTANCE_IDENTIFIER_NOT_SET));

        final var configurationSource = configurationSourceRepository.findBySourceTypeOrCreate(
                configurationType.toLowerCase(),
                haConfigStatus);

        if (CollectionUtils.isEmpty(configurationSource.getConfigurationSigningKeys())) {
            throw new ConfigurationSourceException(NO_CONFIGURATION_SIGNING_KEYS_CONFIGURED);
        }

        final var sources = configurationSourceRepository.findAllBySourceType(configurationType.toLowerCase());
        final var now = ZonedDateTime.now(ZoneId.of("UTC"));
        final var anchorXml = buildAnchorXml(configurationType, instanceIdentifier, now, sources);
        final var anchorXmlBytes = anchorXml.getBytes(StandardCharsets.UTF_8);
        final var anchorXmlHash = auditDataHelper.putAnchorHash(anchorXmlBytes);
        for (final var src : sources) {
            if (src.getConfigurationSigningKey() != null) {
                src.setAnchorGeneratedAt(now.toInstant());
                src.setAnchorFileHash(anchorXmlHash);
                src.setAnchorFile(anchorXmlBytes);
                configurationSourceRepository.save(src);
            }
        }

        return new ConfigurationAnchor(anchorXmlHash, now.toInstant());
    }

    private String buildAnchorXml(final String configurationType,
                                  final String instanceIdentifier,
                                  final ZonedDateTime now,
                                  final List<ConfigurationSourceEntity> sources) {
        try {

            Marshaller marshaller = JAXB_CTX.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);


            final var factory = new ObjectFactory();
            final var configurationAnchor = factory.createConfigurationAnchorType();
            configurationAnchor.setGeneratedAt(DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(now)));
            configurationAnchor.setInstanceIdentifier(instanceIdentifier);

            sources.stream()
                    .map(src -> toXmlSource(src, configurationType, factory))
                    .forEach(configurationAnchor.getSource()::add);

            JAXBElement<ConfigurationAnchorType> root = factory.createConfigurationAnchor(configurationAnchor);

            Writer writer = new StringWriter();
            marshaller.marshal(root, writer);
            return writer.toString();
        } catch (DatatypeConfigurationException | JAXBException e) {
            throw new ConfigurationSourceException(ERROR_RECREATING_ANCHOR);
        }
    }

    @Override
    public GlobalConfDownloadUrl getGlobalDownloadUrl(String sourceType) {
        final String csAddress = systemParameterService.getCentralServerAddress();
        final String sourceDirectory = sourceType.equals(INTERNAL_CONFIGURATION)
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
            throw new ConfigurationPartException(UNKNOWN_CONFIGURATION_PART);
        }

        auditDataHelper.put(UPLOAD_FILE_HASH_ALGORITHM, DEFAULT_UPLOAD_FILE_HASH_ALGORITHM);
        auditDataHelper.put(UPLOAD_FILE_HASH, getFileHash(data));

        validateConfigurationPart(data, partFileName, optionalPartsConf);

        saveConfigurationPart(contentIdentifier, partFileName, data, 0);
    }

    private void validateConfigurationPart(byte[] data, String partFileName, OptionalPartsConf optionalPartsConf) {
        final String validationProgram = optionalPartsConf.getValidationProgram(partFileName);

        // TODO FIXME: Execute bash script

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

    private ConfigurationSourceEntity findConfigurationSourceBySourceType(String sourceType) {
        return configurationSourceRepository.findBySourceTypeAndHaNodeName(sourceType, haConfigStatus.getCurrentHaNodeName())
                .orElseThrow(ConfigurationServiceImpl::notFoundException);
    }

    private String buildGlobalDownloadUrl(final String sourceType, final String haNodeName) {
        final var csAddress = systemParameterService.getCentralServerAddress(haNodeName);
        final String sourceDirectory = sourceType.equals(INTERNAL_CONFIGURATION)
                ? SystemProperties.getCenterInternalDirectory()
                : SystemProperties.getCenterExternalDirectory();

        return String.format("http://%s/%s", csAddress, sourceDirectory);
    }

    private ee.ria.xroad.common.conf.globalconf.privateparameters.v2.ConfigurationSourceType toXmlSource(
            final ConfigurationSourceEntity source,
            final String configurationType,
            final ObjectFactory factory) {
        final var xmlSource = factory.createConfigurationSourceType();

        xmlSource.setDownloadURL(buildGlobalDownloadUrl(configurationType, source.getHaNodeName()));
        source.getConfigurationSigningKeys().stream()
                .map(ConfigurationSigningKeyEntity::getCert)
                .forEach(xmlSource.getVerificationCert()::add);

        return xmlSource;
    }

    private static NotFoundException notFoundException() {
        return new NotFoundException(CONFIGURATION_NOT_FOUND);
    }
}
