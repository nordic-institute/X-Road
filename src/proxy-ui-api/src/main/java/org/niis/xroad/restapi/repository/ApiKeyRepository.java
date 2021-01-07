/**
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
package org.niis.xroad.restapi.repository;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.dao.PersistentApiKeyDAOImpl;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * API key repository which stores encoded keys in DB.
 * Uses simple caching, using ConcurrentHashMaps in memory.
 */
@Slf4j
@Repository
@Transactional
public class ApiKeyRepository {
    public static final String LIST_ALL_KEYS_CACHE = "all-apikeys";
    public static final String GET_KEY_CACHE = "apikey-by-keys";
    private final PersistenceUtils persistenceUtils;

    @Autowired
    public ApiKeyRepository(PersistenceUtils persistenceUtils) {
        this.persistenceUtils = persistenceUtils;
    }

    @CacheEvict(allEntries = true, cacheNames = { LIST_ALL_KEYS_CACHE, GET_KEY_CACHE })
    public void saveOrUpdate(PersistentApiKeyType persistentApiKeyType) {
        persistenceUtils.getCurrentSession().saveOrUpdate(persistentApiKeyType);
    }

    @CacheEvict(allEntries = true, cacheNames = { LIST_ALL_KEYS_CACHE, GET_KEY_CACHE })
    public void delete(PersistentApiKeyType persistentApiKeyType) {
        persistenceUtils.getCurrentSession().delete(persistentApiKeyType);
    }

    @Cacheable(GET_KEY_CACHE)
    public PersistentApiKeyType getApiKey(long id) {
        log.debug("get one api key from db");
        return new PersistentApiKeyDAOImpl().findById(persistenceUtils.getCurrentSession(), id);
    }

    @Cacheable(LIST_ALL_KEYS_CACHE)
    public List<PersistentApiKeyType> getAllApiKeys() {
        log.debug("get all api keys from db");
        return new PersistentApiKeyDAOImpl().findAll(persistenceUtils.getCurrentSession());
    }
}
