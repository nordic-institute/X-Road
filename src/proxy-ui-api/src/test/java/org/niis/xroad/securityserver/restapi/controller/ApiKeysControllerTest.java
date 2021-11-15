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
package org.niis.xroad.securityserver.restapi.controller;

import org.junit.Test;
import org.niis.xroad.restapi.controller.ApiKeysController;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.dto.PlaintextApiKeyDto;
import org.niis.xroad.securityserver.restapi.openapi.AbstractApiControllerTestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test ApiKeysController
 */
public class ApiKeysControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    private ApiKeysController apiKeysController;
    private static final List<String> ROLES = Collections.singletonList("XROAD_SYSTEM_ADMINISTRATION");

    @Test
    @WithMockUser(authorities = "VIEW_API_KEYS")
    public void listApiKeys() {
        apiKeysController.list();
        verify(apiKeyService).listAll();
        verify(publicApiKeyDataConverter).convert(Collections.singleton(any()));
    }

    @Test
    @WithMockUser(authorities = "CREATE_API_KEY")
    public void createApiKey() throws Exception {
        apiKeysController.createKey(ROLES);
        verify(apiKeyService).create(ROLES);
        verify(publicApiKeyDataConverter).convert((PlaintextApiKeyDto) any());
    }

    @Test
    @WithMockUser(authorities = "UPDATE_API_KEY")
    public void updateApiKey() throws Exception {
        long keyId = 1;
        apiKeysController.updateKey(keyId, ROLES);
        verify(apiKeyService).update(keyId, ROLES);
        verify(publicApiKeyDataConverter).convert((PersistentApiKeyType) any());
    }

    @Test
    @WithMockUser(authorities = "REVOKE_API_KEY")
    public void revokeApiKey() throws Exception {
        long keyId = 1;
        apiKeysController.revoke(keyId);
        verify(apiKeyService).removeForId(keyId);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = "REVOKE_API_KEY")
    public void listApiKeysWrongAuthority() {
        apiKeysController.list();
        verify(publicApiKeyDataConverter, times(0)).convert(Collections.singleton(any()));
    }
}
