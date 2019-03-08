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
package org.niis.xroad.restapi.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps roles to granted authorities
 */
@Component
public class GrantedAuthorityMapper {
    private static final Map<String, Set<String>> DUMMY_MAPPINGS = new HashMap();
    static {
        DUMMY_MAPPINGS.put("XROAD_SERVICE_ADMINISTRATOR",
                Stream.of("view_clients", "view_clients.add_client",
                        "view_clients.view_client_details_dialog.view_client_services.edit_wsdl")
                        .collect(Collectors.toSet()));
        DUMMY_MAPPINGS.put("XROAD_REGISTRATION_OFFICER",
                Stream.of("view_clients.add_client")
                        .collect(Collectors.toSet()));
    }

    /**
     * Return granted authorities for given roles.
     * Result contains
     * - SimpleGrantedAuthority for each xroad role, named using standard "ROLE_" + rolename
     * convention
     * - SimpleGrantedAuthority for permissions that are granted for the xroad roles
     * @param roleNames roles, xroad authentication related or others
     * @return
     */
    public Set<GrantedAuthority> getAuthorities(Collection<String> roleNames) {
        Set<GrantedAuthority> auths = new HashSet<>();
        auths.addAll(getPermissionGrants(roleNames));
        auths.addAll(getRoleGrants(roleNames));
        return auths;
    }

    private Set<SimpleGrantedAuthority> getPermissionGrants(Collection<String> rolenames) {
        Set<String> permissions = new HashSet<>();
        for (String role: rolenames) {
            if (DUMMY_MAPPINGS.containsKey(role)) {
                permissions.addAll(DUMMY_MAPPINGS.get(role));
            }
        }
        return permissions
                .stream()
                .map(name -> new SimpleGrantedAuthority(name.toUpperCase()))
                .collect(Collectors.toSet());
    }

    private Set<SimpleGrantedAuthority> getRoleGrants(Collection<String> rolenames) {
        return rolenames.stream()
                .map(name -> new SimpleGrantedAuthority("ROLE_" + name.toUpperCase()))
                .collect(Collectors.toSet());
    }

}
