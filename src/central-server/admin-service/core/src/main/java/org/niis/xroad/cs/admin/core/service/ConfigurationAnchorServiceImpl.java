/*
 * The MIT License
 * <p>
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

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType;
import org.niis.xroad.cs.admin.api.dto.ConfigurationAnchor;
import org.niis.xroad.cs.admin.api.dto.ConfigurationAnchorWithFile;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.service.ConfigurationAnchorService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSigningKeyEntity;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSourceEntity;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSourceRepository;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;

import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INTERNAL_ERROR;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.INTERNAL;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.ERROR_RECREATING_ANCHOR;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INSTANCE_IDENTIFIER_NOT_SET;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.NO_CONFIGURATION_SIGNING_KEYS_CONFIGURED;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.RE_CREATE_EXTERNAL_CONFIGURATION_ANCHOR;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.RE_CREATE_INTERNAL_CONFIGURATION_ANCHOR;

@Service
@Transactional
@RequiredArgsConstructor
public class ConfigurationAnchorServiceImpl implements ConfigurationAnchorService {
    private static final JAXBContext JAXB_CTX;

    private static final DateTimeFormatter ANCHOR_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss")
            .withZone(ZoneId.systemDefault());

    private final ConfigurationSourceRepository configurationSourceRepository;
    private final SystemParameterService systemParameterService;
    private final AuditEventHelper auditEventHelper;
    private final AuditDataHelper auditDataHelper;
    private final HAConfigStatus haConfigStatus;

    static {
        try {
            JAXB_CTX = JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new ServiceException(INTERNAL_ERROR, e);
        }
    }

    @Override
    public Optional<ConfigurationAnchor> getConfigurationAnchor(ConfigurationSourceType sourceType) {
        return findConfigurationSourceBySourceType(sourceType)
                .map(cfgSrc -> new ConfigurationAnchor(cfgSrc.getAnchorFileHash(), cfgSrc.getAnchorGeneratedAt()));
    }

    @Override
    public Optional<ConfigurationAnchorWithFile> getConfigurationAnchorWithFile(ConfigurationSourceType sourceType) {
        return findConfigurationSourceBySourceType(sourceType)
                .map(cfgSrc -> new ConfigurationAnchorWithFile(
                        cfgSrc.getAnchorFileHash(),
                        cfgSrc.getAnchorGeneratedAt(),
                        cfgSrc.getAnchorFile(),
                        getAnchorFilename(cfgSrc))
                );
    }

    @Override
    public ConfigurationAnchor recreateAnchor(ConfigurationSourceType configurationType, boolean addAuditLog) {
        if (addAuditLog) {
            auditEventHelper.changeRequestScopedEvent(configurationType.equals(INTERNAL)
                    ? RE_CREATE_INTERNAL_CONFIGURATION_ANCHOR
                    : RE_CREATE_EXTERNAL_CONFIGURATION_ANCHOR);
        }

        final var instanceIdentifier = Optional.ofNullable(systemParameterService.getInstanceIdentifier())
                .filter(StringUtils::isNotEmpty)
                .orElseThrow(() -> new ServiceException(INSTANCE_IDENTIFIER_NOT_SET));

        final var configurationSource = configurationSourceRepository.findBySourceTypeOrCreate(
                configurationType.name().toLowerCase(),
                haConfigStatus);

        if (CollectionUtils.isEmpty(configurationSource.getConfigurationSigningKeys())) {
            throw new ServiceException(NO_CONFIGURATION_SIGNING_KEYS_CONFIGURED);
        }

        final var sources = configurationSourceRepository.findAllBySourceType(configurationType.name().toLowerCase());
        final var now = ZonedDateTime.now(ZoneId.of("UTC"));
        final var anchorXml = buildAnchorXml(configurationType, instanceIdentifier, now, sources);
        final var anchorXmlBytes = anchorXml.getBytes(StandardCharsets.UTF_8);
        final var anchorXmlHash = CryptoUtils.calculateAnchorHashDelimited(anchorXmlBytes);
        if (addAuditLog) {
            auditDataHelper.putAnchorHash(anchorXmlHash);
        }
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

    private String buildAnchorXml(final ConfigurationSourceType configurationType,
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
            throw new ServiceException(ERROR_RECREATING_ANCHOR);
        }
    }

    private String buildGlobalDownloadUrl(final ConfigurationSourceType sourceType, final String haNodeName) {
        final var csAddress = systemParameterService.getCentralServerAddress(haNodeName);
        final String sourceDirectory = sourceType.equals(INTERNAL)
                ? SystemProperties.getCenterInternalDirectory()
                : SystemProperties.getCenterExternalDirectory();

        return String.format("http://%s/%s", csAddress, sourceDirectory);
    }

    private ee.ria.xroad.common.conf.globalconf.privateparameters.v2.ConfigurationSourceType toXmlSource(
            final ConfigurationSourceEntity source,
            final ConfigurationSourceType configurationType,
            final ObjectFactory factory) {
        final var xmlSource = factory.createConfigurationSourceType();

        xmlSource.setDownloadURL(buildGlobalDownloadUrl(configurationType, source.getHaNodeName()));
        source.getConfigurationSigningKeys().stream()
                .map(ConfigurationSigningKeyEntity::getCert)
                .forEach(xmlSource.getVerificationCert()::add);

        return xmlSource;
    }

    private Optional<ConfigurationSourceEntity> findConfigurationSourceBySourceType(ConfigurationSourceType sourceType) {
        return configurationSourceRepository.findBySourceTypeAndHaNodeName(sourceType.name().toLowerCase(),
                haConfigStatus.getCurrentHaNodeName());
    }


    private String getAnchorFilename(ConfigurationSourceEntity cfgSource) {
        return String.format("configuration_anchor_%s_%s_UTC_%s.xml",
                systemParameterService.getInstanceIdentifier(),
                cfgSource.getSourceType(),
                ANCHOR_DATE_FORMATTER.format(cfgSource.getAnchorGeneratedAt()));
    }
}
