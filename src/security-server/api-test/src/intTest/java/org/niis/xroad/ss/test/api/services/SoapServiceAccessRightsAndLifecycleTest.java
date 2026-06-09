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
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceTypeDto;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.AccessRightsAdminClient;
import org.niis.xroad.ss.test.api.admin.ClientsAdminClient;
import org.niis.xroad.ss.test.api.admin.ServiceDescriptionsAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * WSDL/SOAP service access-rights and lifecycle scenarios migrated from the legacy Cucumber system-test suite.
 *
 * <p>Each test seeds its own namespaced subsystem so tests run independently in any order,
 * warm or cold, without interfering with each other.
 */
@DisplayName("SOAP/WSDL service access rights and lifecycle management")
@SuppressWarnings("checkstyle:magicnumber")
class SoapServiceAccessRightsAndLifecycleTest extends SsApiTest {

    private static final String WSDL1 = "http://mock-server:1080/test-services/testservice1.wsdl";
    private static final String WSDL3 = "http://mock-server:1080/test-services/testservice3.wsdl";

    private static final String SUBJECT_TEST_SAVED = "DEV:COM:1234:TestSaved";
    private static final String SUBJECT_TEST_CONSUMER = "DEV:COM:1234:test-consumer";
    private static final String SUBJECT_TEST_SERVICE = "DEV:COM:1234:TestService";

    // MIGRATED-FROM: 0560-ss-client-soap-services.feature :: "Client service has access rights added to it"
    @Test
    @DisplayName("Access rights added to a WSDL service operation are persisted")
    void accessRightsAddedToWsdlService(SsBaselineSeeder seeder) {
        var seed = seeder.seedClientWithWsdlService("wsdlAclAdd", WSDL1);
        var session = seeder.newSession();
        var accessRights = new AccessRightsAdminClient(session);
        var serviceId = seed.clientId() + ":testOp1";

        when("access rights for TestSaved and test-consumer are added to testOp1", () ->
                accessRights.addServiceClients(serviceId, SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER)
                        .statusCode(200));

        then("both subjects appear in the service clients list", () -> {
            var ids = accessRights.listServiceClientIds(serviceId);
            assertThat(ids).contains(SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER);
        });
    }

    // MIGRATED-FROM: 0560-ss-client-soap-services.feature :: "Client service has one access rights removed"
    @Test
    @DisplayName("Removing one access right from a WSDL service operation leaves the remaining subject in place")
    void removingOneAccessRightFromWsdlService(SsBaselineSeeder seeder) {
        var seed = seeder.seedClientWithWsdlService("wsdlAclRem1", WSDL1);
        var session = seeder.newSession();
        var accessRights = new AccessRightsAdminClient(session);
        var serviceId = seed.clientId() + ":testOp1";

        given("TestSaved and test-consumer are granted access to testOp1", () ->
                accessRights.addServiceClients(serviceId, SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER)
                        .statusCode(200));

        when("TestSaved access right is removed", () ->
                accessRights.removeServiceClients(serviceId, SUBJECT_TEST_SAVED)
                        .statusCode(204));

        then("TestSaved is no longer in the service clients list", () ->
                accessRights.listServiceClients(serviceId)
                        .statusCode(200)
                        .body("id", not(hasItem(SUBJECT_TEST_SAVED))));

        and("test-consumer is still present", () ->
                accessRights.listServiceClients(serviceId)
                        .statusCode(200)
                        .body("id", hasItem(SUBJECT_TEST_CONSUMER)));
    }

    // MIGRATED-FROM: 0560-ss-client-soap-services.feature :: "Client service has all access rights removed"
    @Test
    @DisplayName("Removing all access rights from a WSDL service operation leaves the ACL empty")
    void removingAllAccessRightsFromWsdlService(SsBaselineSeeder seeder) {
        var seed = seeder.seedClientWithWsdlService("wsdlAclRemAll", WSDL1);
        var session = seeder.newSession();
        var accessRights = new AccessRightsAdminClient(session);
        var serviceId = seed.clientId() + ":testOp1";

        given("three access rights are added to testOp1", () ->
                accessRights.addServiceClients(serviceId, SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER, SUBJECT_TEST_SERVICE)
                        .statusCode(200));

        when("all access rights are removed", () ->
                accessRights.removeServiceClients(serviceId, SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER, SUBJECT_TEST_SERVICE)
                        .statusCode(204));

        then("the service client list no longer contains any of the removed subjects", () -> {
            var ids = accessRights.listServiceClientIds(serviceId);
            assertThat(ids).doesNotContain(SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER, SUBJECT_TEST_SERVICE);
        });

        when("TestSaved and TestService are re-granted access via the service-perspective grant", () ->
                accessRights.addServiceClients(serviceId, SUBJECT_TEST_SAVED, SUBJECT_TEST_SERVICE)
                        .statusCode(200));

        then("both re-added subjects are present in the service clients list", () -> {
            var ids = accessRights.listServiceClientIds(serviceId);
            assertThat(ids).contains(SUBJECT_TEST_SAVED, SUBJECT_TEST_SERVICE);
        });
    }

