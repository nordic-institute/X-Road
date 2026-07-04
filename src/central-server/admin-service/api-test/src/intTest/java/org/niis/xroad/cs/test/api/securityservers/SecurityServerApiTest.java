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
package org.niis.xroad.cs.test.api.securityservers;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.niis.xroad.cs.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.ManagementRequestsAdminClient;
import org.niis.xroad.cs.test.api.admin.SecurityServersAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.niis.xroad.cs.test.api.CsBaselineSeeder.BASELINE_MEMBER_CLASS;
import static org.niis.xroad.cs.test.api.CsBaselineSeeder.INSTANCE_IDENTIFIER;

@SuppressWarnings("checkstyle:magicnumber")
class SecurityServerApiTest extends CsApiTest {

    @Test
    void getListOfSecurityServers(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "0801", BASELINE_MEMBER_CLASS));
        var serverId = Step.and("security server seeded", () -> seeder.seedSecurityServer(session, "0801", memberId));
        var client = new SecurityServersAdminClient(session);

        Step.when("server list is queried with q filter", () ->
                client.findSecurityServers(Map.of("q", "ss0801"))
                        .statusCode(200)
                        .body("items.server_id.encoded_id", org.hamcrest.Matchers.hasItem(serverId)));

        Step.and("sorting by unknown field returns 400", () ->
                client.findSecurityServers(Map.of("sort", "nonExistentField"))
                        .statusCode(400));
    }

    @ParameterizedTest
    @CsvSource({
            "server_id.server_code,  false, items.server_id.server_code,  08sa",
            "server_id.server_code,  true,  items.server_id.server_code,  08sb",
            "server_id.member_code,  false, items.server_id.member_code,  08sc",
            "server_id.member_code,  true,  items.server_id.member_code,  08sd",
            "server_id.member_class, false, items.server_id.member_class, 08se",
            "server_id.member_class, true,  items.server_id.member_class, 08sf",
            "owner_name,             false, items.owner_name,             08sg",
            "owner_name,             true,  items.owner_name,             08sh"
    })
    void securityServersListSorting(String sortField, boolean desc, String jsonPathField, String ns,
            CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var mc1 = Step.given("member class 1 seeded", () -> seeder.seedMemberClass(session, ns + "1"));
        var mc2 = Step.and("member class 2 seeded", () -> seeder.seedMemberClass(session, ns + "2"));
        var m1 = Step.and("member 1 seeded", () -> seeder.seedMember(session, ns + "1", mc1));
        var m2 = Step.and("member 2 seeded", () -> seeder.seedMember(session, ns + "2", mc2));
        Step.and("security servers seeded", () -> {
            seeder.seedSecurityServer(session, ns + "1", m1);
            seeder.seedSecurityServer(session, ns + "2", m2);
        });

        var client = new SecurityServersAdminClient(session);

        Step.when("server list is sorted by " + sortField + " " + (desc ? "desc" : "asc"), () -> {
            var values = client.findSecurityServers(Map.of(
                            "q", ns,
                            "sort", sortField,
                            "desc", desc))
                    .statusCode(200)
                    .extract().jsonPath().getList(jsonPathField, String.class);

            Assertions.assertThat(values).hasSizeGreaterThanOrEqualTo(2);
            if (desc) {
                Assertions.assertThat(values).isSortedAccordingTo(Comparator.reverseOrder());
            } else {
                Assertions.assertThat(values).isSorted();
            }
        });
    }

    @Test
    void securityServerListPagingAndQuery(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var mc = Step.given("member class seeded", () -> seeder.seedMemberClass(session, "0803"));
        var m1 = Step.and("member 1 seeded", () -> seeder.seedMember(session, "0803m1", mc));
        var m2 = Step.and("member 2 seeded", () -> seeder.seedMember(session, "0803m2", mc));
        Step.and("three security servers seeded", () -> {
            seeder.seedSecurityServer(session, "0803a", m1);
            seeder.seedSecurityServer(session, "0803b", m2);
            seeder.seedSecurityServer(session, "0803c", m1);
        });

        var client = new SecurityServersAdminClient(session);

        record PagingCase(String q, int limit, int offset, int expectedItems, int expectedTotal) {
        }

        var cases = List.of(
                new PagingCase("0803", 2, 0, 2, 3),
                new PagingCase("0803", 2, 1, 1, 3),
                new PagingCase("0803", 2, 2, 0, 3),
                new PagingCase("0803m1", 2, 0, 2, 2),
                new PagingCase("0803m2", 2, 0, 1, 1),
                new PagingCase("ss0803a", 2, 0, 1, 1),
                new PagingCase("ss0803b", 2, 0, 1, 1),
                new PagingCase("ss0803c", 2, 0, 1, 1),
                new PagingCase("should not find 0803", 25, 0, 0, 0)
        );

        Step.when("paging and query cases are validated", () -> {
            for (var c : cases) {
                client.findSecurityServers(Map.of(
                                "q", c.q(),
                                "limit", c.limit(),
                                "offset", c.offset()))
                        .statusCode(200)
                        .body("items", hasSize(c.expectedItems()))
                        .body("paging_metadata.total_items", equalTo(c.expectedTotal()));
            }
        });
    }

    @Test
    void getSecurityServerDetails(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "0804", BASELINE_MEMBER_CLASS));
        var serverId = Step.and("security server seeded", () -> seeder.seedSecurityServer(session, "0804", memberId));
        var client = new SecurityServersAdminClient(session);

        Step.when("server details are retrieved", () ->
                client.getSecurityServer(serverId)
                        .statusCode(200)
                        .body("server_id.encoded_id", equalTo(serverId))
                        .body("server_id.instance_id", equalTo(INSTANCE_IDENTIFIER))
                        .body("server_id.server_code", equalTo("ss0804"))
                        .body("server_address", equalTo("ss-addr-0804"))
                        .body("created_at", notNullValue()));

        Step.and("getting non-existing server returns 404", () ->
                client.getSecurityServer(INSTANCE_IDENTIFIER + ":NONEXIST:nonexist:nonexist")
                        .statusCode(404));
    }

    @Test
    void getSecurityServerClients(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "0805", BASELINE_MEMBER_CLASS));
        var serverId = Step.and("security server seeded", () -> seeder.seedSecurityServer(session, "0805", memberId));
        var clientMemberId = Step.and("client member seeded",
                () -> seeder.seedMember(session, "0805c", BASELINE_MEMBER_CLASS));
        var client = new SecurityServersAdminClient(session);
        var mgmtClient = new ManagementRequestsAdminClient(session);

        Step.when("server has no clients initially", () ->
                client.getClients(serverId)
                        .statusCode(200)
                        .body("$", hasSize(0)));

        Step.when("client is registered on the server", () -> {
            var req = new ClientRegistrationRequestDto().clientId(clientMemberId);
            req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
            req.setSecurityServerId(serverId);
            var regId = mgmtClient.addManagementRequest(req).statusCode(202).extract().jsonPath().getInt("id");
            mgmtClient.approveRequest(regId).statusCode(200);
        });

        Step.then("server clients list contains the registered client", () ->
                client.getClients(serverId)
                        .statusCode(200)
                        .body("$", hasSize(1))
                        .body("[0].client_id.member_code", equalTo("member0805c")));
    }

    @Test
    void modifySecurityServerAddress(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "0806", BASELINE_MEMBER_CLASS));
        var serverId = Step.and("security server seeded", () -> seeder.seedSecurityServer(session, "0806", memberId));
        var client = new SecurityServersAdminClient(session);

        Step.when("server address is updated", () ->
                client.updateSecurityServerAddress(serverId, "new-addr-0806")
                        .statusCode(200)
                        .body("server_address", equalTo("new-addr-0806")));

        Step.and("updating non-existing server returns 404", () ->
                client.updateSecurityServerAddress(
                        INSTANCE_IDENTIFIER + ":NONEXIST:nonexist:nonexist", "any-addr")
                        .statusCode(404));
    }

    @Test
    void getSecurityServerAuthenticationCertificates(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "0807", BASELINE_MEMBER_CLASS));
        var serverId = Step.and("security server seeded", () -> seeder.seedSecurityServer(session, "0807", memberId));
        var client = new SecurityServersAdminClient(session);

        Step.when("auth certs are retrieved", () ->
                client.getAuthCerts(serverId)
                        .statusCode(200)
                        .body("$", hasSize(1))
                        .body("[0].id", notNullValue())
                        .body("[0].issuer_common_name", equalTo("Cyber"))
                        .body("[0].serial", notNullValue())
                        .body("[0].subject_distinguished_name", equalTo("CN=" + serverId))
                        .body("[0].not_after", notNullValue()));
    }

    @Test
    void deleteSecurityServer(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("owner member seeded", () -> seeder.seedMember(session, "0808", BASELINE_MEMBER_CLASS));
        var serverId = Step.and("security server seeded", () -> seeder.seedSecurityServer(session, "0808", memberId));
        var clientMember1 = Step.and("client member 1 seeded",
                () -> seeder.seedMember(session, "0808c1", BASELINE_MEMBER_CLASS));
        var clientMember2 = Step.and("client member 2 seeded",
                () -> seeder.seedMember(session, "0808c2", BASELINE_MEMBER_CLASS));
        var ssClient = new SecurityServersAdminClient(session);
        var mgmtClient = new ManagementRequestsAdminClient(session);

        Step.and("two clients registered on the server", () -> {
            registerAndApproveClient(mgmtClient, serverId, clientMember1);
            registerAndApproveClient(mgmtClient, serverId, clientMember2);
        });

        Step.and("server is present in the list", () ->
                ssClient.findSecurityServers(Map.of("q", "ss0808"))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(1)));

        Step.when("server is deleted", () ->
                ssClient.deleteSecurityServer(serverId)
                        .statusCode(204));

        Step.then("server is no longer in the list", () ->
                ssClient.findSecurityServers(Map.of("q", "ss0808"))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(0)));

        Step.and("one auth cert deletion request created", () ->
                mgmtClient.findRequests(Map.of("serverId", serverId, "types", "AUTH_CERT_DELETION_REQUEST"))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(1)));

        Step.and("two client deletion requests created", () ->
                mgmtClient.findRequests(Map.of("serverId", serverId, "types", "CLIENT_DELETION_REQUEST"))
                        .statusCode(200)
                        .body("paging_metadata.total_items", equalTo(2)));

        var expectedComment = "SERVER:" + serverId.replace(':', '/') + " deletion";
        Step.and("all deletion requests carry the server deletion comment", () -> {
            var allDeletionIds = mgmtClient.findRequests(Map.of(
                            "serverId", serverId,
                            "types", "AUTH_CERT_DELETION_REQUEST,CLIENT_DELETION_REQUEST"))
                    .statusCode(200)
                    .extract().jsonPath().getList("items.id", Integer.class);
            for (var id : allDeletionIds) {
                mgmtClient.getRequest(id)
                        .statusCode(200)
                        .body("comments", equalTo(expectedComment));
            }
        });
    }

    @Test
    void deletingSecurityServerAuthenticationCertificate(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var memberId = Step.given("member seeded", () -> seeder.seedMember(session, "0809", BASELINE_MEMBER_CLASS));
        var serverId = Step.and("security server seeded", () -> seeder.seedSecurityServer(session, "0809", memberId));
        var client = new SecurityServersAdminClient(session);

        var certId = Step.when("auth cert id is retrieved", () ->
                client.getAuthCerts(serverId)
                        .statusCode(200)
                        .extract().jsonPath().getInt("[0].id"));

        Step.when("auth cert is deleted", () ->
                client.deleteAuthCert(serverId, certId)
                        .statusCode(204));

        Step.then("server has no auth certs", () ->
                client.getAuthCerts(serverId)
                        .statusCode(200)
                        .body("$", hasSize(0)));
    }

    private void registerAndApproveClient(ManagementRequestsAdminClient mgmtClient, String serverId,
            String clientMemberId) {
        var req = new ClientRegistrationRequestDto().clientId(clientMemberId);
        req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
        req.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
        req.setSecurityServerId(serverId);
        var regId = mgmtClient.addManagementRequest(req).statusCode(202).extract().jsonPath().getInt("id");
        mgmtClient.approveRequest(regId).statusCode(200);
    }
}
