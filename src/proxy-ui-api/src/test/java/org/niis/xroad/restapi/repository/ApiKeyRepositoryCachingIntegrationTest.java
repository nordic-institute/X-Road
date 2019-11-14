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
import org.hibernate.query.Query;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.domain.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test api key repository caching while mocking DB
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
public class ApiKeyRepositoryCachingIntegrationTest {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @MockBean
    private EntityManager entityManager;

    @Mock
    private Session session;

    @Mock
    private Query query;

    @Test
    public void testList() {
        when(entityManager.unwrap(any())).thenReturn(session);
        when(session.createQuery(anyString())).thenReturn(query);
        when(query.list()).thenReturn(new ArrayList());
        apiKeyRepository.listAll();
        apiKeyRepository.listAll();
        verify(query, times(1)).list();
    }

    @Test
    public void testCacheEviction() throws Exception {
        // "store" one key
        when(entityManager.unwrap(any())).thenReturn(session);
        when(session.createQuery(anyString())).thenReturn(query);
        doNothing().when(session).persist(any());
        Map.Entry<String, PersistentApiKeyType> keyEntry =
                apiKeyRepository.create(Role.XROAD_REGISTRATION_OFFICER.name());
        List<PersistentApiKeyType> listOfOne = Arrays.asList(keyEntry.getValue());
        when(query.list()).thenReturn(listOfOne);
        // then get this key
        apiKeyRepository.get(keyEntry.getKey());
        apiKeyRepository.get(keyEntry.getKey());
        verify(query, times(1)).list();

        // list uses a different cache
        apiKeyRepository.listAll();
        apiKeyRepository.listAll();
        verify(query, times(2)).list();

        // create new key to force cache invalidation
        apiKeyRepository.create(Role.XROAD_REGISTRATION_OFFICER.name());
        apiKeyRepository.listAll();
        apiKeyRepository.listAll();
        verify(query, times(3)).list();

        // revoke a key to force cache invalidation
        // (remove(key) itself already uses query.findAll)
        apiKeyRepository.remove(keyEntry.getKey());
        verify(query, times(4)).list();
        apiKeyRepository.get(keyEntry.getKey());
        apiKeyRepository.get(keyEntry.getKey());
        verify(query, times(5)).list();
    }

    @Test
    public void testGet() throws Exception {
        // "store" one key
        when(entityManager.unwrap(any())).thenReturn(session);
        when(session.createQuery(anyString())).thenReturn(query);
        doNothing().when(session).persist(any());
        Map.Entry<String, PersistentApiKeyType> keyEntry =
                apiKeyRepository.create(Role.XROAD_REGISTRATION_OFFICER.name());
        List<PersistentApiKeyType> listOfOne = Arrays.asList(keyEntry.getValue());
        when(query.list()).thenReturn(listOfOne);
        // then get this key
        apiKeyRepository.get(keyEntry.getKey());
        apiKeyRepository.get(keyEntry.getKey());
        verify(query, times(1)).list();
    }

}
