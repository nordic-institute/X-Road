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
package org.niis.xroad.cs.admin.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.core.entity.ConfigurationPropertyEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationPropertyMapperImpl;
import org.niis.xroad.cs.admin.core.repository.ConfigurationPropertyRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyDto;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationPropertyServiceImplTest {

    // an exposed key of CsAdminServiceConfigKeys — the catalogue is the only definition source
    private static final String PROPERTY_NAME = "xroad.admin-service.cache-api-key-ttl";
    private static final String PROPERTY_VALUE = "120";
    private static final String PROPERTY_VALUE_2 = "180";
    private static final String DEFAULT_VALUE = "60";
    private static final String SCOPE = "admin-service";

    // an exposed key of a different provider, used to prove the catalogue merges across all three
    private static final String CATALOGUE_PROPERTY_NAME = "xroad.management-service.rate-limit-cache-size";
    private static final String CATALOGUE_DEFAULT_VALUE = "10000";
    private static final String CATALOGUE_SCOPE = "management-service";

    private static final String NOT_EXPOSED_PROPERTY_NAME = "xroad.admin-service.allowed-hostnames";

    private final ConfigurationPropertyMapperImpl mapper = new ConfigurationPropertyMapperImpl();

    @Mock
    private ConfigurationPropertyRepository repository;

    @Mock
    private AuditDataHelper auditDataHelper;

    private ConfigurationPropertyServiceImpl service;

    @BeforeEach
    void setup() {
        service = new ConfigurationPropertyServiceImpl(repository, mapper, auditDataHelper);
    }

    @Test
    void getConfigurationPropertiesOmitsKeysThatAreNotDeclaredExposed() {
        when(repository.findAll()).thenReturn(List.of());

        Set<ConfigurablePropertyDto> properties = service.getConfigurationProperties();

        assertTrue(properties.stream()
                .noneMatch(p -> NOT_EXPOSED_PROPERTY_NAME.equals(p.getPropertyName())));
    }

    @Test
    void getConfigurationPropertiesMergesAllThreeProviders() {
        when(repository.findAll()).thenReturn(List.of());

        Set<ConfigurablePropertyDto> properties = service.getConfigurationProperties();

        ConfigurablePropertyDto parameter = findProperty(properties, CATALOGUE_PROPERTY_NAME);
        assertEquals(CATALOGUE_DEFAULT_VALUE, parameter.getDefaultValue());
        assertEquals(CATALOGUE_SCOPE, parameter.getScope());
        assertNull(parameter.getCurrentValue());
    }

    @Test
    void getConfigurationPropertiesNotInDatabase() {
        when(repository.findAll()).thenReturn(List.of());

        Set<ConfigurablePropertyDto> properties = service.getConfigurationProperties();

        ConfigurablePropertyDto parameter = findProperty(properties, PROPERTY_NAME);
        assertEquals(DEFAULT_VALUE, parameter.getDefaultValue());
        assertEquals(SCOPE, parameter.getScope());
        assertNull(parameter.getCurrentValue());
    }

    @Test
    void getConfigurationPropertiesFoundInDatabase() {
        var entity = new ConfigurationPropertyEntity(PROPERTY_NAME);
        entity.setPropertyValue(PROPERTY_VALUE_2);
        when(repository.findAll()).thenReturn(List.of(entity));

        Set<ConfigurablePropertyDto> properties = service.getConfigurationProperties();

        ConfigurablePropertyDto parameter = findProperty(properties, PROPERTY_NAME);
        assertEquals(DEFAULT_VALUE, parameter.getDefaultValue());
        assertEquals(SCOPE, parameter.getScope());
        assertEquals(PROPERTY_VALUE_2, parameter.getCurrentValue());
    }

    @Test
    void getConfigurationPropertiesIgnoresStoredRowsForOtherKeys() {
        var entity = new ConfigurationPropertyEntity("xroad.admin-service.cache-default-ttl");
        entity.setPropertyValue(PROPERTY_VALUE_2);
        when(repository.findAll()).thenReturn(List.of(entity));

        Set<ConfigurablePropertyDto> properties = service.getConfigurationProperties();

        ConfigurablePropertyDto parameter = findProperty(properties, PROPERTY_NAME);
        assertEquals(SCOPE, parameter.getScope());
        assertNull(parameter.getCurrentValue());
    }

    @Test
    void updateConfigurablePropertyFoundInDatabase() {
        var entity = new ConfigurationPropertyEntity(PROPERTY_NAME);
        entity.setPropertyValue(PROPERTY_VALUE);
        when(repository.findByPropertyKey(PROPERTY_NAME)).thenReturn(Optional.of(entity));

        service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE_2);

        ArgumentCaptor<ConfigurationPropertyEntity> captor = ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);
        verify(repository).save(captor.capture());

        assertEquals(PROPERTY_VALUE_2, captor.getValue().getPropertyValue());
    }

    @Test
    void updateConfigurablePropertyNotFoundInDatabase() {
        when(repository.findByPropertyKey(PROPERTY_NAME)).thenReturn(Optional.empty());

        service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE);

        ArgumentCaptor<ConfigurationPropertyEntity> captor = ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);
        verify(repository).save(captor.capture());

        assertEquals(PROPERTY_NAME, captor.getValue().getPropertyKey());
        assertEquals(PROPERTY_VALUE, captor.getValue().getPropertyValue());
    }

    @Test
    void updateConfigurablePropertyThrowsWhenKeyIsNotInTheCatalogue() {
        assertThrows(NotFoundException.class,
                () -> service.updateConfigurableProperty("unknown.property.key", PROPERTY_VALUE));

        verifyNoInteractions(repository);
    }

    @Test
    void updateConfigurablePropertyThrowsWhenKeyIsDeclaredButNotExposed() {
        assertThrows(NotFoundException.class,
                () -> service.updateConfigurableProperty(NOT_EXPOSED_PROPERTY_NAME, "example.org"));

        verifyNoInteractions(repository);
    }

    @Test
    void updateConfigurablePropertyRejectsValueTheKeyConverterCannotParse() {
        assertThrows(BadRequestException.class,
                () -> service.updateConfigurableProperty(PROPERTY_NAME, "not-a-number"));

        verifyNoInteractions(repository);
    }

    @Test
    void updateConfigurablePropertyFoundInDatabaseAuditsOldAndNewValueAndDerivedScope() {
        var entity = new ConfigurationPropertyEntity(PROPERTY_NAME);
        entity.setPropertyValue(PROPERTY_VALUE);
        when(repository.findByPropertyKey(PROPERTY_NAME)).thenReturn(Optional.of(entity));

        service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE_2);

        verify(auditDataHelper).put(RestApiAuditProperty.SYSTEM_PROPERTY_NAME, PROPERTY_NAME);
        verify(auditDataHelper).put(RestApiAuditProperty.SYSTEM_PROPERTY_NEW_VALUE, PROPERTY_VALUE_2);
        // scope is derived from the key's own category, not supplied by the caller
        verify(auditDataHelper).put(RestApiAuditProperty.SYSTEM_PROPERTY_SCOPE, SCOPE);
        verify(auditDataHelper).put(RestApiAuditProperty.SYSTEM_PROPERTY_OLD_VALUE, PROPERTY_VALUE);
    }

    @Test
    void updateConfigurablePropertyNotFoundInDatabaseDoesNotAuditOldValue() {
        when(repository.findByPropertyKey(PROPERTY_NAME)).thenReturn(Optional.empty());

        service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE);

        verify(auditDataHelper, never()).put(eq(RestApiAuditProperty.SYSTEM_PROPERTY_OLD_VALUE), any());
    }

    private static ConfigurablePropertyDto findProperty(Set<ConfigurablePropertyDto> properties, String propertyName) {
        return properties.stream()
                .filter(p -> propertyName.equals(p.getPropertyName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Property not found: " + propertyName));
    }
}
