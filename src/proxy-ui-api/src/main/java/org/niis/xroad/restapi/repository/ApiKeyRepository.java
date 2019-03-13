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
import org.niis.xroad.restapi.dao.ApiKeyDAOImpl;
import org.niis.xroad.restapi.domain.ApiKeyType;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.exceptions.InvalidParametersException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
    public String create(String roleName) {
        return create(Collections.singletonList(roleName));
    }

    /**
     * create api key with collection of roles
     * @return plaintext key
     */
    public String create(Collection<String> roleNames) {
        if (roleNames.isEmpty()) {
            throw new InvalidParametersException("missing roles");
        }

        Set<Role> roles = Role.getForNames(roleNames);
        String key = createApiKey();
        String encoded = encode(key);
        ApiKeyType apiKeyType = new ApiKeyType(encoded, Collections.unmodifiableCollection(roles));
        ApiKeyDAOImpl apiKeyDAO = new ApiKeyDAOImpl();
        apiKeyDAO.insert(getCurrentSession(), apiKeyType);
        return key;
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
     * Chosen path, TO DO:
     * - get rid of salting, use a fast hashing algorithm
     * @param key
     * @return
     */
    public ApiKeyType get(String key) {
        List<ApiKeyType> keys = new ApiKeyDAOImpl().findAll(getCurrentSession());
        for (ApiKeyType apiKeyType : keys) {
            if (passwordEncoder.matches(key, apiKeyType.getEncodedKey())) {
                return apiKeyType;
            }
        }
        return null;
    }

    /**
     * List all keys
     * @return
     */
    public List<ApiKeyType> listAll() {
        return new ApiKeyDAOImpl().findAll(getCurrentSession());
    }
}
