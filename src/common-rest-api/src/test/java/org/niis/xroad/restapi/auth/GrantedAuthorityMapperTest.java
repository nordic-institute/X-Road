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
package org.niis.xroad.restapi.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.restapi.domain.Role;
import org.springframework.security.core.GrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.SystemProperties.NODE_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.niis.xroad.restapi.auth.GrantedAuthorityMapper.PERMISSION_ACTIVATE_DEACTIVATE_TOKEN;

/**
 * Test GrantedAuthorityMapper
 */
public class GrantedAuthorityMapperTest {
    private static final Collection<Role> ADMIN_ROLES = Arrays.asList(
            Role.XROAD_SECURITY_OFFICER,
            Role.XROAD_REGISTRATION_OFFICER,
            Role.XROAD_SERVICE_ADMINISTRATOR,
            Role.XROAD_SYSTEM_ADMINISTRATOR,
            Role.XROAD_SECURITYSERVER_OBSERVER);

    private final GrantedAuthorityMapper mapper = new GrantedAuthorityMapper();

    @AfterEach
    public void afterTest() {
        System.setProperty(NODE_TYPE, "foo"); // reset the node type property – gibberish/null defaults to STANDALONE
    }

    @Test
    public void simpleMapping() {
        String roleName = "XROAD_REGISTRATION_OFFICER";
        Set<GrantedAuthority> authorities = mapper.getAuthorities(
                Collections.singletonList(Role.valueOf(roleName)));
        assertTrue(authorities.size() > 1);
        Set<String> authStrings = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertTrue(authStrings.contains("ROLE_" + roleName));
        assertTrue(authStrings.contains("ADD_CLIENT"));
        assertFalse(authStrings.contains("INIT_CONFIG"));
    }

    @Test
    public void simpleMappingSystemAdmin() {
        String roleName = "XROAD_SYSTEM_ADMINISTRATOR";
        Set<GrantedAuthority> authorities = mapper.getAuthorities(
                Collections.singletonList(Role.valueOf(roleName)));
        Set<String> authStrings = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertTrue(authStrings.contains("INIT_CONFIG"));
    }

    @Test
    public void shouldAllowWhitelistedPermissionsOnSlaveNode() {
        System.setProperty(NODE_TYPE, "slave"); // secondary node in a cluster

        final Set<GrantedAuthority> result = mapper.getAuthorities(ADMIN_ROLES);

        assertEquals(6, result.size());
        assertTrue(containsPermission(result, Role.XROAD_SECURITY_OFFICER.getGrantedAuthorityName()));
        assertTrue(containsPermission(result, Role.XROAD_REGISTRATION_OFFICER.getGrantedAuthorityName()));
        assertTrue(containsPermission(result, Role.XROAD_SERVICE_ADMINISTRATOR.getGrantedAuthorityName()));
        assertTrue(containsPermission(result, Role.XROAD_SYSTEM_ADMINISTRATOR.getGrantedAuthorityName()));
        assertTrue(containsPermission(result, Role.XROAD_SECURITYSERVER_OBSERVER.getGrantedAuthorityName()));
        assertTrue(containsPermission(result, PERMISSION_ACTIVATE_DEACTIVATE_TOKEN));
    }

    @Test
    public void shouldAllowAllPermissionsOnMasterNode() {
        final Set<GrantedAuthority> result = mapper.getAuthorities(ADMIN_ROLES);

        assertEquals(9, result.size());
        assertTrue(containsPermission(result, Role.XROAD_SECURITY_OFFICER.getGrantedAuthorityName()));
        assertTrue(containsPermission(result, Role.XROAD_REGISTRATION_OFFICER.getGrantedAuthorityName()));
        assertTrue(containsPermission(result, Role.XROAD_SERVICE_ADMINISTRATOR.getGrantedAuthorityName()));
        assertTrue(containsPermission(result, Role.XROAD_SYSTEM_ADMINISTRATOR.getGrantedAuthorityName()));
        assertTrue(containsPermission(result, Role.XROAD_SECURITYSERVER_OBSERVER.getGrantedAuthorityName()));
        assertTrue(containsPermission(result, PERMISSION_ACTIVATE_DEACTIVATE_TOKEN));

        assertTrue(containsPermission(result, "ADD_CLIENT"));
        assertTrue(containsPermission(result, "INIT_CONFIG"));
        assertTrue(containsPermission(result, "GENERATE_KEY"));
    }

    private boolean containsPermission(final Set<GrantedAuthority> grantedAuthorities, final String permission) {
        return grantedAuthorities.stream()
                .anyMatch(authority -> permission.equalsIgnoreCase(authority.getAuthority()));
    }
}
