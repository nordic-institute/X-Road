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
import org.niis.xroad.cs.test.api.admin.DsTlsCertificationAuthoritiesAdminClient;
import org.niis.xroad.cs.test.api.admin.DsTlsIntermediateCasAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * API tests for the {@code /ds-tls-certification-authorities/{id}/intermediate-cas} and
 * {@code /ds-tls-intermediate-cas/{id}} admin resources (XRDDEV-3288). Fixtures are namespaced per test
 * ({@code dtic<NN>-...}) since the suite runs in parallel, random order, on a shared warm stack.
 */
@SuppressWarnings("checkstyle:magicnumber")
class DsTlsIntermediateCasApiTest extends CsApiTest {

    @Test
    void addingDsTlsIntermediateCaIsCreated(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var caClient = new DsTlsCertificationAuthoritiesAdminClient(session);
        var intCaClient = new DsTlsIntermediateCasAdminClient(session);

        var caId = Step.given("parent DS TLS CA seeded", () ->
                caClient.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtic01-root"), "dtic01-name", null, null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var intCaId = Step.when("intermediate CA is added", () ->
                caClient.addIntermediateCa(caId, seeder.generateCertForServer("dtic01-sub"))
                        .statusCode(201)
                        .body("id", notNullValue())
                        .body("ds_tls_certification_authority_id", equalTo(caId))
                        .body("ca_certificate.hash", notNullValue())
                        .body("ca_certificate.issuer_distinguished_name", notNullValue())
                        .body("ca_certificate.subject_distinguished_name", notNullValue())
                        .body("ca_certificate.subject_common_name", notNullValue())
                        .body("ca_certificate.not_before", notNullValue())
                        .body("ca_certificate.not_after", notNullValue())
                        .extract().jsonPath().getInt("id"));

        Step.and("intermediate CAs list contains the added CA", () ->
                caClient.listIntermediateCas(caId)
                        .statusCode(200)
                        .body("id", hasItem(intCaId)));

        Step.then("GET by its own id returns the same intermediate CA", () ->
                intCaClient.getDsTlsIntermediateCa(intCaId)
                        .statusCode(200)
                        .body("id", equalTo(intCaId))
                        .body("ds_tls_certification_authority_id", equalTo(caId))
                        .body("ca_certificate.subject_distinguished_name", notNullValue()));
    }

    @Test
    void addingDsTlsIntermediateCaFailsWithWrongParentId(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var caClient = new DsTlsCertificationAuthoritiesAdminClient(session);

        Step.when("intermediate CA is added with nonexistent parent id", () ->
                caClient.addIntermediateCa(Integer.MAX_VALUE, seeder.generateCertForServer("dtic02-sub"))
                        .statusCode(404)
                        .body("error.code", equalTo("ds_tls_certification_authority_not_found")));
    }

    @Test
    void addingDsTlsIntermediateCaFailsWithMalformedCertificate(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var caClient = new DsTlsCertificationAuthoritiesAdminClient(session);
        var malformedCert = "-----BEGIN CERTIFICATE-----\nnot-a-real-certificate\n-----END CERTIFICATE-----\n"
                .getBytes(StandardCharsets.UTF_8);

        var caId = Step.given("parent DS TLS CA seeded", () ->
                caClient.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtic03-root"), "dtic03-name", null, null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.when("intermediate CA is added with a malformed certificate", () ->
                caClient.addIntermediateCa(caId, "dtic03-sub.pem", malformedCert)
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_certificate")));
    }

    @Test
    void dsTlsIntermediateCaIsDeleted(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var caClient = new DsTlsCertificationAuthoritiesAdminClient(session);
        var intCaClient = new DsTlsIntermediateCasAdminClient(session);

        var caId = Step.given("parent DS TLS CA seeded", () ->
                caClient.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtic04-root"), "dtic04-name", null, null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var intCaId = Step.and("intermediate CA added", () ->
                caClient.addIntermediateCa(caId, seeder.generateCertForServer("dtic04-sub"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.when("intermediate CA is deleted", () ->
                intCaClient.deleteDsTlsIntermediateCa(intCaId).statusCode(204));

        Step.then("intermediate CAs list is empty", () ->
                caClient.listIntermediateCas(caId)
                        .statusCode(200)
                        .body("$", hasSize(0)));

        Step.then("GET the deleted intermediate CA by its own id returns 404", () ->
                intCaClient.getDsTlsIntermediateCa(intCaId)
                        .statusCode(404)
                        .body("error.code", equalTo("ds_tls_intermediate_ca_not_found")));
    }

    @Test
    void dsTlsIntermediateCaGetFailsDueToWrongId(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var caClient = new DsTlsCertificationAuthoritiesAdminClient(session);
        var intCaClient = new DsTlsIntermediateCasAdminClient(session);
        Step.given("DS TLS CA seeded to confirm stack is live", () ->
                caClient.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtic05-root"), "dtic05-name", null, null)
                        .statusCode(201));

        Step.when("GET with nonexistent id returns 404", () ->
                intCaClient.getDsTlsIntermediateCa(Integer.MAX_VALUE)
                        .statusCode(404)
                        .body("error.code", equalTo("ds_tls_intermediate_ca_not_found")));
    }

    @Test
    void dsTlsIntermediateCaDeleteFailsDueToWrongId(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var caClient = new DsTlsCertificationAuthoritiesAdminClient(session);
        var intCaClient = new DsTlsIntermediateCasAdminClient(session);
        Step.given("DS TLS CA seeded to confirm stack is live", () ->
                caClient.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtic06-root"), "dtic06-name", null, null)
                        .statusCode(201));

        Step.when("DELETE with nonexistent id returns 404", () ->
                intCaClient.deleteDsTlsIntermediateCa(Integer.MAX_VALUE - 1)
                        .statusCode(404)
                        .body("error.code", equalTo("ds_tls_intermediate_ca_not_found")));
    }

    @Test
    void managementServiceRoleCannotManageDsTlsIntermediateCas(CsBaselineSeeder seeder) throws Exception {
        var adminSession = Step.given("admin session opened", seeder::newSession);
        var adminCaClient = new DsTlsCertificationAuthoritiesAdminClient(adminSession);

        var caId = Step.given("parent DS TLS CA seeded", () ->
                adminCaClient.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtic07-root"), "dtic07-name", null, null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));
        var intCaId = Step.and("intermediate CA seeded", () ->
                adminCaClient.addIntermediateCa(caId, seeder.generateCertForServer("dtic07-sub"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var session = Step.given("management service only session opened", seeder::newManagementServiceOnlySession);
        var caClient = new DsTlsCertificationAuthoritiesAdminClient(session);
        var intCaClient = new DsTlsIntermediateCasAdminClient(session);

        Step.then("adding an intermediate CA is forbidden", () ->
                caClient.addIntermediateCa(caId, seeder.generateCertForServer("dtic07-sub2"))
                        .statusCode(403));

        Step.then("viewing an intermediate CA by its own id is forbidden", () ->
                intCaClient.getDsTlsIntermediateCa(intCaId)
                        .statusCode(403));

        Step.then("deleting an intermediate CA is forbidden", () ->
                intCaClient.deleteDsTlsIntermediateCa(intCaId)
                        .statusCode(403));
    }
}
