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

package org.niis.xroad.e2e.parity.acl;

import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.parity.acl.AclScenario.Delete;
import org.niis.xroad.e2e.parity.acl.AclScenario.Deleted;
import org.niis.xroad.e2e.parity.acl.AclScenario.Disable;
import org.niis.xroad.e2e.parity.acl.AclScenario.EditedTo;
import org.niis.xroad.e2e.parity.acl.AclScenario.Enable;
import org.niis.xroad.e2e.parity.acl.AclScenario.EndpointSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.FixtureSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.GeneratedEndpointGrant;
import org.niis.xroad.e2e.parity.acl.AclScenario.LocalGroupSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.ManagementService;
import org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiService;
import org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.RenameService;
import org.niis.xroad.e2e.parity.acl.AclScenario.RestService;
import org.niis.xroad.e2e.parity.acl.AclScenario.UndefinedService;
import org.niis.xroad.e2e.parity.acl.AclScenario.WsdlService;
import org.niis.xroad.e2e.parity.acl.AclScenario.WsdlShape;
import org.niis.xroad.e2e.parity.acl.AdminCall.AddEndpoint;
import org.niis.xroad.e2e.parity.acl.AdminCall.AddLocalGroupMember;
import org.niis.xroad.e2e.parity.acl.AdminCall.AddOpenApiDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.AddRestDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.AddWsdlDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.CreateLocalGroup;
import org.niis.xroad.e2e.parity.acl.AdminCall.DeleteDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.DeleteEndpoint;
import org.niis.xroad.e2e.parity.acl.AdminCall.DeleteLocalGroup;
import org.niis.xroad.e2e.parity.acl.AdminCall.DisableDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.EnableDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.GrantAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.GrantEndpointAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.GrantGroupAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.GrantGroupEndpointAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.GrantManagementAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.OverrideBackendStub;
import org.niis.xroad.e2e.parity.acl.AdminCall.RefreshDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.RemoveBackendStubs;
import org.niis.xroad.e2e.parity.acl.AdminCall.RemoveLocalGroupMember;
import org.niis.xroad.e2e.parity.acl.AdminCall.RenameRestService;
import org.niis.xroad.e2e.parity.acl.AdminCall.RevokeAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.RevokeManagementAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.ServiceCall;
import org.niis.xroad.e2e.parity.acl.AdminCall.UpdateEndpoint;
import org.niis.xroad.e2e.parity.acl.AdminCall.UpdateOpenApiDescription;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.globalGroupGranted;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.globalconfPhased;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.globalconfRow;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.management;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.openApiGranted;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.phase;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.phased;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.provided;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.restService;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.restToGlobalGroup;

class FixturePlannerTest {

    private static final FixtureSpec TWO_SERVICES = new FixtureSpec(AclParticipants.PROVIDER, List.of(
            new RestService(List.of(AclParticipants.CONSUMER, AclParticipants.OTHER_CONSUMER)),
            new RestService(List.of())));

