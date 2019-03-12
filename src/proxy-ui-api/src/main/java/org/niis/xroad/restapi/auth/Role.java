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

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * User roles
 */
@Getter
public enum Role {
    XROAD_SECURITY_OFFICER("xroad-security-officer"),
    XROAD_REGISTRATION_OFFICER("xroad-registration-officer"),
    XROAD_SERVICE_ADMINISTRATOR("xroad-service-administrator"),
    XROAD_SYSTEM_ADMINISTRATOR("xroad-system-administrator"),
    XROAD_SECURITYSERVER_OBSERVER("xroad-securityserver-observer");

    /**
     * Some unfortunate extra boilerplate, since role names in @RolesAllowed
     * annotations need to be constants. Keep this in sync with the actual
     * enum values.
     * Alternative is to use approach like https://stackoverflow.com/a/54289956/1469083
     * and non-standard annotations
     */
    public final class Names {
        public static final String XROAD_SECURITY_OFFICER = "ROLE_XROAD_SECURITY_OFFICER";
        public static final String XROAD_REGISTRATION_OFFICER = "ROLE_XROAD_REGISTRATION_OFFICER";
        public static final String XROAD_SERVICE_ADMINISTRATOR = "ROLE_XROAD_SERVICE_ADMINISTRATOR";
        public static final String XROAD_SYSTEM_ADMINISTRATOR = "ROLE_XROAD_SYSTEM_ADMINISTRATOR";
        public static final String XROAD_SECURITYSERVER_OBSERVER = "ROLE_XROAD_SECURITYSERVER_OBSERVER";
        private Names() {
        }
    }

    private String linuxGroupName;

    Role(String linuxGroupName) {
        this.linuxGroupName = linuxGroupName;
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
