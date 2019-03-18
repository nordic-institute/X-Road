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
import org.hibernate.Session;
import org.niis.xroad.restapi.dao.PersistentApiKeyDAOImpl;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.exceptions.InvalidParametersException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * API key repository which stores encoded keys in DB
 */
@Slf4j
@Repository
@Transactional
public class ApiKeyRepository {

    @Autowired
    private EntityManager entityManager;

    private Session getCurrentSession() {
        return entityManager.unwrap(Session.class);
    }

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    public Map.Entry<String, PersistentApiKeyType> create(String roleName) {
        return create(Collections.singletonList(roleName));
    }

    /**
     * create api key with collection of roles
     * @return Map.Entry with key = plaintext key, value = PersistentApiKeyType
     */
    public Map.Entry<String, PersistentApiKeyType> create(Collection<String> roleNames) {
        if (roleNames.isEmpty()) {
            throw new InvalidParametersException("missing roles");
        }

        Set<Role> roles = Role.getForNames(roleNames);
        String plainKey = createApiKey();
        String encodedKey = encode(plainKey);
        PersistentApiKeyType apiKey = new PersistentApiKeyType(encodedKey, Collections.unmodifiableCollection(roles));
        PersistentApiKeyDAOImpl apiKeyDAO = new PersistentApiKeyDAOImpl();
        apiKeyDAO.insert(getCurrentSession(), apiKey);
        Map.Entry<String, PersistentApiKeyType> entry =
                new AbstractMap.SimpleImmutableEntry<>(plainKey, apiKey);
        return entry;
    }

    private String encode(String key) {
        return passwordEncoder.encode(key);
    }

    /**
     * this scales linearly for key number.
     * Options:
     * - add "username"
     * - accept slowness, will not have millions of api keys
     * - get rid of encoding
     * *** Chosen path, TO DO:
     * - get rid of salting, use a fast hashing algorithm
     * @param key
     * @return
     * @throws NotFoundException if api key was not found
     */
    public PersistentApiKeyType get(String key) throws NotFoundException {
        List<PersistentApiKeyType> keys = new PersistentApiKeyDAOImpl().findAll(getCurrentSession());
        for (PersistentApiKeyType apiKeyType : keys) {
            // TO DO: without random salting, no need to use matches,
            // could encode once and compare encoded values
            if (passwordEncoder.matches(key, apiKeyType.getEncodedKey())) {
                return apiKeyType;
            }
        }
        throw new NotFoundException("api key not found");
    }

    /**
     * remove / revoke one key
     * @param key
     * @throws NotFoundException if api key was not found
     */
    public void remove(String key) throws NotFoundException {
        PersistentApiKeyType apiKeyType = get(key);
        new PersistentApiKeyDAOImpl().delete(getCurrentSession(), apiKeyType);
    }

    /**
     * remove / revoke one key by id
     * @param id
     * @throws NotFoundException if api key was not found
     */
    public void removeById(long id) throws NotFoundException {
        PersistentApiKeyDAOImpl dao = new PersistentApiKeyDAOImpl();
        PersistentApiKeyType apiKeyType = dao.findById(getCurrentSession(), id);
        if (apiKeyType == null) {
            throw new NotFoundException("api key with id " + id + " not found");
        }
        dao.delete(getCurrentSession(), apiKeyType);
    }

    /**
     * List all keys
     * @return
     */
    public List<PersistentApiKeyType> listAll() {
        return new PersistentApiKeyDAOImpl().findAll(getCurrentSession());
    }
}
