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

import io.restassured.filter.session.SessionFilter;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

/**
 * Manages a session with the Security Server admin API. Authenticates via form login,
 * captures the XSRF token from the response cookie, and provides a pre-wired
 * {@link RequestSpecification} that carries the session cookie and XSRF header for every call.
 *
 * <p>The default constructor attaches Allure request/response attachments (per-test sessions).
 * Use {@link #silent(String)} to obtain a session that suppresses all Allure output — suitable for
 * baseline/infrastructure calls that must not appear on any test report.
 */
@Slf4j
@SuppressWarnings("checkstyle:magicnumber")
public class AdminApiSession {

    private static final String ADMIN_USERNAME = "xrd";
    private static final String ADMIN_PASSWORD = "secret123!";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";

    private final String baseUrl;
    private final SessionFilter sessionFilter;
    private final boolean reporting;
    private String xsrfToken;

    public AdminApiSession(String baseUrl) {
        this(baseUrl, ADMIN_USERNAME, ADMIN_PASSWORD, true);
    }

    /**
     * Creates a session authenticated as a specific user.
     * Useful for tests that need to act as a non-default admin user.
     */
    public AdminApiSession(String baseUrl, String username, String password) {
        this(baseUrl, username, password, true);
    }

    /**
     * Creates a session that does not attach HTTP calls to the Allure report.
     * Use for infrastructure-level sessions (baseline seeding) that must not appear on any test report.
     */
    public static AdminApiSession silent(String baseUrl) {
        return new AdminApiSession(baseUrl, ADMIN_USERNAME, ADMIN_PASSWORD, false);
    }

    private AdminApiSession(String baseUrl, String username, String password, boolean reporting) {
        this.baseUrl = baseUrl;
        this.sessionFilter = new SessionFilter();
        this.reporting = reporting;
        login(username, password);
    }

    /**
     * Returns a RestAssured request specification pre-wired with:
     * - relaxed HTTPS validation (self-signed test certificates)
     * - Allure report attachment (when this session was created with reporting enabled)
     * - session cookie (shared across calls via {@link SessionFilter})
     * - XSRF token header and cookie (both required by the server-side CSRF validator)
     * - admin API base URI
     */
    public RequestSpecification given() {
        return spec()
                .filter(sessionFilter)
                .header(XSRF_HEADER, xsrfToken)
                .cookie(XSRF_COOKIE, xsrfToken)
                .baseUri(baseUrl)
                .basePath("/api/v1");
    }

    private RequestSpecification spec() {
        return reporting ? RestAssuredFactory.given() : RestAssuredFactory.givenSilent();
    }

    private void login(String username, String password) {
        log.debug("Logging in to admin API at {} as {}", baseUrl, username);
        Response loginResponse = spec()
                .filter(sessionFilter)
                .formParam("username", username)
                .formParam("password", password)
                .post(baseUrl + "/login");

        loginResponse.then().statusCode(200);
        xsrfToken = loginResponse.cookie(XSRF_COOKIE);
        log.debug("Login successful, XSRF token acquired");
    }
}
