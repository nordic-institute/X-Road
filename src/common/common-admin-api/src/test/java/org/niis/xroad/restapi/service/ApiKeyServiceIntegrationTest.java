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

import org.junit.jupiter.api.Test;
import org.niis.xroad.restapi.domain.InvalidRoleNameException;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.dto.PlaintextApiKeyDto;
import org.niis.xroad.restapi.test.AbstractSpringMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @WithMockUser(authorities = {"ROLE_XROAD_SECURITY_OFFICER", "ROLE_XROAD_REGISTRATION_OFFICER"})
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
    @WithMockUser(authorities = {"ROLE_XROAD_SECURITY_OFFICER", "ROLE_XROAD_REGISTRATION_OFFICER", "ROLE_XROAD_SECURITYSERVER_OBSERVER"})
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
        assertTrue(loaded.getRoles().contains(Role.XROAD_REGISTRATION_OFFICER));

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
    @WithMockUser(authorities = {"ROLE_XROAD_SECURITY_OFFICER", "ROLE_XROAD_REGISTRATION_OFFICER"})
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

    @Test
    @WithMockUser(authorities = "ROLE_XROAD_MANAGEMENT_SERVICE")
    void roleManagementServiceCantCreateApiKey() {
        assertThrows(AccessDeniedException.class, () -> apiKeyService.create(List.of("XROAD_MANAGEMENT_SERVICE")),
                "Missing authority: ROLE_XROAD_SYSTEM_ADMINISTRATOR");

        final EnumSet<Role> otherRoles = EnumSet.allOf(Role.class);
        otherRoles.remove(Role.XROAD_MANAGEMENT_SERVICE);

        for (Role role : otherRoles) {
            var exc = assertThrows(AccessDeniedException.class, () -> apiKeyService.create(List.of(role.name())));
            assertEquals("Missing authority: " + role.getGrantedAuthorityName(), exc.getMessage());
        }
    }

    @Test
    void roleCanCreateOnlyItsOwnRoleApiKey() throws InvalidRoleNameException {
        final EnumSet<Role> allRoles = EnumSet.allOf(Role.class);
        allRoles.remove(Role.XROAD_MANAGEMENT_SERVICE);

        for (Role currentRole : allRoles) {
            mockUserRole(currentRole);

            final PlaintextApiKeyDto apiKey = apiKeyService.create(List.of(currentRole.name()));

            // cant add management role to existing key (except system administrator)
            if (currentRole != Role.XROAD_SYSTEM_ADMINISTRATOR) {
                var exc = assertThrows(AccessDeniedException.class,
                        () -> apiKeyService.update(apiKey.getId(), Role.XROAD_MANAGEMENT_SERVICE.name()));
                assertEquals("Missing authority: " + Role.XROAD_SYSTEM_ADMINISTRATOR.getGrantedAuthorityName(), exc.getMessage());
            } else {
                apiKeyService.update(apiKey.getId(), Role.XROAD_MANAGEMENT_SERVICE.name());
            }

            final EnumSet<Role> otherRoles = EnumSet.copyOf(allRoles);
            otherRoles.remove(currentRole);
            for (Role otherRole : otherRoles) {
                // cant create api key for other role
                var exc = assertThrows(AccessDeniedException.class, () -> apiKeyService.create(List.of(otherRole.name())));
                assertEquals("Missing authority: " + otherRole.getGrantedAuthorityName(), exc.getMessage());

                // cant update existing with additiona role
                exc = assertThrows(AccessDeniedException.class, () -> apiKeyService.update(apiKey.getId(), otherRole.name()));
                assertEquals("Missing authority: " + otherRole.getGrantedAuthorityName(), exc.getMessage());
            }
        }
    }

    @Test
    void onlyAdminRoleCanCreateManagementServiceApiKey() throws InvalidRoleNameException {
        mockUserRole(Role.XROAD_SYSTEM_ADMINISTRATOR);
        apiKeyService.create(Role.XROAD_MANAGEMENT_SERVICE.name());
        apiKeyService.create(List.of(Role.XROAD_MANAGEMENT_SERVICE.name(), Role.XROAD_SYSTEM_ADMINISTRATOR.name()));

        final EnumSet<Role> otherRoles = EnumSet.allOf(Role.class);
        otherRoles.remove(Role.XROAD_SYSTEM_ADMINISTRATOR);
        otherRoles.remove(Role.XROAD_MANAGEMENT_SERVICE);

        for (Role role : otherRoles) {
            mockUserRole(role);
            // cant create api key
            var accessDeniedException = assertThrows(AccessDeniedException.class,
                    () -> apiKeyService.create(List.of(Role.XROAD_MANAGEMENT_SERVICE.name())));
            assertEquals("Missing authority: ROLE_XROAD_SYSTEM_ADMINISTRATOR", accessDeniedException.getMessage());
        }
    }

    @Test
    @SuppressWarnings("java:S2699") // Add at least one assertion to this test case
    void testMultipleRoles() throws InvalidRoleNameException {
        mockUserRoles(EnumSet.allOf(Role.class));
        // user having all roles can create API with all roles
        apiKeyService.create(Arrays.stream(Role.values()).map(Role::name).collect(toList()));

        // key with one role can be appended with all roles
        var apiKey = apiKeyService.create(Role.XROAD_MANAGEMENT_SERVICE.name());
        apiKeyService.update(apiKey.getId(), Arrays.stream(Role.values()).map(Role::name).collect(toList()));

        // user with sys_admin role can update key having security officer role
        mockUserRoles(EnumSet.of(Role.XROAD_SYSTEM_ADMINISTRATOR, Role.XROAD_SECURITY_OFFICER));
        apiKey = apiKeyService.create(List.of(Role.XROAD_SYSTEM_ADMINISTRATOR.name(), Role.XROAD_SECURITY_OFFICER.name()));

        mockUserRole(Role.XROAD_SYSTEM_ADMINISTRATOR);
        apiKeyService.update(apiKey.getId(), List.of(Role.XROAD_SECURITY_OFFICER.name(), Role.XROAD_MANAGEMENT_SERVICE.name()));
    }

    @Test
    @SuppressWarnings("java:S2699") // Add at least one assertion to this test case
    void testRemoveRolesFromApiKey() throws InvalidRoleNameException {
        mockUserRoles(EnumSet.allOf(Role.class));
        final PlaintextApiKeyDto apiKey = apiKeyService.create(Arrays.stream(Role.values()).map(Role::name).collect(toList()));

        mockUserRole(Role.XROAD_SYSTEM_ADMINISTRATOR);

        final EnumSet<Role> roles = EnumSet.allOf(Role.class);
        while (roles.size() > 1) {
            apiKeyService.update(apiKey.getId(), roles.stream().map(Role::name).collect(toList()));
            roles.remove(roles.iterator().next());
        }
    }

    private static void mockUserRole(Role role) {
        mockUserRoles(EnumSet.of(role));
    }

    private static void mockUserRoles(EnumSet<Role> roles) {
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        for (Role role : roles) {
            grantedAuthorities.add(new SimpleGrantedAuthority(role.getGrantedAuthorityName()));
        }
        User principal = new User("user", "pass", true, true, true, true, grantedAuthorities);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal,
                principal.getPassword(), principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

}
