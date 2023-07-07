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
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.cs.admin.api.dto.ConfigurationParts;
import org.niis.xroad.cs.admin.api.dto.File;
import org.niis.xroad.cs.admin.api.dto.GlobalConfDownloadUrl;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.service.ConfigurationService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSourceEntity;
import org.niis.xroad.cs.admin.core.entity.DistributedFileEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.DistributedFileMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.DistributedFileMapperImpl;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSigningKeyRepository;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSourceRepository;
import org.niis.xroad.cs.admin.core.repository.DistributedFileRepository;
import org.niis.xroad.cs.admin.core.validation.ConfigurationPartValidator;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;

import java.time.Instant;
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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.EXTERNAL;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.INTERNAL;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SOURCE_TYPE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.UPLOAD_FILE_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.UPLOAD_FILE_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.UPLOAD_FILE_NAME;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceImplTest {

    private static final String CENTRAL_SERVICE = "cs";
    private static final String HA_NODE_NAME = "haNodeName";
    private static final int VERSION = 123;
    private static final String FILE_NAME = "fileName";
    private static final String FILE_NAME_PRIVATE_PARAMS = "private-params.xml";
    private static final String CONTENT_IDENTIFIER = "Content";
    private static final Instant FILE_UPDATED_AT = Instant.now();
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
    private ConfigurationPartValidator configurationPartValidator;
    @Mock
    private ConfigurationService configurationService;
    @Spy
    private DistributedFileMapper distributedFileMapper = new DistributedFileMapperImpl();
    @Mock
    private ConfigurationSigningKeyRepository configurationSigningKeyRepository;
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
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(INTERNAL.name().toLowerCase(), HA_NODE_NAME))
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
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(EXTERNAL.name().toLowerCase(), HA_NODE_NAME))
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
        void shouldReturnEmptyListWhenSourceNotFound() {
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(INTERNAL.name().toLowerCase(), HA_NODE_NAME))
                    .thenReturn(Optional.empty());

            assertThat(configurationService.getConfigurationParts(INTERNAL)).isEmpty();
            verifyNoInteractions(distributedFileRepository);
        }
    }

    @Nested
    class GetDownloadUrl {
        @Test
        void shouldGetInternalGlobalDownloadUrl() {
            when(systemParameterService.getCentralServerAddress())
                    .thenReturn(CENTRAL_SERVICE);

            final GlobalConfDownloadUrl result = configurationService.getGlobalDownloadUrl(INTERNAL);

            assertThat(result.getUrl()).isEqualTo("http://" + CENTRAL_SERVICE + "/internalconf");
        }

        @Test
        void shouldGetExternalGlobalDownloadUrl() {
            when(systemParameterService.getCentralServerAddress())
                    .thenReturn(CENTRAL_SERVICE);

            final GlobalConfDownloadUrl result = configurationService.getGlobalDownloadUrl(EXTERNAL);

            assertThat(result.getUrl()).isEqualTo("http://" + CENTRAL_SERVICE + "/externalconf");
        }
    }

    private ConfigurationServiceImpl createConfigurationService(HAConfigStatus haConfigStatus) {
        return new ConfigurationServiceImpl(
                systemParameterService,
                haConfigStatus,
                configurationSourceRepository,
                configurationSigningKeyRepository,
                distributedFileRepository,
                distributedFileMapper,
                auditDataHelper,
                configurationPartValidator);
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

        @Test
        void hasSigningKeysShouldReturnTrue() {
            when(configurationSigningKeyRepository.countSigningKeysForSourceType(INTERNAL.name().toLowerCase(), HA_NODE_NAME))
                    .thenReturn(1);
            assertThat(configurationServiceHa.hasSigningKeys(INTERNAL)).isTrue();
        }

        @Test
        void hasSigningKeysShouldReturnFalse() {
            when(configurationSigningKeyRepository.countSigningKeysForSourceType(INTERNAL.name().toLowerCase(), HA_NODE_NAME))
                    .thenReturn(0);
            assertThat(configurationServiceHa.hasSigningKeys(INTERNAL)).isFalse();
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

                verify(configurationPartValidator).validate(TEST_CONFIGURATION_PART, FILE_DATA);

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

                assertThrows(ServiceException.class, () -> configurationService.uploadConfigurationPart(EXTERNAL,
                        "TEST-CONFIGURATION-PART", "fn", FILE_DATA));
            }
        }
    }
}
