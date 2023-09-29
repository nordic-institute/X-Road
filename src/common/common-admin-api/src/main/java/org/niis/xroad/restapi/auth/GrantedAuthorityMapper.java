/*
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

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.domain.Role;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.collect.Sets.newHashSet;
import static ee.ria.xroad.common.SystemProperties.NodeType.SLAVE;

/**
 * Maps roles to granted authorities
 */
@Component
@Slf4j
public class GrantedAuthorityMapper {

    static final String PERMISSION_ACTIVATE_DEACTIVATE_TOKEN = "activate_deactivate_token";
    private static final String YAML_PERMISSIONS_RESOURCE = "permissions.yml";
    private static final Set<String> SLAVE_NODE_PERMISSION_WHITELIST = newHashSet(PERMISSION_ACTIVATE_DEACTIVATE_TOKEN);

    private final Map<Role, Set<String>> rolesToPermissions;

    /**
     * constructor
     */
    public GrantedAuthorityMapper() {
        rolesToPermissions = parseYamlPermissions(YAML_PERMISSIONS_RESOURCE);
    }

    /**
     * Read yaml permissions
     *
     * @param res
     */
    private Map<Role, Set<String>> parseYamlPermissions(String res) {

        YamlMapFactoryBean yamlMapFactoryBean = new YamlMapFactoryBean();
        yamlMapFactoryBean.setResources(new ClassPathResource(res));
        Map<String, Object> parsedYamlPermissions = yamlMapFactoryBean.getObject();
        Collection<Map<String, Collection<String>>> permissionsToRolesList =
                (Collection<Map<String, Collection<String>>>) parsedYamlPermissions.get("document");


        Map<Role, Set<String>> rolePermissionMappings = new HashMap<>();
        for (Map<String, Collection<String>> permissionsToRoles : permissionsToRolesList) {
            for (String permissionName : permissionsToRoles.keySet()) {
                for (String roleName : permissionsToRoles.get(permissionName)) {
                    Role role = Role.valueOf(roleName.toUpperCase());
                    if (!rolePermissionMappings.containsKey(role)) {
                        rolePermissionMappings.put(role, new HashSet<>());
                    }
                    rolePermissionMappings.get(role).add(permissionName);
                }
            }
        }

        log.debug("props: {}", rolePermissionMappings);
        return rolePermissionMappings;
    }

    /**
     * Return granted authorities for given Roles.
     * Result contains
     * - SimpleGrantedAuthority for each xroad role, named using standard "ROLE_" + rolename
     * convention
     * - SimpleGrantedAuthority for permissions that are granted for the xroad roles
     *
     * @param roles roles, xroad authentication related or others
     * @return
     */
    public Set<GrantedAuthority> getAuthorities(Collection<Role> roles) {
        Set<GrantedAuthority> auths = new HashSet<>();
        auths.addAll(getAdjustedPermissionGrants(roles));
        auths.addAll(getRoleGrants(roles));
        return auths;
    }

    /**
     * Adjust the given user permissions to match the node type of the Security Server
     * If the server is marked as a SECONDARY server in a cluster:
     * -> if the user has the OBSERVER role, OBSERVER role permissions are allowed
     * -> if the permissions is whitelisted, it will be allowed
     * <p>
     * Otherwise, just returns the given roles in a Set, which means that the user in on a PRIMARY server
     *
     * @param roles roles to adjust
     * @return filtered out roles
     */
    private Set<SimpleGrantedAuthority> getAdjustedPermissionGrants(Collection<Role> roles) {
        final SystemProperties.NodeType nodeType = SystemProperties.getServerNodeType();
        log.trace("Adjusting permission grants for Node type {}", nodeType);

        Set<SimpleGrantedAuthority> permissions = new HashSet<>();
        if (SLAVE.equals(nodeType)) {
            log.debug("This is a secondary node - only observer role and whitelisted permissions are permitted");
            roles.forEach(role -> {
                //All observer permissions are allowed
                if (Role.XROAD_SECURITYSERVER_OBSERVER.equals(role)) {
                    permissions.addAll(getPermissionGrants(role));
                } else {
                    permissions.addAll(getSlaveAllowedPermissions(role));
                }
            });
        } else {
            roles.forEach(role -> permissions.addAll(getPermissionGrants(role)));
        }

        return permissions;
    }

    private Set<SimpleGrantedAuthority> getSlaveAllowedPermissions(Role role) {
        return getPermissionGrants(role).stream()
                .peek(simpleGrantedAuthority -> log.debug("Checking {}", simpleGrantedAuthority.getAuthority()))
                .filter(grantedAuthority ->
                        SLAVE_NODE_PERMISSION_WHITELIST.stream()
                                .anyMatch(grantedAuthority.getAuthority()::equalsIgnoreCase))
                .collect(Collectors.toSet());
    }

    private Set<SimpleGrantedAuthority> getPermissionGrants(Role role) {
        final Set<String> permissions = new HashSet<>();

        if (rolesToPermissions.containsKey(role)) {
            permissions.addAll(rolesToPermissions.get(role));
        }

        return permissions
                .stream()
                .map(name -> new SimpleGrantedAuthority(name.toUpperCase()))
                .collect(Collectors.toSet());
    }

    private Set<SimpleGrantedAuthority> getRoleGrants(Collection<Role> roles) {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getGrantedAuthorityName()))
                .collect(Collectors.toSet());
    }

}
