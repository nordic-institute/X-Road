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
package org.niis.xroad.cs.test.api.issuerservice;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.IssuerServiceAdminClient;

import static org.hamcrest.Matchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for the Central Server Issuer Service host.
 */
@SuppressWarnings("checkstyle:magicnumber")
class IssuerServiceApiTest extends CsApiTest {

    @Test
    @ResourceLock("issuer-service-host")
    void settingTheHostIsReflectedByTheNextGet(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newSecurityOfficerSession());

        // The seeder sets a host before any test runs. Setting that host again keeps the seeded host for
        // the tests that follow; the destructive phase covers changing it to a different host.
        var stateBeforeSet = given("the current issuer service state", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .extract().jsonPath());

        var host = stateBeforeSet.getString("host");

        if (!stateBeforeSet.getBoolean("bound")) {
            when("the current host is set again", () ->
                    issuerService.setIssuerServiceHost(host)
                            .statusCode(200)
                            .body("host", equalTo(host))
                            .body("bound", equalTo(false)));

            then("a following get returns the same host", () ->
                    issuerService.getIssuerServiceState()
                            .statusCode(200)
                            .body("host", equalTo(host))
                            .body("bound", equalTo(false)));
        } else {
            then("a following get still returns the already-bound host", () ->
                    issuerService.getIssuerServiceState()
                            .statusCode(200)
                            .body("host", equalTo(host)));
        }
    }

    @Test
    @ResourceLock(value = "issuer-service-host", mode = ResourceAccessMode.READ)
    void settingTheHostIsForbiddenWithoutThePermission(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newManagementServiceOnlySession());

        then("setting the host is forbidden", () ->
                issuerService.setIssuerServiceHost("cs.example.org")
                        .statusCode(403));
    }

    @Test
    @ResourceLock(value = "issuer-service-host", mode = ResourceAccessMode.READ_WRITE)
    void settingAnInvalidHostIsRejected(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newSecurityOfficerSession());

        var hostBeforeRejection = given("the current issuer service host", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .extract().jsonPath().getString("host"));

        when("an invalid host is set", () ->
                issuerService.setIssuerServiceHost("not a valid host")
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_dns_name")));

        then("the stored host is unchanged", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .body("host", equalTo(hostBeforeRejection)));
    }

    @Test
    @ResourceLock(value = "issuer-service-host", mode = ResourceAccessMode.READ_WRITE)
    void settingLocalhostIsRejected(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newSecurityOfficerSession());

        var hostBeforeRejection = given("the current issuer service host", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .extract().jsonPath().getString("host"));

        when("localhost is set", () ->
                issuerService.setIssuerServiceHost("localhost")
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_dns_name")));

        then("the stored host is unchanged", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .body("host", equalTo(hostBeforeRejection)));
    }

    /**
     * Runs in the destructive phase: binding is permanent, and there is no API to undo it. The issuer
     * identity is bound here by inserting an {@code issuer_dids} row directly, because this stack has no
     * reachable Issuer Service for the provisioning worker to bind against. Idempotent across reruns on a
     * warm stack: if the issuer identity is already bound from an earlier run, the already-bound host is
     * used instead of setting a new one.
     */
    @Test
    @Tag("destructive")
    void aHostChangeIsRefusedOnceTheIssuerIdentityIsBound(CsBaselineSeeder seeder) {
        var issuerService = new IssuerServiceAdminClient(seeder.newSecurityOfficerSession());

        var stateBeforeBinding = given("the current issuer service state", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .extract().jsonPath());

        String boundHost = stateBeforeBinding.getString("host");
        if (!stateBeforeBinding.getBoolean("bound")) {
            boundHost = when("a host is set before binding", () ->
                    issuerService.setIssuerServiceHost("issuer.example.org")
                            .statusCode(200)
                            .body("bound", equalTo(false))
                            .extract().jsonPath().getString("host"));

            seeder.bindIssuerIdentity();
        }
        var host = boundHost;

        then("a following get reports the host as bound", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .body("host", equalTo(host))
                        .body("bound", equalTo(true)));

        when("a different host is set after binding", () ->
                issuerService.setIssuerServiceHost("other-" + host)
                        .statusCode(409)
                        .body("error.code", equalTo("issuer_service_already_bound"))
                        .body("error.metadata[0]", equalTo(host)));

        when("the same host is set again after binding", () ->
                issuerService.setIssuerServiceHost(host)
                        .statusCode(409)
                        .body("error.code", equalTo("issuer_service_already_bound")));

        then("the stored host is unchanged and still reported as bound", () ->
                issuerService.getIssuerServiceState()
                        .statusCode(200)
                        .body("host", equalTo(host))
                        .body("bound", equalTo(true)));
    }
}
