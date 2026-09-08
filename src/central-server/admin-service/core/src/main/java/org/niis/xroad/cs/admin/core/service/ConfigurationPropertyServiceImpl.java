/*
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
package org.niis.xroad.cs.admin.core.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigCatalogue;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.keys.CsAdminServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.CsManagementServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.CsRegistrationServiceConfigKeys;
import org.niis.xroad.cs.admin.api.domain.ConfigurationProperty;
import org.niis.xroad.cs.admin.api.service.ConfigurationPropertyService;
import org.niis.xroad.cs.admin.core.entity.ConfigurationPropertyEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationPropertyMapper;
import org.niis.xroad.cs.admin.core.repository.ConfigurationPropertyRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.niis.xroad.common.core.exception.ErrorCode.NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_PROPERTY_VALUE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SYSTEM_PROPERTY_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SYSTEM_PROPERTY_NEW_VALUE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SYSTEM_PROPERTY_OLD_VALUE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SYSTEM_PROPERTY_SCOPE;

/**
 * Service that handles Central Server configurable system parameters, merging the declared
 * configuration-key registry with the values stored in {@code configuration_properties}.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ConfigurationPropertyServiceImpl implements ConfigurationPropertyService {

    /**
     * Providers whose exposed keys make up the Central Server's system-parameters catalogue — the only
     * source of the property list.
     */
    private static final List<ConfigKeyProvider> CS_PROVIDERS = List.of(
            CsAdminServiceConfigKeys.instance(),
            CsManagementServiceConfigKeys.instance(),
            CsRegistrationServiceConfigKeys.instance());

    private final ConfigurationPropertyRepository repository;
    private final ConfigurationPropertyMapper mapper;
    private final AuditDataHelper auditDataHelper;

    @Override
    public Set<ConfigurablePropertyDto> getConfigurationProperties() {
        var currentPropertiesValues = repository.findAll().stream()
                .map(mapper::toTarget)
                .toList();
        return getAllPropertyDefinitions()
                .stream()
                .map(param -> toConfigurablePropertyDto(param, currentPropertiesValues))
                .collect(Collectors.toSet());
    }

    @Override
    public void updateConfigurableProperty(String propertyKey, String propertyValue) {
        var key = findExposedKey(propertyKey)
                .orElseThrow(() -> new NotFoundException(
                        "Configurable property '%s' is not defined".formatted(propertyKey),
                        NOT_FOUND.build()));
        validateValue(key, propertyValue);

        auditDataHelper.put(SYSTEM_PROPERTY_NAME, propertyKey);
        auditDataHelper.put(SYSTEM_PROPERTY_NEW_VALUE, propertyValue);
        auditDataHelper.put(SYSTEM_PROPERTY_SCOPE, getIfNull(categoryToScope(key.category()), ""));

        var existing = repository.findByPropertyKey(propertyKey);
        existing.map(ConfigurationPropertyEntity::getPropertyValue)
                .ifPresent(oldValue -> auditDataHelper.put(SYSTEM_PROPERTY_OLD_VALUE, oldValue));

        var entity = existing.orElseGet(() -> new ConfigurationPropertyEntity(propertyKey));
        entity.setPropertyValue(propertyValue);
        repository.save(entity);
    }

    private ConfigurablePropertyDto toConfigurablePropertyDto(PropertyDefinition parameter,
                                                              List<ConfigurationProperty> storedValues) {
        var dto = new ConfigurablePropertyDto();
        dto.setPropertyName(parameter.propertyName());
        dto.setDefaultValue(parameter.defaultValue());
        dto.setScope(parameter.scope());
        storedValues.stream()
                .filter(v -> v.getPropertyKey().equals(parameter.propertyName()))
                .map(ConfigurationProperty::getPropertyValue)
                .findAny()
                .ifPresent(dto::setCurrentValue);
        return dto;
    }

    /**
     * A property is configurable when the registry declares it exposed.
     */
    private Optional<ConfigKey<?>> findExposedKey(String propertyKey) {
        return CS_PROVIDERS.stream()
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

    /** @return the exposed keys of {@link #CS_PROVIDERS}, flattened to name/default/target scope */
    private List<PropertyDefinition> getAllPropertyDefinitions() {
        return ConfigCatalogue.exposed(CS_PROVIDERS).stream()
                .map(entry -> new PropertyDefinition(
                        entry.key(), entry.defaultValue(), categoryToScope(entry.category())))
                .toList();
    }

    /**
     * Maps a DSL {@link Category} to the scope string the REST contract and the UI grouping use. The
     * scope also identifies which Central Server process owns the property, for a later UI restart-warning.
     */
    private static String categoryToScope(Category category) {
        return switch (category) {
            case ADMIN_SERVICE -> "admin-service";
            case MANAGEMENT_SERVICE -> "management-service";
            case REGISTRATION_SERVICE -> "registration-service";
            case COMMON -> null;
            default -> throw XrdRuntimeException.systemInternalError(
                    "Unmapped category for configurable properties catalogue: " + category);
        };
    }

    private record PropertyDefinition(String propertyName, String defaultValue, String scope) {
    }
}
