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
package org.niis.xroad.securityserver.restapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.securityserver.restapi.config.ConfigurableSystemPropertiesConfiguration.ConfigurableProperties;
import org.niis.xroad.securityserver.restapi.config.ConfigurableSystemPropertiesConfiguration.ConfigurableProperties.ConfigurableProperty;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServerConfigurablePropertyDto;
import org.niis.xroad.securityserver.restapi.repository.ConfigurationPropertyRepository;
import org.niis.xroad.serverconf.impl.entity.ConfigurationPropertyEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service that handles configurable system parameters
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ConfigurablePropertiesService {

    private static final String COMMON_SCOPE = "common";

    private final ConfigurableProperties configurableProperties;
    private final ConfigurationPropertyRepository repository;

    /**
     * Returns all configurable system parameter defined in the configuration.
     * <p>
     * Combines the static system parameter definitions with the currently stored
     * configuration values from the database. If a value is stored in the repository,
     * it is used as the current value; otherwise the default value and service scope
     * are returned.
     *
     * @return set of system properties with their metadata and current values
     */
    public Set<SecurityServerConfigurablePropertyDto> getConfigurationProperties() {
        return configurableProperties.getConfigurableProperties().entrySet()
                .stream()
                .flatMap(e ->
                        e.getValue()
                                .stream()
                                .map(v -> toSecurityServerSystemParameterDto(e.getKey(), v)))
                .collect(Collectors.toSet());
    }

    /**
     * Updates the value of an existing system parameter or creates a new one
     * if the property does not yet exist in the repository.
     * <p>
     * If a configuration property with the given key is found, its value is updated.
     * Otherwise, a new {@link ConfigurationPropertyEntity} is created and persisted.
     *
     * @param propertyKey   unique key of the system property
     * @param propertyValue new value for the system property
     * @param scope         scope of the property (service name or null)
     */
    public void updateConfigurableProperty(String propertyKey, String propertyValue, String scope) {
        repository.findConfigurationPropertyByPropertyKeyAndScope(propertyKey, scope)
                .ifPresentOrElse(entity -> {
                    entity.setPropertyValue(propertyValue);
                    repository.saveOrUpdate(entity);
                }, () -> {
                    repository.saveOrUpdate(createConfigurationProperty(propertyKey, propertyValue, scope));
                });
    }

    private SecurityServerConfigurablePropertyDto toSecurityServerSystemParameterDto(String serviceName,
                                                                               ConfigurableProperty parameter) {
        var systemPropertyDto = new SecurityServerConfigurablePropertyDto();
        systemPropertyDto.setPropertyName(parameter.getPropertyName());
        systemPropertyDto.setDefaultValue(parameter.getDefaultValue());
        String scope = serviceName.equals(COMMON_SCOPE) ? null : serviceName;
        systemPropertyDto.setScope(scope);
        repository.findConfigurationPropertyByPropertyKeyAndScope(parameter.getPropertyName(), scope)
                .ifPresent(e -> {
                    systemPropertyDto.setCurrentValue(e.getPropertyValue());
                });
        return systemPropertyDto;
    }

    private static ConfigurationPropertyEntity createConfigurationProperty(String propertyKey, String propertyValue, String scope) {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(propertyKey);
        entity.setPropertyValue(propertyValue);
        entity.setScope(scope);
        return entity;
    }
}
