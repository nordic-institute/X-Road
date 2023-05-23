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
package org.niis.xroad.restapi.controller;

import ee.ria.xroad.common.util.JsonUtils;

import org.junit.jupiter.api.Test;
import org.niis.xroad.restapi.config.AllowedHostnamesConfig;
import org.niis.xroad.restapi.converter.PublicApiKeyDataConverter;
import org.niis.xroad.restapi.domain.InvalidRoleNameException;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.dto.PlaintextApiKeyDto;
import org.niis.xroad.restapi.service.ApiKeyService;
import org.niis.xroad.restapi.test.AbstractSpringMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test ApiKeysController
 */
class ApiKeysControllerTest extends AbstractSpringMvcTest {
    @MockBean
    private ApiKeyService apiKeyService;
    @MockBean
    public PublicApiKeyDataConverter publicApiKeyDataConverter;
    @MockBean
    private AllowedHostnamesConfig allowedHostnamesConfig;

    private static final String AUTHORITY_WRONG = "AUTHORITY_WRONG";
    private static final String AUTHORITY_CREATE_API_KEY = "CREATE_API_KEY";
    private static final String VIEW_API_KEYS = "VIEW_API_KEYS";
    private static final String UPDATE_API_KEY = "UPDATE_API_KEY";
    private static final String REVOKE_API_KEY = "REVOKE_API_KEY";
    private static final List<String> ROLES = Collections.singletonList("XROAD_SYSTEM_ADMINISTRATION");

    @Test
    @WithMockUser(authorities = AUTHORITY_CREATE_API_KEY)
    void createHappyPath() throws Exception {
        mockMvc.perform(
                        post(commonModuleEndpointPaths.getApiKeysPath() + "/")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JsonUtils.getObjectWriter().writeValueAsString(ROLES)))
                .andExpect(status().isOk());

