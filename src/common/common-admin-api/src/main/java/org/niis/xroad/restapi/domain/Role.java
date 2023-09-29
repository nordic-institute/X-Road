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
package org.niis.xroad.restapi.domain;

import lombok.Getter;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * User roles
 */
@Getter
public enum Role {
    XROAD_SECURITY_OFFICER,
    XROAD_REGISTRATION_OFFICER,
    XROAD_SERVICE_ADMINISTRATOR,
    XROAD_SYSTEM_ADMINISTRATOR,
    XROAD_SECURITYSERVER_OBSERVER,
    XROAD_MANAGEMENT_SERVICE;

    /**
     * @return name which follows the "ROLE_" + name convention
     */
    public String getGrantedAuthorityName() {
        return "ROLE_" + name();
    }

    /**
     * Return Roles matching the given role names. Throws InvalidRoleNameException
     * if matching Role for some name does not exist.
     * @param names
     * @return set of matching roles
     */
    public static Set<Role> getForNames(Collection<String> names) throws InvalidRoleNameException {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String name: names) {
            try {
                Role.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw new InvalidRoleNameException("Invalid role: " + name, e);
            }
            roles.add(Role.valueOf(name));
        }
        return roles;
    }
}
