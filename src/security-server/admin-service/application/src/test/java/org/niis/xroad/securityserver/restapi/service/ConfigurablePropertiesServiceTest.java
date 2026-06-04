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
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.securityserver.restapi.config.ConfigurableSystemPropertiesConfiguration.ConfigurablePropertiesDefinition;
import org.niis.xroad.securityserver.restapi.dto.ConfigurationPropertyAuditListener;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServerConfigurablePropertyDto;
import org.niis.xroad.securityserver.restapi.repository.ConfigurationPropertyRepository;
import org.niis.xroad.serverconf.impl.entity.ConfigurationPropertyEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurablePropertiesServiceTest {

    private static final String PROPERTY_NAME = "xroad.proxy-ui-api.client-timeout";
    private static final String PROPERTY_VALUE = "10000";
    private static final String PROPERTY_VALUE_2 = "11000";
    private static final String DEFAULT_VALUE = "5000";
    private static final String SCOPE = "proxy-ui-api";

    @Mock
    private ConfigurablePropertiesDefinition configurableProperties;
    @Mock
    private ConfigurationPropertyRepository repository;

    private ConfigurablePropertiesService service;

    @BeforeEach
    void setup() {
        service = new ConfigurablePropertiesService(configurableProperties, repository);
    }

    @Test
    void getConfigurationPropertiesNotConfigured() {
        when(configurableProperties.getConfigurableProperties()).thenReturn(List.of());

        Set<SecurityServerConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();
        assertEquals(0, systemParameters.size());
    }

    @Test
    void getConfigurationPropertiesNotInDatabase() {
        var systemParameter = new ConfigurablePropertiesDefinition.ConfigurableProperty();
        systemParameter.setPropertyName(PROPERTY_NAME);
        systemParameter.setDefaultValue(DEFAULT_VALUE);
        systemParameter.setScope(SCOPE);
        when(configurableProperties.getConfigurableProperties()).thenReturn(List.of(systemParameter));
        when(repository.findAll()).thenReturn(List.of());

        Set<SecurityServerConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();
        assertEquals(1, systemParameters.size());
        SecurityServerConfigurablePropertyDto parameter = systemParameters.iterator().next();
        assertCommonConfigurablePropertyDtoFields(parameter);
        assertEquals(SCOPE, parameter.getScope());
        assertNull(parameter.getCurrentValue());
    }

    @Test
    void getConfigurationPropertiesFoundInDatabase() {
        var systemParameter = new ConfigurablePropertiesDefinition.ConfigurableProperty();
        systemParameter.setPropertyName(PROPERTY_NAME);
        systemParameter.setDefaultValue(DEFAULT_VALUE);
        systemParameter.setScope(SCOPE);
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(PROPERTY_NAME);
        entity.setScope(SCOPE);
        entity.setPropertyValue(PROPERTY_VALUE_2);
        when(configurableProperties.getConfigurableProperties()).thenReturn(List.of(systemParameter));
        when(repository.findAll()).thenReturn(List.of(entity));

        Set<SecurityServerConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();
        assertEquals(1, systemParameters.size());
        SecurityServerConfigurablePropertyDto parameter = systemParameters.iterator().next();
        assertCommonConfigurablePropertyDtoFields(parameter);
        assertEquals(SCOPE, parameter.getScope());
        assertEquals(PROPERTY_VALUE_2, parameter.getCurrentValue());
    }

    @Test
    void getConfigurationPropertiesFoundInDatabaseScopeIsDifferent() {
        var systemParameter = new ConfigurablePropertiesDefinition.ConfigurableProperty();
        systemParameter.setPropertyName(PROPERTY_NAME);
        systemParameter.setDefaultValue(DEFAULT_VALUE);
        systemParameter.setScope(SCOPE);
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(PROPERTY_NAME);
        entity.setPropertyValue(PROPERTY_VALUE_2);
        when(configurableProperties.getConfigurableProperties()).thenReturn(List.of(systemParameter));
        when(repository.findAll()).thenReturn(List.of(entity));

        Set<SecurityServerConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();
        assertEquals(1, systemParameters.size());
        SecurityServerConfigurablePropertyDto parameter = systemParameters.iterator().next();
        assertCommonConfigurablePropertyDtoFields(parameter);
        assertEquals(SCOPE, parameter.getScope());
        assertNull(parameter.getCurrentValue());
    }

    @Test
    void updateConfigurablePropertyFoundInDatabase() {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyValue(PROPERTY_VALUE);
        when(configurableProperties.getConfigurableProperties()).thenReturn(List.of(definedPropertyWithScope(SCOPE)));
        when(repository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME, SCOPE)).thenReturn(Optional.of(entity));

        service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE_2, SCOPE);

        ArgumentCaptor<ConfigurationPropertyEntity> captor =
                ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);

        verify(repository).saveOrUpdate(captor.capture());

        ConfigurationPropertyEntity capturedEntity = captor.getValue();
        assertEquals(PROPERTY_VALUE_2, capturedEntity.getPropertyValue());
    }

    @Test
    void updateConfigurablePropertyNotFoundInDatabase() {
        when(configurableProperties.getConfigurableProperties()).thenReturn(List.of(definedPropertyWithScope(SCOPE)));
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
    void updateConfigurablePropertyNotFoundInDatabaseScopeIsNull() {
        when(configurableProperties.getConfigurableProperties()).thenReturn(List.of(definedProperty()));
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

    @Test
    void updateConfigurablePropertyThrowsWhenScopeNotInDefinition() {
        when(configurableProperties.getConfigurableProperties()).thenReturn(List.of(definedPropertyWithScope("other-scope")));

        assertThrows(NotFoundException.class,
                () -> service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE, SCOPE));
    }

    @Test
    void updateConfigurablePropertyThrowsWhenKeyNotInDefinition() {
        when(configurableProperties.getConfigurableProperties()).thenReturn(List.of(definedPropertyWithScope(SCOPE)));

        assertThrows(NotFoundException.class,
                () -> service.updateConfigurableProperty("unknown.property.key", PROPERTY_VALUE, SCOPE));
    }

    @Test
    void updateConfigurablePropertyFoundInDatabaseWithAuditListener() {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(PROPERTY_NAME);
        entity.setPropertyValue(PROPERTY_VALUE);
        entity.setScope(SCOPE);
        var auditListener = mock(ConfigurationPropertyAuditListener.class);
        when(configurableProperties.getConfigurableProperties()).thenReturn(List.of(definedPropertyWithScope(SCOPE)));
        when(repository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME, SCOPE)).thenReturn(Optional.of(entity));

        service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE_2, SCOPE, auditListener);

        ArgumentCaptor<ConfigurationPropertyEntity> captor =
                ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);

        verify(repository).saveOrUpdate(captor.capture());

        ConfigurationPropertyEntity capturedEntity = captor.getValue();
        assertEquals(PROPERTY_VALUE_2, capturedEntity.getPropertyValue());

        verify(auditListener).onUpdate(PROPERTY_NAME, PROPERTY_VALUE_2, SCOPE, PROPERTY_VALUE);
    }

    @Test
    void updateConfigurablePropertyNotFoundInDatabaseWithAuditListener() {
        var auditListener = mock(ConfigurationPropertyAuditListener.class);
        when(configurableProperties.getConfigurableProperties()).thenReturn(List.of(definedPropertyWithScope(SCOPE)));
        when(repository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME, SCOPE)).thenReturn(Optional.empty());

        service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE, SCOPE, auditListener);

        ArgumentCaptor<ConfigurationPropertyEntity> captor =
                ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);

        verify(repository).saveOrUpdate(captor.capture());

        ConfigurationPropertyEntity capturedEntity = captor.getValue();
        assertEquals(PROPERTY_NAME, capturedEntity.getPropertyKey());
        assertEquals(PROPERTY_VALUE, capturedEntity.getPropertyValue());
        assertEquals(SCOPE, capturedEntity.getScope());

        verify(auditListener).onUpdate(PROPERTY_NAME, PROPERTY_VALUE, SCOPE, null);
    }

    private static void assertCommonConfigurablePropertyDtoFields(SecurityServerConfigurablePropertyDto parameter) {
        assertEquals(PROPERTY_NAME, parameter.getPropertyName());
        assertEquals(DEFAULT_VALUE, parameter.getDefaultValue());
    }

    private static ConfigurablePropertiesDefinition.ConfigurableProperty definedProperty() {
        var property = new ConfigurablePropertiesDefinition.ConfigurableProperty();
        property.setPropertyName(PROPERTY_NAME);
        property.setDefaultValue(DEFAULT_VALUE);
        return property;
    }

    private static ConfigurablePropertiesDefinition.ConfigurableProperty definedPropertyWithScope(String scope) {
        var property = definedProperty();
        property.setScope(scope);
        return property;
    }

}