        verify(apiKeyService).create(ROLES);
        verify(publicApiKeyDataConverter).convert((PlaintextApiKeyDto) any());
    }

    @Test
    @WithMockUser(authorities = AUTHORITY_WRONG)
    void createThrowsAccessDeniedException() throws Exception {
        mockMvc.perform(
                        post(commonModuleEndpointPaths.getApiKeysPath() + "/")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JsonUtils.getObjectWriter().writeValueAsString(ROLES)))
                .andExpect(status().isForbidden());

        verify(apiKeyService, never()).create((anyCollection()));
    }

    @Test
    @WithMockUser(authorities = AUTHORITY_CREATE_API_KEY)
    void createThrowsBadRequestException() throws Exception {
        //Given
        when(apiKeyService.create(anyCollection())).thenThrow(InvalidRoleNameException.class);
        //When / Then
        mockMvc.perform(
                        post(commonModuleEndpointPaths.getApiKeysPath() + "/")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JsonUtils.getObjectWriter().writeValueAsString(ROLES)))
                .andExpect(status().isBadRequest());

        verify(apiKeyService).create((anyCollection()));
    }

    @Test
    @WithMockUser(authorities = VIEW_API_KEYS)
    void listHappyPath() throws Exception {
        mockMvc.perform(
                        get(commonModuleEndpointPaths.getApiKeysPath() + "/"))
                .andExpect(status().isOk());

        verify(apiKeyService).listAll();
        verify(publicApiKeyDataConverter).convert(Collections.singleton(any()));
    }

    @Test
    @WithMockUser(authorities = AUTHORITY_WRONG)
    void listThrowsAccessDeniedException() throws Exception {
        mockMvc.perform(
                        get(commonModuleEndpointPaths.getApiKeysPath() + "/"))
                .andExpect(status().isForbidden());

        verify(publicApiKeyDataConverter, never()).convert(Collections.singleton(any()));
    }

    @Test
    @WithMockUser(authorities = UPDATE_API_KEY)
    void updateHappyPath() throws Exception {
        long keyId = 1;

        mockMvc.perform(
                        put(commonModuleEndpointPaths.getApiKeysPath() + "/{id}", keyId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JsonUtils.getObjectWriter().writeValueAsString(ROLES)))
                .andExpect(status().isOk());

        verify(apiKeyService).update(keyId, ROLES);
        verify(publicApiKeyDataConverter).convert((PersistentApiKeyType) any());
    }

    @Test
    @WithMockUser(authorities = AUTHORITY_WRONG)
    void updateThrowsAccessDeniedException() throws Exception {
        long keyId = 1;

        mockMvc.perform(
                        put(commonModuleEndpointPaths.getApiKeysPath() + "/{id}", keyId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JsonUtils.getObjectWriter().writeValueAsString(ROLES)))
                .andExpect(status().isForbidden());

        verify(apiKeyService, never()).update(keyId, ROLES);
    }

    @Test
    @WithMockUser(authorities = UPDATE_API_KEY)
    void updateThrowsBadRequestException() throws Exception {
        long keyId = 1;

        when(apiKeyService.update(keyId, ROLES)).thenThrow(InvalidRoleNameException.class);

        mockMvc.perform(
                        put(commonModuleEndpointPaths.getApiKeysPath() + "/{id}", keyId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JsonUtils.getObjectWriter().writeValueAsString(ROLES)))
                .andExpect(status().isBadRequest());

        verify(publicApiKeyDataConverter, never()).convert((PersistentApiKeyType) any());
    }

    @Test
    @WithMockUser(authorities = UPDATE_API_KEY)
    void updateThrowsResourceNotFoundException() throws Exception {
        long keyId = 1;

        when(apiKeyService.update(keyId, ROLES)).thenThrow(ApiKeyService.ApiKeyNotFoundException.class);

        mockMvc.perform(
                        put(commonModuleEndpointPaths.getApiKeysPath() + "/{id}", keyId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JsonUtils.getObjectWriter().writeValueAsString(ROLES)))
                .andExpect(status().isNotFound());

        verify(publicApiKeyDataConverter, never()).convert((PersistentApiKeyType) any());
    }

    @Test
    @WithMockUser(authorities = REVOKE_API_KEY)
    void revokeHappyPath() throws Exception {
        long keyId = 1;

        mockMvc.perform(
                        delete(commonModuleEndpointPaths.getApiKeysPath() + "/{id}", keyId))
                .andExpect(status().isOk());

        verify(apiKeyService).removeForId(keyId);
    }

    @Test
    @WithMockUser(authorities = REVOKE_API_KEY)
    void revokeThrowsResourceNotFoundException() throws Exception {
        long keyId = 1;

        doThrow(ApiKeyService.ApiKeyNotFoundException.class).when(apiKeyService).removeForId(keyId);
        mockMvc.perform(
                        delete(commonModuleEndpointPaths.getApiKeysPath() + "/{id}", keyId))
                .andExpect(status().isNotFound());

        verify(apiKeyService).removeForId(keyId);
    }

    @Test
    @WithMockUser(authorities = AUTHORITY_WRONG)
    void revokeThrowsAccessDeniedException() throws Exception {
        long keyId = 1;

        mockMvc.perform(
                        delete(commonModuleEndpointPaths.getApiKeysPath() + "/{id}", keyId))
                .andExpect(status().isForbidden());

        verify(apiKeyService, never()).removeForId(keyId);
    }

    @Test
    @WithMockUser(authorities = VIEW_API_KEYS)
    void getHappyPath() throws Exception {
        long keyId = 1;

        mockMvc.perform(
                        get(commonModuleEndpointPaths.getApiKeysPath() + "/{id}", keyId))
                .andExpect(status().isOk());

        verify(apiKeyService).getForId(keyId);
    }

    @Test
    @WithMockUser(authorities = VIEW_API_KEYS)
    void getThrowsResourceNotFoundException() throws Exception {
        long keyId = 1;

        when(apiKeyService.getForId(keyId)).thenThrow(ApiKeyService.ApiKeyNotFoundException.class);

        mockMvc.perform(
                        get(commonModuleEndpointPaths.getApiKeysPath() + "/{id}", keyId))
                .andExpect(status().isNotFound());

        verify(apiKeyService).getForId(keyId);
    }

    @Test
    @WithMockUser(authorities = AUTHORITY_WRONG)
    void getThrowsAccessDeniedException() throws Exception {
        long keyId = 1;

        mockMvc.perform(
                        get(commonModuleEndpointPaths.getApiKeysPath() + "/{id}", keyId))
                .andExpect(status().isForbidden());

        verify(apiKeyService, never()).getForId(keyId);
    }
}
