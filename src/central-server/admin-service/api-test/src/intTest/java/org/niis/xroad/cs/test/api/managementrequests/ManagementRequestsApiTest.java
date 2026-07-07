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
package org.niis.xroad.cs.test.api.managementrequests;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ClientDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.ClientDisableRequestDto;
import org.niis.xroad.cs.openapi.model.ClientEnableRequestDto;
import org.niis.xroad.cs.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ClientRenameRequestDto;
import org.niis.xroad.cs.openapi.model.MaintenanceModeDisableRequestDto;
import org.niis.xroad.cs.openapi.model.MaintenanceModeEnableRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.cs.openapi.model.NewSubsystemIdDto;
import org.niis.xroad.cs.openapi.model.OwnerChangeRequestDto;
import org.niis.xroad.cs.openapi.model.SubsystemAddDto;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.ManagementRequestsAdminClient;
import org.niis.xroad.cs.test.api.admin.MembersAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.niis.xroad.cs.test.api.CsBaselineSeeder.INSTANCE_IDENTIFIER;

@SuppressWarnings({"checkstyle:magicnumber", "checkstyle:methodlength"})
class ManagementRequestsApiTest extends CsApiTest {

    private static final String MEMBER_CLASS = CsBaselineSeeder.BASELINE_MEMBER_CLASS;
    private static final String OWNERS_GROUP = "security-server-owners";

    @Test
    void addDeleteAuthCert(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "mr01", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);
        var serverId = memberId + ":ssmr01";
        var cert = seeder.generateCertForServer(serverId);

        var regId = Step.when("auth cert registration request posted", () ->
                postAuthCertReg(client, cert, serverId, "security-server-address-ssmr01",
                        ManagementRequestOriginDto.SECURITY_SERVER));

        Step.when("registration request approved", () ->
                client.approveRequest(regId).statusCode(200));

        Step.then("server has one auth cert", () ->
                session.given()
                        .get("/security-servers/{id}/authentication-certificates", serverId)
                        .then()
                        .statusCode(200)
                        .body("$", hasSize(1)));

        Step.when("auth cert deletion request posted", () -> {
            var req = new AuthenticationCertificateDeletionRequestDto()
                    .authenticationCertificate(cert);
            req.setType(ManagementRequestTypeDto.AUTH_CERT_DELETION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            client.addManagementRequest(req).statusCode(202);
        });

        Step.then("server has no auth certs", () ->
                session.given()
                        .get("/security-servers/{id}/authentication-certificates", serverId)
                        .then()
                        .statusCode(200)
                        .body("$", hasSize(0)));
    }

