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
package org.niis.xroad.cs.test.api.admin;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.niis.xroad.cs.openapi.model.SubsystemAddDto;

/**
 * RestAssured client for the {@code /subsystems} admin API resource.
 */
public class SubsystemsAdminClient {

    private final AdminApiSession session;

    public SubsystemsAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Adds a new subsystem and returns the response for assertion or extraction.
     */
    public ValidatableResponse addSubsystem(SubsystemAddDto request) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/subsystems")
                .then();
    }

    /**
     * Deletes the subsystem by its identifier and returns the response.
     */
    public ValidatableResponse deleteSubsystem(String subsystemId) {
        return session.given()
                .delete("/subsystems/{subsystem_id}", subsystemId)
                .then();
    }

    /**
     * Unregisters the subsystem from the given security server and returns the response.
     */
    public ValidatableResponse unregisterSubsystem(String subsystemId, String serverId) {
        return session.given()
                .delete("/subsystems/{subsystem_id}/servers/{server_id}", subsystemId, serverId)
                .then();
    }
}
