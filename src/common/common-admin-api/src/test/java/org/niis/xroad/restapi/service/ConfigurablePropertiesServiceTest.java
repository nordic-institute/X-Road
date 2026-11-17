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
package org.niis.xroad.restapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.Prefix;
import org.niis.xroad.common.properties.config.Validator;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.entity.ConfigurationPropertyEntity;
import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyDto;
import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyValueSource;
import org.niis.xroad.restapi.repository.ConfigurationPropertyRepository;
import org.niis.xroad.restapi.service.ConfigurablePropertySource.CountryProfile;

import java.util.List;
import java.util.Map;
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
class ConfigurablePropertiesServiceTest {

    private static final Prefix SCOPED = Prefix.of(Category.PROXY, "xroad.test-scoped");
    private static final Prefix OTHER_SCOPED = Prefix.of(Category.SIGNER, "xroad.test-other-scoped");
    private static final Prefix SCOPELESS = Prefix.of("xroad.test-common");

    private static final ConfigKey<Integer> EXPOSED_KEY = SCOPED.integer("retry-count")
            .withDefaultValue(60).exposedInUi().build();
    private static final ConfigKey<Integer> CATALOGUE_KEY = OTHER_SCOPED.integer("port")
            .withDefaultValue(5566).exposedInUi().build();
    private static final ConfigKey<String> SCOPELESS_KEY = SCOPELESS.string("temp-files-path")
            .withValidator(Validator.nonEmpty()).exposedInUi().build();
    private static final ConfigKey<String> CONTAINER_KEY = SCOPED.string("mode-aware")
            .withDefaultValue("native-value").withContainerDefaultValue("container-value").exposedInUi().build();
    private static final ConfigKey<String> HIDDEN_KEY = SCOPED.string("hidden")
            .withDefaultValue("secret").build();

    private static final List<ConfigKeyProvider> PROVIDERS = List.of(
            ConfigKeyProvider.forPrefix(SCOPED),
            ConfigKeyProvider.forPrefix(OTHER_SCOPED),
            ConfigKeyProvider.forPrefix(SCOPELESS));

    private static final ConfigurablePropertySource TEST_SOURCE = sourceWith(DeploymentMode.NATIVE,
            CountryProfile.NONE);

    private static ConfigurablePropertySource sourceWith(DeploymentMode mode, CountryProfile profile) {
        return new ConfigurablePropertySource() {
            @Override
            public List<ConfigKeyProvider> getConfigKeyProviders() {
                return PROVIDERS;
            }

            @Override
            public DeploymentMode deploymentMode() {
                return mode;
            }

            @Override
            public CountryProfile countryProfile() {
                return profile;
            }

            @Override
            public String categoryToScope(Category category) {
                return switch (category) {
                    case PROXY -> "proxy";
                    case SIGNER -> "signer";
                    case COMMON -> null;
                    default -> throw new IllegalArgumentException("Unexpected category: " + category);
                };
            }
        };
    }

    private static final String PROFILE_VALUE = "7";
    private static final CountryProfile PROFILE_WITH_RETRY_COUNT =
            new CountryProfile("XX", Map.of(EXPOSED_KEY.key(), PROFILE_VALUE));

    private static final String PROPERTY_NAME = EXPOSED_KEY.key();
    private static final String PROPERTY_VALUE = "10000";
    private static final String PROPERTY_VALUE_2 = "11000";
    private static final String DEFAULT_VALUE = "60";
    private static final String SCOPE = "proxy";

    private static final String SCOPELESS_PROPERTY_NAME = SCOPELESS_KEY.key();
    private static final String SCOPELESS_PROPERTY_VALUE = "/var/tmp/xroad-test/";

    private static final String CATALOGUE_PROPERTY_NAME = CATALOGUE_KEY.key();
    private static final String CATALOGUE_DEFAULT_VALUE = "5566";
    private static final String CATALOGUE_SCOPE = "signer";

    @Mock
    private ConfigurationPropertyRepository repository;

    @Mock
    private AuditDataHelper auditDataHelper;

    private ConfigurablePropertiesService service;

    @BeforeEach
    void setup() {
        service = new ConfigurablePropertiesService(auditDataHelper, repository, TEST_SOURCE);
    }

    @Test
    void getConfigurationPropertiesOmitsKeysThatAreNotDeclaredExposed() {
        when(repository.findAll()).thenReturn(List.of());

        Set<ConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();

        assertTrue(systemParameters.stream()
                .noneMatch(p -> HIDDEN_KEY.key().equals(p.getPropertyName())));
    }

