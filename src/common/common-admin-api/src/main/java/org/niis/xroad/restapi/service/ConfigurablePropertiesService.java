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

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigCatalogue;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.entity.ConfigurationPropertyEntity;
import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyDto;
import org.niis.xroad.restapi.repository.ConfigurationPropertyRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_PROPERTY_VALUE;
import static org.niis.xroad.common.core.exception.ErrorCode.NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SYSTEM_PROPERTY_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SYSTEM_PROPERTY_NEW_VALUE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SYSTEM_PROPERTY_OLD_VALUE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SYSTEM_PROPERTY_SCOPE;

/**
 * Service that handles a product's configurable system parameters: merges the declared
 * configuration-key registry ({@link ConfigurablePropertySource}) with the values stored in
 * {@code configuration_properties}. Shared by Central Server and Security Server — each product
 * plugs in its own {@link ConfigurablePropertySource} to supply its own key providers and scope
 * mapping.
 */
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ConfigurablePropertiesService {

    private final AuditDataHelper auditDataHelper;
    private final ConfigurationPropertyRepository repository;
    private final ConfigurablePropertySource configurablePropertySource;

    /**
     * Returns every configurable system parameter the registry declares as exposed, combined with the
     * currently stored configuration values. If a value is stored in the repository it is used as the
     * current value; otherwise the declared default and target scope are returned.
     *
     * @return set of configurable properties with their metadata and current values
     */
    public Set<ConfigurablePropertyDto> getConfigurationProperties() {
        var currentPropertiesValues = repository.findAll();
        return getAllPropertyDefinitions()
                .stream()
                .map(param -> toConfigurablePropertyDto(param, currentPropertiesValues))
                .collect(Collectors.toSet());
    }

    /**
     * Returns the effective value of a single configurable property: the stored override if one
     * exists, otherwise the registry's declared default.
     *
     * @param propertyKey unique key of the configurable property
     * @return the effective value, or empty if the property is not defined/exposed in this catalogue
     */
    public Optional<String> getEffectiveValue(String propertyKey) {
        return findExposedKey(propertyKey)
                .map(key -> repository.findByPropertyKey(propertyKey)
                        .map(ConfigurationPropertyEntity::getPropertyValue)
                        .orElseGet(key::defaultValue));
    }

    /**
     * Updates the value of an existing configurable property, or creates a new one if the property
     * has no stored override yet, audit logging the change.
     * <p>
     * The scope audit logged alongside the change is derived from the key's own {@link Category} —
     * never a caller-supplied value — so it can't drift out of sync with the catalogue.
     *
     * @param propertyKey   unique key of the configurable property
     * @param propertyValue new value for the configurable property
     */
    public void updateConfigurableProperty(String propertyKey, String propertyValue) {
        var key = findExposedKey(propertyKey)
                .orElseThrow(() -> new NotFoundException(
                        "Configurable property '%s' is not defined".formatted(propertyKey),
                        NOT_FOUND.build()));
        validateValue(key, propertyValue);

        auditDataHelper.put(SYSTEM_PROPERTY_NAME, propertyKey);
        auditDataHelper.put(SYSTEM_PROPERTY_NEW_VALUE, propertyValue);
        auditDataHelper.put(SYSTEM_PROPERTY_SCOPE, getIfNull(categoryToScope(key.category()), StringUtils.EMPTY));

        var existing = repository.findByPropertyKey(propertyKey);
        existing.map(ConfigurationPropertyEntity::getPropertyValue)
                .ifPresent(oldValue -> auditDataHelper.put(SYSTEM_PROPERTY_OLD_VALUE, oldValue));

        var entity = existing.orElseGet(() -> createEmptyConfigurationProperty(propertyKey));
        entity.setPropertyValue(propertyValue);
        repository.save(entity);
    }

    /** @return the exposed keys of {@link #configurablePropertySource}, flattened to name/default/target scope */
    private List<PropertyDefinition> getAllPropertyDefinitions() {
        return ConfigCatalogue.exposed(getConfigKeyProviders()).stream()
                .map(entry -> new PropertyDefinition(
                        entry.key(), entry.defaultValue(), categoryToScope(entry.category())))
                .toList();
    }

    private ConfigurablePropertyDto toConfigurablePropertyDto(PropertyDefinition parameter,
                                                              List<ConfigurationPropertyEntity> storedValues) {
        var systemPropertyDto = new ConfigurablePropertyDto();
        systemPropertyDto.setPropertyName(parameter.propertyName());
        systemPropertyDto.setDefaultValue(parameter.defaultValue());
        systemPropertyDto.setScope(parameter.scope());
        storedValues.stream()
                .filter(v -> v.getPropertyKey().equals(parameter.propertyName()))
                .map(ConfigurationPropertyEntity::getPropertyValue)
                .findAny()
                .ifPresent(systemPropertyDto::setCurrentValue);
        return systemPropertyDto;
    }

    /**
     * A property is configurable when the registry declares it exposed.
     */
    private Optional<ConfigKey<?>> findExposedKey(String propertyKey) {
        return getConfigKeyProviders().stream()
                .flatMap(provider -> provider.keys().stream())
                .filter(key -> key.exposedInUi() && key.key().equals(propertyKey))
                .findFirst();
    }

    /**
     * The owning process converts and validates every stored override eagerly at startup and refuses to
     * start on failure, so a value must never reach the database without passing the key's own converter
     * and validator here.
     */
    private static <T> void validateValue(ConfigKey<T> key, String rawValue) {
        try {
            var result = key.validate(key.convert(rawValue));
            if (!result.valid()) {
                throw new IllegalArgumentException(result.message());
            }
        } catch (RuntimeException e) {
            throw new BadRequestException("Invalid value for property '%s': %s".formatted(key.key(), e.getMessage()),
                    e, INVALID_PROPERTY_VALUE.build());
        }
    }

    private static ConfigurationPropertyEntity createEmptyConfigurationProperty(String propertyKey) {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(propertyKey);
        return entity;
    }

    private List<ConfigKeyProvider> getConfigKeyProviders() {
        return configurablePropertySource.getConfigKeyProviders();
    }

    private String categoryToScope(Category category) {
        return configurablePropertySource.categoryToScope(category);
    }

    private record PropertyDefinition(String propertyName, String defaultValue, String scope) {
    }
}