    @Test
    void setupAddsEnablesAndGrantsEachServiceInOrder() {
        var plan = new FixturePlanner("ab12").plan("V09", TWO_SERVICES);

        assertThat(plan.setup()).containsExactly(
                new AddRestDescription(AclParticipants.PROVIDER, "http://isrest:8080/acl-parity/pab12-v09", "pab12-v09"),
                new EnableDescription("pab12-v09"),
                new GrantAccess(AclParticipants.PROVIDER, AclParticipants.CONSUMER, "pab12-v09"),
                new GrantAccess(AclParticipants.PROVIDER, AclParticipants.OTHER_CONSUMER, "pab12-v09"),
                new AddRestDescription(AclParticipants.PROVIDER, "http://isrest:8080/acl-parity/pab12-v09-2", "pab12-v09-2"),
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
    void singleWsdlAppendsTheCodeTailAndGrantsTheCode() {
        var spec = wsdlSpec(WsdlShape.SINGLE, ".x", List.of(AclParticipants.CONSUMER));

        var plan = new FixturePlanner("ab12").plan("V129", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v129.x");
        assertThat(plan.setup()).containsExactly(
                new AddWsdlDescription(AclParticipants.PROVIDER,
                        "http://isrest:8080/acl-parity/wsdl/single.wsdl?code=pab12-v129.x", "pab12-v129.x"),
                new EnableDescription("pab12-v129.x"),
                new GrantAccess(AclParticipants.PROVIDER, AclParticipants.CONSUMER, "pab12-v129.x"));
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v129.x"));
    }

    @Test
    void pairWsdlOffersTwoCodesAndGrantsEachOnItsOwn() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new WsdlService(WsdlShape.PAIR, "",
                List.of(List.of(AclParticipants.CONSUMER), List.of()))));

        var plan = new FixturePlanner("ab12").plan("V04", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v04", "pab12-v04-b");
        assertThat(plan.setup()).containsExactly(
                new AddWsdlDescription(AclParticipants.PROVIDER,
                        "http://isrest:8080/acl-parity/wsdl/pair.wsdl?code1=pab12-v04&code2=pab12-v04-b", "pab12-v04"),
                new EnableDescription("pab12-v04"),
                new GrantAccess(AclParticipants.PROVIDER, AclParticipants.CONSUMER, "pab12-v04"));
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v04"));
    }

    @Test
    void versionsWsdlOffersOneCodeFromTheVersionsStub() {
        var spec = wsdlSpec(WsdlShape.VERSIONS, "", List.of(AclParticipants.CONSUMER));

        var plan = new FixturePlanner("ab12").plan("V128", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v128");
        assertThat(plan.setup().get(0)).isEqualTo(new AddWsdlDescription(AclParticipants.PROVIDER,
                "http://isrest:8080/acl-parity/wsdl/versions.wsdl?code=pab12-v128", "pab12-v128"));
    }

    @Test
    void unversionedWsdlOffersOneCodeFromTheUnversionedStub() {
        var spec = wsdlSpec(WsdlShape.SINGLE_UNVERSIONED, "", List.of(AclParticipants.CONSUMER));

        var plan = new FixturePlanner("ab12").plan("V132a", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v132a");
        assertThat(plan.setup().get(0)).isEqualTo(new AddWsdlDescription(AclParticipants.PROVIDER,
                "http://isrest:8080/acl-parity/wsdl/single_unversioned.wsdl?code=pab12-v132a", "pab12-v132a"));
    }

    @Test
    void restAndWsdlMixKeepsCodeOrderAndCleansUpEachDescriptionOnceInReverse() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(
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
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new RestService(List.of(),
                List.of(new EndpointSpec("GET", "/foo", List.of(AclParticipants.CONSUMER)),
                        new EndpointSpec("*", "/**", List.of())))));

        var plan = new FixturePlanner("ab12").plan("V22", spec);

        assertThat(plan.setup()).containsExactly(
                new AddRestDescription(AclParticipants.PROVIDER, "http://isrest:8080/acl-parity/pab12-v22", "pab12-v22"),
                new EnableDescription("pab12-v22"),
                new AddEndpoint("pab12-v22", "GET", "/foo"),
                new GrantEndpointAccess("pab12-v22", "GET", "/foo", AclParticipants.CONSUMER),
                new AddEndpoint("pab12-v22", "*", "/**"));
        assertThat(plan.cleanup()).containsExactly(
                new DeleteEndpoint("pab12-v22", "*", "/**"),
                new DeleteEndpoint("pab12-v22", "GET", "/foo"),
                new DeleteDescription("pab12-v22"));
    }

    @Test
    void endpointDeletedInSetupIsNotDeletedAgainInCleanup() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new RestService(List.of(), List.of(
                new EndpointSpec("GET", "/foo", List.of(AclParticipants.CONSUMER), new Deleted()),
                new EndpointSpec("GET", "/bar", List.of())))));
        var plan = new FixturePlanner("ab12").plan("V36", spec);

        assertThat(plan.setup()).containsExactly(
                new AddRestDescription(AclParticipants.PROVIDER, "http://isrest:8080/acl-parity/pab12-v36", "pab12-v36"),
                new EnableDescription("pab12-v36"),
                new AddEndpoint("pab12-v36", "GET", "/foo"),
                new GrantEndpointAccess("pab12-v36", "GET", "/foo", AclParticipants.CONSUMER),
                new DeleteEndpoint("pab12-v36", "GET", "/foo"),
                new AddEndpoint("pab12-v36", "GET", "/bar"));
        assertThat(plan.cleanup()).containsExactly(
                new DeleteEndpoint("pab12-v36", "GET", "/bar"),
                new DeleteDescription("pab12-v36"));
    }

    @Test
    void editedEndpointIsUpdatedAfterItsGrantsAndDeletedUnderTheNewPathInCleanup() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new RestService(List.of(), List.of(
                new EndpointSpec("GET", "/foo", List.of(AclParticipants.CONSUMER), new EditedTo("POST", "/bar"))))));
        var plan = new FixturePlanner("ab12").plan("V37", spec);

        assertThat(plan.setup()).containsExactly(
                new AddRestDescription(AclParticipants.PROVIDER, "http://isrest:8080/acl-parity/pab12-v37", "pab12-v37"),
                new EnableDescription("pab12-v37"),
                new AddEndpoint("pab12-v37", "GET", "/foo"),
                new GrantEndpointAccess("pab12-v37", "GET", "/foo", AclParticipants.CONSUMER),
                new UpdateEndpoint("pab12-v37", "GET", "/foo", "POST", "/bar"));
        assertThat(plan.cleanup()).containsExactly(
                new DeleteEndpoint("pab12-v37", "POST", "/bar"),
                new DeleteDescription("pab12-v37"));
    }

    @Test
    void openApiDescriptionIsAddedFromTheSpecStubGrantedAndDeletedWithoutEndpointDeletes() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new OpenApiService(OpenApiSpec.PETS, List.of(),
                List.of(new GeneratedEndpointGrant("GET", "/pets/*", List.of(AclParticipants.CONSUMER))), null)));
        var plan = new FixturePlanner("ab12").plan("V38", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v38");
        assertThat(plan.setup()).containsExactly(
                new AddOpenApiDescription(AclParticipants.PROVIDER,
                        "http://isrest:8080/acl-parity/openapi/pets.yaml?code=pab12-v38", "pab12-v38"),
                new EnableDescription("pab12-v38"),
                new GrantEndpointAccess("pab12-v38", "GET", "/pets/*", AclParticipants.CONSUMER));
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v38"));
    }

    @Test
    void manualEndpointOfAnOpenApiServiceIsGrantedThenTheDescriptionIsEditedAndNoEndpointIsDeleted() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new OpenApiService(OpenApiSpec.STATUS_ONLY,
                List.of(new EndpointSpec("GET", "/pets/*", List.of(AclParticipants.CONSUMER))), List.of(),
                OpenApiSpec.PETS)));
        var plan = new FixturePlanner("ab12").plan("V40", spec);

        assertThat(plan.setup()).containsExactly(
                new AddOpenApiDescription(AclParticipants.PROVIDER,
                        "http://isrest:8080/acl-parity/openapi/status_only.yaml?code=pab12-v40", "pab12-v40"),
                new EnableDescription("pab12-v40"),
                new AddEndpoint("pab12-v40", "GET", "/pets/*"),
                new GrantEndpointAccess("pab12-v40", "GET", "/pets/*", AclParticipants.CONSUMER),
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
        var spec = new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(AclParticipants.PROVIDER, "partners", List.of(AclParticipants.CONSUMER))));
        var plan = new FixturePlanner("ab12").plan("V60", spec);
        assertThat(plan.setup()).containsExactly(
                new CreateLocalGroup(AclParticipants.PROVIDER, "pab12-v60-partners"),
                new AddLocalGroupMember(AclParticipants.PROVIDER, "pab12-v60-partners", AclParticipants.CONSUMER),
                new AddRestDescription(AclParticipants.PROVIDER, "http://isrest:8080/acl-parity/pab12-v60", "pab12-v60"),
                new EnableDescription("pab12-v60"),
                new GrantGroupAccess(AclParticipants.PROVIDER, "pab12-v60-partners", "pab12-v60"));
        assertThat(plan.cleanup()).containsExactly(
                new DeleteDescription("pab12-v60"),
                new DeleteLocalGroup(AclParticipants.PROVIDER, "pab12-v60-partners"));
    }

    @Test
    void removedMembersAndDeletedGroupAreAppliedInSetupAfterTheGrantsAndStillCoveredByCleanup() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(AclParticipants.PROVIDER, "partners", List.of(AclParticipants.CONSUMER),
                        List.of(AclParticipants.CONSUMER), true)));
        var plan = new FixturePlanner("ab12").plan("V63", spec);
        assertThat(plan.setup()).endsWith(
                new GrantGroupAccess(AclParticipants.PROVIDER, "pab12-v63-partners", "pab12-v63"),
                new RemoveLocalGroupMember(AclParticipants.PROVIDER, "pab12-v63-partners", AclParticipants.CONSUMER),
                new DeleteLocalGroup(AclParticipants.PROVIDER, "pab12-v63-partners"));
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v63"),
                new DeleteLocalGroup(AclParticipants.PROVIDER, "pab12-v63-partners"));
    }

    @Test
    void sameGroupCodeUnderTwoOwnersIsPlannedForBothAndOnlyTheProvidersIsGranted() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(AclParticipants.PROVIDER, "partners", List.of(AclParticipants.OTHER_CONSUMER)),
                        new LocalGroupSpec(SubsystemPool.SECOND_OWNER, "partners", List.of(AclParticipants.CONSUMER))));
        var plan = new FixturePlanner("ab12").plan("V65", spec);
        assertThat(plan.setup()).filteredOn(CreateLocalGroup.class::isInstance).containsExactly(
                new CreateLocalGroup(AclParticipants.PROVIDER, "pab12-v65-partners"),
                new CreateLocalGroup(SubsystemPool.SECOND_OWNER, "pab12-v65-partners"));
        assertThat(plan.setup()).filteredOn(GrantGroupAccess.class::isInstance).containsExactly(
                new GrantGroupAccess(AclParticipants.PROVIDER, "pab12-v65-partners", "pab12-v65"));
        assertThat(plan.cleanup()).containsExactly(
                new DeleteDescription("pab12-v65"),
                new DeleteLocalGroup(SubsystemPool.SECOND_OWNER, "pab12-v65-partners"),
                new DeleteLocalGroup(AclParticipants.PROVIDER, "pab12-v65-partners"));
    }

    @Test
    void groupCodesCarryTheRunTokenSoRunsNeverCollide() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new RestService(List.of())),
                List.of(new LocalGroupSpec(AclParticipants.PROVIDER, "partners", List.of())));
        var firstRun = new FixturePlanner("run1").plan("V65", spec).setup();
        var secondRun = new FixturePlanner("run2").plan("V65", spec).setup();
        assertThat(firstRun.get(0)).isEqualTo(new CreateLocalGroup(AclParticipants.PROVIDER, "prun1-v65-partners"));
        assertThat(secondRun.get(0)).isEqualTo(new CreateLocalGroup(AclParticipants.PROVIDER, "prun2-v65-partners"));
    }

    @Test
    void endpointGrantedToAGroupGetsAGroupEndpointGrant() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new RestService(List.of(), List.of(
                new EndpointSpec("GET", "/a", List.of(AclParticipants.CONSUMER)),
                new EndpointSpec("GET", "/b", List.of(), List.of("partners"))))),
                List.of(new LocalGroupSpec(AclParticipants.PROVIDER, "partners", List.of(AclParticipants.CONSUMER))));
        var plan = new FixturePlanner("ab12").plan("V66", spec);
        var endpointGrants = plan.setup().stream()
                .filter(call -> call instanceof GrantEndpointAccess || call instanceof GrantGroupEndpointAccess)
                .toList();

        assertThat(endpointGrants).containsExactly(
                new GrantEndpointAccess("pab12-v66", "GET", "/a", AclParticipants.CONSUMER),
                new GrantGroupEndpointAccess(AclParticipants.PROVIDER, "pab12-v66-partners", "pab12-v66",
                        "GET", "/b"));
    }

    @Test
    void grantToAGroupTheProviderDoesNotOwnIsRejected() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(SubsystemPool.SECOND_OWNER, "partners", List.of())));
        var planner = new FixturePlanner("ab12");
        assertThatThrownBy(() -> planner.plan("V65", spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partners");
    }

    @Test
    void disableAndEnableRunAfterTheGrantsInTheOrderOfTheChangesAndCleanupStillDeletesTheDescription() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new RestService(List.of())), List.of(),
                List.of(new Disable(0, null), new Disable(0, "later"), new Enable(0)));
        var plan = new FixturePlanner("ab12").plan("V124", spec);

        assertThat(plan.setup()).endsWith(
                new DisableDescription("pab12-v124", null),
                new DisableDescription("pab12-v124", "later"),
                new EnableDescription("pab12-v124"));
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v124"));
    }

    @Test
    void descriptionDeletedInSetupIsAlsoDeletedInCleanupAlongsideTheOtherOne() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of()), new RestService(List.of())), List.of(), List.of(new Delete(0)));
        var plan = new FixturePlanner("ab12").plan("V125", spec);

        assertThat(plan.setup()).endsWith(new DeleteDescription("pab12-v125"));
        assertThat(plan.cleanup()).containsExactly(
                new DeleteDescription("pab12-v125-2"), new DeleteDescription("pab12-v125"));
    }

    @Test
    void renamedCodeIsAppendedAfterTheCreatedCodesAndTheBackendUrlStaysTheOldOne() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new RestService(List.of())), List.of(),
                List.of(new RenameService(0)));
        var plan = new FixturePlanner("ab12").plan("V127", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v127", "pab12-v127-renamed");
        assertThat(plan.setup()).endsWith(new RenameRestService("pab12-v127",
                "http://isrest:8080/acl-parity/pab12-v127", "pab12-v127-renamed"));
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v127"));
    }

    @Test
    void undefinedServiceReservesACodeAndCreatesNothing() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new UndefinedService(), new RestService(List.of())));
        var plan = new FixturePlanner("ab12").plan("V133", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v133", "pab12-v133-2");
        assertThat(serviceCodesOf(plan.setup())).containsOnly("pab12-v133-2");
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v133-2"));
    }

    @Test
    void changeOfAnUndefinedServiceIsRejected() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new UndefinedService()), List.of(),
                List.of(new Enable(0)));
        var planner = new FixturePlanner("ab12");

        assertThatThrownBy(() -> planner.plan("V133", spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no description");
    }

    @Test
    void renameOfAWsdlServiceIsRejected() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new WsdlService(WsdlShape.SINGLE, "", List.of(List.of()))), List.of(),
                List.of(new RenameService(0)));
        var planner = new FixturePlanner("ab12");

        assertThatThrownBy(() -> planner.plan("V127", spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a REST service");
    }

    private static List<String> serviceCodesOf(List<AdminCall> calls) {
        return calls.stream().filter(ServiceCall.class::isInstance)
                .map(call -> ((ServiceCall) call).serviceCode()).toList();
    }

    @Test
    void grantAndRevokeRowsPlanOneCallPerPhaseAndNeedNoBackendCleanup() {
        var grantRow = phased("V10", restService(List.of()), List.of(),
                phase(new AclScenario.Grant(0, AclParticipants.CONSUMER))).as("grant row");
        var revokeRow = phased("V11", restService(AclScenarioBuilders.CONSUMER_ONLY), List.of(),
                phase(new AclScenario.Revoke(0, AclParticipants.CONSUMER))).as("revoke row");
        var grant = new FixturePlanner("ab12").plan(grantRow);
        var revoke = new FixturePlanner("ab12").plan(revokeRow);

        assertThat(grant.phases()).containsExactly(
                List.of(new GrantAccess(AclParticipants.PROVIDER, AclParticipants.CONSUMER, "pab12-v10")));
        assertThat(revoke.phases()).containsExactly(
                List.of(new RevokeAccess(AclParticipants.PROVIDER, AclParticipants.CONSUMER, "pab12-v11")));
        assertThat(grant.cleanup()).containsExactly(new DeleteDescription("pab12-v10"));
        assertThat(revoke.cleanup()).doesNotContain(new RemoveBackendStubs());
    }

    @Test
    void rowWithoutPhasesPlansNone() {
        assertThat(new FixturePlanner("ab12").plan("V09", TWO_SERVICES).phases()).isEmpty();
    }

    @Test
    void openApiSwapServesTheReplacementSpecAtTheOriginalPathAndRefreshesTheDescription() {
        var row = phased("V39", openApiGranted(OpenApiSpec.PETS, "GET", "/pets/*"), List.of(),
                phase(List.of(new AclScenario.SwapOpenApiSpec(0, OpenApiSpec.STATUS_ONLY),
                        new AclScenario.Refresh(0)))).as("swap row");

        var plan = new FixturePlanner("ab12").plan(row);

        assertThat(plan.phases()).containsExactly(List.of(
                new OverrideBackendStub("acl_parity_openapi_status_only", "/acl-parity/openapi/pets.yaml",
                        Map.of("code", "pab12-v39"), Map.of()),
                new RefreshDescription("pab12-v39")));
        assertThat(plan.cleanup()).containsExactly(new RemoveBackendStubs(), new DeleteDescription("pab12-v39"));
    }

    @Test
    void wsdlReplacementKeepsTheFirstCodeDropsTheSecondAndAppendsANewCode() {
        var consumerOnly = List.of(AclParticipants.CONSUMER);
        var fixture = new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new WsdlService(WsdlShape.PAIR, "", List.of(consumerOnly, consumerOnly))));
        var pair = new AclScenario("V126", "test row", fixture,
                List.of(), List.of(new AclScenario.Phase(
                List.of(new AclScenario.ReplaceWsdlSecondService(0), new AclScenario.Refresh(0)), List.of())),
                AclScenario.WaitClass.ACL_CACHE);

        var plan = new FixturePlanner("ab12").plan(pair);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v126", "pab12-v126-b", "pab12-v126-new");
        assertThat(plan.phases()).containsExactly(List.of(
                new OverrideBackendStub("acl_parity_wsdl_pair", "/acl-parity/wsdl/pair.wsdl",
                        Map.of("code1", "pab12-v126", "code2", "pab12-v126-b"),
                        Map.of("{{request.query.code2}}", "pab12-v126-new")),
                new RefreshDescription("pab12-v126")));
        assertThat(plan.cleanup()).containsExactly(new RemoveBackendStubs(), new DeleteDescription("pab12-v126"));
    }

    @Test
    void swapOfAServiceThatIsNotOpenApiAndReplacementOfOneThatIsNotAPairAreRejected() {
        var rest = new FixtureSpec(AclParticipants.PROVIDER, List.of(new RestService(List.of())));
        var single = wsdlSpec(WsdlShape.SINGLE, "", List.of());

        assertThatThrownBy(() -> new FixturePlanner("ab12").plan(withPhase(rest,
                new AclScenario.SwapOpenApiSpec(0, OpenApiSpec.PETS))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not an OpenAPI service");
        assertThatThrownBy(() -> new FixturePlanner("ab12").plan(withPhase(single,
                new AclScenario.ReplaceWsdlSecondService(0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a PAIR WSDL service");
    }

    @Test
    void globalGroupIsNamespacedCreatedInSetupAndDeletedLastInCleanup() {
        var row = globalconfRow("V68", globalGroupGranted(
                new AclScenario.GlobalGroupSpec(AclParticipants.GLOBAL_GROUP, AclScenarioBuilders.CONSUMER_ONLY)))
                .as("global group row");

        var plan = new FixturePlanner("ab12").plan(row);

        assertThat(plan.setup()).contains(new AdminCall.CreateGlobalGroup("pab12-v68-everyone"),
                new AdminCall.AddGlobalGroupMember("pab12-v68-everyone", AclParticipants.CONSUMER),
                new AdminCall.GrantGlobalGroupAccess(AclParticipants.PROVIDER, "pab12-v68-everyone", "pab12-v68"));
        assertThat(plan.cleanup()).last().isEqualTo(new AdminCall.DeleteGlobalGroup("pab12-v68-everyone"));
    }

    @Test
    void ownersGroupKeepsItsCodeAndIsNeverCreatedOrDeleted() {
        var row = globalconfRow("V72",
                provided(restToGlobalGroup(AclScenario.GlobalGroupSpec.SECURITY_SERVER_OWNERS)))
                .as("owners group row");

        var plan = new FixturePlanner("ab12").plan(row);

        assertThat(plan.setup()).contains(new AdminCall.GrantGlobalGroupAccess(AclParticipants.PROVIDER,
                "security-server-owners", "pab12-v72"));
        assertThat(plan.setup()).noneMatch(AdminCall.CreateGlobalGroup.class::isInstance);
        assertThat(plan.cleanup()).noneMatch(AdminCall.DeleteGlobalGroup.class::isInstance);
    }

    @Test
    void ownersGroupCannotBeDeclaredAndAnUndeclaredGroupCannotBeGranted() {
        var owners = new FixtureSpec(AclParticipants.PROVIDER, List.of(new RestService(List.of())), List.of(),
                List.of(), List.of(new AclScenario.GlobalGroupSpec("security-server-owners")));
        var undeclared = new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of(), List.of("nobody"))));

        assertThatThrownBy(() -> new FixturePlanner("ab12").plan("V1", owners))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("never created");
        assertThatThrownBy(() -> new FixturePlanner("ab12").plan("V1", undeclared))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not created");
    }

    @Test
    void globalGroupPhaseChangesUseTheNamespacedCode() {
        var row = globalconfPhased("V75",
                globalGroupGranted(new AclScenario.GlobalGroupSpec(AclParticipants.GLOBAL_GROUP)), List.of(),
                phase(new AclScenario.AddGlobalGroupMember(AclParticipants.GLOBAL_GROUP, AclParticipants.CONSUMER)),
                phase(new AclScenario.RemoveGlobalGroupMember(AclParticipants.GLOBAL_GROUP,
                        AclParticipants.CONSUMER))).as("member change row");

        var plan = new FixturePlanner("ab12").plan(row);

        assertThat(plan.phases().get(0)).containsExactly(
                new AdminCall.AddGlobalGroupMember("pab12-v75-everyone", AclParticipants.CONSUMER));
        assertThat(plan.phases().get(1)).containsExactly(
                new AdminCall.RemoveGlobalGroupMember("pab12-v75-everyone", AclParticipants.CONSUMER));
    }

    @Test
    void managementServiceKeepsItsCodeRevokesThenGrantsInSetupAndCreatesAndDeletesNothing() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new ManagementService("clientReg",
                List.of("DEV:security-server-owners"), List.of(AclParticipants.CONSUMER))));

        var plan = new FixturePlanner("ab12").plan("V106a", spec);

        assertThat(plan.serviceCodes()).containsExactly("clientReg");
        assertThat(plan.setup()).containsExactly(
                new RevokeManagementAccess(AclParticipants.PROVIDER, "DEV:security-server-owners", "clientReg"),
                new GrantManagementAccess(AclParticipants.PROVIDER, AclParticipants.CONSUMER, "clientReg"));
        assertThat(plan.cleanup()).isEmpty();
    }

    @Test
    void managementRowMovesTheGrantFromTheOwnersGroupToTheSubsystemInItsPhaseAndChangesNothingInSetup() {
        var row = phased("V106a",
                management(new ManagementService(AclParticipants.CLIENT_REG, List.of(), List.of())), List.of(),
                phase(List.of(new AclScenario.Revoke(0, AclParticipants.OWNERS_GROUP),
                        new AclScenario.Grant(0, AclParticipants.CONSUMER)))).as("management move row");

        var plan = new FixturePlanner("ab12").plan(row);

        assertThat(plan.setup()).isEmpty();
        assertThat(plan.phases()).containsExactly(List.of(
                new RevokeManagementAccess("DEV:COM:1234:MANAGEMENT", "DEV:security-server-owners", "clientReg"),
                new GrantManagementAccess("DEV:COM:1234:MANAGEMENT", AclParticipants.CONSUMER, "clientReg")));
        assertThat(plan.cleanup()).isEmpty();
    }

    @Test
    void managementRowGrantsItsLocalGroupByTheNamespacedCodeInThePhase() {
        var row = phased("V106b",
                management(new ManagementService(AclParticipants.CLIENT_REG, List.of(AclParticipants.OWNERS_GROUP),
                        List.of()),
                        new LocalGroupSpec(AclParticipants.MANAGEMENT, AclParticipants.GROUP,
                                List.of(AclParticipants.MEMBER_OF_CONSUMER))),
                List.of(),
                phase(new AclScenario.GrantGroup(0, AclParticipants.GROUP))).as("management group row");

        var plan = new FixturePlanner("ab12").plan(row);

        assertThat(plan.setup()).contains(
                new CreateLocalGroup("DEV:COM:1234:MANAGEMENT", "pab12-v106b-partners"));
        assertThat(plan.phases()).containsExactly(List.of(
                new GrantGroupAccess("DEV:COM:1234:MANAGEMENT", "pab12-v106b-partners", "clientReg")));
        assertThat(plan.cleanup()).containsExactly(
                new DeleteLocalGroup("DEV:COM:1234:MANAGEMENT", "pab12-v106b-partners"));
    }

    @Test
    void grantAndRevokeOfAManagementServiceInAPhasePlanManagementCalls() {
        var fixture = new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new ManagementService("clientReg", List.of(), List.of()), new RestService(List.of())));
        var row = new AclScenario("VX", "test row", fixture, List.of(), List.of(new AclScenario.Phase(List.of(
                new AclScenario.Grant(0, AclParticipants.CONSUMER), new AclScenario.Revoke(0, AclParticipants.CONSUMER),
                new AclScenario.Grant(1, AclParticipants.CONSUMER)), List.of())), AclScenario.WaitClass.ACL_CACHE);

        var plan = new FixturePlanner("ab12").plan(row);

        assertThat(plan.phases()).containsExactly(List.of(
                new GrantManagementAccess(AclParticipants.PROVIDER, AclParticipants.CONSUMER, "clientReg"),
                new RevokeManagementAccess(AclParticipants.PROVIDER, AclParticipants.CONSUMER, "clientReg"),
                new GrantAccess(AclParticipants.PROVIDER, AclParticipants.CONSUMER, "pab12-vx-2")));
    }

    private static AclScenario withPhase(FixtureSpec fixture, AclScenario.PhaseChange change) {
        return new AclScenario("VX", "test row", fixture, List.of(), List.of(new AclScenario.Phase(List.of(change),
                List.of())), AclScenario.WaitClass.ACL_CACHE);
    }

    private static FixtureSpec wsdlSpec(WsdlShape shape, String codeTail, List<String> granted) {
        return new FixtureSpec(AclParticipants.PROVIDER, List.of(new WsdlService(shape, codeTail, List.of(granted))));
    }
}