    @Test
    void getConfigurationPropertiesIncludesDslCatalogueDerivedProperty() {
        when(repository.findAll()).thenReturn(List.of());

        Set<ConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();

        ConfigurablePropertyDto parameter = findProperty(systemParameters, CATALOGUE_PROPERTY_NAME);
        assertEquals(CATALOGUE_DEFAULT_VALUE, parameter.getDefaultValue());
        assertEquals(CATALOGUE_SCOPE, parameter.getScope());
        assertNull(parameter.getCurrentValue());
    }

    @Test
    void getConfigurationPropertiesNotInDatabase() {
        when(repository.findAll()).thenReturn(List.of());

        Set<ConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();

        ConfigurablePropertyDto parameter = findProperty(systemParameters, PROPERTY_NAME);
        assertEquals(DEFAULT_VALUE, parameter.getDefaultValue());
        assertEquals(SCOPE, parameter.getScope());
        assertNull(parameter.getCurrentValue());
    }

    @Test
    void getConfigurationPropertiesFoundInDatabase() {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(PROPERTY_NAME);
        entity.setPropertyValue(PROPERTY_VALUE_2);
        when(repository.findAll()).thenReturn(List.of(entity));

        Set<ConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();

        ConfigurablePropertyDto parameter = findProperty(systemParameters, PROPERTY_NAME);
        assertEquals(DEFAULT_VALUE, parameter.getDefaultValue());
        assertEquals(SCOPE, parameter.getScope());
        assertEquals(PROPERTY_VALUE_2, parameter.getCurrentValue());
    }

    @Test
    void getConfigurationPropertiesUsesTheSharedRowWhenNodeRowsAreReturnedToo() {
        var nodeRow = new ConfigurationPropertyEntity();
        nodeRow.setPropertyKey(PROPERTY_NAME);
        nodeRow.setPropertyValue(PROPERTY_VALUE_2);
        nodeRow.setHaNodeName("node_1");
        var sharedRow = new ConfigurationPropertyEntity();
        sharedRow.setPropertyKey(PROPERTY_NAME);
        sharedRow.setPropertyValue(PROPERTY_VALUE);
        when(repository.findAll()).thenReturn(List.of(nodeRow, sharedRow));

        Set<ConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();

        assertEquals(PROPERTY_VALUE, findProperty(systemParameters, PROPERTY_NAME).getCurrentValue());
    }

    @Test
    void getConfigurationPropertiesIgnoresStoredRowsForOtherKeys() {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(CATALOGUE_PROPERTY_NAME);
        entity.setPropertyValue(PROPERTY_VALUE_2);
        when(repository.findAll()).thenReturn(List.of(entity));

        Set<ConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();

        ConfigurablePropertyDto parameter = findProperty(systemParameters, PROPERTY_NAME);
        assertEquals(SCOPE, parameter.getScope());
        assertNull(parameter.getCurrentValue());
    }

    @Test
    void getConfigurationPropertiesGroupsSharedKeysWithoutScope() {
        when(repository.findAll()).thenReturn(List.of());

        Set<ConfigurablePropertyDto> systemParameters = service.getConfigurationProperties();

        assertNull(findProperty(systemParameters, SCOPELESS_PROPERTY_NAME).getScope());
    }

    @Test
    void storedValueWinsOverProfileValueAndReportsDbSource() {
        var profiledService = serviceWith(DeploymentMode.NATIVE, PROFILE_WITH_RETRY_COUNT);
        when(repository.findAll()).thenReturn(List.of(row(PROPERTY_NAME, PROPERTY_VALUE_2)));

        var parameter = findProperty(profiledService.getConfigurationProperties(), PROPERTY_NAME);

        assertEquals(PROPERTY_VALUE_2, parameter.getEffectiveValue());
        assertEquals(PROPERTY_VALUE_2, parameter.getCurrentValue());
        assertEquals(ConfigurablePropertyValueSource.DB, parameter.getValueSource());
        assertNull(parameter.getCountryProfile());
    }

    @Test
    void profileValueWinsOverDefaultAndReportsProfileCode() {
        var profiledService = serviceWith(DeploymentMode.NATIVE, PROFILE_WITH_RETRY_COUNT);
        when(repository.findAll()).thenReturn(List.of());

        var parameter = findProperty(profiledService.getConfigurationProperties(), PROPERTY_NAME);

        assertEquals(PROFILE_VALUE, parameter.getEffectiveValue());
        assertEquals(DEFAULT_VALUE, parameter.getDefaultValue());
        assertNull(parameter.getCurrentValue());
        assertEquals(ConfigurablePropertyValueSource.COUNTRY_PROFILE, parameter.getValueSource());
        assertEquals("XX", parameter.getCountryProfile());
    }

