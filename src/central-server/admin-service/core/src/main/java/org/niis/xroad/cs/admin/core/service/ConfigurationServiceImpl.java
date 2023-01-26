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
import ee.ria.xroad.common.conf.globalconf.privateparameters.v2.ConfigurationSourceType;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v2.ObjectFactory;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.cs.admin.api.domain.DistributedFile;
import org.niis.xroad.cs.admin.api.dto.ConfigurationAnchor;
import org.niis.xroad.cs.admin.api.dto.ConfigurationParts;
import org.niis.xroad.cs.admin.api.dto.File;
import org.niis.xroad.cs.admin.api.dto.GlobalConfDownloadUrl;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS;
import static java.util.stream.Collectors.toSet;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CONFIGURATION_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CONFIGURATION_PART_FILE_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.ERROR_RECREATING_ANCHOR;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INSTANCE_IDENTIFIER_NOT_SET;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.NO_CONFIGURATION_SIGNING_KEYS_CONFIGURED;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.RE_CREATE_EXTERNAL_CONFIGURATION_ANCHOR;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.RE_CREATE_INTERNAL_CONFIGURATION_ANCHOR;

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
    public Set<ConfigurationParts> getConfigurationParts(String sourceType) {
        final ConfigurationSourceEntity configurationSource = findConfigurationSourceBySourceType(
                sourceType.toLowerCase());

        Set<DistributedFile> distributedFiles = distributedFileRepository
                .findAllByHaNodeName(configurationSource.getHaNodeName())
                .stream()
                .map(distributedFileMapper::toTarget)
                .collect(toSet());

        return distributedFiles.stream()
                .map(this::createConfParts)
                .collect(toSet());
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

        return new ConfigurationAnchor(configurationSource.getAnchorFileHash(),
                                       configurationSource.getAnchorGeneratedAt());
    }

    @Override
    public ConfigurationAnchor getConfigurationAnchorWithFile(String sourceType) {
        final ConfigurationSourceEntity configurationSource = findConfigurationSourceBySourceType(
                sourceType.toLowerCase());

        return new ConfigurationAnchor(configurationSource.getAnchorFile(),
                                       configurationSource.getAnchorFileHash(),
                                       configurationSource.getAnchorGeneratedAt());
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

    private ConfigurationParts createConfParts(DistributedFile distributedFile) {
        return new ConfigurationParts(distributedFile.getContentIdentifier(), distributedFile.getFileName(),
                distributedFile.getVersion(), distributedFile.getFileUpdatedAt());
    }

    private String buildGlobalDownloadUrl(final String sourceType, final String haNodeName) {
        final var csAddress = systemParameterService.getCentralServerAddress(haNodeName);
        final String sourceDirectory = sourceType.equals(INTERNAL_CONFIGURATION)
                ? SystemProperties.getCenterInternalDirectory()
                : SystemProperties.getCenterExternalDirectory();

        return String.format("http://%s/%s", csAddress, sourceDirectory);
    }

    private ConfigurationSourceType toXmlSource(final ConfigurationSourceEntity source,
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
