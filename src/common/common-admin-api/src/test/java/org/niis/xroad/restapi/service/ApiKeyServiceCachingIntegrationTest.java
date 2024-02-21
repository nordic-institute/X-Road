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

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.niis.xroad.restapi.auth.ApiKeyAuthenticationHelper;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.dto.PlaintextApiKeyDto;
import org.niis.xroad.restapi.test.AbstractSpringMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test {@link  ApiKeyService} and api key authentication helper
 * caching while mocking DB. Will acquire a new application context because mocks EntityManager, Session and Query
 */
class ApiKeyServiceCachingIntegrationTest extends AbstractSpringMvcTest {

    @Autowired
    ApiKeyService apiKeyService;

    @Autowired
    ApiKeyAuthenticationHelper apiKeyAuthenticationHelper;

    @MockBean
    private EntityManager entityManager;

    @Mock
    private Session session;

    @Mock
    private Query query;

    @Test
    @WithMockUser(authorities = {"ROLE_XROAD_REGISTRATION_OFFICER"})
    void testList() throws Exception {
        when(entityManager.unwrap(any())).thenReturn(session);
        when(session.createQuery(anyString())).thenReturn(query);
        when(query.list()).thenReturn(new ArrayList());
        // No keys
        apiKeyService.listAll();
        apiKeyService.listAll();
        verify(query, times(0)).list();
        // Create one key and then get it
        PlaintextApiKeyDto key = apiKeyService.create(Role.XROAD_REGISTRATION_OFFICER.name());
        apiKeyService.listAll();
        apiKeyService.listAll();
        verify(query, times(1)).list();
    }

    private PersistentApiKeyType getPersistedKey(PlaintextApiKeyDto plainKey) {
        return new PersistentApiKeyType(plainKey.getEncodedKey(), plainKey.getRoles());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_XROAD_REGISTRATION_OFFICER"})
    void testCacheEviction() throws Exception {
        // "store" one key
        when(entityManager.unwrap(any())).thenReturn(session);
        when(session.createQuery(anyString())).thenReturn(query);
        doNothing().when(session).persist(any());
        PlaintextApiKeyDto key = apiKeyService.create(Role.XROAD_REGISTRATION_OFFICER.name());
        List<PersistentApiKeyType> listOfOne = Arrays.asList(getPersistedKey(key));
        when(query.list()).thenReturn(listOfOne);
        // then get this key
        apiKeyService.getForPlaintextKey(key.getPlaintextKey());
        apiKeyService.getForPlaintextKey(key.getPlaintextKey());
        apiKeyAuthenticationHelper.getForPlaintextKey(key.getPlaintextKey());
        apiKeyAuthenticationHelper.getForPlaintextKey(key.getPlaintextKey());
        verify(query, times(1)).list();

        // list uses the same cache
        apiKeyService.listAll();
        apiKeyService.listAll();
        verify(query, times(1)).list();

        // create new key to force cache invalidation
        apiKeyService.create(Role.XROAD_REGISTRATION_OFFICER.name());
        apiKeyService.listAll();
        apiKeyService.listAll();
        verify(query, times(2)).list();

        // revoke a key to force cache invalidation
        // (remove(key) itself already uses query.findAll,
        // but it's a cache hit)
        apiKeyService.removeForPlaintextKey(key.getPlaintextKey());
        verify(query, times(2)).list();
        apiKeyAuthenticationHelper.getForPlaintextKey(key.getPlaintextKey());
        apiKeyService.getForPlaintextKey(key.getPlaintextKey());
        apiKeyService.getForPlaintextKey(key.getPlaintextKey());
        verify(query, times(3)).list();
    }

    @Test
    @WithMockUser(authorities = {"ROLE_XROAD_REGISTRATION_OFFICER"})
    void testGet() throws Exception {
        // "store" one key
        when(entityManager.unwrap(any())).thenReturn(session);
        when(session.createQuery(anyString())).thenReturn(query);
        doNothing().when(session).persist(any());
        PlaintextApiKeyDto key =
                apiKeyService.create(Role.XROAD_REGISTRATION_OFFICER.name());
        List<PersistentApiKeyType> listOfOne = Arrays.asList(getPersistedKey(key));
        when(query.list()).thenReturn(listOfOne);
        // then get this key
        apiKeyService.getForPlaintextKey(key.getPlaintextKey());
        apiKeyAuthenticationHelper.getForPlaintextKey(key.getPlaintextKey());
        apiKeyService.getForPlaintextKey(key.getPlaintextKey());
        verify(query, times(1)).list();
    }

}