    @Test
    void keyWithoutStoredOrProfileValueReportsDefaultSource() {
        var profiledService = serviceWith(DeploymentMode.NATIVE, PROFILE_WITH_RETRY_COUNT);
        when(repository.findAll()).thenReturn(List.of());

        var parameter = findProperty(profiledService.getConfigurationProperties(), CATALOGUE_PROPERTY_NAME);

        assertEquals(CATALOGUE_DEFAULT_VALUE, parameter.getEffectiveValue());
        assertEquals(ConfigurablePropertyValueSource.DEFAULT, parameter.getValueSource());
        assertNull(parameter.getCountryProfile());
    }

    @Test
    void containerizedModeReportsContainerDefaultAndNativeModeReportsNativeDefault() {
        when(repository.findAll()).thenReturn(List.of());

        var containerized = findProperty(
                serviceWith(DeploymentMode.CONTAINERIZED, CountryProfile.NONE).getConfigurationProperties(),
                CONTAINER_KEY.key());
        var nativeMode = findProperty(
                serviceWith(DeploymentMode.NATIVE, CountryProfile.NONE).getConfigurationProperties(),
                CONTAINER_KEY.key());

        assertEquals("container-value", containerized.getEffectiveValue());
        assertEquals(ConfigurablePropertyValueSource.DEFAULT, containerized.getValueSource());
        assertEquals("native-value", containerized.getDefaultValue());
        assertEquals("native-value", nativeMode.getEffectiveValue());
    }

    @Test
    void containerizedModeFallsBackToDefaultForKeyWithoutContainerDefault() {
        when(repository.findAll()).thenReturn(List.of());

        var parameter = findProperty(
                serviceWith(DeploymentMode.CONTAINERIZED, CountryProfile.NONE).getConfigurationProperties(),
                PROPERTY_NAME);

        assertEquals(DEFAULT_VALUE, parameter.getEffectiveValue());
    }

    @Test
    void profileValueWinsOverContainerDefault() {
        var profile = new CountryProfile("XX", Map.of(CONTAINER_KEY.key(), "profile-value"));
        var profiledService = serviceWith(DeploymentMode.CONTAINERIZED, profile);
        when(repository.findAll()).thenReturn(List.of());

        var parameter = findProperty(profiledService.getConfigurationProperties(), CONTAINER_KEY.key());

        assertEquals("profile-value", parameter.getEffectiveValue());
        assertEquals(ConfigurablePropertyValueSource.COUNTRY_PROFILE, parameter.getValueSource());
    }

    @Test
    void getEffectiveValueReturnsStoredValueOverProfileValue() {
        var profiledService = serviceWith(DeploymentMode.NATIVE, PROFILE_WITH_RETRY_COUNT);
        when(repository.findByPropertyKey(PROPERTY_NAME))
                .thenReturn(Optional.of(row(PROPERTY_NAME, PROPERTY_VALUE_2)));

        assertEquals(Optional.of(PROPERTY_VALUE_2), profiledService.getEffectiveValue(PROPERTY_NAME));
    }

    @Test
    void getEffectiveValueReturnsProfileValueWhenNothingIsStored() {
        var profiledService = serviceWith(DeploymentMode.NATIVE, PROFILE_WITH_RETRY_COUNT);
        when(repository.findByPropertyKey(PROPERTY_NAME)).thenReturn(Optional.empty());

        assertEquals(Optional.of(PROFILE_VALUE), profiledService.getEffectiveValue(PROPERTY_NAME));
    }

    @Test
    void getEffectiveValueReturnsModeAwareDefaultWhenNothingIsStoredOrProfiled() {
        when(repository.findByPropertyKey(CONTAINER_KEY.key())).thenReturn(Optional.empty());

        assertEquals(Optional.of("container-value"),
                serviceWith(DeploymentMode.CONTAINERIZED, CountryProfile.NONE).getEffectiveValue(CONTAINER_KEY.key()));
        assertEquals(Optional.of("native-value"),
                serviceWith(DeploymentMode.NATIVE, CountryProfile.NONE).getEffectiveValue(CONTAINER_KEY.key()));
    }

    @Test
    void getEffectiveValueIsEmptyForKeyThatIsNotExposed() {
        assertEquals(Optional.empty(), service.getEffectiveValue(HIDDEN_KEY.key()));
    }

    @Test
    void updateConfigurablePropertyFoundInDatabase() {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyValue(PROPERTY_VALUE);
        when(repository.findByPropertyKey(PROPERTY_NAME)).thenReturn(Optional.of(entity));

        service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE_2);

