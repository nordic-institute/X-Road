/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.service;

import org.junit.jupiter.api.Test;
import org.niis.xroad.restapi.domain.InvalidRoleNameException;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.test.AbstractSpringMvcTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * Test cases for {@link  ApiKeyService}.
 */
class ApiKeyServiceIntegrationTest extends AbstractSpringMvcTest {
    private static final int KEYS_CREATED_ELSEWHERE = 1; // one key in data.sql

    @Autowired
    private ApiKeyService apiKeyService;

    @Test
    void testDelete() throws Exception {
        String plainKey = apiKeyService.create(Arrays.asList("XROAD_SECURITY_OFFICER", "XROAD_REGISTRATION_OFFICER"))
                .getPlaintextKey();
        assertEquals(KEYS_CREATED_ELSEWHERE + 1, apiKeyService.listAll().size());
        PersistentApiKeyType apiKey = apiKeyService.getForPlaintextKey(plainKey);
        assertEquals(2, apiKey.getRoles().size());

        // after remove, listall should be reduced and get(key) should fail
        apiKeyService.removeForPlaintextKey(plainKey);
        assertEquals(KEYS_CREATED_ELSEWHERE, apiKeyService.listAll().size());
        try {
            apiKeyService.removeForPlaintextKey(plainKey);
            fail("should throw exception");
        } catch (ApiKeyService.ApiKeyNotFoundException expected) {
        }
        try {
            apiKeyService.removeForPlaintextKey(plainKey);
            fail("should throw exception");
        } catch (ApiKeyService.ApiKeyNotFoundException expected) {
        }
    }

    @Test
    void testSaveAndLoadAndUpdate() throws Exception {
        // Save
        String plainKey = apiKeyService.create(Arrays.asList("XROAD_SECURITY_OFFICER", "XROAD_REGISTRATION_OFFICER"))
                .getPlaintextKey();
        // Load
        PersistentApiKeyType loaded = apiKeyService.getForPlaintextKey(plainKey);
        assertNotNull(loaded);
        String encodedKey = loaded.getEncodedKey();

        assertEquals(KEYS_CREATED_ELSEWHERE + 1L, loaded.getId());
        assertNotEquals(plainKey, encodedKey);
        assertEquals(encodedKey, loaded.getEncodedKey());
        assertEquals(2, loaded.getRoles().size());
        assertTrue(loaded.getRoles().contains(Role.XROAD_SECURITY_OFFICER));

        // Load by encoded key
        assertNotNull(apiKeyService.getForEncodedKey(encodedKey));

        // Update
        PersistentApiKeyType updated = apiKeyService.update(loaded.getId(), List.of("XROAD_SECURITYSERVER_OBSERVER"));
        assertEquals(KEYS_CREATED_ELSEWHERE + 1L, updated.getId());
        assertEquals(1, updated.getRoles().size());
        assertTrue(updated.getRoles().contains(Role.XROAD_SECURITYSERVER_OBSERVER));
        assertFalse(updated.getRoles().contains(Role.XROAD_SECURITY_OFFICER));
    }

    @Test
    void testDifferentRoles() throws Exception {
        try {
            apiKeyService.create(new ArrayList<>());
            fail("should fail due to missing roles");
        } catch (InvalidRoleNameException expected) {
        }

        try {
            apiKeyService.create(Arrays.asList("XROAD_SECURITY_OFFICER", "FOOBAR"));
            fail("should fail due to bad role");
        } catch (InvalidRoleNameException expected) {
        }

        try {
            apiKeyService.update(1, new ArrayList<>());
            fail("should fail due to missing roles");
        } catch (InvalidRoleNameException expected) {
        }

        try {
            apiKeyService.update(1, Arrays.asList("XROAD_SECURITY_OFFICER", "FOOBAR"));
            fail("should fail due to bad role");
        } catch (InvalidRoleNameException expected) {
        }

        apiKeyService.create(Arrays.asList("XROAD_SECURITY_OFFICER", "XROAD_REGISTRATION_OFFICER"));
    }
}
