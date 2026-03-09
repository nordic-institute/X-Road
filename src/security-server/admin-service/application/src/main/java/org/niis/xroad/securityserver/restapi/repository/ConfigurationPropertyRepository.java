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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.niis.xroad.serverconf.impl.dao.ConfigurationPropertyDAOImpl;
import org.niis.xroad.serverconf.impl.entity.ConfigurationPropertyEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Configuration property repository
 */
@Slf4j
@Repository
@Transactional
@RequiredArgsConstructor
public class ConfigurationPropertyRepository extends AbstractRepository<ConfigurationPropertyEntity> {

    @Getter(AccessLevel.PROTECTED)
    private final PersistenceUtils persistenceUtils;

    /**
     *
     * Find a configuration property by its key.
     *
     * @param propertyKey the property key
     * @return Optional containing the property if found
     */
    public Optional<ConfigurationPropertyEntity> findConfigurationPropertyByPropertyKey(String propertyKey) {
        ConfigurationPropertyDAOImpl serviceDescriptionDAO = new ConfigurationPropertyDAOImpl();
        return Optional.ofNullable(serviceDescriptionDAO.getConfigurationProperty(persistenceUtils.getCurrentSession(), propertyKey));
    }

    /**
     * Save or update a configuration property entity.
     *
     * @param entity the entity to save or update
     */
    public void saveOrUpdate(ConfigurationPropertyEntity entity) {
        entity.setUpdatedAt(Instant.now());
        persistenceUtils.getCurrentSession().merge(entity);
    }

}
