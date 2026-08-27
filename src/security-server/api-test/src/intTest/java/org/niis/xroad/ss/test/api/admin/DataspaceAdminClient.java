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
package org.niis.xroad.ss.test.api.admin;

import io.restassured.response.ValidatableResponse;

/**
 * RestAssured client for the SS-specific {@code /dataspace} admin API resource (security-server admin-service,
 * {@code DataspaceApiController}) — provisioning status and DS TLS certificate ACME enrollment status.
 */
public class DataspaceAdminClient {

    private final AdminApiSession session;

    public DataspaceAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Gets the current DS TLS certificate ACME enrollment status: enrollment method (NONE/MANUAL/ACME),
     * next scheduled renewal time, and last enrollment/renewal error.
     */
    public ValidatableResponse getTlsCertificateEnrollmentStatus() {
        return session.given()
                .get("/dataspace/tls-certificate/enrollment-status")
                .then();
    }
}
