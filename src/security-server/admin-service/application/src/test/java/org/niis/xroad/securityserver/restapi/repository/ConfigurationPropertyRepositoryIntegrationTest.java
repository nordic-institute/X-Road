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
package org.niis.xroad.securityserver.restapi.repository;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.serverconf.impl.entity.ConfigurationPropertyEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigurationPropertyRepositoryIntegrationTest extends AbstractFacadeMockingTestContext {

    private static final String PROPERTY_NAME = "xroad.proxy-ui-api.client-timeout";
    private static final String PROPERTY_VALUE = "10000";
    private static final String PROPERTY_NAME_2 = "xroad.proxy-ui-api.rate-limit-cache-size";
    private static final String PROPERTY_VALUE_2 = "30000";

    @Autowired
    ConfigurationPropertyRepository configurationPropertyRepository;

    @Test
    public void saveAndFindByPropertyKey() {
        Optional<ConfigurationPropertyEntity> found =
                configurationPropertyRepository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME, null);

        assertTrue(found.isPresent());
        assertEquals(PROPERTY_VALUE, found.get().getPropertyValue());
    }

    @Test
    public void updateExistingPropertyValue() {
        Optional<ConfigurationPropertyEntity> initial =
                configurationPropertyRepository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME, null);

        assertTrue(initial.isPresent());
        assertEquals(PROPERTY_VALUE, initial.get().getPropertyValue());

        initial.get().setPropertyValue(PROPERTY_VALUE_2);
        configurationPropertyRepository.saveOrUpdate(initial.get());

        Optional<ConfigurationPropertyEntity> found =
                configurationPropertyRepository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME, null);

        assertTrue(found.isPresent());
        assertEquals(PROPERTY_VALUE_2, found.get().getPropertyValue());
    }

    @Test
    public void updatePropertyValueThatDoesNotExist() {
        Optional<ConfigurationPropertyEntity> initial =
                configurationPropertyRepository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME_2, null);

        assertTrue(initial.isEmpty());

        configurationPropertyRepository.saveOrUpdate(getConfigurationPropertyEntity());

        Optional<ConfigurationPropertyEntity> found =
                configurationPropertyRepository.findConfigurationPropertyByPropertyKeyAndScope(PROPERTY_NAME_2, null);

        assertTrue(found.isPresent());
        assertEquals(PROPERTY_VALUE_2, found.get().getPropertyValue());
    }

    private static ConfigurationPropertyEntity getConfigurationPropertyEntity() {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(PROPERTY_NAME_2);
        entity.setPropertyValue(PROPERTY_VALUE_2);
        return entity;
    }
}
