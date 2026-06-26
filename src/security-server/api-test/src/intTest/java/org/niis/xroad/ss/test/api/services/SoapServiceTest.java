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
package org.niis.xroad.ss.test.api.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionUpdateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceUpdateDto;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.ClientsAdminClient;
import org.niis.xroad.ss.test.api.admin.ServiceDescriptionsAdminClient;
import org.niis.xroad.ss.test.api.admin.ServicesAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * WSDL/SOAP service scenarios migrated from the legacy Cucumber system-test suite.
 *
 * <p>Each test seeds its own namespaced subsystem so tests run independently in any order,
 * warm or cold, without interfering with each other.
 */
@DisplayName("SOAP/WSDL service management")
@SuppressWarnings("checkstyle:magicnumber")
class SoapServiceTest extends SsApiTest {

    private static final String WSDL1 = "http://mock-server:1080/test-services/testservice1.wsdl";
    private static final String WSDL2 = "http://mock-server:1080/test-services/testservice2.wsdl";
    private static final String WSDL3 = "http://mock-server:1080/test-services/testservice3.wsdl";
    private static final String WSDL_MISSING = "http://mock-server:1080/test-services/missing.wsdl";
    private static final String WSDL_INVALID = "https://www.niis.org/";

    @Test
    @DisplayName("WSDL services added to a client are listed in the service description list")
    void wsdlServiceConfiguredAndListedCorrectly(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "wsdlCfg");

        when("WSDL service testservice1 is added", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto(WSDL1, ServiceTypeDto.WSDL))
                        .statusCode(201));

        and("WSDL service testservice3 is added", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto(WSDL3, ServiceTypeDto.WSDL))
                        .statusCode(201));

        then("both WSDL service descriptions appear in the client's service list", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .map(ClientsAdminClient.ServiceDescriptionView::url)
                    .toList();
            assertThat(urls).contains(WSDL1, WSDL3);
        });
    }

    @Test
    @DisplayName("Adding a WSDL service description with a URL already in use is rejected with wsdl_exists")
    void duplicateWsdlIsRejected(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "wsdlDup");

        given("testservice1 WSDL is already added", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto(WSDL1, ServiceTypeDto.WSDL))
                        .statusCode(201));

        then("adding testservice1 WSDL again is rejected with wsdl_exists", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto(WSDL1, ServiceTypeDto.WSDL))
                        .statusCode(409)
                        .body("error.code", equalTo("wsdl_exists")));
    }

    @Test
    @DisplayName("Adding a WSDL service description pointing to a non-WSDL URL is rejected with invalid_wsdl")
    void invalidWsdlIsRejected(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "wsdlInv");

        then("adding a WSDL service description pointing to a non-WSDL URL is rejected with invalid_wsdl", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto(WSDL_INVALID, ServiceTypeDto.WSDL))
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_wsdl")));
    }

    @Test
    @DisplayName("Adding a WSDL service description with a URL that returns 404 is rejected with wsdl_download_failed")
    void nonRespondingWsdlUrlIsRejected(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "wsdlDown");

        then("adding a WSDL service description pointing to a missing URL is rejected with wsdl_download_failed", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto(WSDL_MISSING, ServiceTypeDto.WSDL))
                        .statusCode(400)
                        .body("error.code", equalTo("wsdl_download_failed")));
    }

    @Test
    @DisplayName("Updating a WSDL service description URL swaps the old URL for the new one in the list")
    void wsdlUrlCanBeUpdated(SsBaselineSeeder seeder) {
        var seed = seeder.seedClientWithWsdlService("wsdlUpd", WSDL1);
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var serviceDescriptions = new ServiceDescriptionsAdminClient(session);
        var clientId = seed.clientId();
        var sdId = seed.serviceDescriptionId();

        when("the WSDL URL is updated from testservice1 to testservice2", () ->
                serviceDescriptions.updateServiceDescription(sdId,
                                new ServiceDescriptionUpdateDto(WSDL2, ServiceTypeDto.WSDL)
                                        .ignoreWarnings(true))
                        .statusCode(200));

        then("testservice1 URL is no longer in the list", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .map(ClientsAdminClient.ServiceDescriptionView::url)
                    .toList();
            assertThat(urls).doesNotContain(WSDL1);
        });

        and("testservice2 URL is now in the list", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .map(ClientsAdminClient.ServiceDescriptionView::url)
                    .toList();
            assertThat(urls).contains(WSDL2);
        });

        and("reverting the WSDL URL back to testservice1 succeeds", () ->
                serviceDescriptions.updateServiceDescription(sdId,
                                new ServiceDescriptionUpdateDto(WSDL1, ServiceTypeDto.WSDL)
                                        .ignoreWarnings(true))
                        .statusCode(200));

        then("testservice2 URL is gone and testservice1 URL is back", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .map(ClientsAdminClient.ServiceDescriptionView::url)
                    .toList();
            assertThat(urls).doesNotContain(WSDL2);
            assertThat(urls).contains(WSDL1);
        });
    }

    @Test
    @DisplayName("Editing a WSDL operation with apply-all propagates the update to all sibling operations")
    void wsdlOperationEditedWithApplyAllPropagates(SsBaselineSeeder seeder) {
        var seed = seeder.seedClientWithWsdlService("wsdlEdit", WSDL1);
        var session = seeder.newSession();
        var services = new ServicesAdminClient(session);
        var clientId = seed.clientId();
        var testOp1Id = clientId + ":testOp1";
        var testOpAId = clientId + ":testOpA";

        when("testOp1 is updated with url/timeout/TLS applied to all operations", () ->
                services.updateService(testOp1Id,
                                new ServiceUpdateDto("https://www.niis.org/nosuch-updated/", 45, false)
                                        .urlAll(true)
                                        .timeoutAll(true)
                                        .sslAuthAll(true)
                                        .ignoreWarnings(true))
                        .statusCode(200));

        then("testOp1 reflects the updated url, timeout and ssl_auth", () ->
                services.getService(testOp1Id)
                        .statusCode(200)
                        .body("url", equalTo("https://www.niis.org/nosuch-updated/"))
                        .body("timeout", equalTo(45))
                        .body("ssl_auth", equalTo(false)));

        and("testOpA was also updated because apply-all was set", () ->
                services.getService(testOpAId)
                        .statusCode(200)
                        .body("url", equalTo("https://www.niis.org/nosuch-updated/"))
                        .body("timeout", equalTo(45))
                        .body("ssl_auth", equalTo(false)));

        when("testOpA is updated without apply-all", () ->
                services.updateService(testOpAId,
                                new ServiceUpdateDto("https://www.niis.org/second-update/", 33, true)
                                        .urlAll(false)
                                        .timeoutAll(false)
                                        .sslAuthAll(false)
                                        .ignoreWarnings(true))
                        .statusCode(200));

        then("testOpA reflects the second update", () ->
                services.getService(testOpAId)
                        .statusCode(200)
                        .body("url", equalTo("https://www.niis.org/second-update/"))
                        .body("timeout", equalTo(33))
                        .body("ssl_auth", equalTo(true)));

        and("testOp1 is unchanged from the first update", () ->
                services.getService(testOp1Id)
                        .statusCode(200)
                        .body("url", equalTo("https://www.niis.org/nosuch-updated/"))
                        .body("timeout", equalTo(45))
                        .body("ssl_auth", equalTo(false)));
    }
}