    @Test
    void addDeleteAuthCertForNonExistingMember(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new ManagementRequestsAdminClient(session);
        var serverId = INSTANCE_IDENTIFIER + ":" + MEMBER_CLASS + ":membermr02m2:ssmr02m2";
        var cert = seeder.generateCertForServer(serverId);

        var regId = Step.when("auth cert registration posted for non-existing member", () ->
                postAuthCertReg(client, cert, serverId, "security-server-address-ssmr02m2",
                        ManagementRequestOriginDto.SECURITY_SERVER));

        Step.then("member does not exist yet", () ->
                session.given()
                        .get("/members/{id}", INSTANCE_IDENTIFIER + ":" + MEMBER_CLASS + ":membermr02m2")
                        .then()
                        .statusCode(404));

        Step.when("registration approved", () ->
                client.approveRequest(regId).statusCode(200));

        Step.then("server has one auth cert", () ->
                session.given()
                        .get("/security-servers/{id}/authentication-certificates", serverId)
                        .then()
                        .statusCode(200)
                        .body("$", hasSize(1)));

        Step.then("member was created on approval", () ->
                session.given()
                        .get("/members/{id}", INSTANCE_IDENTIFIER + ":" + MEMBER_CLASS + ":membermr02m2")
                        .then()
                        .statusCode(200)
                        .body("member_name", equalTo("membermr02m2")));

        Step.when("auth cert deletion posted", () -> {
            var req = new AuthenticationCertificateDeletionRequestDto()
                    .authenticationCertificate(cert);
            req.setType(ManagementRequestTypeDto.AUTH_CERT_DELETION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            client.addManagementRequest(req).statusCode(202);
        });

        Step.then("no auth certs remain", () ->
                session.given()
                        .get("/security-servers/{id}/authentication-certificates", serverId)
                        .then()
                        .statusCode(200)
                        .body("$", hasSize(0)));
    }

    @Test
    void addDeleteNotYetApprovedAuthCert(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "mr03", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);
        var serverId = memberId + ":ssmr03";
        var cert = seeder.generateCertForServer(serverId);

        var regId = Step.when("auth cert registration posted", () ->
                postAuthCertReg(client, cert, serverId, "security-server-address-ssmr03",
                        ManagementRequestOriginDto.SECURITY_SERVER));

        Step.then("request is in WAITING state", () ->
                client.getRequest(regId)
                        .statusCode(200)
                        .body("status", equalTo("WAITING")));

        Step.when("auth cert deletion posted for pending server", () -> {
            var req = new AuthenticationCertificateDeletionRequestDto()
                    .authenticationCertificate(cert);
            req.setType(ManagementRequestTypeDto.AUTH_CERT_DELETION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            client.addManagementRequest(req).statusCode(202);
        });

        Step.then("registration request is REVOKED", () ->
                client.findRequests(Map.of(
                        "serverId", serverId,
                        "types", "AUTH_CERT_REGISTRATION_REQUEST",
                        "status", "REVOKED"))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(1)));

        Step.then("deletion request exists", () ->
                client.findRequests(Map.of(
                        "serverId", serverId,
                        "types", "AUTH_CERT_DELETION_REQUEST"))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(1)));
    }

    @Test
    void autoApproveAuthCert(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var roSession = Step.given("registration officer session opened", seeder::newRegistrationOfficerSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "mr04", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);
        var roClient = new ManagementRequestsAdminClient(roSession);
        var serverId = memberId + ":ssmr04";
        var cert = seeder.generateCertForServer(serverId);

        var id1 = Step.when("CENTER auth cert registration posted", () ->
                postAuthCertReg(roClient, cert, serverId, "security-server-address-ssmr04",
                        ManagementRequestOriginDto.CENTER));

        Step.then("CENTER request is WAITING", () ->
                roClient.getRequest(id1).statusCode(200).body("status", equalTo("WAITING")));

        var id2 = Step.when("SECURITY_SERVER auth cert registration posted", () -> {
            var req = new AuthenticationCertificateRegistrationRequestDto()
                    .serverAddress("security-server-address-ssmr04")
                    .authenticationCertificate(cert);
            req.setType(ManagementRequestTypeDto.AUTH_CERT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(201)
                    .extract().jsonPath().getInt("id");
        });

        Step.then("SECURITY_SERVER request is auto-approved", () ->
                roClient.getRequest(id2).statusCode(200).body("status", equalTo("APPROVED")));

        Step.then("server has one auth cert", () ->
                session.given()
                        .get("/security-servers/{id}/authentication-certificates", serverId)
                        .then()
                        .statusCode(200)
                        .body("$", hasSize(1)));
    }

    @Test
    void dontAutoApproveAuthCertForNonExistingMember(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new ManagementRequestsAdminClient(session);
        var serverId = INSTANCE_IDENTIFIER + ":" + MEMBER_CLASS + ":membermr05m2:ssmr05m2";
        var cert = seeder.generateCertForServer(serverId);

        var id = Step.when("SECURITY_SERVER auth cert registration posted for non-existing member", () ->
                postAuthCertReg(client, cert, serverId, "security-server-address-ssmr05m2",
                        ManagementRequestOriginDto.SECURITY_SERVER));

        Step.then("request stays WAITING", () ->
                client.getRequest(id).statusCode(200).body("status", equalTo("WAITING")));

        Step.then("member still does not exist", () ->
                session.given()
                        .get("/members/{id}", INSTANCE_IDENTIFIER + ":" + MEMBER_CLASS + ":membermr05m2")
                        .then()
                        .statusCode(404));
    }

    @Test
    void declineAuthCertRegistration(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "mr06", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);
        var serverId = memberId + ":ssmr06";
        var cert = seeder.generateCertForServer(serverId);

        var id = Step.when("auth cert registration posted", () ->
                postAuthCertReg(client, cert, serverId, "security-server-address-ssmr06",
                        ManagementRequestOriginDto.SECURITY_SERVER));

        Step.then("request is WAITING", () ->
                client.getRequest(id).statusCode(200).body("status", equalTo("WAITING")));

        Step.when("request is declined", () ->
                client.revokeRequest(id).statusCode(200));

        Step.then("request is DECLINED", () ->
                client.getRequest(id).statusCode(200).body("status", equalTo("DECLINED")));
    }

    @Test
    void registerMemberAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr07", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr07", memberId));
        var memberId2 = Step.and("client member seeded", () -> seeder.seedMember(session, "mr07m2", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);

        var regId = Step.when("client registration request posted", () -> {
            var req = new ClientRegistrationRequestDto()
                    .clientId(memberId2);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(202)
                    .extract().jsonPath().getInt("id");
        });

        Step.when("registration approved", () ->
                client.approveRequest(regId).statusCode(200));

        Step.then("server clients contains new member", () ->
                session.given()
                        .get("/security-servers/{id}/clients", serverId)
                        .then()
                        .statusCode(200)
                        .body("find { it.client_id.member_code == 'membermr07m2' }", notNullValue()));
    }

    @Test
    void registerNonExistingMemberAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr08", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr08", memberId));
        var client = new ManagementRequestsAdminClient(session);
        var clientMemberId = INSTANCE_IDENTIFIER + ":" + MEMBER_CLASS + ":membermr08m2";

        var regId = Step.when("client registration posted for non-existing member", () -> {
            var req = new ClientRegistrationRequestDto()
                    .clientId(clientMemberId);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(202)
                    .extract().jsonPath().getInt("id");
        });

        Step.when("approved", () -> client.approveRequest(regId).statusCode(200));

        Step.then("server clients contains new member", () ->
                session.given()
                        .get("/security-servers/{id}/clients", serverId)
                        .then()
                        .statusCode(200)
                        .body("find { it.client_id.member_code == 'membermr08m2' }", notNullValue()));

        Step.then("member was created on approval", () ->
                session.given()
                        .get("/members/{id}", clientMemberId)
                        .then()
                        .statusCode(200)
                        .body("member_name", equalTo("membermr08m2")));
    }

    @Test
    void deleteMemberAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr09", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr09", memberId));
        var memberId2 = Step.and("client member seeded", () -> seeder.seedMember(session, "mr09m2", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);

        Step.and("member registered as client and approved", () -> {
            var req = new ClientRegistrationRequestDto().clientId(memberId2);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            var regId = client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
            client.approveRequest(regId).statusCode(200);
        });

        Step.then("server clients contains mr09m2", () ->
                session.given()
                        .get("/security-servers/{id}/clients", serverId)
                        .then()
                        .statusCode(200)
                        .body("find { it.client_id.member_code == 'membermr09m2' }", notNullValue()));

        Step.when("client deletion posted", () -> {
            var req = new ClientDeletionRequestDto();
            req.setType(ManagementRequestTypeDto.CLIENT_DELETION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            req.setClientId(memberId2);
            client.addManagementRequest(req).statusCode(202);
        });

        Step.then("server has no clients", () ->
                session.given()
                        .get("/security-servers/{id}/clients", serverId)
                        .then()
                        .statusCode(200)
                        .body("$", hasSize(0)));
    }

    @Test
    void deleteStillPendingMemberAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr10", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr10", memberId));
        var memberId2 = Step.and("client member seeded", () -> seeder.seedMember(session, "mr10m2", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);

        var regId = Step.when("client registration posted (not yet approved)", () -> {
            var req = new ClientRegistrationRequestDto().clientId(memberId2);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
        });

        Step.then("request is WAITING", () ->
                client.getRequest(regId).statusCode(200).body("status", equalTo("WAITING")));

        Step.when("client deletion posted while registration is pending", () -> {
            var req = new ClientDeletionRequestDto();
            req.setType(ManagementRequestTypeDto.CLIENT_DELETION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            req.setClientId(memberId2);
            client.addManagementRequest(req).statusCode(202);
        });

        Step.then("server has no clients", () ->
                session.given()
                        .get("/security-servers/{id}/clients", serverId)
                        .then()
                        .statusCode(200)
                        .body("$", hasSize(0)));

        Step.then("registration request is REVOKED", () ->
                client.findRequests(Map.of(
                        "serverId", serverId,
                        "types", "CLIENT_REGISTRATION_REQUEST",
                        "status", "REVOKED"))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(1)));

        Step.then("deletion request exists", () ->
                client.findRequests(Map.of(
                        "serverId", serverId,
                        "types", "CLIENT_DELETION_REQUEST"))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(1)));
    }

    @Test
    void autoApproveRegistrationOfMemberAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var roSession = Step.given("registration officer session opened", seeder::newRegistrationOfficerSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr11", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr11", memberId));
        var memberId2 = Step.and("client member seeded", () -> seeder.seedMember(session, "mr11m2", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);
        var roClient = new ManagementRequestsAdminClient(roSession);

        var id1 = Step.when("CENTER client registration posted", () -> {
            var req = new ClientRegistrationRequestDto().clientId(memberId2);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.CENTER);
            req.setSecurityServerId(serverId);
            return roClient.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
        });

        Step.then("CENTER request is WAITING", () ->
                roClient.getRequest(id1).statusCode(200).body("status", equalTo("WAITING")));

        var id2 = Step.when("SECURITY_SERVER client registration posted", () -> {
            var req = new ClientRegistrationRequestDto().clientId(memberId2);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(201).extract().jsonPath().getInt("id");
        });

        Step.then("SECURITY_SERVER request is auto-approved", () ->
                roClient.getRequest(id2).statusCode(200).body("status", equalTo("APPROVED")));

        Step.then("server clients contains mr11m2", () ->
                session.given()
                        .get("/security-servers/{id}/clients", serverId)
                        .then()
                        .statusCode(200)
                        .body("find { it.client_id.member_code == 'membermr11m2' }", notNullValue()));
    }

    @Test
    void dontAutoApproveNonExistingMemberAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr12", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr12", memberId));
        var client = new ManagementRequestsAdminClient(session);
        var nonExistingMemberId = INSTANCE_IDENTIFIER + ":" + MEMBER_CLASS + ":membermr12m2";

        var id = Step.when("SECURITY_SERVER client registration posted for non-existing member", () -> {
            var req = new ClientRegistrationRequestDto().clientId(nonExistingMemberId);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
        });

        Step.then("request is WAITING", () ->
                client.getRequest(id).statusCode(200).body("status", equalTo("WAITING")));

        Step.then("member still does not exist", () ->
                session.given()
                        .get("/members/{id}", nonExistingMemberId)
                        .then()
                        .statusCode(404));
    }

    @Test
    void declineRegistrationOfMemberAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr13", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr13", memberId));
        var memberId2 = Step.and("client member seeded", () -> seeder.seedMember(session, "mr13m2", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);

        var id = Step.when("client registration posted", () -> {
            var req = new ClientRegistrationRequestDto().clientId(memberId2);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
        });

        Step.then("request is WAITING", () ->
                client.getRequest(id).statusCode(200).body("status", equalTo("WAITING")));

        Step.when("request declined", () ->
                client.revokeRequest(id).statusCode(200));

        Step.then("request is DECLINED", () ->
                client.getRequest(id).statusCode(200).body("status", equalTo("DECLINED")));
    }

    @Test
    void registerNewSubsystemAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr14", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr14", memberId));
        var memberId2 = Step.and("client member seeded", () -> seeder.seedMember(session, "mr14m2", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);
        var subsystemId = memberId2 + ":submr14";

        var regId = Step.when("subsystem client registration posted", () -> {
            var req = new ClientRegistrationRequestDto().clientId(subsystemId);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
        });

        Step.when("approved", () -> client.approveRequest(regId).statusCode(200));

        Step.then("server clients contains subsystem", () ->
                session.given()
                        .get("/security-servers/{id}/clients", serverId)
                        .then()
                        .statusCode(200)
                        .body("find { it.client_id.subsystem_code == 'submr14' }", notNullValue()));

        Step.then("subsystem exists under member", () ->
                session.given()
                        .get("/members/{id}/subsystems", memberId2)
                        .then()
                        .statusCode(200)
                        .body("find { it.subsystem_id.subsystem_code == 'submr14' }", notNullValue()));
    }

    @Test
    void registerNewSubsystemWithNameAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr15", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr15", memberId));
        var client = new ManagementRequestsAdminClient(session);
        var subsystemId = memberId + ":submr15";

        var regId = Step.when("subsystem registration with name posted", () -> {
            var req = new ClientRegistrationRequestDto()
                    .clientId(subsystemId)
                    .subsystemName("Subsystem mr15");
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
        });

        Step.when("approved", () -> client.approveRequest(regId).statusCode(200));

        Step.then("server clients contains subsystem", () ->
                session.given()
                        .get("/security-servers/{id}/clients", serverId)
                        .then()
                        .statusCode(200)
                        .body("find { it.client_id.subsystem_code == 'submr15' }", notNullValue()));

        Step.then("subsystem has the given name", () ->
                session.given()
                        .get("/members/{id}/subsystems", memberId)
                        .then()
                        .statusCode(200)
                        .body("find { it.subsystem_id.subsystem_code == 'submr15' }.subsystem_name",
                                equalTo("Subsystem mr15")));
    }

    @Test
    void registerSubsystemOfNonExistingMemberAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr16", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr16", memberId));
        var client = new ManagementRequestsAdminClient(session);
        var nonExistingMemberId = INSTANCE_IDENTIFIER + ":" + MEMBER_CLASS + ":membermr16m2";
        var subsystemId = nonExistingMemberId + ":submr16";

        var regId = Step.when("subsystem registration posted for non-existing member", () -> {
            var req = new ClientRegistrationRequestDto().clientId(subsystemId);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
        });

        Step.when("approved", () -> client.approveRequest(regId).statusCode(200));

        Step.then("server clients contains subsystem", () ->
                session.given()
                        .get("/security-servers/{id}/clients", serverId)
                        .then()
                        .statusCode(200)
                        .body("find { it.client_id.subsystem_code == 'submr16' }", notNullValue()));

        Step.then("member was created", () ->
                session.given()
                        .get("/members/{id}", nonExistingMemberId)
                        .then()
                        .statusCode(200)
                        .body("member_name", equalTo("membermr16m2")));

        Step.then("subsystem exists under created member", () ->
                session.given()
                        .get("/members/{id}/subsystems", nonExistingMemberId)
                        .then()
                        .statusCode(200)
                        .body("find { it.subsystem_id.subsystem_code == 'submr16' }", notNullValue()));
    }

    @Test
    void registerExistingSubsystemAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr17", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr17", memberId));
        var memberId2 = Step.and("client member seeded", () -> seeder.seedMember(session, "mr17m2", MEMBER_CLASS));
        Step.and("subsystem seeded", () -> seeder.seedSubsystem(session, "mr17sub", memberId2));
        var client = new ManagementRequestsAdminClient(session);
        var subsystemId = memberId2 + ":subsysmr17sub";

        var regId = Step.when("existing subsystem registration posted", () -> {
            var req = new ClientRegistrationRequestDto().clientId(subsystemId);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
        });

        Step.when("approved", () -> client.approveRequest(regId).statusCode(200));

        Step.then("server clients contains subsystem", () ->
                session.given()
                        .get("/security-servers/{id}/clients", serverId)
                        .then()
                        .statusCode(200)
                        .body("find { it.client_id.subsystem_code == 'subsysmr17sub' }", notNullValue()));

        Step.then("subsystem still exists under member", () ->
                session.given()
                        .get("/members/{id}/subsystems", memberId2)
                        .then()
                        .statusCode(200)
                        .body("find { it.subsystem_id.subsystem_code == 'subsysmr17sub' }", notNullValue()));
    }

    @Test
    void deleteSubsystemAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr18", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr18", memberId));
        var memberId2 = Step.and("client member seeded", () -> seeder.seedMember(session, "mr18m2", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);
        var subsystemId = memberId2 + ":submr18";

        Step.and("subsystem registered as client and approved", () -> {
            var req = new ClientRegistrationRequestDto().clientId(subsystemId);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            var regId = client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
            client.approveRequest(regId).statusCode(200);
        });

        Step.when("client deletion posted for subsystem", () -> {
            var req = new ClientDeletionRequestDto();
            req.setType(ManagementRequestTypeDto.CLIENT_DELETION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            req.setClientId(subsystemId);
            client.addManagementRequest(req).statusCode(202);
        });

        Step.then("server has no clients", () ->
                session.given()
                        .get("/security-servers/{id}/clients", serverId)
                        .then()
                        .statusCode(200)
                        .body("$", hasSize(0)));

        Step.then("subsystem still exists under member", () ->
                session.given()
                        .get("/members/{id}/subsystems", memberId2)
                        .then()
                        .statusCode(200)
                        .body("find { it.subsystem_id.subsystem_code == 'submr18' }", notNullValue()));
    }

    @Test
    void autoApproveRegistrationOfSubsystemAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var roSession = Step.given("registration officer session opened", seeder::newRegistrationOfficerSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr19", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr19", memberId));
        var memberId2 = Step.and("client member seeded", () -> seeder.seedMember(session, "mr19m2", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);
        var roClient = new ManagementRequestsAdminClient(roSession);
        var subsystemId = memberId2 + ":submr19";

        var id1 = Step.when("CENTER subsystem registration posted", () -> {
            var req = new ClientRegistrationRequestDto().clientId(subsystemId);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.CENTER);
            req.setSecurityServerId(serverId);
            return roClient.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
        });

        Step.then("CENTER request is WAITING", () ->
                roClient.getRequest(id1).statusCode(200).body("status", equalTo("WAITING")));

        var id2 = Step.when("SECURITY_SERVER subsystem registration posted", () -> {
            var req = new ClientRegistrationRequestDto().clientId(subsystemId);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(201).extract().jsonPath().getInt("id");
        });

        Step.then("SECURITY_SERVER request is auto-approved", () ->
                roClient.getRequest(id2).statusCode(200).body("status", equalTo("APPROVED")));

        Step.then("server clients contains member", () ->
                session.given()
                        .get("/security-servers/{id}/clients", serverId)
                        .then()
                        .statusCode(200)
                        .body("find { it.client_id.member_code == 'membermr19m2' }", notNullValue()));

        Step.then("subsystem exists under member", () ->
                session.given()
                        .get("/members/{id}/subsystems", memberId2)
                        .then()
                        .statusCode(200)
                        .body("find { it.subsystem_id.subsystem_code == 'submr19' }", notNullValue()));
    }

    // "Dont't auto approve registration of subsystem of non existing member as security server client"
    @Test
    void dontAutoApproveSubsystemOfNonExistingMemberAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr20", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr20", memberId));
        var client = new ManagementRequestsAdminClient(session);
        var nonExistingSubsystemId = INSTANCE_IDENTIFIER + ":" + MEMBER_CLASS + ":membermr20m2:submr20";

        var id = Step.when("SECURITY_SERVER subsystem registration posted for non-existing member", () -> {
            var req = new ClientRegistrationRequestDto().clientId(nonExistingSubsystemId);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
        });

        Step.then("request is WAITING", () ->
                client.getRequest(id).statusCode(200).body("status", equalTo("WAITING")));

        Step.then("member still does not exist", () ->
                session.given()
                        .get("/members/{id}", INSTANCE_IDENTIFIER + ":" + MEMBER_CLASS + ":membermr20m2")
                        .then()
                        .statusCode(404));
    }

    @Test
    void declineRegistrationOfSubsystemAsSSClient(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr21", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr21", memberId));
        var memberId2 = Step.and("client member seeded", () -> seeder.seedMember(session, "mr21m2", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);
        var subsystemId = memberId2 + ":submr21";

        var id = Step.when("subsystem registration posted", () -> {
            var req = new ClientRegistrationRequestDto().clientId(subsystemId);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            return client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
        });

        Step.then("request is WAITING", () ->
                client.getRequest(id).statusCode(200).body("status", equalTo("WAITING")));

        Step.when("request declined", () ->
                client.revokeRequest(id).statusCode(200));

        Step.then("request is DECLINED", () ->
                client.getRequest(id).statusCode(200).body("status", equalTo("DECLINED")));

        Step.then("subsystem does not exist under member", () ->
                session.given()
                        .get("/members/{id}/subsystems", memberId2)
                        .then()
                        .statusCode(200)
                        .body("find { it.subsystem_id.subsystem_code == 'submr21' }", nullValue()));
    }

    @Test
    void changeSecurityServerOwner(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr22", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr22", memberId));
        var memberId2 = Step.and("new owner member seeded", () -> seeder.seedMember(session, "mr22m2", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);
        var members = new MembersAdminClient(session);

        Step.then("original owner is in security-server-owners", () ->
                members.getGlobalGroups(memberId)
                        .statusCode(200)
                        .body("group_code", hasItem(OWNERS_GROUP)));

        Step.then("new owner is NOT in security-server-owners", () ->
                members.getGlobalGroups(memberId2)
                        .statusCode(200)
                        .body("group_code", not(hasItem(OWNERS_GROUP))));

        Step.and("new owner registered as client", () -> {
            var req = new ClientRegistrationRequestDto().clientId(memberId2);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            var regId = client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
            client.approveRequest(regId).statusCode(200);
        });

        Step.then("server clients contain mr22m2", () ->
                session.given()
                        .get("/security-servers/{id}/clients", serverId)
                        .then()
                        .statusCode(200)
                        .body("find { it.client_id.member_code == 'membermr22m2' }", notNullValue()));

        var ownerChangeId = Step.when("owner change request posted", () -> {
            var req = new OwnerChangeRequestDto();
            req.setType(ManagementRequestTypeDto.OWNER_CHANGE_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            req.setClientId(memberId2);
            return client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
        });

        Step.when("owner change approved", () ->
                client.approveRequest(ownerChangeId).statusCode(200));

        Step.then("owner change request is APPROVED", () ->
                client.getRequest(ownerChangeId).statusCode(200).body("status", equalTo("APPROVED")));

        var newServerId = memberId2 + ":ssmr22";

        Step.then("new server has original owner as client", () ->
                session.given()
                        .get("/security-servers/{id}/clients", newServerId)
                        .then()
                        .statusCode(200)
                        .body("find { it.client_id.member_code == 'membermr22' }", notNullValue()));

        Step.then("original owner is no longer in security-server-owners", () ->
                members.getGlobalGroups(memberId)
                        .statusCode(200)
                        .body("group_code", not(hasItem(OWNERS_GROUP))));

        Step.then("new owner is now in security-server-owners", () ->
                members.getGlobalGroups(memberId2)
                        .statusCode(200)
                        .body("group_code", hasItem(OWNERS_GROUP)));

        Step.then("new owner has the server in owned-servers", () ->
                members.getOwnedServers(memberId2)
                        .statusCode(200)
                        .body("server_id.encoded_id", hasItem(newServerId)));
    }

    @Test
    void disablingEnablingMaintenanceMode(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr23", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr23", memberId));
        var client = new ManagementRequestsAdminClient(session);

        Step.then("server is not in maintenance mode", () ->
                session.given()
                        .get("/security-servers/{id}", serverId)
                        .then()
                        .statusCode(200)
                        .body("in_maintenance_mode", equalTo(false)));

        Step.when("maintenance mode enable request posted", () -> {
            var req = new MaintenanceModeEnableRequestDto()
                    .type(ManagementRequestTypeDto.MAINTENANCE_MODE_ENABLE_REQUEST)
                    .origin(ManagementRequestOriginDto.SECURITY_SERVER)
                    .securityServerId(serverId);
            client.addManagementRequest(req).statusCode(202);
        });

        Step.then("server is in maintenance mode with no message", () ->
                session.given()
                        .get("/security-servers/{id}", serverId)
                        .then()
                        .statusCode(200)
                        .body("in_maintenance_mode", equalTo(true))
                        .body("maintenance_mode_message", nullValue()));

        Step.when("maintenance mode disable request posted", () -> {
            var req = new MaintenanceModeDisableRequestDto()
                    .type(ManagementRequestTypeDto.MAINTENANCE_MODE_DISABLE_REQUEST)
                    .origin(ManagementRequestOriginDto.SECURITY_SERVER)
                    .securityServerId(serverId);
            client.addManagementRequest(req).statusCode(202);
        });

        Step.then("server is not in maintenance mode", () ->
                session.given()
                        .get("/security-servers/{id}", serverId)
                        .then()
                        .statusCode(200)
                        .body("in_maintenance_mode", equalTo(false)));

        Step.when("maintenance mode enable request posted with message", () -> {
            var req = new MaintenanceModeEnableRequestDto()
                    .type(ManagementRequestTypeDto.MAINTENANCE_MODE_ENABLE_REQUEST)
                    .origin(ManagementRequestOriginDto.SECURITY_SERVER)
                    .securityServerId(serverId)
                    .message("Will be back up soon");
            client.addManagementRequest(req).statusCode(202);
        });

        Step.then("server is in maintenance mode with message", () ->
                session.given()
                        .get("/security-servers/{id}", serverId)
                        .then()
                        .statusCode(200)
                        .body("in_maintenance_mode", equalTo(true))
                        .body("maintenance_mode_message", equalTo("Will be back up soon")));
    }

    @Test
    void enablingMaintenanceModeForMgmtServiceSSFails(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr24", MEMBER_CLASS));
        Step.and("management subsystem added", () ->
                addSubsystemDirect(session, "membermr24", "submgmtmr24"));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr24", memberId));
        var client = new ManagementRequestsAdminClient(session);
        var subsystemId = memberId + ":submgmtmr24";

        Step.and("management service provider configured", () -> {
            session.given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("service_provider_id", subsystemId))
                    .patch("/management-services-configuration")
                    .then()
                    .statusCode(200);
            session.given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("security_server_id", serverId))
                    .post("/management-services-configuration/register-provider")
                    .then()
                    .statusCode(200);
        });

        Step.then("server is not in maintenance mode", () ->
                session.given()
                        .get("/security-servers/{id}", serverId)
                        .then()
                        .statusCode(200)
                        .body("in_maintenance_mode", equalTo(false)));

        Step.when("maintenance mode enable request posted for mgmt SS", () -> {
            var req = new MaintenanceModeEnableRequestDto()
                    .type(ManagementRequestTypeDto.MAINTENANCE_MODE_ENABLE_REQUEST)
                    .origin(ManagementRequestOriginDto.SECURITY_SERVER)
                    .securityServerId(serverId);
            client.addManagementRequest(req)
                    .statusCode(409)
                    .body("error.code", equalTo("mr_forbidden_enable_maintenance_mode_for_management_service"));
        });
    }

    @Test
    void viewManagementRequestDetails(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "mr25", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);
        var serverId = memberId + ":ssmr25";
        var cert = seeder.generateCertForServer(serverId);

        var id = Step.when("auth cert registration posted", () ->
                postAuthCertReg(client, cert, serverId, "security-server-address-ssmr25",
                        ManagementRequestOriginDto.SECURITY_SERVER));

        Step.then("request details are correct", () ->
                client.getRequest(id)
                        .statusCode(200)
                        .body("address", equalTo("security-server-address-ssmr25"))
                        .body("security_server_id.instance_id", equalTo(INSTANCE_IDENTIFIER))
                        .body("security_server_id.member_class", equalTo(MEMBER_CLASS))
                        .body("security_server_id.member_code", equalTo("membermr25"))
                        .body("type", equalTo("AUTH_CERT_REGISTRATION_REQUEST"))
                        .body("origin", equalTo("SECURITY_SERVER"))
                        .body("status", equalTo("WAITING")));
    }

    @Test
    void disablingEnablingSubsystem(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr26", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr26", memberId));
        var client = new ManagementRequestsAdminClient(session);
        var subsystemId = memberId + ":submr26";

        Step.and("subsystem registered as client and approved", () -> {
            var req = new ClientRegistrationRequestDto().clientId(subsystemId);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            var regId = client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
            client.approveRequest(regId).statusCode(200);
        });

        Step.when("client disable request posted", () -> {
            var req = new ClientDisableRequestDto();
            req.setType(ManagementRequestTypeDto.CLIENT_DISABLE_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            req.setClientId(subsystemId);
            client.addManagementRequest(req).statusCode(202);
        });

        Step.then("subsystem has DISABLED status on this server", () ->
                session.given()
                        .get("/members/{id}/subsystems", memberId)
                        .then()
                        .statusCode(200)
                        .body("find { it.subsystem_id.subsystem_code == 'submr26' }.used_security_servers"
                                + ".find { it.server_code == 'ssmr26' }.status",
                                equalTo("DISABLED")));

        Step.when("client enable request posted", () -> {
            var req = new ClientEnableRequestDto();
            req.setType(ManagementRequestTypeDto.CLIENT_ENABLE_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            req.setClientId(subsystemId);
            client.addManagementRequest(req).statusCode(202);
        });

        Step.then("subsystem has APPROVED status on this server", () ->
                session.given()
                        .get("/members/{id}/subsystems", memberId)
                        .then()
                        .statusCode(200)
                        .body("find { it.subsystem_id.subsystem_code == 'submr26' }.used_security_servers"
                                + ".find { it.server_code == 'ssmr26' }.status",
                                equalTo("APPROVED")));
    }

    @Test
    void disablingSubsystemThatIsMgmtProviderFails(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr27", MEMBER_CLASS));
        Step.and("management subsystem added", () ->
                addSubsystemDirect(session, "membermr27", "submgmtmr27"));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr27", memberId));
        var client = new ManagementRequestsAdminClient(session);
        var subsystemId = memberId + ":submgmtmr27";

        Step.and("management service provider configured", () -> {
            session.given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("service_provider_id", subsystemId))
                    .patch("/management-services-configuration")
                    .then()
                    .statusCode(200);
            session.given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("security_server_id", serverId))
                    .post("/management-services-configuration/register-provider")
                    .then()
                    .statusCode(200);
        });

        Step.when("client disable request posted for mgmt subsystem", () -> {
            var req = new ClientDisableRequestDto();
            req.setType(ManagementRequestTypeDto.CLIENT_DISABLE_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            req.setClientId(subsystemId);
            client.addManagementRequest(req)
                    .statusCode(409)
                    .body("error.code", equalTo("mr_forbidden_disable_management_service_client"));
        });
    }

    @Test
    void renamingSubsystem(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "mr28", MEMBER_CLASS));
        var serverId = Step.and("security server registered", () ->
                seeder.seedSecurityServer(session, "mr28", memberId));
        var client = new ManagementRequestsAdminClient(session);
        var subsystemId = memberId + ":submr28";

        Step.and("subsystem registered as client and approved", () -> {
            var req = new ClientRegistrationRequestDto().clientId(subsystemId);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            var regId = client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
            client.approveRequest(regId).statusCode(200);
        });

        Step.then("subsystem has no name initially", () ->
                session.given()
                        .get("/members/{id}/subsystems", memberId)
                        .then()
                        .statusCode(200)
                        .body("find { it.subsystem_id.subsystem_code == 'submr28' }.subsystem_name", nullValue()));

        Step.when("rename request posted", () -> {
            var req = new ClientRenameRequestDto();
            req.setType(ManagementRequestTypeDto.CLIENT_RENAME_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            req.setClientId(subsystemId);
            req.setSubsystemName("Subsystem mr28");
            client.addManagementRequest(req).statusCode(202);
        });

        Step.then("subsystem has new name", () ->
                session.given()
                        .get("/members/{id}/subsystems", memberId)
                        .then()
                        .statusCode(200)
                        .body("find { it.subsystem_id.subsystem_code == 'submr28' }.subsystem_name",
                                equalTo("Subsystem mr28")));

        Step.when("rename request posted again with different name", () -> {
            var req = new ClientRenameRequestDto();
            req.setType(ManagementRequestTypeDto.CLIENT_RENAME_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            req.setClientId(subsystemId);
            req.setSubsystemName("Just Subsystem");
            client.addManagementRequest(req).statusCode(202);
        });

        Step.then("subsystem has updated name", () ->
                session.given()
                        .get("/members/{id}/subsystems", memberId)
                        .then()
                        .statusCode(200)
                        .body("find { it.subsystem_id.subsystem_code == 'submr28' }.subsystem_name",
                                equalTo("Just Subsystem")));
    }

    @Test
    void managementRequestsList(CsBaselineSeeder seeder) throws Exception {
        var session = Step.given("admin session opened", seeder::newSession);
        var roSession = Step.given("registration officer session opened", seeder::newRegistrationOfficerSession);
        var memberId1 = Step.given("member 1 seeded", () -> seeder.seedMember(session, "mr29m1", MEMBER_CLASS));
        var client = new ManagementRequestsAdminClient(session);
        var roClient = new ManagementRequestsAdminClient(roSession);

        var ss1Id = memberId1 + ":ssmr29ss1";
        var ss2Id = memberId1 + ":ssmr29ss2";
        var ss3Id = memberId1 + ":ssmr29ss3";
        var ss4Id = memberId1 + ":ssmr29ss4";
        var ss5Id = memberId1 + ":ssmr29ss5";
        var cert1 = seeder.generateCertForServer(ss1Id);
        var cert2 = seeder.generateCertForServer(ss2Id);
        var cert3 = seeder.generateCertForServer(ss3Id);
        var cert4 = seeder.generateCertForServer(ss4Id);
        var cert5 = seeder.generateCertForServer(ss5Id);

        Step.and("SS1 registered and approved", () -> {
            var regId = postAuthCertReg(client, cert1, ss1Id, "ss-addr-mr29ss1",
                    ManagementRequestOriginDto.SECURITY_SERVER);
            client.approveRequest(regId).statusCode(200);
        });

        Step.and("SS2 registered and approved", () -> {
            var regId = postAuthCertReg(client, cert2, ss2Id, "ss-addr-mr29ss2",
                    ManagementRequestOriginDto.SECURITY_SERVER);
            client.approveRequest(regId).statusCode(200);
        });

        Step.and("SS3 registered and approved", () -> {
            var regId = postAuthCertReg(client, cert3, ss3Id, "ss-addr-mr29ss3",
                    ManagementRequestOriginDto.SECURITY_SERVER);
            client.approveRequest(regId).statusCode(200);
        });

        Step.and("SS4 registered and declined", () -> {
            var regId = postAuthCertReg(client, cert4, ss4Id, "ss-addr-mr29ss4",
                    ManagementRequestOriginDto.SECURITY_SERVER);
            client.revokeRequest(regId).statusCode(200);
        });

        Step.and("SS5 registered and left pending", () ->
                postAuthCertReg(client, cert5, ss5Id, "ss-addr-mr29ss5",
                        ManagementRequestOriginDto.SECURITY_SERVER));

        var memberId2 = Step.and("member 2 seeded", () -> seeder.seedMember(session, "mr29m2", MEMBER_CLASS));
        var memberId3 = Step.and("member 3 seeded", () -> seeder.seedMember(session, "mr29m3", MEMBER_CLASS));

        Step.and("CLIENT_REG for mr29m2 on SS1 from CENTER, approved", () -> {
            var req = new ClientRegistrationRequestDto().clientId(memberId2);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.CENTER);
            req.setSecurityServerId(ss1Id);
            var regId = roClient.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
            client.approveRequest(regId).statusCode(200);
        });

        Step.and("CLIENT_REG for mr29m2 on SS3 from SECURITY_SERVER, approved", () -> {
            var req = new ClientRegistrationRequestDto().clientId(memberId2);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(ss3Id);
            var regId = client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
            client.approveRequest(regId).statusCode(200);
        });

        Step.and("OWNER_CHANGE for SS3 to mr29m2, approved", () -> {
            var req = new OwnerChangeRequestDto();
            req.setType(ManagementRequestTypeDto.OWNER_CHANGE_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(ss3Id);
            req.setClientId(memberId2);
            var changeId = client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
            client.approveRequest(changeId).statusCode(200);
        });

        Step.and("AUTH_CERT_DELETION for SS2", () -> {
            var req = new AuthenticationCertificateDeletionRequestDto()
                    .authenticationCertificate(cert2);
            req.setType(ManagementRequestTypeDto.AUTH_CERT_DELETION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(ss2Id);
            client.addManagementRequest(req).statusCode(202);
        });

        Step.and("CLIENT_REG for mr29m3 on SS1 from SECURITY_SERVER, approved", () -> {
            var req = new ClientRegistrationRequestDto().clientId(memberId3);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(ss1Id);
            var regId = client.addManagementRequest(req)
                    .statusCode(202).extract().jsonPath().getInt("id");
            client.approveRequest(regId).statusCode(200);
        });

        Step.and("CLIENT_DEL for mr29m3 on SS1", () -> {
            var req = new ClientDeletionRequestDto();
            req.setType(ManagementRequestTypeDto.CLIENT_DELETION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(ss1Id);
            req.setClientId(memberId3);
            client.addManagementRequest(req).statusCode(202);
        });

        record ListCase(Map<String, Object> params, int itemsInPage, int total) {
        }

        var serverScopedCases = List.of(
                new ListCase(Map.of("serverId", ss1Id), 4, 4),
                new ListCase(Map.of("serverId", ss1Id, "status", "APPROVED"), 3, 3),
                new ListCase(Map.of("serverId", ss4Id), 1, 1),
                new ListCase(Map.of("serverId", ss4Id, "status", "DECLINED"), 1, 1),
                new ListCase(Map.of("serverId", ss5Id), 1, 1),
                new ListCase(Map.of("serverId", ss5Id, "status", "WAITING"), 1, 1),
                new ListCase(Map.of("serverId", ss1Id, "origin", "CENTER"), 1, 1),
                new ListCase(Map.of("serverId", ss1Id, "origin", "SECURITY_SERVER"), 3, 3),
                new ListCase(Map.of("serverId", ss1Id, "types", "AUTH_CERT_REGISTRATION_REQUEST"), 1, 1),
                new ListCase(Map.of("serverId", ss1Id, "types", "CLIENT_REGISTRATION_REQUEST"), 2, 2),
                new ListCase(Map.of("serverId", ss1Id, "types", "CLIENT_DELETION_REQUEST"), 1, 1),
                new ListCase(mapOf("serverId", ss1Id, "limit", 2, "offset", 0), 2, 4),
                new ListCase(mapOf("serverId", ss1Id, "limit", 2, "offset", 1), 2, 4),
                new ListCase(mapOf("serverId", ss1Id, "sort", "id", "desc", true), 4, 4),
                new ListCase(mapOf("serverId", ss1Id, "sort", "type", "desc", false), 4, 4)
        );

        Step.then("server-scoped filter cases verified", () -> {
            for (var c : serverScopedCases) {
                client.findRequests(c.params())
                        .statusCode(200)
                        .body("items", hasSize(c.itemsInPage()))
                        .body("paging_metadata.total_items", equalTo(c.total()));
            }
        });

        // All 11 test-specific requests are matched by query=mr29 (substring in server_code "ssmr29ss*").
        // Counts: AUTH_CERT_REG×5, CLIENT_REG×3, OWNER_CHANGE×1, AUTH_CERT_DEL×1, CLIENT_DEL×1.
        // APPROVED=7 (SS1/2/3 auth-cert, m2-SS1 client, m2-SS3 client, SS3 owner-change, m3-SS1 client).
        // DECLINED=1 (SS4 auth-cert). WAITING=3 (SS5 auth-cert, SS2 auth-cert-del, m3-SS1 client-del).
        // CENTER=1 (m2-SS1 client-reg). SECURITY_SERVER=10 (all others).
        var globalCases = List.of(
                new ListCase(Map.of("query", "mr29"), 11, 11),
                new ListCase(Map.of("query", "mr29ss3"), 3, 3),
                new ListCase(mapOf("query", "mr29ss5", "status", "WAITING"), 1, 1),
                new ListCase(Map.of("status", "DECLINED", "query", "mr29"), 1, 1),
                new ListCase(Map.of("status", "APPROVED", "query", "mr29"), 7, 7),
                new ListCase(mapOf("query", "mr29ss3", "status", "APPROVED"), 3, 3),
                new ListCase(Map.of("origin", "CENTER", "query", "mr29"), 1, 1),
                new ListCase(Map.of("origin", "SECURITY_SERVER", "query", "mr29"), 10, 10),
                new ListCase(mapOf("query", "mr29ss3", "status", "APPROVED", "origin", "SECURITY_SERVER"), 3, 3),
                new ListCase(Map.of("types", "AUTH_CERT_REGISTRATION_REQUEST", "query", "mr29"), 5, 5),
                new ListCase(Map.of("types", "CLIENT_REGISTRATION_REQUEST", "query", "mr29"), 3, 3),
                new ListCase(Map.of("types", "OWNER_CHANGE_REQUEST", "query", "mr29"), 1, 1),
                new ListCase(Map.of("types", "CLIENT_DELETION_REQUEST", "query", "mr29"), 1, 1),
                new ListCase(Map.of("types", "AUTH_CERT_DELETION_REQUEST", "query", "mr29"), 1, 1),
                new ListCase(mapOf("status", "APPROVED", "types", "AUTH_CERT_REGISTRATION_REQUEST,CLIENT_DELETION_REQUEST",
                        "query", "mr29"), 3, 3),
                new ListCase(mapOf("query", "mr29ss1", "types", "AUTH_CERT_REGISTRATION_REQUEST,CLIENT_DELETION_REQUEST"), 2, 2),
                new ListCase(mapOf("origin", "CENTER", "types", "OWNER_CHANGE_REQUEST", "query", "mr29"), 0, 0)
        );

        Step.then("global filter cases verified", () -> {
            for (var c : globalCases) {
                client.findRequests(c.params())
                        .statusCode(200)
                        .body("items", hasSize(c.itemsInPage()))
                        .body("paging_metadata.total_items", equalTo(c.total()));
            }
        });

        Step.then("list is sorted by id descending", () -> {
            var ids = client.findRequests(mapOf("query", "mr29", "sort", "id", "desc", true))
                    .statusCode(200)
                    .extract().jsonPath().getList("items.id", Integer.class);
            assertThat(ids).hasSize(11).isSortedAccordingTo(Comparator.reverseOrder());
        });

        Step.then("list is sorted by created_at ascending", () -> {
            var values = client.findRequests(mapOf("query", "mr29", "sort", "created_at", "desc", false))
                    .statusCode(200)
                    .extract().jsonPath().getList("items.created_at", String.class);
            assertThat(values).hasSize(11);
            assertThat(values.stream().filter(java.util.Objects::nonNull).toList())
                    .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        });

        Step.then("list is sorted by type descending", () -> {
            var values = client.findRequests(mapOf("query", "mr29", "sort", "type", "desc", true))
                    .statusCode(200)
                    .extract().jsonPath().getList("items.type", String.class);
            assertThat(values).hasSize(11);
            assertThat(values.stream().filter(java.util.Objects::nonNull).toList())
                    .isSortedAccordingTo(Comparator.reverseOrder());
        });

        Step.then("list is sorted by security_server_owner ascending", () -> {
            var values = client.findRequests(mapOf("query", "mr29", "sort", "security_server_owner", "desc", false))
                    .statusCode(200)
                    .extract().jsonPath().getList("items.security_server_owner", String.class);
            assertThat(values).hasSize(11);
            assertThat(values.stream().filter(java.util.Objects::nonNull).toList())
                    .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        });

        Step.then("list is sorted by security_server_id descending", () -> {
            var values = client.findRequests(mapOf("query", "mr29", "sort", "security_server_id", "desc", true))
                    .statusCode(200)
                    .extract().jsonPath().getList("items.security_server_id.encoded_id", String.class);
            assertThat(values).hasSize(11);
            assertThat(values.stream().filter(java.util.Objects::nonNull).toList())
                    .isSortedAccordingTo(Comparator.reverseOrder());
        });

        Step.then("list is sorted by status ascending", () -> {
            var values = client.findRequests(mapOf("query", "mr29", "sort", "status", "desc", false))
                    .statusCode(200)
                    .extract().jsonPath().getList("items.status", String.class);
            assertThat(values).hasSize(11);
            assertThat(values.stream().filter(java.util.Objects::nonNull).toList())
                    .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        });
    }

    private int postAuthCertReg(ManagementRequestsAdminClient client, byte[] cert, String serverId,
                                String address, ManagementRequestOriginDto origin) {
        var req = new AuthenticationCertificateRegistrationRequestDto()
                .serverAddress(address)
                .authenticationCertificate(cert);
        req.setType(ManagementRequestTypeDto.AUTH_CERT_REGISTRATION_REQUEST);
        req.setOrigin(origin);
        req.setSecurityServerId(serverId);
        return client.addManagementRequest(req)
                .statusCode(202)
                .extract().jsonPath().getInt("id");
    }

    private void addSubsystemDirect(org.niis.xroad.cs.test.api.admin.AdminApiSession session,
                                    String memberCode, String subsystemCode) {
        var idDto = new NewSubsystemIdDto().subsystemCode(subsystemCode);
        idDto.setMemberClass(MEMBER_CLASS);
        idDto.setMemberCode(memberCode);
        session.given()
                .contentType(ContentType.JSON)
                .body(new SubsystemAddDto().subsystemId(idDto))
                .post("/subsystems")
                .then()
                .statusCode(201);
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        var map = new HashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
