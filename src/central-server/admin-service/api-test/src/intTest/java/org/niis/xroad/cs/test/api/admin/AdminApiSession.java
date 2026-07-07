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

import io.restassured.specification.RequestSpecification;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

/**
 * Pre-wired RestAssured session for the Central Server admin API. Authenticates via API key header
 * ({@code X-ROAD-APIKEY TOKEN=<uuid>}) using the system-administrator token pre-seeded in the test
 * database via Liquibase.
 *
 * <p>Use {@link #given()} for per-test calls (Allure attachments enabled). Use {@link #givenSilent()}
 * for baseline/infrastructure calls that must not appear on any test report.
 */
public class AdminApiSession {

    static final String SYSTEM_ADMINISTRATOR_TOKEN = "d56e1ca7-4134-4ed4-8030-5f330bdb602a";
    public static final String REGISTRATION_OFFICER_TOKEN = "4a5842e5-4ede-49f1-ab32-1b6be33d81c3";
    public static final String SECURITY_OFFICER_TOKEN = "3964334d-1f65-4629-a4a4-73c62ade0c9c";
    public static final String SYSTEM_ADMINISTRATOR_ONLY_TOKEN = "7d56e1ca-7413-4ed4-8030-5f330bdb0002";
    private static final String API_KEY_HEADER = "Authorization";
    private static final String API_KEY_PREFIX = "X-ROAD-APIKEY TOKEN=";

    private final String baseUrl;
    private final String apiKeyToken;

    public AdminApiSession(String baseUrl) {
        this(baseUrl, SYSTEM_ADMINISTRATOR_TOKEN);
    }

    public AdminApiSession(String baseUrl, String apiKeyToken) {
        this.baseUrl = baseUrl;
        this.apiKeyToken = apiKeyToken;
    }

    /**
     * Returns a RestAssured request specification with Allure attachments, API key auth, and CS admin base URI.
     */
    public RequestSpecification given() {
        return withApiKey(RestAssuredFactory.given());
    }

    /**
     * Returns a RestAssured request specification without Allure attachments. For baseline/infrastructure calls.
     */
    public RequestSpecification givenSilent() {
        return withApiKey(RestAssuredFactory.givenSilent());
    }

    private RequestSpecification withApiKey(RequestSpecification spec) {
        return spec
                .header(API_KEY_HEADER, API_KEY_PREFIX + apiKeyToken)
                .baseUri(baseUrl)
                .basePath("/api/v1");
    }
}
