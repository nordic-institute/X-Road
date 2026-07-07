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
import org.niis.xroad.cs.openapi.model.MemberClassDescriptionDto;
import org.niis.xroad.cs.openapi.model.MemberClassDto;

import java.util.Arrays;
import java.util.List;

/**
 * RestAssured client for the {@code /member-classes} admin API resource.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class MemberClassesAdminClient {

    private final AdminApiSession session;

    public MemberClassesAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Lists all member classes on the Central Server.
     */
    public ValidatableResponse listMemberClasses() {
        return session.given()
                .get("/member-classes")
                .then();
    }

    /**
     * Lists all member class codes on the Central Server. Returns an empty list when none exist.
     */
    public List<String> listMemberClassCodes() {
        var classes = session.givenSilent()
                .get("/member-classes")
                .then()
                .statusCode(200)
                .extract()
                .as(MemberClassDto[].class);
        return Arrays.stream(classes)
                .map(MemberClassDto::getCode)
                .toList();
    }

    /**
     * Adds a new member class and returns the response for assertion or extraction.
     */
    public ValidatableResponse addMemberClass(MemberClassDto request) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/member-classes")
                .then();
    }

    /**
     * Deletes the member class identified by {@code code} and returns the response.
     */
    public ValidatableResponse deleteMemberClass(String code) {
        return session.given()
                .delete("/member-classes/{code}", code)
                .then();
    }

    /**
     * Updates the description of the member class identified by {@code code}.
     */
    public ValidatableResponse updateMemberClass(String code, MemberClassDescriptionDto request) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .patch("/member-classes/{code}", code)
                .then();
    }
}
