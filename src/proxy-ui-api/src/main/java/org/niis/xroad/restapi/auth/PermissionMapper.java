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

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps roles to permissions
 */
@Component
public class PermissionMapper {
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
     * Return permissions for given role
     * @param roles
     * @return
     */
    public Collection<String> getPermissions(Collection<String> roles) {
        Set<String> permissions = new HashSet<>();
        for (String role: roles) {
            if (DUMMY_MAPPINGS.containsKey(role)) {
                permissions.addAll(DUMMY_MAPPINGS.get(role));
            }
        }
        return permissions;
    }
}
