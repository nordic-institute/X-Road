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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.exceptions.InvalidParametersException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;

/**
 * Test api key repository
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
public class ApiKeyRepositoryIntegrationTest {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Test
    public void testDelete() {
        String plainKey = apiKeyRepository.create(
                Arrays.asList("XROAD_SECURITY_OFFICER", "XROAD_REGISTRATION_OFFICER"))
                .getKey();
        assertEquals(1, apiKeyRepository.listAll().size());
        PersistentApiKeyType apiKey = apiKeyRepository.get(plainKey);
        assertEquals(2, apiKey.getRoles().size());

        // after remove, listall should be 0 and get(key) should fail
        apiKeyRepository.remove(plainKey);
        assertEquals(0, apiKeyRepository.listAll().size());
        try {
            apiKey = apiKeyRepository.get(plainKey);
            fail("should throw exception");
        } catch (NotFoundException expected) {
        }
        try {
            apiKeyRepository.remove(plainKey);
            fail("should throw exception");
        } catch (NotFoundException expected) {
        }
    }

    @Test
    public void testSaveAndLoad() {
        String plainKey = apiKeyRepository.create(
                Arrays.asList("XROAD_SECURITY_OFFICER", "XROAD_REGISTRATION_OFFICER"))
                .getKey();
        PersistentApiKeyType loaded = apiKeyRepository.get(plainKey);
        assertNotNull(loaded);
        String encodedKey = loaded.getEncodedKey();
        assertEquals(new Long(1), loaded.getId());
        assertTrue(!plainKey.equals(encodedKey));
        assertEquals(encodedKey, loaded.getEncodedKey());
        assertEquals(2, loaded.getRoles().size());
        assertTrue(loaded.getRoles().contains(Role.XROAD_SECURITY_OFFICER));
    }

    @Test
    public void testDifferentRoles() {
        try {
            String key = apiKeyRepository.create(new ArrayList<>()).getKey();
            fail("should fail due to missing roles");
        } catch (InvalidParametersException expected) { }

        try {
            String key = apiKeyRepository.create(Arrays.asList("XROAD_SECURITY_OFFICER",
                    "FOOBAR")).getKey();
            fail("should fail due to bad role");
        } catch (InvalidParametersException expected) { }

        String key = apiKeyRepository.create(Arrays.asList("XROAD_SECURITY_OFFICER",
                "XROAD_REGISTRATION_OFFICER")).getKey();
    }
}