    // MIGRATED-FROM: 0560-ss-client-soap-services.feature :: "Newly added services are enabled and one of them disabled"
    @Test
    @DisplayName("Enabling both WSDL service descriptions then disabling one persists state correctly")
    void wsdlServicesEnabledAndOneDisabled(SsBaselineSeeder seeder) {
        var seed = seeder.seedClientWithWsdlService("wsdlToggle", WSDL1);
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var serviceDescriptions = new ServiceDescriptionsAdminClient(session);
        var clientId = seed.clientId();
        var sd1Id = seed.serviceDescriptionId();

        given("a second WSDL service (testservice3) is added", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto(WSDL3, ServiceTypeDto.WSDL))
                        .statusCode(201));

        var sd3Id = when("the service description ID for testservice3 is retrieved", () ->
                clients.listServiceDescriptions(clientId).stream()
                        .filter(sd -> WSDL3.equals(sd.url()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("testservice3 service description not found"))
                        .id());

        and("testservice1 is enabled", () ->
                serviceDescriptions.enableServiceDescription(sd1Id)
                        .statusCode(200));

        and("testservice3 is enabled", () ->
                serviceDescriptions.enableServiceDescription(sd3Id)
                        .statusCode(200));

        then("testservice1 service description shows disabled=false", () ->
                session.given()
                        .get("/service-descriptions/{id}", sd1Id)
                        .then()
                        .statusCode(200)
                        .body("disabled", equalTo(false)));

        when("testservice1 is disabled with notice 'just disabled.'", () ->
                serviceDescriptions.disableServiceDescription(sd1Id, "just disabled.")
                        .statusCode(200));

        then("testservice1 service description shows disabled=true with the expected notice", () ->
                session.given()
                        .get("/service-descriptions/{id}", sd1Id)
                        .then()
                        .statusCode(200)
                        .body("disabled", equalTo(true))
                        .body("disabled_notice", equalTo("just disabled.")));

        and("testservice3 remains enabled", () ->
                session.given()
                        .get("/service-descriptions/{id}", sd3Id)
                        .then()
                        .statusCode(200)
                        .body("disabled", equalTo(false)));
    }

    // MIGRATED-FROM: 0560-ss-client-soap-services.feature :: "Newly added service is deleted"
    @Test
    @DisplayName("Deleting a WSDL service description removes it from the client list")
    void newlyAddedWsdlServiceDeleted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var serviceDescriptions = new ServiceDescriptionsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "wsdlDel");

        given("testservice3 WSDL is added", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto(WSDL3, ServiceTypeDto.WSDL))
                        .statusCode(201));

        var sd3Id = when("the service description ID for testservice3 is retrieved", () ->
                clients.listServiceDescriptions(clientId).stream()
                        .filter(sd -> WSDL3.equals(sd.url()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("testservice3 service description not found"))
                        .id());

        and("the service description is deleted", () ->
                serviceDescriptions.deleteServiceDescription(sd3Id)
                        .statusCode(204));

        then("testservice3 is no longer in the client's service description list", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .map(ClientsAdminClient.ServiceDescriptionView::url)
                    .toList();
            assertThat(urls).doesNotContain(WSDL3);
        });
    }

    // MIGRATED-FROM: 0560-ss-client-soap-services.feature :: "Service is refreshed"
    @Test
    @DisplayName("Refreshing a WSDL service description re-fetches the WSDL and returns 200")
    void wsdlServiceRefreshed(SsBaselineSeeder seeder) {
        var seed = seeder.seedClientWithWsdlService("wsdlRefresh", WSDL1);
        var session = seeder.newSession();
        var serviceDescriptions = new ServiceDescriptionsAdminClient(session);
        var sdId = seed.serviceDescriptionId();

        when("the WSDL service description is refreshed", () ->
                serviceDescriptions.refreshServiceDescription(sdId)
                        .statusCode(200));

        then("the service description is still present and accessible", () ->
                session.given()
                        .get("/service-descriptions/{id}", sdId)
                        .then()
                        .statusCode(200)
                        .body("url", equalTo(WSDL1)));
    }
}
