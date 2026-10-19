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
import org.niis.xroad.e2e.parity.AdminCall.AddEndpoint;
import org.niis.xroad.e2e.parity.AdminCall.AddLocalGroupMember;
import org.niis.xroad.e2e.parity.AdminCall.AddOpenApiDescription;
import org.niis.xroad.e2e.parity.AdminCall.AddRestDescription;
import org.niis.xroad.e2e.parity.AdminCall.AddWsdlDescription;
import org.niis.xroad.e2e.parity.AdminCall.CreateLocalGroup;
import org.niis.xroad.e2e.parity.AdminCall.DeleteDescription;
import org.niis.xroad.e2e.parity.AdminCall.DeleteEndpoint;
import org.niis.xroad.e2e.parity.AdminCall.DeleteLocalGroup;
import org.niis.xroad.e2e.parity.AdminCall.EnableDescription;
import org.niis.xroad.e2e.parity.AdminCall.GrantAccess;
import org.niis.xroad.e2e.parity.AdminCall.GrantEndpointAccess;
import org.niis.xroad.e2e.parity.AdminCall.GrantGroupAccess;
import org.niis.xroad.e2e.parity.AdminCall.GrantGroupEndpointAccess;
import org.niis.xroad.e2e.parity.AdminCall.RemoveLocalGroupMember;
import org.niis.xroad.e2e.parity.AdminCall.ServiceCall;
import org.niis.xroad.e2e.parity.AdminCall.UpdateEndpoint;
import org.niis.xroad.e2e.parity.AdminCall.UpdateOpenApiDescription;
import org.niis.xroad.e2e.parity.Scenario.Deleted;
import org.niis.xroad.e2e.parity.Scenario.EditedTo;
import org.niis.xroad.e2e.parity.Scenario.EndpointSpec;
import org.niis.xroad.e2e.parity.Scenario.FixtureSpec;
import org.niis.xroad.e2e.parity.Scenario.GeneratedEndpointGrant;
import org.niis.xroad.e2e.parity.Scenario.LocalGroupSpec;
import org.niis.xroad.e2e.parity.Scenario.OpenApiService;
import org.niis.xroad.e2e.parity.Scenario.OpenApiSpec;
import org.niis.xroad.e2e.parity.Scenario.RestService;
import org.niis.xroad.e2e.parity.Scenario.WsdlService;
import org.niis.xroad.e2e.parity.Scenario.WsdlShape;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixturePlannerTest {

    private static final FixtureSpec TWO_SERVICES = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(
            new RestService(List.of(ScenarioCatalogue.CONSUMER, ScenarioCatalogue.OTHER_CONSUMER)),
            new RestService(List.of())));

    @Test
    void setupAddsEnablesAndGrantsEachServiceInOrder() {
        var plan = new FixturePlanner("ab12").plan("V09", TWO_SERVICES);

        assertThat(plan.setup()).containsExactly(
                new AddRestDescription(ScenarioCatalogue.PROVIDER, "http://isrest:8080/acl-parity/pab12-v09", "pab12-v09"),
                new EnableDescription("pab12-v09"),
                new GrantAccess(ScenarioCatalogue.PROVIDER, ScenarioCatalogue.CONSUMER, "pab12-v09"),
                new GrantAccess(ScenarioCatalogue.PROVIDER, ScenarioCatalogue.OTHER_CONSUMER, "pab12-v09"),
                new AddRestDescription(ScenarioCatalogue.PROVIDER, "http://isrest:8080/acl-parity/pab12-v09-2", "pab12-v09-2"),
                new EnableDescription("pab12-v09-2"));
    }

    @Test
    void cleanupDeletesInReverseCreationOrder() {
        var plan = new FixturePlanner("ab12").plan("V09", TWO_SERVICES);

        assertThat(plan.cleanup()).containsExactly(
                new DeleteDescription("pab12-v09-2"),
                new DeleteDescription("pab12-v09"));
    }

    @Test
    void namesCarryTheRunTokenAndScenarioIdSoRunsAndScenariosNeverCollide() {
        var firstRun = new FixturePlanner("run1").plan("V01", TWO_SERVICES).serviceCodes();
        var secondRun = new FixturePlanner("run2").plan("V01", TWO_SERVICES).serviceCodes();
        var otherScenario = new FixturePlanner("run1").plan("V02", TWO_SERVICES).serviceCodes();

        assertThat(firstRun).doesNotContainAnyElementsOf(secondRun).doesNotContainAnyElementsOf(otherScenario);
        assertThat(firstRun).allMatch(code -> code.contains("run1") && code.contains("v01"));
    }

    @Test
    void neverTouchesTheSharedDescriptions() {
        var plan = new FixturePlanner("ab12").plan("V01", TWO_SERVICES);

        assertThat(plan.serviceCodes()).noneMatch(code -> code.equals("mock1") || code.equals("restapi"));
        assertThat(serviceCodesOf(plan.setup())).doesNotContain("mock1", "restapi");
        assertThat(serviceCodesOf(plan.cleanup())).doesNotContain("mock1", "restapi");
    }

    @Test
    void catalogueScenariosPlanDistinctCodesAndTheRequestedServiceExists() {
        var planner = new FixturePlanner("ab12");

        for (var scenario : ScenarioCatalogue.all()) {
            var codes = planner.plan(scenario.id(), scenario.fixture()).serviceCodes();

            assertThat(codes).as(scenario.id()).isNotEmpty().doesNotHaveDuplicates();
            assertThat(scenario.checks()).as(scenario.id()).isNotEmpty()
                    .allSatisfy(check -> assertThat(check.request().serviceIndex()).isLessThan(codes.size()));
        }
    }

    @Test
    void singleWsdlAppendsTheCodeTailAndGrantsTheCode() {
        var spec = wsdlSpec(WsdlShape.SINGLE, ".x", List.of(ScenarioCatalogue.CONSUMER));

        var plan = new FixturePlanner("ab12").plan("V129", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v129.x");
        assertThat(plan.setup()).containsExactly(
                new AddWsdlDescription(ScenarioCatalogue.PROVIDER,
                        "http://isrest:8080/acl-parity/wsdl/single.wsdl?code=pab12-v129.x", "pab12-v129.x"),
                new EnableDescription("pab12-v129.x"),
                new GrantAccess(ScenarioCatalogue.PROVIDER, ScenarioCatalogue.CONSUMER, "pab12-v129.x"));
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v129.x"));
    }

    @Test
    void pairWsdlOffersTwoCodesAndGrantsEachOnItsOwn() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new WsdlService(WsdlShape.PAIR, "",
                List.of(List.of(ScenarioCatalogue.CONSUMER), List.of()))));

        var plan = new FixturePlanner("ab12").plan("V04", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v04", "pab12-v04-b");
        assertThat(plan.setup()).containsExactly(
                new AddWsdlDescription(ScenarioCatalogue.PROVIDER,
                        "http://isrest:8080/acl-parity/wsdl/pair.wsdl?code1=pab12-v04&code2=pab12-v04-b", "pab12-v04"),
                new EnableDescription("pab12-v04"),
                new GrantAccess(ScenarioCatalogue.PROVIDER, ScenarioCatalogue.CONSUMER, "pab12-v04"));
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v04"));
    }

    @Test
    void versionsWsdlOffersOneCodeFromTheVersionsStub() {
        var spec = wsdlSpec(WsdlShape.VERSIONS, "", List.of(ScenarioCatalogue.CONSUMER));

        var plan = new FixturePlanner("ab12").plan("V128", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v128");
        assertThat(plan.setup().get(0)).isEqualTo(new AddWsdlDescription(ScenarioCatalogue.PROVIDER,
                "http://isrest:8080/acl-parity/wsdl/versions.wsdl?code=pab12-v128", "pab12-v128"));
    }

    @Test
    void unversionedWsdlOffersOneCodeFromTheUnversionedStub() {
        var spec = wsdlSpec(WsdlShape.SINGLE_UNVERSIONED, "", List.of(ScenarioCatalogue.CONSUMER));

        var plan = new FixturePlanner("ab12").plan("V132a", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v132a");
        assertThat(plan.setup().get(0)).isEqualTo(new AddWsdlDescription(ScenarioCatalogue.PROVIDER,
                "http://isrest:8080/acl-parity/wsdl/single_unversioned.wsdl?code=pab12-v132a", "pab12-v132a"));
    }

    @Test
    void restAndWsdlMixKeepsCodeOrderAndCleansUpEachDescriptionOnceInReverse() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(
                new RestService(List.of()),
                new WsdlService(WsdlShape.PAIR, "", List.of(List.of(), List.of()))));

        var plan = new FixturePlanner("ab12").plan("V09", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v09", "pab12-v09-2", "pab12-v09-2-b");
        assertThat(plan.cleanup()).containsExactly(
                new DeleteDescription("pab12-v09-2"),
                new DeleteDescription("pab12-v09"));
    }

    @Test
    void endpointsAreCreatedAndGrantedAfterTheBaseGrantsAndDeletedInReverseBeforeTheDescription() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new RestService(List.of(),
                List.of(new EndpointSpec("GET", "/foo", List.of(ScenarioCatalogue.CONSUMER)),
                        new EndpointSpec("*", "/**", List.of())))));

        var plan = new FixturePlanner("ab12").plan("V22", spec);

        assertThat(plan.setup()).containsExactly(
                new AddRestDescription(ScenarioCatalogue.PROVIDER, "http://isrest:8080/acl-parity/pab12-v22", "pab12-v22"),
                new EnableDescription("pab12-v22"),
                new AddEndpoint("pab12-v22", "GET", "/foo"),
                new GrantEndpointAccess("pab12-v22", "GET", "/foo", ScenarioCatalogue.CONSUMER),
                new AddEndpoint("pab12-v22", "*", "/**"));
        assertThat(plan.cleanup()).containsExactly(
                new DeleteEndpoint("pab12-v22", "*", "/**"),
                new DeleteEndpoint("pab12-v22", "GET", "/foo"),
                new DeleteDescription("pab12-v22"));
    }

    @Test
    void everyEndpointDeleteInCleanupMatchesAnEndpointCreatedAndKeptInSetup() {
        for (var scenario : ScenarioCatalogue.all()) {
            var plan = new FixturePlanner("ab12").plan(scenario.id(), scenario.fixture());
            var created = plan.setup().stream().filter(AddEndpoint.class::isInstance).count();
            var deletedInSetup = plan.setup().stream().filter(DeleteEndpoint.class::isInstance).count();
            assertThat(plan.cleanup().stream().filter(DeleteEndpoint.class::isInstance).count())
                    .as(scenario.id())
                    .isEqualTo(scenario.fixture().services().stream().anyMatch(OpenApiService.class::isInstance)
                            ? 0 : created - deletedInSetup);
        }
    }

    @Test
    void endpointDeletedInSetupIsNotDeletedAgainInCleanup() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new RestService(List.of(), List.of(
                new EndpointSpec("GET", "/foo", List.of(ScenarioCatalogue.CONSUMER), new Deleted()),
                new EndpointSpec("GET", "/bar", List.of())))));
        var plan = new FixturePlanner("ab12").plan("V36", spec);

        assertThat(plan.setup()).containsExactly(
                new AddRestDescription(ScenarioCatalogue.PROVIDER, "http://isrest:8080/acl-parity/pab12-v36", "pab12-v36"),
                new EnableDescription("pab12-v36"),
                new AddEndpoint("pab12-v36", "GET", "/foo"),
                new GrantEndpointAccess("pab12-v36", "GET", "/foo", ScenarioCatalogue.CONSUMER),
                new DeleteEndpoint("pab12-v36", "GET", "/foo"),
                new AddEndpoint("pab12-v36", "GET", "/bar"));
        assertThat(plan.cleanup()).containsExactly(
                new DeleteEndpoint("pab12-v36", "GET", "/bar"),
                new DeleteDescription("pab12-v36"));
    }

    @Test
    void editedEndpointIsUpdatedAfterItsGrantsAndDeletedUnderTheNewPathInCleanup() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new RestService(List.of(), List.of(
                new EndpointSpec("GET", "/foo", List.of(ScenarioCatalogue.CONSUMER), new EditedTo("POST", "/bar"))))));
        var plan = new FixturePlanner("ab12").plan("V37", spec);

        assertThat(plan.setup()).containsExactly(
                new AddRestDescription(ScenarioCatalogue.PROVIDER, "http://isrest:8080/acl-parity/pab12-v37", "pab12-v37"),
                new EnableDescription("pab12-v37"),
                new AddEndpoint("pab12-v37", "GET", "/foo"),
                new GrantEndpointAccess("pab12-v37", "GET", "/foo", ScenarioCatalogue.CONSUMER),
                new UpdateEndpoint("pab12-v37", "GET", "/foo", "POST", "/bar"));
        assertThat(plan.cleanup()).containsExactly(
                new DeleteEndpoint("pab12-v37", "POST", "/bar"),
                new DeleteDescription("pab12-v37"));
    }

    @Test
    void openApiDescriptionIsAddedFromTheSpecStubGrantedAndDeletedWithoutEndpointDeletes() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new OpenApiService(OpenApiSpec.PETS, List.of(),
                List.of(new GeneratedEndpointGrant("GET", "/pets/*", List.of(ScenarioCatalogue.CONSUMER))), null)));
        var plan = new FixturePlanner("ab12").plan("V38", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v38");
        assertThat(plan.setup()).containsExactly(
                new AddOpenApiDescription(ScenarioCatalogue.PROVIDER,
                        "http://isrest:8080/acl-parity/openapi/pets.yaml?code=pab12-v38", "pab12-v38"),
                new EnableDescription("pab12-v38"),
                new GrantEndpointAccess("pab12-v38", "GET", "/pets/*", ScenarioCatalogue.CONSUMER));
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v38"));
    }

    @Test
    void manualEndpointOfAnOpenApiServiceIsGrantedThenTheDescriptionIsEditedAndNoEndpointIsDeleted() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new OpenApiService(OpenApiSpec.STATUS_ONLY,
                List.of(new EndpointSpec("GET", "/pets/*", List.of(ScenarioCatalogue.CONSUMER))), List.of(),
                OpenApiSpec.PETS)));
        var plan = new FixturePlanner("ab12").plan("V40", spec);

        assertThat(plan.setup()).containsExactly(
                new AddOpenApiDescription(ScenarioCatalogue.PROVIDER,
                        "http://isrest:8080/acl-parity/openapi/status_only.yaml?code=pab12-v40", "pab12-v40"),
                new EnableDescription("pab12-v40"),
                new AddEndpoint("pab12-v40", "GET", "/pets/*"),
                new GrantEndpointAccess("pab12-v40", "GET", "/pets/*", ScenarioCatalogue.CONSUMER),
                new UpdateOpenApiDescription("pab12-v40",
                        "http://isrest:8080/acl-parity/openapi/pets.yaml?code=pab12-v40"));
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v40"));
    }

    @Test
    void wsdlServiceNeedsOneGrantListPerShapeCode() {
        assertThatThrownBy(() -> new WsdlService(WsdlShape.PAIR, "", List.of(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAIR");
    }

    @Test
    void groupIsCreatedFilledAndGrantedBeforeItsServiceAndDeletedAfterTheDescriptions() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(ScenarioCatalogue.PROVIDER, "partners", List.of(ScenarioCatalogue.CONSUMER))));
        var plan = new FixturePlanner("ab12").plan("V60", spec);
        assertThat(plan.setup()).containsExactly(
                new CreateLocalGroup(ScenarioCatalogue.PROVIDER, "pab12-v60-partners"),
                new AddLocalGroupMember(ScenarioCatalogue.PROVIDER, "pab12-v60-partners", ScenarioCatalogue.CONSUMER),
                new AddRestDescription(ScenarioCatalogue.PROVIDER, "http://isrest:8080/acl-parity/pab12-v60", "pab12-v60"),
                new EnableDescription("pab12-v60"),
                new GrantGroupAccess(ScenarioCatalogue.PROVIDER, "pab12-v60-partners", "pab12-v60"));
        assertThat(plan.cleanup()).containsExactly(
                new DeleteDescription("pab12-v60"),
                new DeleteLocalGroup(ScenarioCatalogue.PROVIDER, "pab12-v60-partners"));
    }

    @Test
    void removedMembersAndDeletedGroupAreAppliedInSetupAfterTheGrantsAndNotDeletedAgainInCleanup() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(ScenarioCatalogue.PROVIDER, "partners", List.of(ScenarioCatalogue.CONSUMER),
                        List.of(ScenarioCatalogue.CONSUMER), true)));
        var plan = new FixturePlanner("ab12").plan("V63", spec);
        assertThat(plan.setup()).endsWith(
                new GrantGroupAccess(ScenarioCatalogue.PROVIDER, "pab12-v63-partners", "pab12-v63"),
                new RemoveLocalGroupMember(ScenarioCatalogue.PROVIDER, "pab12-v63-partners", ScenarioCatalogue.CONSUMER),
                new DeleteLocalGroup(ScenarioCatalogue.PROVIDER, "pab12-v63-partners"));
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v63"));
    }

    @Test
    void sameGroupCodeUnderTwoOwnersIsPlannedForBothAndOnlyTheProvidersIsGranted() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(ScenarioCatalogue.PROVIDER, "partners", List.of(ScenarioCatalogue.OTHER_CONSUMER)),
                        new LocalGroupSpec(SubsystemPool.SECOND_OWNER, "partners", List.of(ScenarioCatalogue.CONSUMER))));
        var plan = new FixturePlanner("ab12").plan("V65", spec);
        assertThat(plan.setup()).filteredOn(CreateLocalGroup.class::isInstance).containsExactly(
                new CreateLocalGroup(ScenarioCatalogue.PROVIDER, "pab12-v65-partners"),
                new CreateLocalGroup(SubsystemPool.SECOND_OWNER, "pab12-v65-partners"));
        assertThat(plan.setup()).filteredOn(GrantGroupAccess.class::isInstance).containsExactly(
                new GrantGroupAccess(ScenarioCatalogue.PROVIDER, "pab12-v65-partners", "pab12-v65"));
        assertThat(plan.cleanup()).containsExactly(
                new DeleteDescription("pab12-v65"),
                new DeleteLocalGroup(SubsystemPool.SECOND_OWNER, "pab12-v65-partners"),
                new DeleteLocalGroup(ScenarioCatalogue.PROVIDER, "pab12-v65-partners"));
    }

    @Test
    void groupCodesCarryTheRunTokenSoRunsNeverCollide() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new RestService(List.of())),
                List.of(new LocalGroupSpec(ScenarioCatalogue.PROVIDER, "partners", List.of())));
        var firstRun = new FixturePlanner("run1").plan("V65", spec).setup();
        var secondRun = new FixturePlanner("run2").plan("V65", spec).setup();
        assertThat(firstRun.get(0)).isEqualTo(new CreateLocalGroup(ScenarioCatalogue.PROVIDER, "prun1-v65-partners"));
        assertThat(secondRun.get(0)).isEqualTo(new CreateLocalGroup(ScenarioCatalogue.PROVIDER, "prun2-v65-partners"));
    }

    @Test
    void endpointGrantedToAGroupGetsAGroupEndpointGrant() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new RestService(List.of(), List.of(
                new EndpointSpec("GET", "/a", List.of(ScenarioCatalogue.CONSUMER)),
                new EndpointSpec("GET", "/b", List.of(), List.of("partners"))))),
                List.of(new LocalGroupSpec(ScenarioCatalogue.PROVIDER, "partners", List.of(ScenarioCatalogue.CONSUMER))));
        var plan = new FixturePlanner("ab12").plan("V66", spec);
        var endpointGrants = plan.setup().stream()
                .filter(call -> call instanceof GrantEndpointAccess || call instanceof GrantGroupEndpointAccess)
                .toList();

        assertThat(endpointGrants).containsExactly(
                new GrantEndpointAccess("pab12-v66", "GET", "/a", ScenarioCatalogue.CONSUMER),
                new GrantGroupEndpointAccess(ScenarioCatalogue.PROVIDER, "pab12-v66-partners", "pab12-v66",
                        "GET", "/b"));
    }

    @Test
    void grantToAGroupTheProviderDoesNotOwnIsRejected() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(SubsystemPool.SECOND_OWNER, "partners", List.of())));
        var planner = new FixturePlanner("ab12");
        assertThatThrownBy(() -> planner.plan("V65", spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partners");
    }

    private static List<String> serviceCodesOf(List<AdminCall> calls) {
        return calls.stream().filter(ServiceCall.class::isInstance)
                .map(call -> ((ServiceCall) call).serviceCode()).toList();
    }

    private static FixtureSpec wsdlSpec(WsdlShape shape, String codeTail, List<String> granted) {
        return new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new WsdlService(shape, codeTail, List.of(granted))));
    }
}
