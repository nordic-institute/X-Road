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
package org.niis.xroad.securityserver.restapi.service;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.domain.InvalidRoleNameException;
import org.niis.xroad.securityserver.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.securityserver.restapi.domain.Role;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Test api key service
 */
public class ApiKeyServiceIntegrationTest extends AbstractServiceIntegrationTestContext {
    private static final int KEYS_CREATED_ELSEWHERE = 1; // one key in data.sql

    @Autowired
    ApiKeyService apiKeyService;

    @Test
    public void testDelete() throws Exception {
        String plainKey = apiKeyService.create(
                Arrays.asList("XROAD_SECURITY_OFFICER", "XROAD_REGISTRATION_OFFICER"))
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
    public void testSaveAndLoadAndUpdate() throws Exception {
        // Save
        String plainKey = apiKeyService.create(
                Arrays.asList("XROAD_SECURITY_OFFICER", "XROAD_REGISTRATION_OFFICER"))
                .getPlaintextKey();
        // Load
        PersistentApiKeyType loaded = apiKeyService.getForPlaintextKey(plainKey);
        assertNotNull(loaded);
        String encodedKey = loaded.getEncodedKey();

        assertEquals(new Long(KEYS_CREATED_ELSEWHERE + 1), loaded.getId());
        assertTrue(!plainKey.equals(encodedKey));
        assertEquals(encodedKey, loaded.getEncodedKey());
        assertEquals(2, loaded.getRoles().size());
        assertTrue(loaded.getRoles().contains(Role.XROAD_SECURITY_OFFICER));

        // Load by encoded key
        assertNotNull(apiKeyService.getForEncodedKey(encodedKey));

        // Update
        PersistentApiKeyType updated = apiKeyService.update(loaded.getId(),
                Arrays.asList("XROAD_SECURITYSERVER_OBSERVER"));
        assertEquals(new Long(KEYS_CREATED_ELSEWHERE + 1), updated.getId());
        assertEquals(1, updated.getRoles().size());
        assertTrue(updated.getRoles().contains(Role.XROAD_SECURITYSERVER_OBSERVER));
        assertFalse(updated.getRoles().contains(Role.XROAD_SECURITY_OFFICER));
    }

    @Test
    public void testDifferentRoles() throws Exception {
        try {
            String key = apiKeyService.create(new ArrayList<>()).getPlaintextKey();
            fail("should fail due to missing roles");
        } catch (InvalidRoleNameException expected) {
        }

        try {
            String key = apiKeyService.create(Arrays.asList("XROAD_SECURITY_OFFICER",
                    "FOOBAR")).getPlaintextKey();
            fail("should fail due to bad role");
        } catch (InvalidRoleNameException expected) {
        }

        try {
            PersistentApiKeyType key = apiKeyService.update(1, new ArrayList<>());
            fail("should fail due to missing roles");
        } catch (InvalidRoleNameException expected) {
        }

        try {
            PersistentApiKeyType key = apiKeyService.update(1, Arrays.asList("XROAD_SECURITY_OFFICER", "FOOBAR"));
            fail("should fail due to bad role");
        } catch (InvalidRoleNameException expected) {
        }

        String key = apiKeyService.create(Arrays.asList("XROAD_SECURITY_OFFICER",
                "XROAD_REGISTRATION_OFFICER")).getPlaintextKey();
    }
}
