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
package org.niis.xroad.restapi.auth;

import org.junit.jupiter.api.Test;
import org.niis.xroad.restapi.domain.InvalidRoleNameException;
import org.niis.xroad.restapi.domain.Role;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.restapi.domain.Role.XROAD_SERVICE_ADMINISTRATOR;

class RoleTest {

    @Test
    void getGrantedAuthorityName() {
        String result = XROAD_SERVICE_ADMINISTRATOR.getGrantedAuthorityName();
        assertThat(result).isEqualTo("ROLE_" + XROAD_SERVICE_ADMINISTRATOR.name());
    }

    @Test
    void getForNames() throws InvalidRoleNameException {
        Set<Role> result = Role.getForNames(Set.of(XROAD_SERVICE_ADMINISTRATOR.name()));
        assertThat(result).containsOnly(XROAD_SERVICE_ADMINISTRATOR);

        assertThatThrownBy(() -> Role.getForNames(Set.of("INVALID_ROLE")))
                .isInstanceOf(InvalidRoleNameException.class)
                .hasMessage("Invalid role: INVALID_ROLE");
    }
}
