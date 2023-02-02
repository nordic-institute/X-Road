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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.commonui.OptionalPartsConf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.dto.ConfigurationAnchor;
import org.niis.xroad.cs.admin.api.dto.ConfigurationParts;
import org.niis.xroad.cs.admin.api.dto.File;
import org.niis.xroad.cs.admin.api.dto.GlobalConfDownloadUrl;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.exception.ConfigurationPartException;
import org.niis.xroad.cs.admin.api.exception.ConfigurationSourceException;
import org.niis.xroad.cs.admin.api.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSigningKeyEntity;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSourceEntity;
import org.niis.xroad.cs.admin.core.entity.DistributedFileEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.DistributedFileMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.DistributedFileMapperImpl;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSourceRepository;
import org.niis.xroad.cs.admin.core.repository.DistributedFileRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.util.Base64Utils;
import org.xmlunit.assertj3.XmlAssert;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS;
import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_UPLOAD_FILE_HASH_ALGORITHM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.EXTERNAL;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.INTERNAL;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.RE_CREATE_EXTERNAL_CONFIGURATION_ANCHOR;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SOURCE_TYPE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.UPLOAD_FILE_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.UPLOAD_FILE_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.UPLOAD_FILE_NAME;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceImplTest {

    private static final String INTERNAL_CONFIGURATION = "internal";
    private static final String EXTERNAL_CONFIGURATION = "external";
    private static final String CENTRAL_SERVICE = "cs";
    private static final String HA_NODE_NAME = "haNodeName";
    private static final int VERSION = 123;
    private static final String FILE_NAME = "fileName";
    private static final String FILE_NAME_PRIVATE_PARAMS = "private-params.xml";
    private static final String CONTENT_IDENTIFIER = "Content";
    private static final Instant FILE_UPDATED_AT = Instant.now();
    private static final String HASH = "F5:1B:1F:9C:07:23:4C:DA:E6:4C:99:CB:FC:D8:EE:0E:C5:5F:A4:AF";
    private static final byte[] FILE_DATA = "file-data".getBytes(UTF_8);
    private static final String NODE_LOCAL_CONTENT_ID = CONTENT_ID_PRIVATE_PARAMETERS;
    private static final String TEST_CONFIGURATION_PART = "TEST-CONFIGURATION-PART";
    private static final String CONF_PARTS_DIR = "src/test/resources/configuration-parts";

    @Mock
    private SystemParameterService systemParameterService;
    @Mock
    private ConfigurationSourceRepository configurationSourceRepository;
    @Mock
    private DistributedFileRepository distributedFileRepository;
    @Mock
    private ConfigurationSourceEntity configurationSource;
    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private AuditEventHelper auditEventHelper;
    @Mock
    private ExternalProcessRunner externalProcessRunner;
    @Spy
    private DistributedFileMapper distributedFileMapper = new DistributedFileMapperImpl();
    private ConfigurationServiceImpl configurationService;
    private ConfigurationServiceImpl configurationServiceHa;

    @BeforeEach
    void initConfigurationService() {
        configurationService = createConfigurationService(new HAConfigStatus(HA_NODE_NAME, false));
        configurationServiceHa = createConfigurationService(new HAConfigStatus(HA_NODE_NAME, true));
    }

    @Nested
    class GetConfigurationParts {
        @Test
        void shouldGetInternalConfigurationParts() throws Exception {
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(INTERNAL_CONFIGURATION, HA_NODE_NAME))
                    .thenReturn(Optional.of(configurationSource));
            when(configurationSource.getHaNodeName()).thenReturn(HA_NODE_NAME);

            when(distributedFileRepository.findAllByContentIdentifierAndHaNodeName(CONTENT_ID_PRIVATE_PARAMETERS, HA_NODE_NAME))
                    .thenReturn(Set.of(new DistributedFileEntity(VERSION, FILE_NAME_PRIVATE_PARAMS, CONTENT_ID_PRIVATE_PARAMETERS,
                            FILE_UPDATED_AT)));

            when(distributedFileRepository.findAllByContentIdentifierAndHaNodeName(CONTENT_ID_SHARED_PARAMETERS, HA_NODE_NAME))
                    .thenReturn(Set.of(new DistributedFileEntity(VERSION, FILE_NAME, CONTENT_ID_SHARED_PARAMETERS, FILE_UPDATED_AT)));

            try (MockedStatic<OptionalPartsConf> mockedConfParts = mockStatic(OptionalPartsConf.class)) {
                mockedConfParts.when(OptionalPartsConf::getOptionalPartsConf).thenReturn(new OptionalPartsConf(CONF_PARTS_DIR));

                final Set<ConfigurationParts> result = configurationService.getConfigurationParts(INTERNAL);

                assertThat(result).hasSize(3);

                assertThat(result).filteredOn("fileName", FILE_NAME_PRIVATE_PARAMS)
                        .hasSize(1)
                        .satisfiesExactly(item -> assertAll(
                                () -> assertThat(item.getFileUpdatedAt()).isEqualTo(FILE_UPDATED_AT),
                                () -> assertThat(item.getContentIdentifier()).isEqualTo(CONTENT_ID_PRIVATE_PARAMETERS),
                                () -> assertThat(item.getVersion()).isEqualTo(VERSION),
                                () -> assertThat(item.isOptional()).isFalse()
                        ));

                assertThat(result).filteredOn("fileName", FILE_NAME)
                        .hasSize(1)
                        .satisfiesExactly(item -> assertAll(
                                () -> assertThat(item.getFileUpdatedAt()).isEqualTo(FILE_UPDATED_AT),
                                () -> assertThat(item.getContentIdentifier()).isEqualTo(CONTENT_ID_SHARED_PARAMETERS),
                                () -> assertThat(item.getVersion()).isEqualTo(VERSION),
                                () -> assertThat(item.isOptional()).isFalse()
                        ));

                assertThat(result).filteredOn("fileName", "test-configuration-part.xml")
                        .hasSize(1)
                        .satisfiesExactly(item -> assertAll(
                                () -> assertThat(item.getFileUpdatedAt()).isNull(),
                                () -> assertThat(item.getContentIdentifier()).isEqualTo(TEST_CONFIGURATION_PART),
                                () -> assertThat(item.getVersion()).isNull(),
                                () -> assertThat(item.isOptional()).isTrue()
                        ));
            }
        }

        @Test
        void shouldGetExternalConfigurationParts() throws Exception {
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(EXTERNAL_CONFIGURATION, HA_NODE_NAME))
                    .thenReturn(Optional.of(configurationSource));
            when(configurationSource.getHaNodeName()).thenReturn(HA_NODE_NAME);
            when(distributedFileRepository.findAllByContentIdentifierAndHaNodeName(CONTENT_ID_SHARED_PARAMETERS, HA_NODE_NAME))
                    .thenReturn(Set.of(new DistributedFileEntity(VERSION, FILE_NAME, CONTENT_ID_SHARED_PARAMETERS, FILE_UPDATED_AT)));

            try (MockedStatic<OptionalPartsConf> mockedConfParts = mockStatic(OptionalPartsConf.class)) {
                mockedConfParts.when(OptionalPartsConf::getOptionalPartsConf).thenReturn(new OptionalPartsConf(CONF_PARTS_DIR));

                final Set<ConfigurationParts> result = configurationService.getConfigurationParts(EXTERNAL);

                assertThat(result).hasSize(1);
                final ConfigurationParts configurationPart = result.iterator().next();
                assertThat(configurationPart.getVersion()).isEqualTo(VERSION);
                assertThat(configurationPart.getFileName()).isEqualTo(FILE_NAME);
                assertThat(configurationPart.getFileUpdatedAt()).isEqualTo(FILE_UPDATED_AT);
                assertThat(configurationPart.isOptional()).isFalse();
            }
        }

        @Test
        void shouldThrowNotFoundExceptionWhenSourceNotFound() {
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(INTERNAL_CONFIGURATION, HA_NODE_NAME))
                    .thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> configurationService.getConfigurationParts(INTERNAL));
            verifyNoInteractions(distributedFileRepository);
        }

    }

    @Nested
    class GetConfigurationAnchor {
        @Test
        void shouldGetInternalConfigurationAnchor() {
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(INTERNAL_CONFIGURATION, HA_NODE_NAME))
                    .thenReturn(Optional.of(configurationSourceEntity()));

            final ConfigurationAnchor result = configurationService.getConfigurationAnchor(INTERNAL_CONFIGURATION);

            assertThat(result.getAnchorFileHash()).isEqualTo(HASH);
            assertThat(result.getAnchorGeneratedAt()).isEqualTo(FILE_UPDATED_AT);
        }

        @Test
        void shouldGetInternalConfigurationAnchorWithFile() {
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(INTERNAL_CONFIGURATION, HA_NODE_NAME))
                    .thenReturn(Optional.of(configurationSourceEntityWithFile()));

            final ConfigurationAnchor result = configurationService.getConfigurationAnchorWithFile(INTERNAL_CONFIGURATION);

            assertThat(result.getAnchorFile()).isEqualTo(FILE_DATA);
            assertThat(result.getAnchorFileHash()).isEqualTo(HASH);
            assertThat(result.getAnchorGeneratedAt()).isEqualTo(FILE_UPDATED_AT);
        }

        @Test
        void shouldGetExternalConfigurationAnchor() {
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(EXTERNAL_CONFIGURATION, HA_NODE_NAME))
                    .thenReturn(Optional.of(configurationSourceEntity()));

            final ConfigurationAnchor result = configurationService.getConfigurationAnchor(EXTERNAL_CONFIGURATION);

            assertThat(result.getAnchorFileHash()).isEqualTo(HASH);
            assertThat(result.getAnchorGeneratedAt()).isEqualTo(FILE_UPDATED_AT);
        }

        @Test
        void shouldGetExternalConfigurationAnchorWithFile() {
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(EXTERNAL_CONFIGURATION, HA_NODE_NAME))
                    .thenReturn(Optional.of(configurationSourceEntityWithFile()));

            final ConfigurationAnchor result = configurationService.getConfigurationAnchorWithFile(EXTERNAL_CONFIGURATION);

            assertThat(result.getAnchorFile()).isEqualTo(FILE_DATA);
            assertThat(result.getAnchorFileHash()).isEqualTo(HASH);
            assertThat(result.getAnchorGeneratedAt()).isEqualTo(FILE_UPDATED_AT);
        }
    }

    @Nested
    class GetDownloadUrl {
        @Test
        void shouldGetInternalGlobalDownloadUrl() {
            when(systemParameterService.getCentralServerAddress())
                    .thenReturn(CENTRAL_SERVICE);

            final GlobalConfDownloadUrl result = configurationService.getGlobalDownloadUrl("INTERNAL");

            assertThat(result.getUrl()).isEqualTo("http://" + CENTRAL_SERVICE + "/internalconf");
        }

        @Test
        void shouldGetExternalGlobalDownloadUrl() {
            when(systemParameterService.getCentralServerAddress())
                    .thenReturn(CENTRAL_SERVICE);

            final GlobalConfDownloadUrl result = configurationService.getGlobalDownloadUrl("EXTERNAL");

            assertThat(result.getUrl()).isEqualTo("http://" + CENTRAL_SERVICE + "/externalconf");
        }
    }

    private ConfigurationServiceImpl createConfigurationService(HAConfigStatus haConfigStatus) {
        return new ConfigurationServiceImpl(
                systemParameterService,
                haConfigStatus,
                configurationSourceRepository,
                distributedFileRepository,
                distributedFileMapper,
                auditEventHelper,
                auditDataHelper,
                externalProcessRunner);
    }

    private ConfigurationSourceEntity configurationSourceEntity() {
        return new ConfigurationSourceEntity(HASH, FILE_UPDATED_AT);
    }

    private ConfigurationSourceEntity configurationSourceEntityWithFile() {
        return new ConfigurationSourceEntity(FILE_DATA, HASH, FILE_UPDATED_AT);
    }

    @Nested
    class RecreateAnchor {
        final Map<String, String> namespace = Map.of(
                "ns2", "http://x-road.eu/xsd/identifiers",
                "ns3", "http://x-road.eu/xsd/xroad.xsd"
        );
        static final String INSTANCE_IDENTIFIER = "inId";
        static final String CERT1 = "cert1";
        static final String CERT2 = "cert2";
        static final String CERT3 = "cert3";
        static final String HA_NODE_NAME2 = "haNodeName2";
        static final String CENTRAL_SERVICE2 = "cs2";
        @Mock
        private ConfigurationSigningKeyEntity signingKeyEntity1, signingKeyEntity2, signingKeyEntity3;
        @Mock
        private ConfigurationSourceEntity configurationSource2;
        @Captor
        private ArgumentCaptor<byte[]> xmlCaptor;

        @Test
        void shouldSuccessfullyRecreate() {
            when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
            when(systemParameterService.getCentralServerAddress(HA_NODE_NAME)).thenReturn(CENTRAL_SERVICE);
            when(systemParameterService.getCentralServerAddress(HA_NODE_NAME2)).thenReturn(CENTRAL_SERVICE2);
            when(configurationSourceRepository.findBySourceTypeOrCreate(INTERNAL_CONFIGURATION.toLowerCase(),
                    new HAConfigStatus(HA_NODE_NAME, false)))
                    .thenReturn(configurationSource);
            when(configurationSourceRepository.findAllBySourceType(INTERNAL_CONFIGURATION.toLowerCase()))
                    .thenReturn(List.of(configurationSource, configurationSource2));
            when(configurationSource.getHaNodeName()).thenReturn(HA_NODE_NAME);
            when(configurationSource.getConfigurationSigningKeys())
                    .thenReturn(new LinkedHashSet<>(List.of(signingKeyEntity1, signingKeyEntity2)));
            when(configurationSource.getConfigurationSigningKey()).thenReturn(signingKeyEntity1);
            when(configurationSource2.getHaNodeName()).thenReturn(HA_NODE_NAME2);
            when(configurationSource2.getConfigurationSigningKeys()).thenReturn(Set.of(signingKeyEntity3));
            when(configurationSource2.getConfigurationSigningKey()).thenReturn(null);
            when(signingKeyEntity1.getCert()).thenReturn(CERT1.getBytes(UTF_8));
            when(signingKeyEntity2.getCert()).thenReturn(CERT2.getBytes(UTF_8));
            when(signingKeyEntity3.getCert()).thenReturn(CERT3.getBytes(UTF_8));

            final var result = configurationService.recreateAnchor(INTERNAL_CONFIGURATION);

            verify(configurationSourceRepository).save(configurationSource);
            verify(configurationSourceRepository, never()).save(configurationSource2);
            verify(configurationSource).setAnchorFile(xmlCaptor.capture());
            verify(configurationSource).setAnchorFileHash(result.getAnchorFileHash());
            verify(configurationSource).setAnchorGeneratedAt(result.getAnchorGeneratedAt());
            verify(auditEventHelper).changeRequestScopedEvent(RE_CREATE_EXTERNAL_CONFIGURATION_ANCHOR);
            verify(auditDataHelper).putAnchorHash(any());

            assertThat(result.getAnchorGeneratedAt().truncatedTo(ChronoUnit.MINUTES))
                    .isEqualTo(Instant.now().truncatedTo(ChronoUnit.MINUTES));

            final var xml = new String(xmlCaptor.getValue());
            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/instanceIdentifier").isEqualTo(INSTANCE_IDENTIFIER);
            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/generatedAt").isEqualTo(asString(result.getAnchorGeneratedAt()));
            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/source[1]/downloadURL")
                    .isEqualTo("http://cs/externalconf");
            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/source[1]/verificationCert[1]")
                    .isEqualTo(Base64Utils.encodeToString(CERT1.getBytes(UTF_8)));
            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/source[1]/verificationCert[2]")
                    .isEqualTo(Base64Utils.encodeToString(CERT2.getBytes(UTF_8)));

            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/source[2]/downloadURL")
                    .isEqualTo("http://cs2/externalconf");
            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/source[2]/verificationCert[1]")
                    .isEqualTo(Base64Utils.encodeToString(CERT3.getBytes(UTF_8)));
        }

        @Test
        void shouldFailIfInstanceIdentifierNotSet() {
            when(systemParameterService.getInstanceIdentifier()).thenReturn(null);

            assertThatThrownBy(() -> configurationService.recreateAnchor(INTERNAL_CONFIGURATION))
                    .isInstanceOf(ConfigurationSourceException.class)
                    .hasMessage("System parameter for instance identifier not set");
        }

        @Test
        void shouldFailIfCfgSourceDoesntHaveSigningKeys() {
            when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
            when(configurationSourceRepository.findBySourceTypeOrCreate(INTERNAL_CONFIGURATION.toLowerCase(),
                    new HAConfigStatus(HA_NODE_NAME, false)))
                    .thenReturn(configurationSource);
            when(configurationSource.getConfigurationSigningKeys()).thenReturn(Set.of());

            assertThatThrownBy(() -> configurationService.recreateAnchor(INTERNAL_CONFIGURATION))
                    .isInstanceOf(ConfigurationSourceException.class)
                    .hasMessage("No configuration signing keys configured");
        }

        private String asString(final Instant instant) {
            return instant.truncatedTo(ChronoUnit.MILLIS).toString();
        }
    }

    @Nested
    class SaveConfigurationPart {
        @Captor
        private ArgumentCaptor<DistributedFileEntity> distributedFileCaptor;

        @Test
        void shouldCreateNew() {
            configurationService.saveConfigurationPart(CONTENT_IDENTIFIER, FILE_NAME, FILE_DATA, VERSION);

            verify(distributedFileRepository).save(distributedFileCaptor.capture());
            var df = distributedFileCaptor.getValue();
            assertFieldsChanged(df);
        }

        @Test
        void shouldUpdateExisting() {
            var originalDf = new DistributedFileEntity(CONTENT_IDENTIFIER, VERSION, null);

            when(distributedFileRepository.findByContentIdAndVersion(CONTENT_IDENTIFIER, VERSION, null))
                    .thenReturn(Optional.of(originalDf));

            configurationService.saveConfigurationPart(CONTENT_IDENTIFIER, FILE_NAME, FILE_DATA, VERSION);

            verify(distributedFileRepository).save(distributedFileCaptor.capture());
            var df = distributedFileCaptor.getValue();
            assertThat(df).isSameAs(originalDf);
            assertFieldsChanged(df);
        }

        @Test
        void shouldFindByNodeNameInHaConf() {
            configurationServiceHa.saveConfigurationPart(NODE_LOCAL_CONTENT_ID, FILE_NAME, FILE_DATA, VERSION);

            verify(distributedFileRepository).findByContentIdAndVersion(NODE_LOCAL_CONTENT_ID, VERSION, HA_NODE_NAME);
        }

        @Test
        void shouldNotFindByNodeNameInHaConfWhenNotNodeLocalContentId() {
            configurationServiceHa.saveConfigurationPart(CONTENT_IDENTIFIER, FILE_NAME, FILE_DATA, VERSION);

            verify(distributedFileRepository).findByContentIdAndVersion(CONTENT_IDENTIFIER, VERSION, null);
        }

        private void assertFieldsChanged(DistributedFileEntity df) {
            assertAll(
                    () -> assertThat(df.getContentIdentifier()).as("content identifier").isEqualTo(CONTENT_IDENTIFIER),
                    () -> assertThat(df.getFileName()).as("file name").isEqualTo(FILE_NAME),
                    () -> assertThat(df.getFileData()).as("file data").isEqualTo(FILE_DATA)
            );
        }
    }

    @Nested
    class GetConfigurationPartFile {

        @Test
        void getConfigurationPartFile() {
            var fileEntity = new DistributedFileEntity(VERSION, FILE_NAME, CONTENT_IDENTIFIER, Instant.now());
            fileEntity.setFileData(new byte[]{1, 2, 3});

            when(distributedFileRepository.findByContentIdAndVersion(CONTENT_IDENTIFIER, VERSION, null))
                    .thenReturn(Optional.of(fileEntity));

            final File configurationPartFile = configurationService.getConfigurationPartFile(CONTENT_IDENTIFIER, VERSION);

            assertThat(configurationPartFile.getData()).isEqualTo(new byte[]{1, 2, 3});
        }

        @Test
        void getConfigurationPartFileThrowsNotFound() {
            when(distributedFileRepository.findByContentIdAndVersion(CONTENT_IDENTIFIER, VERSION, null))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> configurationService.getConfigurationPartFile(CONTENT_IDENTIFIER, VERSION))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Configuration part file not found");
        }
    }

    @Nested
    class UploadConfigurationPart {

        private static final String PART_FILE_NAME = "test-configuration-part.xml";

        @Captor
        private ArgumentCaptor<DistributedFileEntity> distributedFileCaptor;

        @Test
        void shouldUploadConfigurationPart() throws Exception {
            try (MockedStatic<OptionalPartsConf> mockedConfParts = mockStatic(OptionalPartsConf.class)) {
                mockedConfParts.when(OptionalPartsConf::getOptionalPartsConf).thenReturn(new OptionalPartsConf(CONF_PARTS_DIR));

                configurationService.uploadConfigurationPart(INTERNAL, TEST_CONFIGURATION_PART,
                        "original-filename.xml", FILE_DATA);

                verify(auditDataHelper).put(SOURCE_TYPE, "INTERNAL");
                verify(auditDataHelper).put(RestApiAuditProperty.CONTENT_IDENTIFIER, TEST_CONFIGURATION_PART);
                verify(auditDataHelper).put(RestApiAuditProperty.PART_FILE_NAME, PART_FILE_NAME);
                verify(auditDataHelper).put(UPLOAD_FILE_NAME, "original-filename.xml");
                verify(auditDataHelper).put(UPLOAD_FILE_HASH_ALGORITHM, DEFAULT_UPLOAD_FILE_HASH_ALGORITHM);
                verify(auditDataHelper).put(UPLOAD_FILE_HASH, "8ffeeed59eae93366fdbb7805821b5f99da7ccdacd718056ddd740d4");

                verify(externalProcessRunner).executeAndThrowOnFailure(eq("test-validate.sh"),
                        contains(PART_FILE_NAME));
                verify(distributedFileRepository).save(distributedFileCaptor.capture());
                final DistributedFileEntity distributedFileEntity = distributedFileCaptor.getValue();

                assertThat(distributedFileEntity.getFileData()).isEqualTo(FILE_DATA);
                assertThat(distributedFileEntity.getContentIdentifier()).isEqualTo(TEST_CONFIGURATION_PART);
                assertThat(distributedFileEntity.getFileName()).isEqualTo(PART_FILE_NAME);
                assertThat(distributedFileEntity.getVersion()).isEqualTo(0);
            }
        }

        @Test
        void shouldThrowException() throws Exception {
            try (MockedStatic<OptionalPartsConf> mockedConfParts = mockStatic(OptionalPartsConf.class)) {
                mockedConfParts.when(OptionalPartsConf::getOptionalPartsConf).thenReturn(new OptionalPartsConf(CONF_PARTS_DIR));

                assertThrows(CodedException.class, () -> configurationService.uploadConfigurationPart(INTERNAL,
                        "NON-EXISTING", "fn", FILE_DATA));

                assertThrows(ConfigurationPartException.class, () -> configurationService.uploadConfigurationPart(EXTERNAL,
                        "TEST-CONFIGURATION-PART", "fn", FILE_DATA));
            }
        }
    }

}
