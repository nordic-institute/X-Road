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
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigCatalogue;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.ConfClientConfigKeys;
import org.niis.xroad.common.properties.config.keys.GlobalConfConfigKeys;
import org.niis.xroad.common.properties.config.keys.HealthCheckConfigKeys;
import org.niis.xroad.common.properties.config.keys.MessageLogArchiverConfigKeys;
import org.niis.xroad.common.properties.config.keys.MonitorConfigKeys;
import org.niis.xroad.common.properties.config.keys.OcspVerifierConfigKeys;
import org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.common.properties.config.keys.ServerConfConfigKeys;
import org.niis.xroad.messagelog.MessageLogEncryptionConfigKeys;
import org.niis.xroad.securityserver.restapi.config.ConfigurableSystemPropertiesConfiguration.ConfigurablePropertiesDefinition;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServerConfigurablePropertyDto;
import org.niis.xroad.securityserver.restapi.repository.ConfigurationPropertyRepository;
import org.niis.xroad.serverconf.impl.entity.ConfigurationPropertyEntity;
import org.niis.xroad.signer.common.config.SignerConfigKeys;
import org.niis.xroad.signer.common.config.SignerKeyConfigKeys;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.niis.xroad.common.core.exception.ErrorCode.NOT_FOUND;

/**
 * Service that handles configurable system parameters
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ConfigurablePropertiesService {

    private static final Consumer<String> NOOP_VALUE_CONSUMER = _ -> { };

    /**
     * Providers whose exposed keys make up the Security Server's aggregated system-parameters catalogue.
     * Any provider whose keys should surface in the system-parameters UI belongs here instead of the
     * residual {@code configurable-properties.yaml}. The last five live outside properties-core, in the
     * lightest module that can hold them, and reach this list through a compile dependency.
     */
    private static final List<ConfigKeyProvider> SS_PROVIDERS = List.of(
            CommonConfigKeys.instance(),
            CommonRpcConfigKeys.instance(),
            ProxyConfigKeys.instance(),
            ConfClientConfigKeys.instance(),
            OpMonitorConfigKeys.instance(),
            AuxiliaryServiceConfigKeys.instance(),
            AdminServiceConfigKeys.instance(),
            OcspVerifierConfigKeys.instance(),
            GlobalConfConfigKeys.instance(),
            ServerConfConfigKeys.instance(),
            HealthCheckConfigKeys.instance(),
            SignerConfigKeys.instance(),
            SignerKeyConfigKeys.instance(),
            MonitorConfigKeys.instance(),
            MessageLogArchiverConfigKeys.instance(),
            MessageLogEncryptionConfigKeys.instance());

    private final ConfigurablePropertiesDefinition configurableProperties;
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
        var currentPropertiesValues = repository.findAll();
        return getAllPropertyDefinitions()
                .stream()
                .map(param -> toSecurityServerSystemParameterDto(param, currentPropertiesValues))
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
        updateConfigurableProperty(propertyKey, propertyValue, scope, NOOP_VALUE_CONSUMER);
    }

    /**
     * Updates the value of an existing system parameter or creates a new one
     * if the property does not yet exist in the repository.
     * <p>
     * If a configuration property with the given key and scope is found, its value is updated.
     * Otherwise, a new {@link ConfigurationPropertyEntity} is created and persisted.
     * </p>
     * <p>
     * This variant allows the caller to provide a consumer for the existing value before the update.
     * </p>
     *
     * @param propertyKey           unique key of the system property
     * @param propertyValue         new value for the system property
     * @param scope                 scope of the property (service name or null)
     * @param existingValueConsumer callback that consumes the existing persisted property value
     *                              before the update, not invoked if the property does not exist
     *                              or has a {@code null} value
     */
    public void updateConfigurableProperty(
            String propertyKey, String propertyValue, String scope, Consumer<String> existingValueConsumer) {

        if (!isPropertyConfigurable(propertyKey, scope)) {
            throw new NotFoundException(
                    "Configurable property '%s' with scope '%s' is not defined".formatted(propertyKey, scope),
                    NOT_FOUND.build());
        }

        var existing = repository.findConfigurationPropertyByPropertyKeyAndScope(propertyKey, scope);
        existing.map(ConfigurationPropertyEntity::getPropertyValue).ifPresent(existingValueConsumer);

        var entity = existing.orElseGet(() -> createEmptyConfigurationProperty(propertyKey, scope));
        entity.setPropertyValue(propertyValue);
        repository.saveOrUpdate(entity);
    }

    private SecurityServerConfigurablePropertyDto toSecurityServerSystemParameterDto(
            PropertyDefinition parameter, List<ConfigurationPropertyEntity> storedValues) {
        var systemPropertyDto = new SecurityServerConfigurablePropertyDto();
        systemPropertyDto.setPropertyName(parameter.propertyName());
        systemPropertyDto.setDefaultValue(parameter.defaultValue());
        systemPropertyDto.setScope(parameter.scope());
        storedValues.stream()
                .filter(v ->
                        v.getPropertyKey().equals(parameter.propertyName())
                                && Objects.equals(v.getScope(), parameter.scope()))
                .map(ConfigurationPropertyEntity::getPropertyValue)
                .findAny()
                .ifPresent(systemPropertyDto::setCurrentValue);
        return systemPropertyDto;
    }

    private boolean isPropertyConfigurable(String propertyKey, String scope) {
        return getAllPropertyDefinitions().stream()
                .anyMatch(p -> p.propertyName().equals(propertyKey) && Objects.equals(p.scope(), scope));
    }

    /**
     * Merges the DSL-derived catalogue ({@link #SS_PROVIDERS}) with the residual
     * {@code configurable-properties.yaml} definitions into a single flat list.
     */
    private List<PropertyDefinition> getAllPropertyDefinitions() {
        return Stream.concat(
                        ConfigCatalogue.exposed(SS_PROVIDERS).stream()
                                .map(entry -> new PropertyDefinition(
                                        entry.key(), entry.defaultValue(), categoryToScope(entry.category()))),
                        configurableProperties.getConfigurableProperties().stream()
                                .map(property -> new PropertyDefinition(
                                        property.getPropertyName(), property.getDefaultValue(), property.getScope())))
                .toList();
    }

    /**
     * Maps a DSL {@link Category} to the legacy scope string the {@code configuration_properties} table and the
     * UI expect. Transitional: removed once the {@code scope} column is dropped (see issue 06).
     */
    private static String categoryToScope(Category category) {
        return switch (category) {
            case PROXY -> "proxy";
            case SIGNER -> "signer";
            case PROXY_UI_API -> "proxy-ui-api";
            case OP_MONITOR_DAEMON -> "op-monitor-daemon";
            case MONITOR -> "monitor";
            case CONFIGURATION_CLIENT -> "configuration-client";
            case AUXILIARY_SERVICE -> "auxiliary-service";
            case MESSAGE_LOG_ARCHIVER -> "message-log-archiver";
            case COMMON -> null;
            default -> throw XrdRuntimeException.systemInternalError(
                    "Unmapped category for configurable properties catalogue: " + category);
        };
    }

    private static ConfigurationPropertyEntity createEmptyConfigurationProperty(String propertyKey, String scope) {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(propertyKey);
        entity.setScope(scope);
        return entity;
    }

    private record PropertyDefinition(String propertyName, String defaultValue, String scope) {
    }
}
