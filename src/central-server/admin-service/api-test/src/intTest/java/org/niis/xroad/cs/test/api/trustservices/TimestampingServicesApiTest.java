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
package org.niis.xroad.cs.test.api.trustservices;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.TimestampingServicesAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@SuppressWarnings("checkstyle:magicnumber")
class TimestampingServicesApiTest extends CsApiTest {

    @Test
    void viewListOfTimestampingServices(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var tsa = new TimestampingServicesAdminClient(session);
        var url = "https://tsa01-" + UUID.randomUUID();

        var id = Step.when("timestamping service is added", () ->
                tsa.addTimestampingService(url, seeder.generateCertForServer("tsa01"))
                        .statusCode(201)
                        .body("id", notNullValue())
                        .body("url", equalTo(url))
                        .extract().jsonPath().getInt("id"));

        Step.then("list contains the added service", () ->
                tsa.listTimestampingServices()
                        .statusCode(200)
                        .body("id", hasItem(id))
                        .body("url", hasItem(url)));
    }

    @Test
    void viewTimestampingServiceById(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var tsa = new TimestampingServicesAdminClient(session);
        var url = "https://tsa02-" + UUID.randomUUID();

        var id = Step.when("timestamping service is added", () ->
                tsa.addTimestampingService(url, seeder.generateCertForServer("tsa02"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.then("GET by id returns the correct URL", () ->
                tsa.getTimestampingService(id)
                        .statusCode(200)
                        .body("url", equalTo(url)));
    }

    @Test
    void addingTimestampingServiceWithInvalidUrlIsNotAllowed(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var tsa = new TimestampingServicesAdminClient(session);

        Step.when("timestamping service with invalid URL is added", () ->
                tsa.addTimestampingService("not valid url", seeder.generateCertForServer("tsa03"))
                        .statusCode(400));
    }

    @Test
    void deletingTimestampingService(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var tsa = new TimestampingServicesAdminClient(session);
        var url = "https://tsa04-" + UUID.randomUUID();

        var id = Step.given("timestamping service added", () ->
                tsa.addTimestampingService(url, seeder.generateCertForServer("tsa04"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.and("service is in list", () ->
                tsa.listTimestampingServices()
                        .statusCode(200)
                        .body("id", hasItem(id)));

        Step.when("service is deleted", () ->
                tsa.deleteTimestampingService(id).statusCode(204));

        Step.then("service is no longer in list", () ->
                tsa.listTimestampingServices()
                        .statusCode(200)
                        .body("id", org.hamcrest.Matchers.not(hasItem(id))));
    }

    @Test
    void deletingNotExistingTimestampingService(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var tsa = new TimestampingServicesAdminClient(session);

        Step.when("DELETE with nonexistent id returns 404", () ->
                tsa.deleteTimestampingService(Integer.MIN_VALUE)
                        .statusCode(404));
    }

    @Test
    void modifyingTimestampServiceUrl(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var tsa = new TimestampingServicesAdminClient(session);
        var originalUrl = "https://tsa06-" + UUID.randomUUID();
        var updatedUrl = "https://tsa06-updated-" + UUID.randomUUID();

        var id = Step.given("timestamping service added", () ->
                tsa.addTimestampingService(originalUrl, seeder.generateCertForServer("tsa06"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.and("service is in list", () ->
                tsa.listTimestampingServices()
                        .statusCode(200)
                        .body("id", hasItem(id)));

        Step.when("URL is updated (no cert change)", () ->
                tsa.updateTimestampingService(id, updatedUrl, null)
                        .statusCode(200)
                        .body("url", equalTo(updatedUrl))
                        .body("certificate", notNullValue()));
    }

    @Test
    void modifyingTimestampServiceUrlAndCertificate(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var tsa = new TimestampingServicesAdminClient(session);
        var originalUrl = "https://tsa07-" + UUID.randomUUID();
        var updatedUrl = "https://tsa07-updated-" + UUID.randomUUID();

        var id = Step.given("timestamping service added", () ->
                tsa.addTimestampingService(originalUrl, seeder.generateCertForServer("tsa07"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.and("service is in list", () ->
                tsa.listTimestampingServices()
                        .statusCode(200)
                        .body("id", hasItem(id)));

        Step.when("URL and certificate are updated", () ->
                tsa.updateTimestampingService(id, updatedUrl, seeder.generateCertForServer("tsa07-new"))
                        .statusCode(200)
                        .body("url", equalTo(updatedUrl))
                        .body("certificate", notNullValue()));
    }
}
