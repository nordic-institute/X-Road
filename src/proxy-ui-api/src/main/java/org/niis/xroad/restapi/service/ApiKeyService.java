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
package org.niis.xroad.restapi.service;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.domain.InvalidRoleNameException;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.repository.ApiKeyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_API_KEY_NOT_FOUND;

/**
 * ApiKey service.
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class ApiKeyService {

    private final PasswordEncoder passwordEncoder;
    private final ApiKeyRepository apiKeyRepository;
    private final CacheManager cacheManager;
    private final AuditDataHelper auditDataHelper;

    @Autowired
    public ApiKeyService(PasswordEncoder passwordEncoder, ApiKeyRepository apiKeyRepository,
            CacheManager cacheManager, AuditDataHelper auditDataHelper) {
        this.passwordEncoder = passwordEncoder;
        this.apiKeyRepository = apiKeyRepository;
        this.cacheManager = cacheManager;
        this.auditDataHelper = auditDataHelper;
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
    public PersistentApiKeyType create(String roleName) throws InvalidRoleNameException {
        return create(Collections.singletonList(roleName));
    }

    /**
     * create api key with collection of roles
     * @return new PersistentApiKeyType that contains the new key in plain text
     * @throws InvalidRoleNameException if roleNames was empty or contained invalid roles
     */
    public PersistentApiKeyType create(Collection<String> roleNames)
            throws InvalidRoleNameException {
        if (roleNames.isEmpty()) {
            throw new InvalidRoleNameException("missing roles");
        }
        Set<Role> roles = Role.getForNames(roleNames);
        String plainKey = createApiKey();
        String encodedKey = encode(plainKey);
        PersistentApiKeyType apiKey = new PersistentApiKeyType(plainKey, encodedKey,
                Collections.unmodifiableCollection(roles));
        apiKeyRepository.saveOrUpdate(apiKey);
        auditLog(apiKey);
        return apiKey;
    }

    private void auditLog(PersistentApiKeyType apiKey) {
        auditDataHelper.put(RestApiAuditProperty.API_KEY_ID, apiKey.getId());
        auditDataHelper.put(RestApiAuditProperty.API_KEY_ROLES, apiKey.getRoles());
    }

    private void auditLog(Long id, Collection<String> roleNames) {
        auditDataHelper.put(RestApiAuditProperty.API_KEY_ID, id);
        auditDataHelper.put(RestApiAuditProperty.API_KEY_ROLES, roleNames);
    }

    /**
     * update api key with one role by key id
     * @param id
     * @param roleName
     * @throws InvalidRoleNameException if roleNames was empty or contained invalid roles
     * @throws ApiKeyService.ApiKeyNotFoundException if api key was not found
     */
    public PersistentApiKeyType update(long id, String roleName)
            throws InvalidRoleNameException, ApiKeyService.ApiKeyNotFoundException {
        return update(id, Collections.singletonList(roleName));
    }

    /**
     * update api key with collection of roles by key id
     * @param id
     * @param roleNames
     * @return
     * @throws InvalidRoleNameException if roleNames was empty or contained invalid roles
     * @throws ApiKeyService.ApiKeyNotFoundException if api key was not found
     */
    public PersistentApiKeyType update(long id, Collection<String> roleNames)
            throws InvalidRoleNameException, ApiKeyService.ApiKeyNotFoundException {
        auditLog(id, roleNames);
        PersistentApiKeyType apiKeyType = apiKeyRepository.getApiKey(id);
        if (apiKeyType == null) {
            throw new ApiKeyService.ApiKeyNotFoundException("api key with id " + id + " not found");
        }
        if (roleNames.isEmpty()) {
            throw new InvalidRoleNameException("missing roles");
        }
        Set<Role> roles = Role.getForNames(roleNames);
        apiKeyType.setRoles(roles);
        apiKeyRepository.saveOrUpdate(apiKeyType);
        return apiKeyType;
    }

    private String encode(String key) {
        return passwordEncoder.encode(key);
    }

    /**
     * get matching key
     * @param key
     * @return
     * @throws ApiKeyService.ApiKeyNotFoundException if api key was not found
     */
    public PersistentApiKeyType get(String key) throws ApiKeyService.ApiKeyNotFoundException {
        String encodedKey = passwordEncoder.encode(key);
        List<PersistentApiKeyType> keys = apiKeyRepository.getAllApiKeys();
        for (PersistentApiKeyType apiKeyType : keys) {
            if (apiKeyType.getEncodedKey().equals(encodedKey)) {
                return apiKeyType;
            }
        }
        throw new ApiKeyService.ApiKeyNotFoundException("api key not found");
    }

    /**
     * remove / revoke one key
     * @param key
     * @throws ApiKeyService.ApiKeyNotFoundException if api key was not found
     */
    public void remove(String key) throws ApiKeyService.ApiKeyNotFoundException {
        PersistentApiKeyType apiKeyType = get(key);
        auditLog(apiKeyType);
        apiKeyRepository.delete(apiKeyType);
    }

    /**
     * remove / revoke one key by id
     * @param id
     * @throws ApiKeyService.ApiKeyNotFoundException if api key was not found
     */
    public void removeById(long id) throws ApiKeyService.ApiKeyNotFoundException {
        PersistentApiKeyType apiKeyType = apiKeyRepository.getApiKey(id);
        if (apiKeyType == null) {
            throw new ApiKeyService.ApiKeyNotFoundException("api key with id " + id + " not found");
        }
        auditLog(apiKeyType);
        apiKeyRepository.delete(apiKeyType);
    }

    /**
     * Clears api key caches. Used after a successful restore operation to ensure that the api keys are
     * up to date.
     */
    public void clearApiKeyCaches() {
        Cache keyCache = cacheManager.getCache(ApiKeyRepository.GET_KEY_CACHE);
        if (keyCache != null) {
            keyCache.clear();
        }
        Cache allKeysCache = cacheManager.getCache(ApiKeyRepository.LIST_ALL_KEYS_CACHE);
        if (allKeysCache != null) {
            allKeysCache.clear();
        }
    }

    /**
     * List all keys
     * @return
     */
    public List<PersistentApiKeyType> listAll() {
        return apiKeyRepository.getAllApiKeys();
    }

    public static class ApiKeyNotFoundException extends NotFoundException {
        public ApiKeyNotFoundException(String s) {
            super(s, new ErrorDeviation(ERROR_API_KEY_NOT_FOUND));
        }
    }
}
