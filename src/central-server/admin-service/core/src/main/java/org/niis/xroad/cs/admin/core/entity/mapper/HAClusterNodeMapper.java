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
package org.niis.xroad.cs.admin.core.entity.mapper;

import ee.ria.xroad.common.util.TimeUtils;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.niis.xroad.cs.admin.api.converter.GenericUniDirectionalMapper;
import org.niis.xroad.cs.admin.api.domain.HAClusterNode;
import org.niis.xroad.cs.admin.api.domain.HAClusterNodeStatus;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.config.AdminServiceProperties;
import org.niis.xroad.cs.admin.core.entity.HAClusterStatusViewEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.temporal.ChronoUnit;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class HAClusterNodeMapper implements GenericUniDirectionalMapper<HAClusterStatusViewEntity, HAClusterNode> {

    private static final int CONFIGURATION_GENERATION_WARN_THRESHOLD = 10;

    @Autowired
    private AdminServiceProperties properties;

    @Autowired
    protected SystemParameterService systemParameterService;

    @Mapping(target = "status", source = "entity", qualifiedByName = "mapNodeStatus")
    public abstract HAClusterNode toTarget(HAClusterStatusViewEntity entity);

    @Named("mapNodeStatus")
    protected HAClusterNodeStatus mapNodeStatus(HAClusterStatusViewEntity entity) {
        if (entity.getConfigurationGenerated() == null) {
            return HAClusterNodeStatus.UNKNOWN;
        }
        long secondsPassedSinceConfGeneration = ChronoUnit.SECONDS.between(entity.getConfigurationGenerated(), TimeUtils.now());
        if (secondsPassedSinceConfGeneration > systemParameterService.getConfExpireIntervalSeconds()) {
            return HAClusterNodeStatus.ERROR;
        } else if (secondsPassedSinceConfGeneration
                > properties.getGlobalConfigurationGenerationRateInSeconds() + CONFIGURATION_GENERATION_WARN_THRESHOLD) {
            return HAClusterNodeStatus.WARN;
        } else {
            return HAClusterNodeStatus.OK;
        }
    }

}
