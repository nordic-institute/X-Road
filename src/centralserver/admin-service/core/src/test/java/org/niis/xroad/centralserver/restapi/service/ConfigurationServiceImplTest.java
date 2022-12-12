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

package org.niis.xroad.centralserver.restapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.ConfigurationAnchor;
import org.niis.xroad.cs.admin.api.dto.ConfigurationParts;
import org.niis.xroad.cs.admin.api.dto.GlobalConfDownloadUrl;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.service.SystemParameterService.CENTRAL_SERVER_ADDRESS;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceImplTest {

    private static final String INTERNAL_CONFIGURATION = "internal";
    private static final String CENTRAL_SERVICE = "cs";
    private static final String HA_NODE_NAME = "haNodeName";
    private static final int VERSION = 123;
    private static final String FILE_NAME = "fileName";
    private static final String CONTENT_IDENTIFIER = "Content";
    private static final Instant FILE_UPDATED_AT = Instant.now();
    private static final String HASH = "F5:1B:1F:9C:07:23:4C:DA:E6:4C:99:CB:FC:D8:EE:0E:C5:5F:A4:AF";

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

    @InjectMocks
    private ConfigurationServiceImpl configurationService;

    @Test
    void shouldGetConfigurationParts() {
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
    void shouldGetConfigurationAnchor() {
        when(configurationSourceRepository.findBySourceType(INTERNAL_CONFIGURATION))
                .thenReturn(Optional.of(configurationSourceEntity()));

        final ConfigurationAnchor result = configurationService.getConfigurationAnchor(INTERNAL_CONFIGURATION);

        assertThat(result.getAnchorFileHash()).isEqualTo(HASH);
        assertThat(result.getAnchorGeneratedAt()).isEqualTo(FILE_UPDATED_AT);
    }

    @Test
    void shouldGetGlobalDownloadUrl() {
        when(systemParameterService.getParameterValue(CENTRAL_SERVER_ADDRESS, ""))
                .thenReturn(CENTRAL_SERVICE);

        final GlobalConfDownloadUrl result = configurationService.getGlobalDownloadUrl("INTERNAL");

        assertThat(result.getUrl()).isEqualTo("http://" + CENTRAL_SERVICE + "/internalconf");
    }

    @Test
    void getShouldThrowNotFoundExceptionWhenSourceNotFound() {
        when(configurationSourceRepository.findBySourceType(INTERNAL_CONFIGURATION))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> configurationService.getConfigurationParts(INTERNAL_CONFIGURATION));
        verifyNoInteractions(distributedFileRepository);
    }

    private Set<DistributedFileEntity> distributedFileEntitySet() {
        final DistributedFileEntity entity = new DistributedFileEntity(VERSION, FILE_NAME,
                CONTENT_IDENTIFIER, FILE_UPDATED_AT);
        return Collections.singleton(entity);
    }

    private ConfigurationSourceEntity configurationSourceEntity() {
        return new ConfigurationSourceEntity(HASH, FILE_UPDATED_AT);
    }
}
