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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.dto.ConfigurationAnchor;
import org.niis.xroad.cs.admin.api.dto.ConfigurationParts;
import org.niis.xroad.cs.admin.api.dto.File;
import org.niis.xroad.cs.admin.api.dto.GlobalConfDownloadUrl;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSourceEntity;
import org.niis.xroad.cs.admin.core.entity.DistributedFileEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.DistributedFileMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.DistributedFileMapperImpl;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSourceRepository;
import org.niis.xroad.cs.admin.core.repository.DistributedFileRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceImplTest {

    private static final String INTERNAL_CONFIGURATION = "internal";
    private static final String EXTERNAL_CONFIGURATION = "external";
    private static final String CENTRAL_SERVICE = "cs";
    private static final String HA_NODE_NAME = "haNodeName";
    private static final int VERSION = 123;
    private static final String FILE_NAME = "fileName";
    private static final String CONTENT_IDENTIFIER = "Content";
    private static final Instant FILE_UPDATED_AT = Instant.now();
    private static final String HASH = "F5:1B:1F:9C:07:23:4C:DA:E6:4C:99:CB:FC:D8:EE:0E:C5:5F:A4:AF";
    private static final byte[] FILE_DATA = "file-data".getBytes(UTF_8);
    private static final String NODE_LOCAL_CONTENT_ID = CONTENT_ID_PRIVATE_PARAMETERS;


    @Mock
    private SystemParameterService systemParameterService;
    @Mock
    private ConfigurationSourceRepository configurationSourceRepository;
    @Mock
    private DistributedFileRepository distributedFileRepository;
    @Mock
    private ConfigurationSourceEntity configurationSource;
    @Spy
    private DistributedFileMapper distributedFileMapper = new DistributedFileMapperImpl();

    private ConfigurationServiceImpl configurationService;
    private ConfigurationServiceImpl configurationServiceHa;

    @BeforeEach
    void initConfigurationService() {
        configurationService = createConfigurationService(new HAConfigStatus(HA_NODE_NAME, false));
        configurationServiceHa = createConfigurationService(new HAConfigStatus(HA_NODE_NAME, true));
    }

    @Test
    void shouldGetInternalConfigurationParts() {
        when(configurationSourceRepository.findBySourceType(INTERNAL_CONFIGURATION))
                .thenReturn(Optional.of(configurationSource));
        when(configurationSource.getHaNodeName()).thenReturn(HA_NODE_NAME);
        when(distributedFileRepository.findAllByHaNodeName(HA_NODE_NAME)).thenReturn(distributedFileEntitySet());

        final Set<ConfigurationParts> result = configurationService.getConfigurationParts(INTERNAL_CONFIGURATION);

        result.forEach(configurationsParts -> {
            assertThat(configurationsParts.getVersion()).isEqualTo(VERSION);
            assertThat(configurationsParts.getFileName()).isEqualTo(FILE_NAME);
            assertThat(configurationsParts.getFileUpdatedAt()).isEqualTo(FILE_UPDATED_AT);
        });
    }

    @Test
    void shouldGetExternalConfigurationParts() {
        when(configurationSourceRepository.findBySourceType(EXTERNAL_CONFIGURATION))
                .thenReturn(Optional.of(configurationSource));
        when(configurationSource.getHaNodeName()).thenReturn(HA_NODE_NAME);
        when(distributedFileRepository.findAllByHaNodeName(HA_NODE_NAME)).thenReturn(distributedFileEntitySet());

        final Set<ConfigurationParts> result = configurationService.getConfigurationParts(EXTERNAL_CONFIGURATION);

        result.forEach(configurationsParts -> {
            assertThat(configurationsParts.getVersion()).isEqualTo(VERSION);
            assertThat(configurationsParts.getFileName()).isEqualTo(FILE_NAME);
            assertThat(configurationsParts.getFileUpdatedAt()).isEqualTo(FILE_UPDATED_AT);
        });
    }

    @Test
    void shouldGetInternalConfigurationAnchor() {
        when(configurationSourceRepository.findBySourceType(INTERNAL_CONFIGURATION))
                .thenReturn(Optional.of(configurationSourceEntity()));

        final ConfigurationAnchor result = configurationService.getConfigurationAnchor(INTERNAL_CONFIGURATION);

        assertThat(result.getAnchorFileHash()).isEqualTo(HASH);
        assertThat(result.getAnchorGeneratedAt()).isEqualTo(FILE_UPDATED_AT);
    }

    @Test
    void shouldGetExternalConfigurationAnchor() {
        when(configurationSourceRepository.findBySourceType(EXTERNAL_CONFIGURATION))
                .thenReturn(Optional.of(configurationSourceEntity()));

        final ConfigurationAnchor result = configurationService.getConfigurationAnchor(EXTERNAL_CONFIGURATION);

        assertThat(result.getAnchorFileHash()).isEqualTo(HASH);
        assertThat(result.getAnchorGeneratedAt()).isEqualTo(FILE_UPDATED_AT);
    }

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

    @Test
    void shouldThrowNotFoundExceptionWhenSourceNotFound() {
        when(configurationSourceRepository.findBySourceType(INTERNAL_CONFIGURATION))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> configurationService.getConfigurationParts(INTERNAL_CONFIGURATION));
        verifyNoInteractions(distributedFileRepository);
    }

    private ConfigurationServiceImpl createConfigurationService(HAConfigStatus haConfigStatus) {
        return new ConfigurationServiceImpl(
                systemParameterService,
                haConfigStatus,
                configurationSourceRepository,
                distributedFileRepository,
                distributedFileMapper);
    }

    private Set<DistributedFileEntity> distributedFileEntitySet() {
        final DistributedFileEntity entity = new DistributedFileEntity(VERSION, FILE_NAME,
                CONTENT_IDENTIFIER, FILE_UPDATED_AT);
        return Collections.singleton(entity);
    }

    private ConfigurationSourceEntity configurationSourceEntity() {
        return new ConfigurationSourceEntity(HASH, FILE_UPDATED_AT);
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
}
