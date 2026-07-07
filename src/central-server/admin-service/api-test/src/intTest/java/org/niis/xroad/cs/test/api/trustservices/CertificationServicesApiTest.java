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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.CertificationServicesAdminClient;
import org.niis.xroad.cs.test.api.admin.IntermediateCasAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@SuppressWarnings("checkstyle:magicnumber")
class CertificationServicesApiTest extends CsApiTest {

    private static final String BASIC_PROFILE =
            "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider";
    private static final String FIVRK_PROFILE =
            "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider";

    @Test
    void certificationServicesAreCreatedAndListed(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);

        var id1 = Step.when("first CA is added", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs01-cert1"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .body("id", notNullValue())
                        .extract().jsonPath().getInt("id"));

        var id2 = Step.and("second CA is added", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs01-cert2"), FIVRK_PROFILE, "DER", null)
                        .statusCode(201)
                        .body("id", notNullValue())
                        .extract().jsonPath().getInt("id"));

        Step.then("list contains both CAs", () ->
                cs.listCertificationServices()
                        .statusCode(200)
                        .body("id", hasItem(id1))
                        .body("id", hasItem(id2)));
    }

    @Test
    void certificationServiceIsCreatedAndRetrieved(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);

        var id = Step.when("CA is added", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs02-cert1"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.then("GET by id returns expected fields", () ->
                cs.getCertificationService(id)
                        .statusCode(200)
                        .body("id", equalTo(id))
                        .body("certificate_profile_info", equalTo(BASIC_PROFILE))
                        .body("issuer_distinguished_name", notNullValue())
                        .body("subject_distinguished_name", notNullValue())
                        .body("not_after", notNullValue())
                        .body("not_before", notNullValue())
                        .body("tls_auth", notNullValue()));
    }

    //   :: "Certification Service creation failed when incorrect certificateProfileInfo is used"
    @ParameterizedTest
    @MethodSource("badProfileArgs")
    void certificationServiceCreationFailedWithBadProfile(
            String certSuffix, String profileInfo, String expectedErrorCode, CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);

        Step.when("CA is added with bad profile", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs03-" + certSuffix), profileInfo, "DER", null)
                        .statusCode(400)
                        .body("error.code", equalTo(expectedErrorCode)));
    }

    static Stream<Arguments> badProfileArgs() {
        return Stream.of(
                Arguments.of("empty", null, "certificate_profile_info_class_not_found"),
                Arguments.of("missing", "missing.class", "certificate_profile_info_class_not_found")
        );
    }

    @Test
    void certificationServiceGetFailsDueToWrongId(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        Step.given("CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs04-cert"), BASIC_PROFILE, "DER", null)
                        .statusCode(201));

        Step.when("GET with nonexistent id returns 404", () ->
                cs.getCertificationService(Integer.MAX_VALUE)
                        .statusCode(404)
                        .body("error.code", equalTo("certification_service_not_found")));
    }

    @Test
    void certificationServiceIsCreatedAndUpdated(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);

        var id = Step.given("CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs05-cert1"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.when("CA is updated with tlsAuth=true and FIVRK profile", () ->
                cs.updateCertificationService(id, "true", FIVRK_PROFILE)
                        .statusCode(200)
                        .body("id", equalTo(id))
                        .body("tls_auth", equalTo(true))
                        .body("certificate_profile_info", equalTo(FIVRK_PROFILE)));
    }

    //   :: "Certification Service is created and fails due to missing default_csr_format field"
    @Test
    void certificationServiceCreationFailsDueToMissingDefaultCsrFormat(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);

        Step.when("CA is added without default_csr_format", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs06-cert1"), BASIC_PROFILE, null, null)
                        .statusCode(400));
    }

    @ParameterizedTest
    @MethodSource("badUpdateProfileArgs")
    void certificationServiceUpdateFailedWithBadProfile(
            String label, String profileInfo, String errorCode, int expectedStatus,
            CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        var id = Step.given("CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs07-" + label), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.when("PATCH with bad profile returns error", () ->
                cs.updateCertificationService(id, "false", profileInfo)
                        .statusCode(expectedStatus)
                        .body("error.code", equalTo(errorCode)));
    }

    static Stream<Arguments> badUpdateProfileArgs() {
        return Stream.of(
                Arguments.of("empty", "", "certificate_profile_info_class_not_found", 400),
                Arguments.of("missing", "missing.class", "certificate_profile_info_class_not_found", 400)
        );
    }

    @Test
    void certificationServiceUpdateFailsForNonexistentId(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        Step.given("CA seeded to confirm stack is live", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs08-cert"), BASIC_PROFILE, "DER", null)
                        .statusCode(201));

        Step.when("PATCH with nonexistent id returns 404", () ->
                cs.updateCertificationService(Integer.MAX_VALUE, "false", BASIC_PROFILE)
                        .statusCode(404)
                        .body("error.code", equalTo("certification_service_not_found")));
    }

    @Test
    void certificationServicesListingForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) {
        var session = Step.given("registration officer session opened", seeder::newRegistrationOfficerSession);
        var cs = new CertificationServicesAdminClient(session);

        Step.when("GET /certification-services as REGISTRATION_OFFICER returns 403", () ->
                cs.listCertificationServices()
                        .statusCode(403));
    }

    @Test
    void certificationServiceIsCreatedWithOcspRespondersAndDeleted(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        var ocspCert = seeder.generateCertForServer("cs09-ocsp");

        var id = Step.given("CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs09-cert1"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.and("two OCSP responders added", () -> {
            cs.addCertificationServiceOcspResponder(id, "https://cs09-ocsp1.test", ocspCert).statusCode(201);
            cs.addCertificationServiceOcspResponder(id, "https://cs09-ocsp2.test", ocspCert).statusCode(201);
        });

        Step.when("OCSP responders listed", () ->
                cs.listCertificationServiceOcspResponders(id)
                        .statusCode(200)
                        .body("url", hasItem("https://cs09-ocsp1.test"))
                        .body("url", hasItem("https://cs09-ocsp2.test"))
                        .body("has_certificate", hasItem(true)));

        Step.and("CA is deleted", () ->
                cs.deleteCertificationService(id).statusCode(204));

        Step.then("GET CA returns 404", () ->
                cs.getCertificationService(id)
                        .statusCode(404)
                        .body("error.code", equalTo("certification_service_not_found")));
    }

    @Test
    void certificationServiceDeletion(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        var intCaClient = new IntermediateCasAdminClient(session);
        var ocspCert = seeder.generateCertForServer("cs10-ocsp");
        var intCaCert1 = seeder.generateCertForServer("cs10-intca");
        var intCaCert2 = seeder.generateCertForServer("cs10-intca2");

        var id = Step.given("CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs10-cert1"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.and("OCSP responders + intermediate CAs with OCSP added", () -> {
            cs.addCertificationServiceOcspResponder(id, "https://cs10-ocsp1.test", ocspCert).statusCode(201);
            cs.addCertificationServiceOcspResponder(id, "https://cs10-ocsp2.test", ocspCert).statusCode(201);
            var intCaId = cs.addIntermediateCa(id, intCaCert1)
                    .statusCode(201)
                    .extract().jsonPath().getInt("id");
            intCaClient.addOcspResponder(intCaId, "https://cs10-intca-ocsp.test", ocspCert).statusCode(201);
            cs.addIntermediateCa(id, intCaCert2).statusCode(201);
        });

        Step.when("CA is deleted (cascade)", () ->
                cs.deleteCertificationService(id).statusCode(204));

        Step.then("GET CA returns 404", () ->
                cs.getCertificationService(id)
                        .statusCode(404)
                        .body("error.code", equalTo("certification_service_not_found")));
    }

    @Test
    void certificationServiceDeleteFailsDueToWrongId(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        Step.given("CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs11-cert"), BASIC_PROFILE, "DER", null)
                        .statusCode(201));

        Step.when("DELETE with nonexistent id returns 404", () ->
                cs.deleteCertificationService(Integer.MAX_VALUE - 1)
                        .statusCode(404)
                        .body("error.code", equalTo("certification_service_not_found")));
    }

    @Test
    void certificationServiceGetOcspRespondersFailsDueToWrongId(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        Step.given("CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs12-cert"), BASIC_PROFILE, "DER", null)
                        .statusCode(201));

        Step.when("GET OCSP responders for nonexistent CA returns 404", () ->
                cs.listCertificationServiceOcspResponders(Integer.MAX_VALUE - 2)
                        .statusCode(404)
                        .body("error.code", equalTo("certification_service_not_found")));
    }

    @Test
    void certificationServiceCertificateIsViewed(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);

        var id = Step.when("CA is added", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs13-cert1"), BASIC_PROFILE, "DER", null)
                        .statusCode(201)
                        .extract().jsonPath().getInt("id"));

        Step.then("certificate details are returned with expected fields", () ->
                cs.getCertificationServiceCertificate(id)
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
    void certificationServiceCertificateGetFailsDueToWrongId(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var cs = new CertificationServicesAdminClient(session);
        Step.given("CA seeded", () ->
                cs.addCertificationService(seeder.generateCertForServer("cs14-cert"), BASIC_PROFILE, "DER", null)
                        .statusCode(201));

        Step.when("GET certificate for nonexistent CA returns 404", () ->
                cs.getCertificationServiceCertificate(Integer.MAX_VALUE - 3)
                        .statusCode(404)
                        .body("error.code", equalTo("certification_service_not_found")));
    }
}
