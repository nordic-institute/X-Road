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

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSigningKeyEntity;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSourceEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationSigningKeyMapperTest {

    private final ConfigurationSigningKeyMapper configurationSigningKeyMapper = new ConfigurationSigningKeyMapperImpl();

    @Test
    void toTargetAsActiveSigningKey() {
        ConfigurationSigningKeyEntity entity = createConfigurationSigningEntity(
                ConfigurationSourceType.EXTERNAL.name(), true);

        ConfigurationSigningKey target = configurationSigningKeyMapper.toTarget(entity);

        assertThat(target.getKeyIdentifier()).isEqualTo(entity.getKeyIdentifier());
        assertThat(target.getCert()).isEqualTo(entity.getCert());
        assertThat(target.getKeyGeneratedAt()).isEqualTo(entity.getKeyGeneratedAt());
        assertThat(target.getTokenIdentifier()).isEqualTo(entity.getTokenIdentifier());
        assertThat(target.getSourceType()).isEqualTo(ConfigurationSourceType.EXTERNAL);
        assertThat(target.isActiveSourceSigningKey()).isTrue();
    }

    @Test
    void toTargetAsInactiveSigningKeyAndWithInvalidSourceType() {
        ConfigurationSigningKeyEntity entity = createConfigurationSigningEntity("INVALID_SOURCE_TYPE",
                false);

        ConfigurationSigningKey target = configurationSigningKeyMapper.toTarget(entity);

        assertThat(target.getSourceType()).isNull();
        assertThat(target.isActiveSourceSigningKey()).isFalse();
    }

    private ConfigurationSigningKeyEntity createConfigurationSigningEntity(
            String sourceType, boolean activeSigningKey) {
        ConfigurationSigningKeyEntity configurationSigningKey = new ConfigurationSigningKeyEntity();
        configurationSigningKey.setKeyIdentifier("keyIdentifier");
        configurationSigningKey.setCert("keyCert".getBytes());
        configurationSigningKey.setKeyGeneratedAt(TimeUtils.now());
        configurationSigningKey.setTokenIdentifier("tokenIdentifier");

        ConfigurationSourceEntity configurationSource = new ConfigurationSourceEntity();
        configurationSource.setSourceType(sourceType);
        if (activeSigningKey) {
            configurationSource.setConfigurationSigningKey(configurationSigningKey);
        }

        configurationSigningKey.setConfigurationSource(configurationSource);
        return configurationSigningKey;
    }
}
