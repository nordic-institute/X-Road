/**
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
package org.niis.xroad.cs.admin.core.entity.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.HAClusterNodeStatus;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.config.AdminServiceProperties;
import org.niis.xroad.cs.admin.core.entity.HAClusterStatusViewEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl.DEFAULT_CONF_EXPIRE_INTERVAL_SECONDS;

@ExtendWith(MockitoExtension.class)
class HAClusterNodeMapperTest {

    @Mock
    private SystemParameterService systemParameterService;

    @Mock
    private AdminServiceProperties properties;

    @InjectMocks
    private HAClusterNodeMapper mapper = new HAClusterNodeMapperImpl();

    @BeforeEach
    void setUp() {
        lenient().when(systemParameterService.getConfExpireIntervalSeconds()).thenReturn(DEFAULT_CONF_EXPIRE_INTERVAL_SECONDS);
        lenient().when(properties.getGlobalConfigurationGenerationRateInSeconds()).thenReturn(60);
    }

    @ParameterizedTest
    @MethodSource("configurationsGeneratedWithExpectedStatus")
    void configurationGeneratedShouldAffectStatus(Instant configurationGenerated, HAClusterNodeStatus expectedNodeStatus) {
        var entity = createEntity(configurationGenerated);
        var result = mapper.toTarget(entity);

        assertThat(result.getNodeName()).isEqualTo(entity.getNodeName());
        assertThat(result.getNodeAddress()).isEqualTo(entity.getNodeAddress());
        assertThat(result.getConfigurationGenerated()).isEqualTo(entity.getConfigurationGenerated());
        assertThat(result.getStatus()).isEqualTo(expectedNodeStatus);
    }

    private static Stream<Arguments> configurationsGeneratedWithExpectedStatus() {
        return Stream.of(
                Arguments.of(Instant.now(), HAClusterNodeStatus.OK),
                Arguments.of(Instant.now().minus(80, ChronoUnit.SECONDS), HAClusterNodeStatus.WARN),
                Arguments.of(Instant.now().minus(610, ChronoUnit.SECONDS), HAClusterNodeStatus.ERROR),
                Arguments.of(null, HAClusterNodeStatus.UNKNOWN)
        );
    }

    private HAClusterStatusViewEntity createEntity(Instant confGenerated) {
        var entity = new HAClusterStatusViewEntity();
        entity.setNodeName("node_0");
        entity.setNodeAddress("localhost");
        entity.setConfigurationGenerated(confGenerated);
        return entity;
    }

}
