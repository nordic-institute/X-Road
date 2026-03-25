/*
 * The MIT License
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
package org.niis.xroad.securityserver.restapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.securityserver.restapi.config.ConfigurableSystemPropertiesConfiguration.ConfigurableProperties;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServerConfigurablePropertyDto;
import org.niis.xroad.securityserver.restapi.repository.ConfigurationPropertyRepository;
import org.niis.xroad.serverconf.impl.entity.ConfigurationPropertyEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConfigurablePropertiesServiceTest {

    private static final String PROPERTY_NAME = "xroad.proxy-ui-api.client-timeout";
    private static final String PROPERTY_VALUE = "10000";
    private static final String PROPERTY_VALUE_2 = "11000";
    private static final String DEFAULT_VALUE = "5000";
    private static final String SCOPE = "proxy-ui-api";
    private static final String COMMON_SCOPE = "common";

    @Mock
    private ConfigurableProperties configurableProperties;
    @Mock
    private ConfigurationPropertyRepository repository;

    private ConfigurablePropertiesService service;

    @BeforeEach
    public void setup() {
        service = new ConfigurablePropertiesService(configurableProperties, repository);
    }

    @Test
    public void getConfigurationPropertiesNotConfigured() {
        when(configurableProperties.getConfigurableProperties()).thenReturn(Map.of());

        Set<SecurityServerConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();
        assertEquals(0, systemParameters.size());
    }

    @Test
    public void getConfigurationPropertiesNotInDatabase() {
        var systemParameter = new ConfigurableProperties.ConfigurableProperty();
        systemParameter.setPropertyName(PROPERTY_NAME);
        systemParameter.setDefaultValue(DEFAULT_VALUE);
        when(configurableProperties.getConfigurableProperties()).thenReturn(Map.of(SCOPE, List.of(systemParameter)));
        when(repository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME, SCOPE)).thenReturn(Optional.empty());

        Set<SecurityServerConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();
        assertEquals(1, systemParameters.size());
        SecurityServerConfigurablePropertyDto parameter = systemParameters.iterator().next();
        assertCommonConfigurablePropertyDtoFields(parameter);
        assertEquals(SCOPE, parameter.getScope());
        assertNull(parameter.getCurrentValue());
    }

    @Test
    public void getConfigurationPropertiesFoundInDatabase() {
        var systemParameter = new ConfigurableProperties.ConfigurableProperty();
        systemParameter.setPropertyName(PROPERTY_NAME);
        systemParameter.setDefaultValue(DEFAULT_VALUE);
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyValue(PROPERTY_VALUE_2);
        when(configurableProperties.getConfigurableProperties()).thenReturn(Map.of(SCOPE, List.of(systemParameter)));
        when(repository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME, SCOPE)).thenReturn(Optional.of(entity));

        Set<SecurityServerConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();
        assertEquals(1, systemParameters.size());
        SecurityServerConfigurablePropertyDto parameter = systemParameters.iterator().next();
        assertCommonConfigurablePropertyDtoFields(parameter);
        assertEquals(SCOPE, parameter.getScope());
        assertEquals(PROPERTY_VALUE_2, parameter.getCurrentValue());
    }

    @Test
    public void getConfigurationPropertiesFoundInDatabaseCommonScope() {
        var systemParameter = new ConfigurableProperties.ConfigurableProperty();
        systemParameter.setPropertyName(PROPERTY_NAME);
        systemParameter.setDefaultValue(DEFAULT_VALUE);
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyValue(PROPERTY_VALUE_2);
        when(configurableProperties.getConfigurableProperties()).thenReturn(Map.of(COMMON_SCOPE, List.of(systemParameter)));
        when(repository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME, null)).thenReturn(Optional.of(entity));

        Set<SecurityServerConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();
        assertEquals(1, systemParameters.size());
        SecurityServerConfigurablePropertyDto parameter = systemParameters.iterator().next();
        assertCommonConfigurablePropertyDtoFields(parameter);
        assertNull(parameter.getScope());
        assertEquals(PROPERTY_VALUE_2, parameter.getCurrentValue());
    }

    @Test
    public void updateConfigurablePropertyFoundInDatabase() {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyValue(PROPERTY_VALUE);
        when(repository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME, SCOPE)).thenReturn(Optional.of(entity));

        service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE_2, SCOPE);

        ArgumentCaptor<ConfigurationPropertyEntity> captor =
                ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);

        verify(repository).saveOrUpdate(captor.capture());

        ConfigurationPropertyEntity capturedEntity = captor.getValue();
        assertEquals(PROPERTY_VALUE_2, capturedEntity.getPropertyValue());
    }

    @Test
    public void updateConfigurablePropertyNotFoundInDatabase() {
        when(repository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME, SCOPE)).thenReturn(Optional.empty());

        service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE, SCOPE);

        ArgumentCaptor<ConfigurationPropertyEntity> captor =
                ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);

        verify(repository).saveOrUpdate(captor.capture());

        ConfigurationPropertyEntity capturedEntity = captor.getValue();
        assertEquals(PROPERTY_NAME, capturedEntity.getPropertyKey());
        assertEquals(PROPERTY_VALUE, capturedEntity.getPropertyValue());
        assertEquals(SCOPE, capturedEntity.getScope());
    }

    @Test
    public void updateConfigurablePropertyNotFoundInDatabaseScopeIsNull() {
        when(repository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME, null)).thenReturn(Optional.empty());

        service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE, null);

        ArgumentCaptor<ConfigurationPropertyEntity> captor =
                ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);

        verify(repository).saveOrUpdate(captor.capture());

        ConfigurationPropertyEntity capturedEntity = captor.getValue();
        assertEquals(PROPERTY_NAME, capturedEntity.getPropertyKey());
        assertEquals(PROPERTY_VALUE, capturedEntity.getPropertyValue());
        assertNull(capturedEntity.getScope());
    }

    private static void assertCommonConfigurablePropertyDtoFields(SecurityServerConfigurablePropertyDto parameter) {
        assertEquals(PROPERTY_NAME, parameter.getPropertyName());
        assertEquals(DEFAULT_VALUE, parameter.getDefaultValue());
    }

}
