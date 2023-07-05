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
package org.niis.xroad.restapi.domain;

import com.google.common.collect.MoreCollectors;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * User roles
 */
@Getter
public enum Role {
    XROAD_SECURITY_OFFICER(1, "xroad-security-officer"),
    XROAD_REGISTRATION_OFFICER(2, "xroad-registration-officer"),
    XROAD_SERVICE_ADMINISTRATOR(3, "xroad-service-administrator"),
    XROAD_SYSTEM_ADMINISTRATOR(4, "xroad-system-administrator"),
    XROAD_SECURITYSERVER_OBSERVER(5, "xroad-securityserver-observer"),
    //management service does not exist as a linux group
    XROAD_MANAGEMENT_SERVICE(6, null);

    /**
     * Some unfortunate extra boilerplate, since role names in e.g. @RolesAllowed
     * annotations need to be constants. Keep this in sync with the actual
     * enum values.
     * Alternative is to use approach like https://stackoverflow.com/a/54289956/1469083
     * and non-standard annotations
     */
    public final class Names {
        public static final String XROAD_SECURITY_OFFICER = "XROAD_SECURITY_OFFICER";
        public static final String XROAD_REGISTRATION_OFFICER = "XROAD_REGISTRATION_OFFICER";
        public static final String XROAD_SERVICE_ADMINISTRATOR = "XROAD_SERVICE_ADMINISTRATOR";
        public static final String XROAD_SYSTEM_ADMINISTRATOR = "XROAD_SYSTEM_ADMINISTRATOR";
        public static final String XROAD_SECURITYSERVER_OBSERVER = "XROAD_SECURITYSERVER_OBSERVER";
        private Names() {
        }
    }

    private final String linuxGroupName;
    // primary key in db
    private final int key;

    Role(int key, String linuxGroupName) {
        this.key = key;
        this.linuxGroupName = linuxGroupName;
    }

    /**
     * get key
     * @return
     */
    public int getKey() {
        return key;
    }

    /**
     * return Role matching given key
     * @param key
     * @return
     */
    public static Role getForKey(int key) {
        return Arrays.stream(values())
                .filter(role -> role.getKey() == key)
                .collect(MoreCollectors.onlyElement());
    }

    /**
     * @return name which follows the "ROLE_" + name convention
     */
    public String getGrantedAuthorityName() {
        return "ROLE_" + name();
    }

    /**
     * return Role matching given linuxGroupName, if any
     * @param linuxGroupName
     * @return
     */
    public static Optional<Role> getForGroupName(String linuxGroupName) {
        return Arrays.stream(values())
                .filter(role -> role.linuxGroupName.equals(linuxGroupName))
                .findFirst();
    }

    /**
     * Return Roles matching the given role names. Throws InvalidRoleNameException
     * if matching Role for some name does not exist.
     * @param names
     * @return
     */
    public static Set<Role> getForNames(Collection<String> names) throws InvalidRoleNameException {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String name: names) {
            if (!Role.contains(name)) {
                throw new InvalidRoleNameException("invalid role " + name);
            }
            roles.add(Role.valueOf(name));
        }
        return roles;
    }

    /**
     * Tells if parameter string is one of Role names
     * @param name
     * @return
     */
    public static boolean contains(String name) {
        for (Role role: Role.values()) {
            if (role.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
