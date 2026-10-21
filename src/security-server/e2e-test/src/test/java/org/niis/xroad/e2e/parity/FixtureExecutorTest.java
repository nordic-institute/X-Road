/*
 * The MIT License
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

package org.niis.xroad.e2e.parity;

import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.parity.Scenario.Delete;
import org.niis.xroad.e2e.parity.Scenario.Deleted;
import org.niis.xroad.e2e.parity.Scenario.Disable;
import org.niis.xroad.e2e.parity.Scenario.EditedTo;
import org.niis.xroad.e2e.parity.Scenario.EndpointSpec;
import org.niis.xroad.e2e.parity.Scenario.FixtureSpec;
import org.niis.xroad.e2e.parity.Scenario.GeneratedEndpointGrant;
import org.niis.xroad.e2e.parity.Scenario.LocalGroupSpec;
import org.niis.xroad.e2e.parity.Scenario.OpenApiService;
import org.niis.xroad.e2e.parity.Scenario.OpenApiSpec;
import org.niis.xroad.e2e.parity.Scenario.RenameService;
import org.niis.xroad.e2e.parity.Scenario.RestService;
import org.niis.xroad.e2e.parity.Scenario.WsdlService;
import org.niis.xroad.e2e.parity.Scenario.WsdlShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixtureExecutorTest {

    private static final FixtureSpec TWO_SERVICES = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(
            new RestService(List.of(ScenarioCatalogue.CONSUMER)),
            new RestService(List.of())));

    private static final FixturePlan DELETED_GROUP_PLAN = new FixturePlanner("t1").plan("V63",
            new FixtureSpec(ScenarioCatalogue.PROVIDER,
                    List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                    List.of(new LocalGroupSpec(ScenarioCatalogue.PROVIDER, "partners",
                            List.of(ScenarioCatalogue.CONSUMER), List.of(ScenarioCatalogue.CONSUMER), true))));

    private static final String DELETED_GROUP_ID = "grp-%s-pt1-v63-partners".formatted(ScenarioCatalogue.PROVIDER);

    private static final class RecordingGateway implements AdminGateway {
        final List<String> calls = new ArrayList<>();
        Map<String, Integer> deleteStatus = Map.of();
        Set<String> throwOnDeleteOf = Set.of();
        String failOnAddOf;
        String failOnGrantOf;
        String alreadyGrantedTo;

        @Override
        public String addRestDescription(String providerClientId, String backendUrl, String serviceCode) {
            if (serviceCode.equals(failOnAddOf)) {
                throw new IllegalStateException("add refused");
            }
            calls.add("add " + serviceCode);
            return "id-" + serviceCode;
        }

        @Override
        public String addWsdlDescription(String providerClientId, String wsdlUrl) {
            calls.add("add wsdl " + wsdlUrl);
            return "id-wsdl";
        }

        @Override
        public String addOpenApiDescription(String providerClientId, String specUrl, String serviceCode) {
            calls.add("add openapi " + specUrl);
            return "id-" + serviceCode;
        }
        @Override
        public void updateOpenApiDescription(String descriptionId, String specUrl, String serviceCode) {
            calls.add("update openapi %s to %s".formatted(descriptionId, specUrl));
        }
        @Override
        public void enableDescription(String descriptionId) {
            calls.add("enable " + descriptionId);
        }

        @Override
        public void disableDescription(String descriptionId, String notice) {
            calls.add("disable %s with %s".formatted(descriptionId, notice));
        }

        @Override
        public void renameRestService(String descriptionId, String backendUrl, String serviceCode,
                                      String newServiceCode) {
            calls.add("rename %s of %s to %s".formatted(serviceCode, descriptionId, newServiceCode));
        }

        @Override
        public boolean grantAccess(String providerClientId, String consumerClientId, String serviceCode) {
            if (serviceCode.equals(failOnGrantOf)) {
                throw new AssertionError("grant answered 500");
            }
            calls.add("grant " + serviceCode + " to " + consumerClientId);
            return !consumerClientId.equals(alreadyGrantedTo);
        }

        @Override
        public int requestAccessGrant(String providerClientId, String subjectId, String serviceCode) {
            calls.add("request grant " + serviceCode + " to " + subjectId);
            return 201;
        }

        @Override
        public void revokeAccess(String providerClientId, String consumerClientId, String serviceCode) {
            calls.add("revoke " + serviceCode + " from " + consumerClientId);
        }

        @Override
        public void refreshDescription(String descriptionId) {
            calls.add("refresh " + descriptionId);
        }

        @Override
        public String overrideBackendStub(AdminCall.OverrideBackendStub override) {
            calls.add("override %s at %s".formatted(override.stubName(), override.urlPath()));
            return "stub-" + override.stubName();
        }

        @Override
        public int removeBackendStub(String overrideId) {
            calls.add("remove " + overrideId);
            return deleteStatus.getOrDefault(overrideId, 200);
        }

        @Override
        public String addEndpoint(String descriptionId, String serviceCode, String method, String path) {
            calls.add("add endpoint %s %s on %s".formatted(method, path, descriptionId));
            return "ep-%s-%s".formatted(method, path.replace('/', '_'));
        }

        @Override
        public String findEndpointId(String descriptionId, String serviceCode, String method, String path) {
            calls.add("find endpoint %s %s on %s".formatted(method, path, descriptionId));
            return "gen-%s-%s".formatted(method, path.replace('/', '_'));
        }
        @Override
        public void updateEndpoint(String endpointId, String method, String path) {
            calls.add("update endpoint %s to %s %s".formatted(endpointId, method, path));
        }
        @Override
        public void grantEndpointAccess(String endpointId, String consumerClientId) {
            calls.add("grant endpoint " + endpointId + " to " + consumerClientId);
        }

        @Override
        public int deleteEndpoint(String endpointId) {
            calls.add("delete endpoint " + endpointId);
            return deleteStatus.getOrDefault(endpointId, 204);
        }

        @Override
        public int deleteDescription(String descriptionId) {
            calls.add("delete " + descriptionId);
            return deleteStatus.getOrDefault(descriptionId, 204);
        }
        @Override
        public String createLocalGroup(String ownerClientId, String code) {
            calls.add("create group %s of %s".formatted(code, ownerClientId));
            return "grp-%s-%s".formatted(ownerClientId, code);
        }
        @Override
        public void addLocalGroupMember(String groupId, String memberId) {
            calls.add("add member %s to %s".formatted(memberId, groupId));
        }
        @Override
        public void removeLocalGroupMember(String groupId, String memberId) {
            calls.add("remove member %s from %s".formatted(memberId, groupId));
        }
        @Override
        public int deleteLocalGroup(String groupId) {
            calls.add("delete group " + groupId);
            if (throwOnDeleteOf.contains(groupId)) {
                throw new IllegalStateException("gateway down");
            }
            return deleteStatus.getOrDefault(groupId, 204);
        }

        @Override
        public void createGlobalGroup(String code) {
            calls.add("create global group " + code);
        }

        @Override
        public void addGlobalGroupMember(String code, String memberId) {
            calls.add("add %s to global group %s".formatted(memberId, code));
        }

        @Override
        public void removeGlobalGroupMember(String code, String memberId) {
            calls.add("remove %s from global group %s".formatted(memberId, code));
        }

        @Override
        public int deleteGlobalGroup(String code) {
            calls.add("delete global group " + code);
            if (throwOnDeleteOf.contains("global-" + code)) {
                throw new IllegalStateException("gateway down");
            }
            return deleteStatus.getOrDefault("global-" + code, 204);
        }

        @Override
        public void grantGlobalGroupAccess(String providerClientId, String groupId, String serviceCode) {
            calls.add("grant %s to global group %s".formatted(serviceCode, groupId));
        }

        @Override
        public void grantGlobalGroupEndpointAccess(String endpointId, String groupId) {
            calls.add("grant endpoint %s to global group %s".formatted(endpointId, groupId));
        }
    }

    private final FixturePlan plan = new FixturePlanner("t1").plan("V09", TWO_SERVICES);

    @Test
    void setupRunsInPlanOrderAndCloseDeletesInReverse() {
        var gateway = new RecordingGateway();

        try (var ignored = new FixtureExecutor(gateway).open(plan)) {
            assertThat(gateway.calls).containsExactly(
                    "add pt1-v09",
                    "enable id-pt1-v09",
                    "grant pt1-v09 to " + ScenarioCatalogue.CONSUMER,
                    "add pt1-v09-2",
                    "enable id-pt1-v09-2");
        }

        assertThat(gateway.calls.subList(5, 7)).containsExactly("delete id-pt1-v09-2", "delete id-pt1-v09");
    }

    @Test
    void cleanupToleratesADescriptionThatIsAlreadyGone() {
        var gateway = new RecordingGateway();
        gateway.deleteStatus = Map.of("id-pt1-v09-2", 404);

        var fixture = new FixtureExecutor(gateway).open(plan);

        fixture.close();

        assertThat(gateway.calls).contains("delete id-pt1-v09-2", "delete id-pt1-v09");
    }

    @Test
    void cleanupKeepsGoingAfterAFailedStepThenReportsIt() {
        var gateway = new RecordingGateway();
        gateway.deleteStatus = Map.of("id-pt1-v09-2", 500);
        var fixture = new FixtureExecutor(gateway).open(plan);

        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pt1-v09-2")
                .hasMessageContaining("500");
        assertThat(gateway.calls).contains("delete id-pt1-v09");
    }

    @Test
    void phaseCallsRunOnDemandAndBackendOverridesAreRemovedBeforeTheDescriptionDeletes() {
        var gateway = new RecordingGateway();
        var swapPlan = new FixturePlanner("t1").plan(scenario("V39"));

        try (var fixture = new FixtureExecutor(gateway).open(swapPlan)) {
            gateway.calls.clear();
            fixture.apply(swapPlan.phases().get(0));
            assertThat(gateway.calls).containsExactly(
                    "override acl_parity_openapi_status_only at /acl-parity/openapi/pets.yaml",
                    "refresh id-pt1-v39");
            gateway.calls.clear();
        }

        assertThat(gateway.calls).containsExactly("remove stub-acl_parity_openapi_status_only", "delete id-pt1-v39");
    }

    @Test
    void revokeIsSentToTheGateway() {
        var gateway = new RecordingGateway();
        var revokePlan = new FixturePlanner("t1").plan(scenario("V11"));

        try (var fixture = new FixtureExecutor(gateway).open(revokePlan)) {
            fixture.apply(revokePlan.phases().get(0));
            assertThat(gateway.calls).contains("revoke pt1-v11 from " + ScenarioCatalogue.CONSUMER);
        }
    }

    @Test
    void missingBackendOverrideIsToleratedAndARefusedRemovalIsReported() {
        var gateway = new RecordingGateway();
        var swapPlan = new FixturePlanner("t1").plan(scenario("V39"));
        gateway.deleteStatus = Map.of("stub-acl_parity_openapi_status_only", 404);
        var fixture = new FixtureExecutor(gateway).open(swapPlan);
        fixture.apply(swapPlan.phases().get(0));
        assertThatCode(fixture::close).doesNotThrowAnyException();

        gateway.deleteStatus = Map.of("stub-acl_parity_openapi_status_only", 500);
        var refused = new FixtureExecutor(gateway).open(swapPlan);
        refused.apply(swapPlan.phases().get(0));
        assertThatThrownBy(refused::close).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("backend override").hasMessageContaining("500");
    }

    @Test
    void failedSetupRemovesWhatItCreatedAndRethrows() {
        var gateway = new RecordingGateway();
        gateway.failOnAddOf = "pt1-v09-2";

        assertThatThrownBy(() -> new FixtureExecutor(gateway).open(plan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("add refused");
        assertThat(gateway.calls).endsWith("delete id-pt1-v09");
        assertThat(gateway.calls).doesNotContain("delete id-pt1-v09-2");
    }

    @Test
    void failedAdminAssertionDuringSetupRemovesWhatItCreatedAndRethrows() {
        var gateway = new RecordingGateway();
        gateway.failOnGrantOf = "pt1-v09";

        assertThatThrownBy(() -> new FixtureExecutor(gateway).open(plan))
                .isInstanceOf(AssertionError.class)
                .hasMessage("grant answered 500");
        assertThat(gateway.calls).endsWith("delete id-pt1-v09");
    }

    @Test
    void wsdlDescriptionIsAddedThroughItsFirstCodeAndDeletedOnClose() {
        var gateway = new RecordingGateway();
        var wsdlPlan = new FixturePlanner("t1").plan("V04", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new WsdlService(WsdlShape.PAIR, "", List.of(List.of(ScenarioCatalogue.CONSUMER), List.of())))));

        try (var ignored = new FixtureExecutor(gateway).open(wsdlPlan)) {
            assertThat(gateway.calls).containsExactly(
                    "add wsdl http://isrest:8080/acl-parity/wsdl/pair.wsdl?code1=pt1-v04&code2=pt1-v04-b",
                    "enable id-wsdl",
                    "grant pt1-v04 to " + ScenarioCatalogue.CONSUMER);
        }

        assertThat(gateway.calls).endsWith("delete id-wsdl");
        assertThat(gateway.calls).filteredOn(call -> call.startsWith("delete")).hasSize(1);
    }

    @Test
    void endpointsAreCreatedGrantedAndDeletedBeforeTheirDescription() {
        var gateway = new RecordingGateway();
        var endpointPlan = new FixturePlanner("t1").plan("V22", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(),
                        List.of(new EndpointSpec("GET", "/foo", List.of(ScenarioCatalogue.CONSUMER)))))));

        try (var ignored = new FixtureExecutor(gateway).open(endpointPlan)) {
            assertThat(gateway.calls).containsExactly(
                    "add pt1-v22",
                    "enable id-pt1-v22",
                    "add endpoint GET /foo on id-pt1-v22",
                    "grant endpoint ep-GET-_foo to " + ScenarioCatalogue.CONSUMER);
        }

        assertThat(gateway.calls.subList(4, 6)).containsExactly("delete endpoint ep-GET-_foo", "delete id-pt1-v22");
    }

    @Test
    void failedEndpointDeleteIsReportedAndTheDescriptionIsStillDeleted() {
        var gateway = new RecordingGateway();
        gateway.deleteStatus = Map.of("ep-GET-_foo", 500);
        var endpointPlan = new FixturePlanner("t1").plan("V22", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(), List.of(new EndpointSpec("GET", "/foo", List.of()))))));
        var fixture = new FixtureExecutor(gateway).open(endpointPlan);

        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint GET /foo")
                .hasMessageContaining("500");
        assertThat(gateway.calls).endsWith("delete id-pt1-v22");
    }

    @Test
    void grantOnAGeneratedEndpointLooksItUpByMethodAndPath() {
        var gateway = new RecordingGateway();
        var openApiPlan = new FixturePlanner("t1").plan("V38", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new OpenApiService(OpenApiSpec.PETS, List.of(),
                        List.of(new GeneratedEndpointGrant("GET", "/pets/*", List.of(ScenarioCatalogue.CONSUMER))),
                        null))));

        try (var ignored = new FixtureExecutor(gateway).open(openApiPlan)) {
            assertThat(gateway.calls).containsExactly(
                    "add openapi http://isrest:8080/acl-parity/openapi/pets.yaml?code=pt1-v38",
                    "enable id-pt1-v38",
                    "find endpoint GET /pets/* on id-pt1-v38",
                    "grant endpoint gen-GET-_pets_* to " + ScenarioCatalogue.CONSUMER);
        }
        assertThat(gateway.calls).endsWith("delete id-pt1-v38");
        assertThat(gateway.calls).filteredOn(call -> call.startsWith("delete")).hasSize(1);
    }

    @Test
    void descriptionEditRunsAfterTheManualEndpointIsGrantedWithoutLookingItUp() {
        var gateway = new RecordingGateway();
        var openApiPlan = new FixturePlanner("t1").plan("V40", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new OpenApiService(OpenApiSpec.STATUS_ONLY,
                        List.of(new EndpointSpec("GET", "/pets/*", List.of(ScenarioCatalogue.CONSUMER))), List.of(),
                        OpenApiSpec.PETS))));

        try (var ignored = new FixtureExecutor(gateway).open(openApiPlan)) {
            assertThat(gateway.calls).containsExactly(
                    "add openapi http://isrest:8080/acl-parity/openapi/status_only.yaml?code=pt1-v40",
                    "enable id-pt1-v40",
                    "add endpoint GET /pets/* on id-pt1-v40",
                    "grant endpoint ep-GET-_pets_* to " + ScenarioCatalogue.CONSUMER,
                    "update openapi id-pt1-v40 to http://isrest:8080/acl-parity/openapi/pets.yaml?code=pt1-v40");
        }
        assertThat(gateway.calls).filteredOn(call -> call.contains("endpoint") && call.startsWith("delete")).isEmpty();
    }

    @Test
    void endpointDeletedInSetupIsDeletedOnceBeforeTheRequestsAndNotAgainOnClose() {
        var gateway = new RecordingGateway();
        var deletePlan = new FixturePlanner("t1").plan("V36", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(), List.of(
                        new EndpointSpec("GET", "/foo", List.of(ScenarioCatalogue.CONSUMER), new Deleted()))))));

        try (var ignored = new FixtureExecutor(gateway).open(deletePlan)) {
            assertThat(gateway.calls).containsExactly(
                    "add pt1-v36",
                    "enable id-pt1-v36",
                    "add endpoint GET /foo on id-pt1-v36",
                    "grant endpoint ep-GET-_foo to " + ScenarioCatalogue.CONSUMER,
                    "delete endpoint ep-GET-_foo");
        }
        assertThat(gateway.calls).endsWith("delete id-pt1-v36");
        assertThat(gateway.calls).filteredOn(call -> call.equals("delete endpoint ep-GET-_foo")).hasSize(1);
    }

    @Test
    void endpointDeleteRefusedDuringSetupFailsTheSetupAndStillRemovesTheDescription() {
        var gateway = new RecordingGateway();
        gateway.deleteStatus = Map.of("ep-GET-_foo", 400);
        var deletePlan = new FixturePlanner("t1").plan("V36", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(), List.of(new EndpointSpec("GET", "/foo", List.of(), new Deleted()))))));

        assertThatThrownBy(() -> new FixtureExecutor(gateway).open(deletePlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint GET /foo")
                .hasMessageContaining("400");
        assertThat(gateway.calls).endsWith("delete id-pt1-v36");
    }

    @Test
    void editedEndpointKeepsItsIdAndIsDeletedUnderItAfterTheEdit() {
        var gateway = new RecordingGateway();
        var editPlan = new FixturePlanner("t1").plan("V37", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(), List.of(new EndpointSpec("GET", "/foo",
                        List.of(ScenarioCatalogue.CONSUMER), new EditedTo("GET", "/bar")))))));

        try (var ignored = new FixtureExecutor(gateway).open(editPlan)) {
            assertThat(gateway.calls).containsExactly(
                    "add pt1-v37",
                    "enable id-pt1-v37",
                    "add endpoint GET /foo on id-pt1-v37",
                    "grant endpoint ep-GET-_foo to " + ScenarioCatalogue.CONSUMER,
                    "update endpoint ep-GET-_foo to GET /bar");
        }
        assertThat(gateway.calls.subList(5, 7)).containsExactly("delete endpoint ep-GET-_foo", "delete id-pt1-v37");
    }

    @Test
    void groupIsCreatedFilledAndGrantedByItsIdThenDeletedAfterTheDescription() {
        var gateway = new RecordingGateway();
        var groupPlan = new FixturePlanner("t1").plan("V60", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(ScenarioCatalogue.PROVIDER, "partners", List.of(ScenarioCatalogue.CONSUMER)))));
        var groupId = "grp-%s-pt1-v60-partners".formatted(ScenarioCatalogue.PROVIDER);
        try (var ignored = new FixtureExecutor(gateway).open(groupPlan)) {
            assertThat(gateway.calls).containsExactly(
                    "create group pt1-v60-partners of " + ScenarioCatalogue.PROVIDER,
                    "add member %s to %s".formatted(ScenarioCatalogue.CONSUMER, groupId),
                    "add pt1-v60",
                    "enable id-pt1-v60",
                    "grant pt1-v60 to " + groupId);
        }
        assertThat(gateway.calls.subList(5, 7)).containsExactly("delete id-pt1-v60", "delete group " + groupId);
    }

    @Test
    void groupDeletedInSetupIsDeletedOnceAndNotAgainOnClose() {
        var gateway = new RecordingGateway();
        var groupPlan = new FixturePlanner("t1").plan("V63", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(ScenarioCatalogue.PROVIDER, "partners", List.of(ScenarioCatalogue.CONSUMER),
                        List.of(ScenarioCatalogue.CONSUMER), true))));
        var groupId = "grp-%s-pt1-v63-partners".formatted(ScenarioCatalogue.PROVIDER);
        try (var ignored = new FixtureExecutor(gateway).open(groupPlan)) {
            assertThat(gateway.calls).endsWith(
                    "grant pt1-v63 to " + groupId,
                    "remove member %s from %s".formatted(ScenarioCatalogue.CONSUMER, groupId),
                    "delete group " + groupId);
        }
        assertThat(gateway.calls).filteredOn(call -> call.startsWith("delete group")).hasSize(1);
    }

    @Test
    void localGroupWhoseSetupDeleteAnswersAnErrorIsDeletedAgainInCleanup() {
        var gateway = new RecordingGateway();
        gateway.deleteStatus = Map.of(DELETED_GROUP_ID, 500);

        assertThatThrownBy(() -> new FixtureExecutor(gateway).open(DELETED_GROUP_PLAN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delete of local group pt1-v63-partners")
                .hasMessageContaining("500");

        assertThat(gateway.calls).filteredOn(("delete group " + DELETED_GROUP_ID)::equals).hasSize(2);
    }

    @Test
    void localGroupWhoseSetupDeleteThrowsIsDeletedAgainInCleanup() {
        var gateway = new RecordingGateway();
        gateway.throwOnDeleteOf = Set.of(DELETED_GROUP_ID);

        assertThatThrownBy(() -> new FixtureExecutor(gateway).open(DELETED_GROUP_PLAN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("gateway down");

        assertThat(gateway.calls).filteredOn(("delete group " + DELETED_GROUP_ID)::equals).hasSize(2);
    }

    @Test
    void globalGroupWhoseDeleteAnswersAnErrorIsDeletedAgainInCleanup() {
        var gateway = new RecordingGateway();
        gateway.deleteStatus = Map.of("global-pt1-v74-everyone", 500);
        var globalPlan = new FixturePlanner("t1").plan(scenario("V74"));

        try (var fixture = new FixtureExecutor(gateway).open(globalPlan)) {
            assertThatThrownBy(() -> fixture.apply(globalPlan.phases().get(0)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("delete of global group pt1-v74-everyone")
                    .hasMessageContaining("500");
            assertThatThrownBy(fixture::close).isInstanceOf(IllegalStateException.class);
        }

        assertThat(gateway.calls).filteredOn("delete global group pt1-v74-everyone"::equals).hasSize(2);
    }

    @Test
    void globalGroupWhoseDeleteThrowsIsDeletedAgainInCleanup() {
        var gateway = new RecordingGateway();
        gateway.throwOnDeleteOf = Set.of("global-pt1-v74-everyone");
        var globalPlan = new FixturePlanner("t1").plan(scenario("V74"));

        try (var fixture = new FixtureExecutor(gateway).open(globalPlan)) {
            assertThatThrownBy(() -> fixture.apply(globalPlan.phases().get(0)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("gateway down");
            assertThatThrownBy(fixture::close).isInstanceOf(IllegalStateException.class);
        }

        assertThat(gateway.calls).filteredOn("delete global group pt1-v74-everyone"::equals).hasSize(2);
    }

    @Test
    void groupGrantOnAnEndpointLooksTheGroupUpByOwnerAndCode() {
        var gateway = new RecordingGateway();
        var groupPlan = new FixturePlanner("t1").plan("V66", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(), List.of(new EndpointSpec("GET", "/b", List.of(), List.of("partners"))))),
                List.of(new LocalGroupSpec(ScenarioCatalogue.PROVIDER, "partners", List.of()))));
        try (var ignored = new FixtureExecutor(gateway).open(groupPlan)) {
            assertThat(gateway.calls).endsWith("grant endpoint ep-GET-_b to grp-%s-pt1-v66-partners"
                    .formatted(ScenarioCatalogue.PROVIDER));
        }
    }

    @Test
    void failedGroupCleanupIsReportedAndTheOtherGroupIsStillDeleted() {
        var gateway = new RecordingGateway();
        gateway.deleteStatus = Map.of("grp-%s-pt1-v65-partners".formatted(SubsystemPool.SECOND_OWNER), 500);
        var groupPlan = new FixturePlanner("t1").plan("V65", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of())),
                List.of(new LocalGroupSpec(ScenarioCatalogue.PROVIDER, "partners", List.of()),
                        new LocalGroupSpec(SubsystemPool.SECOND_OWNER, "partners", List.of()))));
        var fixture = new FixtureExecutor(gateway).open(groupPlan);
        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local group pt1-v65-partners of " + SubsystemPool.SECOND_OWNER)
                .hasMessageContaining("500");
        assertThat(gateway.calls).endsWith("delete group grp-%s-pt1-v65-partners".formatted(ScenarioCatalogue.PROVIDER));
    }

    @Test
    void failedSetupAfterGroupCreationDeletesTheGroup() {
        var gateway = new RecordingGateway();
        gateway.failOnAddOf = "pt1-v60";
        var groupPlan = new FixturePlanner("t1").plan("V60", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(ScenarioCatalogue.PROVIDER, "partners", List.of()))));
        assertThatThrownBy(() -> new FixtureExecutor(gateway).open(groupPlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("add refused");
        assertThat(gateway.calls).endsWith("delete group grp-%s-pt1-v60-partners".formatted(ScenarioCatalogue.PROVIDER));
    }

    @Test
    void closeTwiceDeletesOnce() {
        var gateway = new RecordingGateway();
        var fixture = new FixtureExecutor(gateway).open(plan);

        fixture.close();
        fixture.close();

        assertThat(gateway.calls).filteredOn(call -> call.startsWith("delete")).hasSize(2);
    }

    @Test
    void disableSendsTheNoticeOrNoneAtAll() {
        var gateway = new RecordingGateway();
        var disablePlan = new FixturePlanner("t1").plan("V123", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of())), List.of(), List.of(new Disable(0, null))));

        try (var ignored = new FixtureExecutor(gateway).open(disablePlan)) {
            assertThat(gateway.calls).endsWith("disable id-pt1-v123 with null");
        }
    }

    @Test
    void renameRunsOnTheDescriptionIdOfTheOldCodeAndCleanupStillDeletesThatDescription() {
        var gateway = new RecordingGateway();
        var renamePlan = new FixturePlanner("t1").plan("V127", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of())), List.of(), List.of(new RenameService(0))));

        try (var ignored = new FixtureExecutor(gateway).open(renamePlan)) {
            assertThat(gateway.calls).endsWith("rename pt1-v127 of id-pt1-v127 to pt1-v127-renamed");
        }

        assertThat(gateway.calls).endsWith("delete id-pt1-v127");
    }

    @Test
    void descriptionDeletedInSetupIsDeletedOnceAndNotAgainOnClose() {
        var gateway = new RecordingGateway();
        var deletePlan = new FixturePlanner("t1").plan("V125", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of())), List.of(), List.of(new Delete(0))));

        try (var ignored = new FixtureExecutor(gateway).open(deletePlan)) {
            assertThat(gateway.calls).endsWith("delete id-pt1-v125");
        }

        assertThat(gateway.calls).filteredOn(call -> call.startsWith("delete")).hasSize(1);
    }

    @Test
    void failedGrantBeforeTheSetupDeleteStillDeletesTheDescriptionInCleanup() {
        var gateway = new RecordingGateway();
        gateway.failOnGrantOf = "pt1-v125";
        var executor = new FixtureExecutor(gateway);
        var deletePlan = new FixturePlanner("t1").plan("V125", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(ScenarioCatalogue.CONSUMER))), List.of(), List.of(new Delete(0))));

        assertThatThrownBy(() -> executor.open(deletePlan))
                .isInstanceOf(AssertionError.class)
                .hasMessage("grant answered 500");
        assertThat(gateway.calls).containsExactly("add pt1-v125", "enable id-pt1-v125", "delete id-pt1-v125");
    }

    @Test
    void cleanupDeleteOfADescriptionAlreadyGoneIsNotAFailure() {
        var gateway = new RecordingGateway();
        gateway.failOnGrantOf = "pt1-v125";
        gateway.deleteStatus = Map.of("id-pt1-v125", 404);
        var executor = new FixtureExecutor(gateway);
        var deletePlan = new FixturePlanner("t1").plan("V125", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(ScenarioCatalogue.CONSUMER))), List.of(), List.of(new Delete(0))));

        assertThatThrownBy(() -> executor.open(deletePlan))
                .isInstanceOf(AssertionError.class)
                .hasMessage("grant answered 500")
                .hasNoSuppressedExceptions();
        assertThat(gateway.calls).endsWith("delete id-pt1-v125");
    }

    @Test
    void closeAfterASuccessfulSetupDeleteDoesNotDeleteAgainAndDoesNotFail() {
        var gateway = new RecordingGateway();
        var deletePlan = new FixturePlanner("t1").plan("V125", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of())), List.of(), List.of(new Delete(0))));
        var fixture = new FixtureExecutor(gateway).open(deletePlan);

        assertThatCode(fixture::close).doesNotThrowAnyException();
        assertThat(gateway.calls).filteredOn(call -> call.startsWith("delete")).hasSize(1);
    }

    @Test
    void descriptionDeleteRefusedDuringSetupKeepsTheIdSoCleanupDeletesIt() {
        var gateway = new RecordingGateway();
        gateway.deleteStatus = Map.of("id-pt1-v125", 500);
        var executor = new FixtureExecutor(gateway);
        var deletePlan = new FixturePlanner("t1").plan("V125", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of())), List.of(), List.of(new Delete(0))));

        assertThatThrownBy(() -> executor.open(deletePlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pt1-v125 answered 500");
        assertThat(gateway.calls).filteredOn(call -> call.equals("delete id-pt1-v125")).hasSize(2);
    }

    @Test
    void descriptionDeleteRefusedDuringSetupFailsTheSetup() {
        var gateway = new RecordingGateway();
        gateway.deleteStatus = Map.of("id-pt1-v125", 500);
        var executor = new FixtureExecutor(gateway);
        var deletePlan = new FixturePlanner("t1").plan("V125", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of())), List.of(), List.of(new Delete(0))));

        assertThatThrownBy(() -> executor.open(deletePlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pt1-v125 answered 500");
    }

    @Test
    void globalGroupIsCreatedBeforeItsGrantAndDeletedInCleanup() {
        var gateway = new RecordingGateway();
        var globalPlan = new FixturePlanner("t1").plan(scenario("V68"));

        try (var ignored = new FixtureExecutor(gateway).open(globalPlan)) {
            assertThat(gateway.calls).containsSubsequence(
                    "create global group pt1-v68-everyone",
                    "add %s to global group pt1-v68-everyone".formatted(ScenarioCatalogue.CONSUMER),
                    "grant pt1-v68 to global group DEV:pt1-v68-everyone");
        }

        assertThat(gateway.calls).endsWith("delete global group pt1-v68-everyone");
    }

    @Test
    void groupDeletedByAPhaseIsNotDeletedAgainInCleanup() {
        var gateway = new RecordingGateway();
        var globalPlan = new FixturePlanner("t1").plan(scenario("V74"));

        try (var fixture = new FixtureExecutor(gateway).open(globalPlan)) {
            fixture.apply(globalPlan.phases().get(0));
        }

        assertThat(gateway.calls).filteredOn("delete global group pt1-v74-everyone"::equals).hasSize(1);
    }

    @Test
    void ownersGroupIsGrantedButNeverCreatedOrDeleted() {
        var gateway = new RecordingGateway();
        var ownersPlan = new FixturePlanner("t1").plan(scenario("V72"));

        try (var ignored = new FixtureExecutor(gateway).open(ownersPlan)) {
            assertThat(gateway.calls).contains("grant pt1-v72 to global group DEV:security-server-owners");
        }

        assertThat(gateway.calls).noneMatch(call -> call.contains("create global group")
                || call.contains("delete global group"));
    }

    @Test
    void missingGlobalGroupIsToleratedInCleanupAndOtherFailuresAreReported() {
        var gateway = new RecordingGateway();
        gateway.deleteStatus = Map.of("global-pt1-v68-everyone", 404);
        var globalPlan = new FixturePlanner("t1").plan(scenario("V68"));
        assertThatCode(() -> new FixtureExecutor(gateway).open(globalPlan).close()).doesNotThrowAnyException();

        gateway.deleteStatus = Map.of("global-pt1-v68-everyone", 500);
        var fixture = new FixtureExecutor(gateway).open(globalPlan);
        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("global group pt1-v68-everyone")
                .hasMessageContaining("500");
    }

    @Test
    void managementGrantsChangedInSetupAreChangedBackNewestFirstOnClose() {
        var gateway = new RecordingGateway();
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new Scenario.ManagementService("clientReg",
                List.of("DEV:security-server-owners"), List.of(ScenarioCatalogue.CONSUMER))));
        var managementPlan = new FixturePlanner("t1").plan("VX", spec);

        try (var ignored = new FixtureExecutor(gateway).open(managementPlan)) {
            assertThat(gateway.calls).containsExactly(
                    "revoke clientReg from DEV:security-server-owners",
                    "grant clientReg to " + ScenarioCatalogue.CONSUMER);
        }

        assertThat(gateway.calls.subList(2, 4)).containsExactly(
                "revoke clientReg from " + ScenarioCatalogue.CONSUMER,
                "grant clientReg to DEV:security-server-owners");
        assertThat(gateway.calls).hasSize(4);
    }

    @Test
    void managementRowThatMovesTheGrantInAPhaseGivesTheOwnersGroupItsGrantBackOnClose() {
        var gateway = new RecordingGateway();
        var phasePlan = new FixturePlanner("t1").plan(scenario("V106a"));

        try (var fixture = new FixtureExecutor(gateway).open(phasePlan)) {
            assertThat(gateway.calls).isEmpty();
            fixture.apply(phasePlan.phases().get(0));
            assertThat(gateway.calls).containsExactly(
                    "revoke clientReg from DEV:security-server-owners",
                    "grant clientReg to " + ScenarioCatalogue.CONSUMER);
        }

        assertThat(gateway.calls.subList(2, 4)).containsExactly(
                "revoke clientReg from " + ScenarioCatalogue.CONSUMER,
                "grant clientReg to DEV:security-server-owners");
        assertThat(gateway.calls).hasSize(4);
    }

    @Test
    void managementGrantThatAlreadyExistedIsNotRevokedOnClose() {
        var gateway = new RecordingGateway();
        gateway.alreadyGrantedTo = ScenarioCatalogue.CONSUMER;
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new Scenario.ManagementService("clientReg",
                List.of(), List.of(ScenarioCatalogue.CONSUMER))));
        var managementPlan = new FixturePlanner("t1").plan("VX", spec);

        try (var ignored = new FixtureExecutor(gateway).open(managementPlan)) {
            assertThat(gateway.calls).containsExactly("grant clientReg to " + ScenarioCatalogue.CONSUMER);
        }

        assertThat(gateway.calls).containsExactly("grant clientReg to " + ScenarioCatalogue.CONSUMER);
    }

    @Test
    void managementGrantChangedByAPhaseIsChangedBackOnClose() {
        var gateway = new RecordingGateway();
        var fixtureSpec = new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new Scenario.ManagementService("clientReg", List.of(), List.of())));
        var row = new Scenario("VX", "test row", fixtureSpec, List.of(), List.of(new Scenario.Phase(
                List.of(new Scenario.Revoke(0, ScenarioCatalogue.CONSUMER)), List.of())),
                Scenario.WaitClass.ACL_CACHE);
        var phasePlan = new FixturePlanner("t1").plan(row);

        try (var fixture = new FixtureExecutor(gateway).open(phasePlan)) {
            fixture.apply(phasePlan.phases().get(0));
            assertThat(gateway.calls).containsExactly("revoke clientReg from " + ScenarioCatalogue.CONSUMER);
        }

        assertThat(gateway.calls).containsExactly(
                "revoke clientReg from " + ScenarioCatalogue.CONSUMER,
                "grant clientReg to " + ScenarioCatalogue.CONSUMER);
    }

    @Test
    void managementGrantChangedInASetupThatFailsLaterIsChangedBackBeforeTheFailureIsRethrown() {
        var gateway = new RecordingGateway();
        gateway.failOnAddOf = "pt1-vx-2";
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(
                new Scenario.ManagementService("clientReg", List.of("DEV:security-server-owners"), List.of()),
                new RestService(List.of())));
        var failingPlan = new FixturePlanner("t1").plan("VX", spec);

        assertThatThrownBy(() -> new FixtureExecutor(gateway).open(failingPlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("add refused");
        assertThat(gateway.calls).containsExactly(
                "revoke clientReg from DEV:security-server-owners",
                "grant clientReg to DEV:security-server-owners");
    }

    @Test
    void failedManagementRestoreIsReportedAndTheRestOfTheCleanupStillRuns() {
        var gateway = new RecordingGateway();
        gateway.failOnGrantOf = "clientReg";
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(
                new Scenario.ManagementService("clientReg",
                        List.of("DEV:security-server-owners", ScenarioCatalogue.CONSUMER), List.of()),
                new RestService(List.of())));
        var restorePlan = new FixturePlanner("t1").plan("VX", spec);
        var fixture = new FixtureExecutor(gateway).open(restorePlan);

        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fixture cleanup incomplete")
                .hasMessageContaining("restore of")
                .hasMessageContaining("DEV:security-server-owners")
                .hasMessageContaining(ScenarioCatalogue.CONSUMER)
                .hasMessageContaining("grant answered 500");
        assertThat(gateway.calls).endsWith("delete id-pt1-vx-2");
    }

    private static Scenario scenario(String id) {
        return ScenarioCatalogue.all().stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
    }
}
