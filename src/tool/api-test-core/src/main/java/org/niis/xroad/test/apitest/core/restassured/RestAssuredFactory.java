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
package org.niis.xroad.test.apitest.core.restassured;

import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.mapper.ObjectMapper;
import io.restassured.mapper.ObjectMapperDeserializationContext;
import io.restassured.mapper.ObjectMapperSerializationContext;
import io.restassured.specification.RequestSpecification;
import org.niis.xroad.test.apitest.core.report.NamedHttpAttachmentFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Produces RestAssured request specifications pre-wired for the API tier.
 *
 * <p>{@link #given()} — relaxed TLS validation for self-signed test certificates and the
 * {@link NamedHttpAttachmentFilter} so every call attaches its request, response, and headers to
 * the Allure report with names in the form {@code Request: <METHOD> <path>} /
 * {@code Response: <METHOD> <path>}.
 *
 * <p>{@link #givenSilent()} — relaxed TLS validation only; no Allure attachment. Use for
 * infrastructure-level calls (baseline seeding, bootstrap) that must not appear on any test report.
 */
public final class RestAssuredFactory {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private static final ObjectMapper JACKSON_MAPPER = new ObjectMapper() {
        @Override
        public Object deserialize(ObjectMapperDeserializationContext context) {
            return JSON_MAPPER.readValue(context.getDataToDeserialize().asString(),
                    JSON_MAPPER.constructType(context.getType()));
        }

        @Override
        public Object serialize(ObjectMapperSerializationContext context) {
            return JSON_MAPPER.writeValueAsString(context.getObjectToSerialize());
        }
    };

    private static final RestAssuredConfig CONFIG = RestAssuredConfig.config()
            .objectMapperConfig(ObjectMapperConfig.objectMapperConfig().defaultObjectMapper(JACKSON_MAPPER));

    private RestAssuredFactory() {
    }

    /**
     * Returns a {@link RequestSpecification} with relaxed HTTPS validation and the Allure attachment
     * filter. Use for all per-test HTTP calls.
     */
    public static RequestSpecification given() {
        return RestAssured.given()
                .config(CONFIG)
                .relaxedHTTPSValidation()
                .filter(new NamedHttpAttachmentFilter());
    }

    /**
     * Returns a {@link RequestSpecification} with relaxed HTTPS validation and no Allure filter.
     * Use for baseline/infrastructure calls that must not attach to any test report.
     */
    public static RequestSpecification givenSilent() {
        return RestAssured.given()
                .config(CONFIG)
                .relaxedHTTPSValidation();
    }
}
