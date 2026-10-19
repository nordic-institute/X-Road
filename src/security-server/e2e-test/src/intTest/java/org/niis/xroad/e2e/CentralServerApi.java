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
package org.niis.xroad.e2e;

import lombok.experimental.UtilityClass;
import org.niis.xroad.e2e.AdminApi.AdminSession;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.e2e.AdminApi.authed;

/**
 * Central Server admin REST API access: approval of pending client registration requests.
 */
@UtilityClass
class CentralServerApi {

    static final String CS_ENV = "aux";

    private static final int HTTP_OK = 200;
    private static final int HTTP_CONFLICT = 409;
    private static final String CLIENT_REGISTRATION_REQUEST = "CLIENT_REGISTRATION_REQUEST";

    /** The mapped {@code https://host:port} of the Central Server admin UI. */
    static String baseUrl(E2eEnvironment env) {
        return AdminApi.adminBaseUrl(env, CS_ENV);
    }

    /**
     * Approves the WAITING client registration requests of {@code clientIds}, matched by request type and
     * the client's encoded id, so a warm shared environment's unrelated pending requests are never touched.
     * No match means either the environment auto-approved the request or it has not surfaced yet; the
     * caller's registration-status poll handles both, so this returns quietly rather than asserting.
     */
    static void approvePendingClientRegistrations(String csBaseUrl, AdminSession cs, Collection<String> clientIds) {
        var response = authed(cs).get(csBaseUrl + "/api/v1/management-requests?sort=id&desc=true&status=WAITING");
        assertThat(response.getStatusCode()).as("list WAITING management requests").isEqualTo(HTTP_OK);

        List<Map<String, Object>> items = response.jsonPath().getList("items");
        if (items == null) {
            return;
        }
        for (var item : items) {
            var encodedId = encodedClientId(item);
            if (CLIENT_REGISTRATION_REQUEST.equals(item.get("type")) && encodedId != null && clientIds.contains(encodedId)) {
                approve(csBaseUrl, cs, item.get("id"));
            }
        }
    }

    private static String encodedClientId(Map<String, Object> request) {
        return request.get("client_id") instanceof Map<?, ?> clientId ? String.valueOf(clientId.get("encoded_id")) : null;
    }

    private static void approve(String csBaseUrl, AdminSession cs, Object requestId) {
        var approval = authed(cs).post(csBaseUrl + "/api/v1/management-requests/" + requestId + "/approval");
        assertThat(approval.getStatusCode())
                .as("approve client registration request %s (409 if a prior tick already approved it)", requestId)
                .isIn(HTTP_OK, HTTP_CONFLICT);
    }
}
