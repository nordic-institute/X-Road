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
import org.niis.xroad.cs.test.api.admin.CertificationServicesAdminClient;
import org.niis.xroad.cs.test.api.admin.IntermediateCasAdminClient;
import org.niis.xroad.cs.test.api.admin.OcspRespondersAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@SuppressWarnings("checkstyle:magicnumber")
class IntermediateCasApiTest extends CsApiTest {

    private static final String BASIC_PROFILE =
            "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider";

    @Test
    void addingIntermediateCaIsCreated(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);

        var csId = Step.given("parent CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("ica01-root"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var intCaId = Step.when("intermediate CA is added", () ->
                cs.addIntermediateCa(csId, seeder.generateCertForServer("ica01-sub"))
                        .statusCode(201)
                        .body("id", notNullValue())
                        .body("ca_certificate.hash", notNullValue())
                        .body("ca_certificate.issuer_distinguished_name", notNullValue())
                        .body("ca_certificate.subject_distinguished_name", notNullValue())
                        .body("ca_certificate.subject_common_name", notNullValue())
                        .body("ca_certificate.not_before", notNullValue())
                        .body("ca_certificate.not_after", notNullValue())
                        .extract().jsonPath().getInt("id"));

        Step.then("intermediate CAs list contains the added CA", () ->
                cs.listIntermediateCas(csId)
                        .statusCode(200)
                        .body("id", org.hamcrest.Matchers.hasItem(intCaId)));
    }

    @Test
    void addingIntermediateCaFailsWithWrongCertId(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);

        Step.when("intermediate CA is added with nonexistent parent id", () ->
                cs.addIntermediateCa(Integer.MAX_VALUE, seeder.generateCertForServer("ica02-sub"))
                        .statusCode(404)
                        .body("error.code", equalTo("certification_service_not_found")));
    }

    @Test
    void addingIntermediateCaIsDeleted(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        var intCaClient = new IntermediateCasAdminClient(session);

        var csId = Step.given("parent CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("ica03-root"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var intCaId = Step.and("intermediate CA added", () ->
                cs.addIntermediateCa(csId, seeder.generateCertForServer("ica03-sub"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.when("intermediate CA is deleted", () ->
                intCaClient.deleteIntermediateCa(intCaId).statusCode(204));

        Step.then("intermediate CAs list is empty", () ->
                cs.listIntermediateCas(csId)
                        .statusCode(200)
                        .body("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void addingOcspResponderToIntermediateCa(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        var intCaClient = new IntermediateCasAdminClient(session);

        var csId = Step.given("parent CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("ica04-root"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var intCaId = Step.and("intermediate CA added", () ->
                cs.addIntermediateCa(csId, seeder.generateCertForServer("ica04-sub"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.when("OCSP responder is added to the intermediate CA", () ->
                intCaClient.addOcspResponder(intCaId, "https://ica04-ocsp.test",
                        seeder.generateCertForServer("ica04-ocsp"))
                        .statusCode(201)
                        .body("id", notNullValue())
                        .body("url", equalTo("https://ica04-ocsp.test"))
                        .body("has_certificate", equalTo(true)));
    }

    @Test
    void viewOcspRespondersOfIntermediateCa(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        var intCaClient = new IntermediateCasAdminClient(session);

        var csId = Step.given("parent CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("ica05-root"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var intCaId = Step.and("intermediate CA added", () ->
                cs.addIntermediateCa(csId, seeder.generateCertForServer("ica05-sub"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var ocspCert = seeder.generateCertForServer("ica05-ocsp");
        Step.and("three OCSP responders added", () -> {
            intCaClient.addOcspResponder(intCaId, "https://ica05-ocsp1.test", ocspCert).statusCode(201);
            intCaClient.addOcspResponder(intCaId, "https://ica05-ocsp2.test", ocspCert).statusCode(201);
            intCaClient.addOcspResponder(intCaId, "https://ica05-ocsp3.test", ocspCert).statusCode(201);
        });

        Step.then("list returns 3 OCSP responders", () ->
                intCaClient.listOcspResponders(intCaId)
                        .statusCode(200)
                        .body("$", org.hamcrest.Matchers.hasSize(3)));
    }

    @Test
    void deletingOcspResponderFromIntermediateCa(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        var intCaClient = new IntermediateCasAdminClient(session);

        var csId = Step.given("parent CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("ica06-root"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var intCaId = Step.and("intermediate CA added", () ->
                cs.addIntermediateCa(csId, seeder.generateCertForServer("ica06-sub"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var ocspId = Step.and("OCSP responder added", () ->
                intCaClient.addOcspResponder(intCaId, "https://ica06-ocsp.test",
                        seeder.generateCertForServer("ica06-ocsp"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.and("list has 1 OCSP responder", () ->
                intCaClient.listOcspResponders(intCaId)
                        .statusCode(200)
                        .body("$", org.hamcrest.Matchers.hasSize(1)));

        Step.when("OCSP responder is deleted via CA endpoint", () ->
                intCaClient.deleteOcspResponder(intCaId, ocspId).statusCode(204));

        Step.then("list has 0 OCSP responders", () ->
                intCaClient.listOcspResponders(intCaId)
                        .statusCode(200)
                        .body("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void deletingOcspResponderFromIntermediateCaById(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        var intCaClient = new IntermediateCasAdminClient(session);
        var ocspClient = new OcspRespondersAdminClient(session);

        var csId = Step.given("parent CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("ica07-root"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var intCaId = Step.and("intermediate CA added", () ->
                cs.addIntermediateCa(csId, seeder.generateCertForServer("ica07-sub"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var ocspId = Step.and("OCSP responder added", () ->
                intCaClient.addOcspResponder(intCaId, "https://ica07-ocsp.test",
                        seeder.generateCertForServer("ica07-ocsp"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.and("list has 1 OCSP responder", () ->
                intCaClient.listOcspResponders(intCaId)
                        .statusCode(200)
                        .body("$", org.hamcrest.Matchers.hasSize(1)));

        Step.when("OCSP responder is deleted via /ocsp-responders/{id}", () ->
                ocspClient.deleteOcspResponder(ocspId).statusCode(204));

        Step.then("list has 0 OCSP responders", () ->
                intCaClient.listOcspResponders(intCaId)
                        .statusCode(200)
                        .body("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void modifyOcspResponderOfIntermediateCa(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        var intCaClient = new IntermediateCasAdminClient(session);
        var ocspClient = new OcspRespondersAdminClient(session);

        var csId = Step.given("parent CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("ica08-root"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var intCaId = Step.and("intermediate CA added", () ->
                cs.addIntermediateCa(csId, seeder.generateCertForServer("ica08-sub"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var ocspId = Step.and("OCSP responder added", () ->
                intCaClient.addOcspResponder(intCaId, "https://ica08-ocsp.test",
                        seeder.generateCertForServer("ica08-ocsp"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var newUrl = "https://ica08-ocsp-updated.test";
        Step.when("OCSP responder URL is updated (no cert change)", () ->
                ocspClient.updateOcspResponder(ocspId, newUrl, null)
                        .statusCode(200)
                        .body("url", equalTo(newUrl)));

        Step.then("list still has 1 OCSP responder with updated URL", () ->
                intCaClient.listOcspResponders(intCaId)
                        .statusCode(200)
                        .body("$", org.hamcrest.Matchers.hasSize(1))
                        .body("[0].url", equalTo(newUrl))
                        .body("[0].has_certificate", equalTo(true)));
    }

    @Test
    void modifyOcspResponderOfIntermediateCaWithNewCert(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        var intCaClient = new IntermediateCasAdminClient(session);
        var ocspClient = new OcspRespondersAdminClient(session);

        var csId = Step.given("parent CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("ica09-root"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var intCaId = Step.and("intermediate CA added", () ->
                cs.addIntermediateCa(csId, seeder.generateCertForServer("ica09-sub"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var ocspId = Step.and("OCSP responder added", () ->
                intCaClient.addOcspResponder(intCaId, "https://ica09-ocsp.test",
                        seeder.generateCertForServer("ica09-ocsp"))
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        var oldCertHash = Step.and("old certificate hash captured", () ->
                ocspClient.getOcspResponderCertificate(ocspId)
                        .statusCode(200)
                        .extract().jsonPath().getString("hash"));

        var newUrl = "https://ica09-ocsp-updated.test";
        Step.when("OCSP responder URL and certificate are updated", () ->
                ocspClient.updateOcspResponder(ocspId, newUrl, seeder.generateCertForServer("ica09-ocsp-new"))
                        .statusCode(200)
                        .body("url", equalTo(newUrl)));

        Step.then("new certificate hash differs from old", () ->
                ocspClient.getOcspResponderCertificate(ocspId)
                        .statusCode(200)
                        .body("hash", not(equalTo(oldCertHash))));
    }
}
