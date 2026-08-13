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

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * API tests for the {@code /ds-tls-certification-authorities} admin resource (approved DS TLS CA management,
 * XRDDEV-3288). Every fixture name is namespaced per test ({@code dtca<NN>-...}) since the suite runs in
 * parallel, random order, on a shared warm stack with no reset between tests.
 */
@SuppressWarnings("checkstyle:magicnumber")
class DsTlsCertificationAuthoritiesApiTest extends CsApiTest {

    private static final String ACME_DIRECTORY_URL = "https://acme-v02.api.letsencrypt.org/directory";
    private static final String DS_TLS_PROFILE_ID = "xrd-ds-tls";

    @Test
    void dsTlsCertificationAuthoritiesAreCreatedAndListed(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);

        var id1 = Step.when("first DS TLS CA is added (manual path, no ACME)", () ->
                client.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtca01-cert1"), "dtca01-name1", null, null)
                        .statusCode(201)
                        .body("id", notNullValue())
                        .extract().jsonPath().getInt("id"));

        var id2 = Step.and("second DS TLS CA is added (with ACME)", () ->
                client.addDsTlsCertificationAuthority(
                        seeder.generateCertForServer("dtca01-cert2"), "dtca01-name2", ACME_DIRECTORY_URL, DS_TLS_PROFILE_ID)
                        .statusCode(201)
                        .body("id", notNullValue())
                        .extract().jsonPath().getInt("id"));

