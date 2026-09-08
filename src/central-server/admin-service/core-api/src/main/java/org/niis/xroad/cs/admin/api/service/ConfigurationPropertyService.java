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
package org.niis.xroad.cs.admin.api.service;

import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyDto;

import java.util.Set;

/**
 * Service that handles the Central Server's configurable system parameters: merges the declared
 * configuration-key registry with stored overrides to produce each exposed key's effective value.
 */
public interface ConfigurationPropertyService {

    /**
     * Returns every configurable system parameter the registry declares as exposed, combined with the
     * currently stored configuration values. If a value is stored, it is used as the current value;
     * otherwise the declared default is returned.
     *
     * @return set of configurable properties with their metadata and current values
     */
    Set<ConfigurablePropertyDto> getConfigurationProperties();

    /**
     * Updates the value of an existing configuration property, or creates a new one if the property has
     * no stored override yet, audit logging the change.
     *
     * @param propertyKey   unique key of the configuration property
     * @param propertyValue new value for the configuration property
     */
    void updateConfigurableProperty(String propertyKey, String propertyValue);

}
