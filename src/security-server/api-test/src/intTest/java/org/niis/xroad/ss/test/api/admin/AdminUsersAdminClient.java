/*
 * The MIT License
 *
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
package org.niis.xroad.ss.test.api.admin;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RestAssured client for the admin-users management API.
 *
 * <p>The endpoint is served at {@code /api/v1/users} by {@code AdminUsersController} from
 * {@code common-admin-api}. It uses the standard form-login session (same as other admin API calls).
 * Database-based admin-user auth must be active on the stack (the {@code containerized} Spring profile
 * enables it — the api-test Docker image includes that profile).
 */
@SuppressWarnings("checkstyle:magicnumber")
public class AdminUsersAdminClient {

    private final AdminApiSession session;

    public AdminUsersAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Lists all admin users.
     */
    public ValidatableResponse listUsers() {
        return session.given()
                .get("/users")
                .then();
    }

    /**
     * Creates a new admin user with the given username, password, and roles.
     */
    public ValidatableResponse createUser(String username, String password, List<String> roles) {
        var body = Map.of("username", username, "password", password, "roles", roles);
        return session.given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/users")
                .then();
    }

    /**
     * Updates the role set for the given admin user.
     */
    public ValidatableResponse updateRoles(String username, List<String> roles) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(roles)
                .put("/users/{username}/roles", username)
                .then();
    }

    /**
     * Changes the password of the given admin user.
     * When called as a sysadmin changing another user, {@code oldPassword} may be null.
     * When called as the user changing their own password, {@code oldPassword} is required.
     */
    public ValidatableResponse changePassword(String username, String oldPassword, String newPassword) {
        var body = new HashMap<String, String>();
        body.put("new_password", newPassword);
        if (oldPassword != null) {
            body.put("old_password", oldPassword);
        }
        return session.given()
                .contentType(ContentType.JSON)
                .body(body)
                .put("/users/{username}/password", username)
                .then();
    }

    /**
     * Deletes the given admin user.
     */
    public ValidatableResponse deleteUser(String username) {
        return session.given()
                .delete("/users/{username}", username)
                .then();
    }

    /**
     * Returns the username list from GET /users as a list of raw maps for safe extraction
     * without Jackson date-time dependencies.
     */
    public List<Map<String, Object>> listUsersRaw() {
        return listUsers()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
    }

    /**
     * Returns {@code true} if a user with the given username exists in the GET /users list.
     */
    public boolean userExists(String username) {
        return listUsersRaw().stream()
                .anyMatch(u -> username.equals(u.get("username")));
    }

    /**
     * Returns the roles list for the given username from GET /users, or an empty list if not found.
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesForUser(String username) {
        return listUsersRaw().stream()
                .filter(u -> username.equals(u.get("username")))
                .map(u -> (List<String>) u.get("roles"))
                .findFirst()
                .orElse(List.of());
    }
}
