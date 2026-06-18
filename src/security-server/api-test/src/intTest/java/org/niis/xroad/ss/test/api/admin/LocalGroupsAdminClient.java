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
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupDescriptionDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MembersDto;

import java.util.List;
import java.util.Map;

/**
 * RestAssured client for the {@code /clients/{id}/local-groups} and {@code /local-groups/{group_id}} admin API resources.
 *
 * <p>Uses raw JSON extraction to avoid Jackson date-time deserialization issues
 * (the test classpath carries no JSR-310 module).
 */
@SuppressWarnings("checkstyle:magicnumber")
public class LocalGroupsAdminClient {

    private final AdminApiSession session;

    public LocalGroupsAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Lists all local groups for the given client.
     */
    public List<LocalGroupView> listLocalGroups(String clientId) {
        List<Map<String, Object>> raw = session.given()
                .get("/clients/{id}/local-groups", clientId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
        return raw.stream().map(LocalGroupView::from).toList();
    }

    /**
     * Adds a new local group to the given client and returns the response for assertion or extraction.
     */
    public ValidatableResponse addLocalGroup(String clientId, LocalGroupAddDto request) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/clients/{id}/local-groups", clientId)
                .then();
    }

    /**
     * Adds a new local group to the given client and returns its generated ID.
     */
    public String createLocalGroup(String clientId, LocalGroupAddDto request) {
        return addLocalGroup(clientId, request)
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("id");
    }

    /**
     * Gets local group details by group ID, including the current member list.
     */
    public LocalGroupView getLocalGroup(String groupId) {
        Map<String, Object> raw = session.given()
                .get("/local-groups/{group_id}", groupId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
        return LocalGroupView.from(raw);
    }

    /**
     * Updates the description of a local group and returns the response for assertion or extraction.
     */
    public ValidatableResponse updateLocalGroup(String groupId, LocalGroupDescriptionDto request) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .patch("/local-groups/{group_id}", groupId)
                .then();
    }

    /**
     * Deletes a local group by ID and returns the response for assertion or extraction.
     */
    public ValidatableResponse deleteLocalGroup(String groupId) {
        return session.given()
                .delete("/local-groups/{group_id}", groupId)
                .then();
    }

    /**
     * Adds members to a local group and returns the response for assertion or extraction.
     */
    public ValidatableResponse addMembers(String groupId, MembersDto request) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/local-groups/{group_id}/members", groupId)
                .then();
    }

    /**
     * Removes members from a local group and returns the response for assertion or extraction.
     */
    public ValidatableResponse removeMembers(String groupId, MembersDto request) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/local-groups/{group_id}/members/delete", groupId)
                .then();
    }

    /**
     * Lightweight, read-only view of a local group from the admin API.
     * Extracted from raw JSON to avoid Jackson date-time module requirements in the test tier.
     *
     * @param id          the local group numeric identifier
     * @param code        the group code
     * @param description the group description
     * @param memberIds   the set of member X-Road identifiers currently in the group
     */
    public record LocalGroupView(String id, String code, String description, List<String> memberIds) {

        @SuppressWarnings("unchecked")
        static LocalGroupView from(Map<String, Object> raw) {
            var members = (List<Map<String, Object>>) raw.getOrDefault("members", List.of());
            var ids = members.stream()
                    .map(m -> (String) m.get("id"))
                    .toList();
            return new LocalGroupView(
                    (String) raw.get("id"),
                    (String) raw.get("code"),
                    (String) raw.get("description"),
                    ids
            );
        }
    }
}