        Step.then("list contains both DS TLS CAs", () ->
                client.listDsTlsCertificationAuthorities()
                        .statusCode(200)
                        .body("id", hasItem(id1))
                        .body("id", hasItem(id2)));
    }

    @Test
    void managementServiceRoleCannotViewDsTlsCertificationAuthorities(CsBaselineSeeder seeder) {
        var session = Step.given("management service only session opened", seeder::newManagementServiceOnlySession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);

        Step.then("listing DS TLS certification authorities is forbidden", () ->
                client.listDsTlsCertificationAuthorities()
                        .statusCode(403));

        Step.then("viewing DS TLS certification authority details is forbidden", () ->
                client.getDsTlsCertificationAuthority(1)
                        .statusCode(403));
    }

    @Test
    void dsTlsCertificationAuthorityIsCreatedAndRetrieved(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);

        var id = Step.when("DS TLS CA is added without ACME", () ->
                client.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtca02-cert1"), "dtca02-name", null, null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.then("GET by id returns expected fields with no ACME server", () ->
                client.getDsTlsCertificationAuthority(id)
                        .statusCode(200)
                        .body("id", equalTo(id))
                        .body("name", equalTo("dtca02-name"))
                        .body("issuer_distinguished_name", notNullValue())
                        .body("subject_distinguished_name", notNullValue())
                        .body("not_after", notNullValue())
                        .body("not_before", notNullValue())
                        .body("acme_server_directory_url", anyOf(nullValue(), emptyOrNullString()))
                        .body("ds_tls_certificate_profile_id", anyOf(nullValue(), emptyOrNullString())));
    }

    @Test
    void dsTlsCertificationAuthorityIsCreatedWithAcmeAndRetrieved(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);

        var id = Step.when("DS TLS CA is added with ACME server", () ->
                client.addDsTlsCertificationAuthority(
                        seeder.generateCertForServer("dtca03-cert1"), "dtca03-name", ACME_DIRECTORY_URL, DS_TLS_PROFILE_ID)
                        .statusCode(201)
                        .body("acme_server_directory_url", equalTo(ACME_DIRECTORY_URL))
                        .body("ds_tls_certificate_profile_id", equalTo(DS_TLS_PROFILE_ID))
                        .extract().jsonPath().getInt("id"));

        Step.then("GET by id returns the ACME fields", () ->
                client.getDsTlsCertificationAuthority(id)
                        .statusCode(200)
                        .body("acme_server_directory_url", equalTo(ACME_DIRECTORY_URL))
                        .body("ds_tls_certificate_profile_id", equalTo(DS_TLS_PROFILE_ID)));
    }

    @Test
    void dsTlsCertificationAuthorityCreationFailsWithMalformedCertificate(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);
        var malformedCert = "-----BEGIN CERTIFICATE-----\nnot-a-real-certificate\n-----END CERTIFICATE-----\n"
                .getBytes(StandardCharsets.UTF_8);

        Step.when("DS TLS CA is added with a malformed certificate", () ->
                client.addDsTlsCertificationAuthority("dtca04-cert.pem", malformedCert, "dtca04-name", null, null)
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_certificate")));
    }

    @Test
    void dsTlsCertificationAuthorityCreationFailsWithBadAcmeUrl(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);

        Step.when("DS TLS CA is added with a malformed ACME directory URL", () ->
                client.addDsTlsCertificationAuthority(
                        seeder.generateCertForServer("dtca05-cert1"), "dtca05-name", "not-a-url", null)
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_url")));
    }

    @Test
    void dsTlsCertificationAuthorityCreationFailsDueToMissingName(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);

        Step.when("DS TLS CA is added without a name", () ->
                client.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtca06-cert1"), null, null, null)
                        .statusCode(400));
    }

    @Test
    void dsTlsCertificationAuthorityGetFailsDueToWrongId(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);
        Step.given("DS TLS CA seeded to confirm stack is live", () ->
                client.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtca07-cert"), "dtca07-name", null, null)
                        .statusCode(201));

        Step.when("GET with nonexistent id returns 404", () ->
                client.getDsTlsCertificationAuthority(Integer.MAX_VALUE)
                        .statusCode(404)
                        .body("error.code", equalTo("ds_tls_certification_authority_not_found")));
    }

    @Test
    void dsTlsCertificationAuthorityIsUpdated(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);

        var id = Step.given("DS TLS CA seeded without ACME", () ->
                client.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtca08-cert1"), "dtca08-name", null, null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.when("DS TLS CA is updated with an ACME server", () ->
                client.updateDsTlsCertificationAuthority(id, null, ACME_DIRECTORY_URL, DS_TLS_PROFILE_ID)
                        .statusCode(200)
                        .body("id", equalTo(id))
                        .body("acme_server_directory_url", equalTo(ACME_DIRECTORY_URL))
                        .body("ds_tls_certificate_profile_id", equalTo(DS_TLS_PROFILE_ID)));
    }

    @Test
    void dsTlsCertificationAuthorityAcmeFieldsAreCleared(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);

        var id = Step.given("DS TLS CA seeded with ACME", () ->
                client.addDsTlsCertificationAuthority(
                        seeder.generateCertForServer("dtca09-cert1"), "dtca09-name", ACME_DIRECTORY_URL, DS_TLS_PROFILE_ID)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.when("ACME fields are cleared via PATCH with empty strings", () ->
                client.updateDsTlsCertificationAuthority(id, null, "", "")
                        .statusCode(200)
                        .body("acme_server_directory_url", anyOf(nullValue(), emptyOrNullString()))
                        .body("ds_tls_certificate_profile_id", anyOf(nullValue(), emptyOrNullString())));

        Step.then("GET confirms ACME fields stay cleared", () ->
                client.getDsTlsCertificationAuthority(id)
                        .statusCode(200)
                        .body("acme_server_directory_url", anyOf(nullValue(), emptyOrNullString()))
                        .body("ds_tls_certificate_profile_id", anyOf(nullValue(), emptyOrNullString())));
    }

    @Test
    void dsTlsCertificationAuthorityUpdateFailsWithBadAcmeUrl(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);
        var id = Step.given("DS TLS CA seeded", () ->
                client.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtca10-cert1"), "dtca10-name", null, null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.when("PATCH with malformed ACME URL returns 400", () ->
                client.updateDsTlsCertificationAuthority(id, null, "not-a-url", null)
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_url")));
    }

    @Test
    void dsTlsCertificationAuthorityUpdateFailsForNonexistentId(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);
        Step.given("DS TLS CA seeded to confirm stack is live", () ->
                client.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtca11-cert"), "dtca11-name", null, null)
                        .statusCode(201));

        Step.when("PATCH with nonexistent id returns 404", () ->
                client.updateDsTlsCertificationAuthority(Integer.MAX_VALUE, "x", null, null)
                        .statusCode(404)
                        .body("error.code", equalTo("ds_tls_certification_authority_not_found")));
    }

    @Test
    void dsTlsCertificationAuthoritiesListingForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) {
        var session = Step.given("registration officer session opened", seeder::newRegistrationOfficerSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);

        Step.when("GET /ds-tls-certification-authorities as REGISTRATION_OFFICER returns 403", () ->
                client.listDsTlsCertificationAuthorities()
                        .statusCode(403));
    }

    @Test
    void dsTlsCertificationAuthorityDeletion(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);
        var intCaClient = new DsTlsIntermediateCasAdminClient(session);

        var id = Step.given("DS TLS CA seeded", () ->
                client.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtca12-cert1"), "dtca12-name", null, null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var intCaId = Step.and("intermediate CA added (cascades on delete)", () ->
                client.addIntermediateCa(id, seeder.generateCertForServer("dtca12-intca"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.when("DS TLS CA is deleted", () ->
                client.deleteDsTlsCertificationAuthority(id).statusCode(204));

        Step.then("GET DS TLS CA returns 404", () ->
                client.getDsTlsCertificationAuthority(id)
                        .statusCode(404)
                        .body("error.code", equalTo("ds_tls_certification_authority_not_found")));

        Step.then("GET the cascaded intermediate CA returns 404", () ->
                intCaClient.getDsTlsIntermediateCa(intCaId)
                        .statusCode(404)
                        .body("error.code", equalTo("ds_tls_intermediate_ca_not_found")));
    }

    @Test
    void dsTlsCertificationAuthorityDeleteFailsDueToWrongId(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);
        Step.given("DS TLS CA seeded", () ->
                client.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtca13-cert"), "dtca13-name", null, null)
                        .statusCode(201));

        Step.when("DELETE with nonexistent id returns 404", () ->
                client.deleteDsTlsCertificationAuthority(Integer.MAX_VALUE - 1)
                        .statusCode(404)
                        .body("error.code", equalTo("ds_tls_certification_authority_not_found")));
    }

    @Test
    void dsTlsCertificationAuthorityCertificateIsViewed(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);

        var id = Step.when("DS TLS CA is added", () ->
                client.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtca14-cert1"), "dtca14-name", null, null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.then("certificate details are returned with expected fields", () ->
                client.getDsTlsCertificationAuthorityCertificate(id)
                        .statusCode(200)
                        .body("hash", notNullValue())
                        .body("issuer_common_name", notNullValue())
                        .body("issuer_distinguished_name", notNullValue())
                        .body("not_after", notNullValue())
                        .body("not_before", notNullValue())
                        .body("public_key_algorithm", notNullValue())
                        .body("signature_algorithm", notNullValue())
                        .body("subject_common_name", notNullValue())
                        .body("subject_distinguished_name", notNullValue())
                        .body("version", notNullValue()));
    }

    @Test
    void dsTlsCertificationAuthorityCertificateGetFailsDueToWrongId(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new DsTlsCertificationAuthoritiesAdminClient(session);
        Step.given("DS TLS CA seeded", () ->
                client.addDsTlsCertificationAuthority(seeder.generateCertForServer("dtca15-cert"), "dtca15-name", null, null)
                        .statusCode(201));

        Step.when("GET certificate for nonexistent DS TLS CA returns 404", () ->
                client.getDsTlsCertificationAuthorityCertificate(Integer.MAX_VALUE - 2)
                        .statusCode(404)
                        .body("error.code", equalTo("ds_tls_certification_authority_not_found")));
    }
}
