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
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.domain.InvalidRoleNameException;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.dto.PlaintextApiKeyDto;
import org.niis.xroad.restapi.repository.ApiKeyRepository;
import org.niis.xroad.restapi.util.SecurityHelper;
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

import static org.niis.xroad.common.exception.util.CommonDeviationMessage.API_KEY_NOT_FOUND;
import static org.niis.xroad.restapi.config.ApiCachingConfiguration.LIST_ALL_KEYS_CACHE;

/**
 * ApiKey service.
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ApiKeyService {

    private final PasswordEncoder passwordEncoder;
    private final ApiKeyRepository apiKeyRepository;
    private final CacheManager cacheManager;
    private final AuditDataHelper auditDataHelper;
    private final SecurityHelper securityHelper;

    /**
     * Api keys are created with UUID.randomUUID which uses SecureRandom,
     * which is cryptographically secure.
     *
     * @return pseudo-random "unique" api key.
     */
    private String createApiKey() {
        return UUID.randomUUID().toString();
    }

    /**
     * create api key with one role
     *
     * @throws InvalidRoleNameException if roleNames was empty or contained invalid roles
     */
    public PlaintextApiKeyDto create(String roleName) throws InvalidRoleNameException {
        return create(Collections.singletonList(roleName));
    }

    /**
     * create api key with collection of roles
     *
     * @return new PersistentApiKeyType that contains the new key in plain text
     * @throws InvalidRoleNameException if roleNames was empty or contained invalid roles
     */
    public PlaintextApiKeyDto create(Collection<String> roleNames)
            throws InvalidRoleNameException {
        if (roleNames.isEmpty()) {
            throw new InvalidRoleNameException("missing roles");
        }
        Set<Role> roles = Role.getForNames(roleNames);
        verifyUserCanCreateApiKeyForRoles(roles);
        String plainKey = createApiKey();
        String encodedKey = encode(plainKey);
        PersistentApiKeyType apiKey = new PersistentApiKeyType(encodedKey,
                Collections.unmodifiableCollection(roles));

        apiKeyRepository.saveOrUpdate(apiKey);
        auditLog(apiKey);

        return new PlaintextApiKeyDto(apiKey.getId(), plainKey, encodedKey, roles);
    }

    private void verifyUserCanUpdateApiKeyRoles(PersistentApiKeyType apiKey, Set<Role> roles) {
        final Set<Role> currentKeyRoles = apiKey.getRoles();
        for (Role role : roles) {
            // if the role assigned to api key, it is allowed to leave it
            if (!currentKeyRoles.contains(role)) {
                verifyUserCanAssignRole(role);
            }
        }
    }

    private void verifyUserCanAssignRole(Role role) {
        if (role != Role.XROAD_MANAGEMENT_SERVICE) {
            securityHelper.verifyAuthority(role.getGrantedAuthorityName());
        } else {
            securityHelper.verifyAuthority(Role.XROAD_SYSTEM_ADMINISTRATOR.getGrantedAuthorityName());
        }
    }

    private void verifyUserCanCreateApiKeyForRoles(Set<Role> roles) {
        roles.forEach(this::verifyUserCanAssignRole);
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
     * Update api key with one role by key id.
     *
     * @param id       apiKey id.
     * @param roleName a role name.
     * @return updated {@link  PersistentApiKeyType} entity.
     * @throws InvalidRoleNameException              if roleNames was empty or contained invalid roles
     * @throws ApiKeyService.ApiKeyNotFoundException if api key was not found
     */
    public PersistentApiKeyType update(long id, String roleName)
            throws InvalidRoleNameException, ApiKeyService.ApiKeyNotFoundException {
        return update(id, Collections.singletonList(roleName));
    }

    /**
     * Update api key with collection of roles by key id.
     *
     * @param id        apiKey id.
     * @param roleNames a list of role names.
     * @return updated {@link  PersistentApiKeyType} entity.
     * @throws InvalidRoleNameException              if roleNames was empty or contained invalid roles.
     * @throws ApiKeyService.ApiKeyNotFoundException if api key was not found.
     */
    public PersistentApiKeyType update(long id, Collection<String> roleNames)
            throws InvalidRoleNameException, ApiKeyService.ApiKeyNotFoundException {
        auditLog(id, roleNames);
        PersistentApiKeyType apiKeyType = getForId(id);
        if (roleNames.isEmpty()) {
            throw new InvalidRoleNameException("missing roles");
        }
        Set<Role> roles = Role.getForNames(roleNames);
        verifyUserCanUpdateApiKeyRoles(apiKeyType, roles);
        apiKeyType.setRoles(roles);
        apiKeyRepository.saveOrUpdate(apiKeyType);
        return apiKeyType;
    }

    /**
     * Get one API key.
     *
     * @param id apiKey id.
     * @throws ApiKeyService.ApiKeyNotFoundException if api key was not found
     */
    public PersistentApiKeyType getForId(long id)
            throws ApiKeyService.ApiKeyNotFoundException {
        PersistentApiKeyType apiKeyType = apiKeyRepository.getApiKey(id);
        if (apiKeyType == null) {
            throw new ApiKeyService.ApiKeyNotFoundException(id);
        }
        return apiKeyType;
    }

    /**
     * Encode a plaintext key.
     *
     * @param key plaintext api key.
     * @return encoded api key.
     */
    private String encode(String key) {
        return passwordEncoder.encode(key);
    }

    /**
     * Get matching key.
     *
     * @param key plaintext api key.
     * @return a {@link  PersistentApiKeyType} entity.
     * @throws ApiKeyService.ApiKeyNotFoundException if api key was not found
     */
    public PersistentApiKeyType getForPlaintextKey(String key) throws ApiKeyService.ApiKeyNotFoundException {
        return getForEncodedKey(encode(key));
    }

    /**
     * Get matching Api key.
     *
     * @param key encoded api key.
     * @return a {@link  PersistentApiKeyType} entity.
     * @throws ApiKeyService.ApiKeyNotFoundException if api key was not found.
     */
    public PersistentApiKeyType getForEncodedKey(String key) throws ApiKeyService.ApiKeyNotFoundException {
        List<PersistentApiKeyType> keys = apiKeyRepository.getAllApiKeys();
        for (PersistentApiKeyType apiKeyType : keys) {
            if (apiKeyType.getEncodedKey().equals(key)) {
                return apiKeyType;
            }
        }
        throw new ApiKeyService.ApiKeyNotFoundException();
    }

    /**
     * Remove / revoke one key.
     *
     * @param key plaintext api key.
     * @throws ApiKeyService.ApiKeyNotFoundException if api key was not found.
     */
    public void removeForPlaintextKey(String key) throws ApiKeyService.ApiKeyNotFoundException {
        PersistentApiKeyType apiKeyType = getForPlaintextKey(key);
        auditLog(apiKeyType);
        apiKeyRepository.delete(apiKeyType);
    }

    /**
     * Remove / revoke one key based on id.
     *
     * @param id apiKey id.
     * @throws ApiKeyService.ApiKeyNotFoundException if api key was not found
     */
    public void removeForId(long id) throws ApiKeyService.ApiKeyNotFoundException {
        PersistentApiKeyType apiKeyType = getForId(id);
        auditLog(apiKeyType);
        apiKeyRepository.delete(apiKeyType);
    }

    /**
     * Clears api key caches. Used after a successful restore operation to ensure that the api keys are
     * up-to date.
     */
    public void clearApiKeyCaches() {
        Cache allKeysCache = cacheManager.getCache(LIST_ALL_KEYS_CACHE);
        if (allKeysCache != null) {
            allKeysCache.clear();
        }
    }

    /**
     * List all keys.
     *
     * @return a list of {@link PersistentApiKeyType}.
     */
    public List<PersistentApiKeyType> listAll() {
        return apiKeyRepository.getAllApiKeys();
    }

    @SuppressWarnings("squid:S110")
    public static class ApiKeyNotFoundException extends NotFoundException {
        public ApiKeyNotFoundException(final Object... metadata) {
            super(API_KEY_NOT_FOUND, metadata);
        }
    }
}
