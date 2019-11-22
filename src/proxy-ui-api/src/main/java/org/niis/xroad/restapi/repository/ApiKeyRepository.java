/**
 * The MIT License
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
import org.niis.xroad.restapi.domain.InvalidRoleNameException;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.service.NotFoundException;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * API key repository which stores encoded keys in DB.
 * Uses simple caching, using ConcurrentHashMaps in memory.
 */
@Slf4j
@Repository
@Transactional
public class ApiKeyRepository {

    // two caches
    public static final String GET_KEY_CACHE = "apikey-by-keys";
    public static final String LIST_ALL_KEYS_CACHE = "all-apikeys";

    private final PasswordEncoder passwordEncoder;
    private final PersistenceUtils persistenceUtils;

    @Autowired
    public ApiKeyRepository(PasswordEncoder passwordEncoder, PersistenceUtils persistenceUtils) {
        this.passwordEncoder = passwordEncoder;
        this.persistenceUtils = persistenceUtils;
    }

    /**
     * Api keys are created with UUID.randomUUID which uses SecureRandom,
     * which is cryptographically secure.
     * @return
     */
    private String createApiKey() {
        return UUID.randomUUID().toString();
    }

    /**
     * create api key with one role
     */
    @CacheEvict(allEntries = true, cacheNames = { LIST_ALL_KEYS_CACHE, GET_KEY_CACHE })
    public Map.Entry<String, PersistentApiKeyType> create(String roleName) throws InvalidRoleNameException {
        return create(Collections.singletonList(roleName));
    }

    /**
     * create api key with collection of roles
     * @return Map.Entry with key = plaintext key, value = PersistentApiKeyType
     * @throws InvalidRoleNameException if roleNames was empty or contained invalid roles
     */
    @CacheEvict(allEntries = true, cacheNames = { LIST_ALL_KEYS_CACHE, GET_KEY_CACHE })
    public Map.Entry<String, PersistentApiKeyType> create(Collection<String> roleNames)
            throws InvalidRoleNameException {
        if (roleNames.isEmpty()) {
            throw new InvalidRoleNameException("missing roles");
        }
        Set<Role> roles = Role.getForNames(roleNames);
        String plainKey = createApiKey();
        String encodedKey = encode(plainKey);
        PersistentApiKeyType apiKey = new PersistentApiKeyType(encodedKey, Collections.unmodifiableCollection(roles));
        PersistentApiKeyDAOImpl apiKeyDAO = new PersistentApiKeyDAOImpl();
        apiKeyDAO.insert(persistenceUtils.getCurrentSession(), apiKey);
        Map.Entry<String, PersistentApiKeyType> entry =
                new AbstractMap.SimpleImmutableEntry<>(plainKey, apiKey);
        return entry;
    }

    private String encode(String key) {
        return passwordEncoder.encode(key);
    }


    /**
     * get matching key
     * @param key
     * @return
     * @throws ApiKeyNotFoundException if api key was not found
     */
    @Cacheable(GET_KEY_CACHE)
    public PersistentApiKeyType get(String key) throws ApiKeyNotFoundException {
        String encodedKey = passwordEncoder.encode(key);
        List<PersistentApiKeyType> keys = new PersistentApiKeyDAOImpl().findAll(persistenceUtils.getCurrentSession());
        for (PersistentApiKeyType apiKeyType : keys) {
            if (apiKeyType.getEncodedKey().equals(encodedKey)) {
                return apiKeyType;
            }
        }
        throw new ApiKeyNotFoundException("api key not found");
    }

    /**
     * remove / revoke one key
     * @param key
     * @throws ApiKeyNotFoundException if api key was not found
     */
    @CacheEvict(allEntries = true, cacheNames = { LIST_ALL_KEYS_CACHE, GET_KEY_CACHE })
    public void remove(String key) throws ApiKeyNotFoundException {
        PersistentApiKeyType apiKeyType = get(key);
        new PersistentApiKeyDAOImpl().delete(persistenceUtils.getCurrentSession(), apiKeyType);
    }

    /**
     * remove / revoke one key by id
     * @param id
     * @throws ApiKeyNotFoundException if api key was not found
     */
    @CacheEvict(allEntries = true, cacheNames = { LIST_ALL_KEYS_CACHE, GET_KEY_CACHE })
    public void removeById(long id) throws ApiKeyNotFoundException {
        PersistentApiKeyDAOImpl dao = new PersistentApiKeyDAOImpl();
        PersistentApiKeyType apiKeyType = dao.findById(persistenceUtils.getCurrentSession(), id);
        if (apiKeyType == null) {
            throw new ApiKeyNotFoundException("api key with id " + id + " not found");
        }
        dao.delete(persistenceUtils.getCurrentSession(), apiKeyType);
    }

    /**
     * List all keys
     * @return
     */
    @Cacheable(LIST_ALL_KEYS_CACHE)
    public List<PersistentApiKeyType> listAll() {
        return new PersistentApiKeyDAOImpl().findAll(persistenceUtils.getCurrentSession());
    }

    public static class ApiKeyNotFoundException extends NotFoundException {
        public static final String ERROR_API_KEY_NOT_FOUND = "api_key_not_found";
        ApiKeyNotFoundException(String s) {
            super(s, new ErrorDeviation(ERROR_API_KEY_NOT_FOUND));
        }
    }


}