        ArgumentCaptor<ConfigurationPropertyEntity> captor =
                ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);

        verify(repository).save(captor.capture());

        ConfigurationPropertyEntity capturedEntity = captor.getValue();
        assertEquals(PROPERTY_VALUE_2, capturedEntity.getPropertyValue());
    }

    @Test
    void updateConfigurablePropertyNotFoundInDatabase() {
        when(repository.findByPropertyKey(PROPERTY_NAME)).thenReturn(Optional.empty());

        service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE);

        ArgumentCaptor<ConfigurationPropertyEntity> captor =
                ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);

        verify(repository).save(captor.capture());

        ConfigurationPropertyEntity capturedEntity = captor.getValue();
        assertEquals(PROPERTY_NAME, capturedEntity.getPropertyKey());
        assertEquals(PROPERTY_VALUE, capturedEntity.getPropertyValue());
    }

    @Test
    void updateConfigurablePropertyNotFoundInDatabaseForScopelessKey() {
        when(repository.findByPropertyKey(SCOPELESS_PROPERTY_NAME))
                .thenReturn(Optional.empty());

        service.updateConfigurableProperty(SCOPELESS_PROPERTY_NAME, SCOPELESS_PROPERTY_VALUE);

        ArgumentCaptor<ConfigurationPropertyEntity> captor =
                ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);

        verify(repository).save(captor.capture());

        ConfigurationPropertyEntity capturedEntity = captor.getValue();
        assertEquals(SCOPELESS_PROPERTY_NAME, capturedEntity.getPropertyKey());
        assertEquals(SCOPELESS_PROPERTY_VALUE, capturedEntity.getPropertyValue());
    }

    @Test
    void updateConfigurablePropertyThrowsWhenKeyIsNotInTheCatalogue() {
        assertThrows(NotFoundException.class,
                () -> service.updateConfigurableProperty("unknown.property.key", PROPERTY_VALUE));
    }

    @Test
    void updateConfigurablePropertyThrowsWhenKeyIsDeclaredButNotExposed() {
        assertThrows(NotFoundException.class,
                () -> service.updateConfigurableProperty(HIDDEN_KEY.key(), PROPERTY_VALUE));
    }

    @Test
    void updateConfigurablePropertyRejectsValueTheKeyConverterCannotParse() {
        assertThrows(BadRequestException.class,
                () -> service.updateConfigurableProperty(PROPERTY_NAME, "not-a-number"));

        verifyNoInteractions(repository);
    }

    @Test
    void updateConfigurablePropertyRejectsValueTheKeyValidatorRefuses() {
        assertThrows(BadRequestException.class,
                () -> service.updateConfigurableProperty(SCOPELESS_PROPERTY_NAME, ""));

        verifyNoInteractions(repository);
    }

    @Test
    void updateConfigurablePropertyFoundInDatabaseAuditsOldAndNewValueAndDerivedScope() {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(PROPERTY_NAME);
        entity.setPropertyValue(PROPERTY_VALUE);
        when(repository.findByPropertyKey(PROPERTY_NAME)).thenReturn(Optional.of(entity));

        service.updateConfigurableProperty(PROPERTY_NAME, PROPERTY_VALUE_2);

        ArgumentCaptor<ConfigurationPropertyEntity> captor =
                ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);

        verify(repository).save(captor.capture());

        ConfigurationPropertyEntity capturedEntity = captor.getValue();
        assertEquals(PROPERTY_VALUE_2, capturedEntity.getPropertyValue());

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

        ArgumentCaptor<ConfigurationPropertyEntity> captor =
                ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);

        verify(repository).save(captor.capture());

        ConfigurationPropertyEntity capturedEntity = captor.getValue();
        assertEquals(PROPERTY_NAME, capturedEntity.getPropertyKey());
        assertEquals(PROPERTY_VALUE, capturedEntity.getPropertyValue());

        verify(auditDataHelper, never()).put(eq(RestApiAuditProperty.SYSTEM_PROPERTY_OLD_VALUE), any());
    }

    @Test
    void updateConfigurablePropertyForScopelessKeyAuditsEmptyScope() {
        when(repository.findByPropertyKey(SCOPELESS_PROPERTY_NAME)).thenReturn(Optional.empty());

        service.updateConfigurableProperty(SCOPELESS_PROPERTY_NAME, SCOPELESS_PROPERTY_VALUE);

        verify(auditDataHelper).put(RestApiAuditProperty.SYSTEM_PROPERTY_SCOPE, "");
    }

    private ConfigurablePropertiesService serviceWith(DeploymentMode mode, CountryProfile profile) {
        return new ConfigurablePropertiesService(auditDataHelper, repository, sourceWith(mode, profile));
    }

    private static ConfigurationPropertyEntity row(String key, String value) {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(key);
        entity.setPropertyValue(value);
        return entity;
    }

    private static ConfigurablePropertyDto findProperty(
            Set<ConfigurablePropertyDto> properties, String propertyName) {
        return properties.stream()
                .filter(p -> propertyName.equals(p.getPropertyName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Property not found: " + propertyName));
    }
}
