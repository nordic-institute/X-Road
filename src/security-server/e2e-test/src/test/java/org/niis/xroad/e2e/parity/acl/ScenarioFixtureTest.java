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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.parity.acl.AclScenario.AddGlobalGroupMember;
import org.niis.xroad.e2e.parity.acl.AclScenario.Delete;
import org.niis.xroad.e2e.parity.acl.AclScenario.DeleteDescription;
import org.niis.xroad.e2e.parity.acl.AclScenario.DeleteGlobalGroup;
import org.niis.xroad.e2e.parity.acl.AclScenario.DeleteLocalGroup;
import org.niis.xroad.e2e.parity.acl.AclScenario.Deleted;
import org.niis.xroad.e2e.parity.acl.AclScenario.Disable;
import org.niis.xroad.e2e.parity.acl.AclScenario.EditedTo;
import org.niis.xroad.e2e.parity.acl.AclScenario.Enable;
import org.niis.xroad.e2e.parity.acl.AclScenario.EndpointSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.FixtureSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.GeneratedEndpointGrant;
import org.niis.xroad.e2e.parity.acl.AclScenario.GlobalGroupSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.Grant;
import org.niis.xroad.e2e.parity.acl.AclScenario.GrantGroup;
import org.niis.xroad.e2e.parity.acl.AclScenario.LocalGroupSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.ManagementService;
import org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiService;
import org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.Phase;
import org.niis.xroad.e2e.parity.acl.AclScenario.PhaseChange;
import org.niis.xroad.e2e.parity.acl.AclScenario.Refresh;
import org.niis.xroad.e2e.parity.acl.AclScenario.RemoveGlobalGroupMember;
import org.niis.xroad.e2e.parity.acl.AclScenario.RemoveLocalGroupMember;
import org.niis.xroad.e2e.parity.acl.AclScenario.RenameService;
import org.niis.xroad.e2e.parity.acl.AclScenario.RestService;
import org.niis.xroad.e2e.parity.acl.AclScenario.Revoke;
import org.niis.xroad.e2e.parity.acl.AclScenario.SwapBackendSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.UndefinedService;
import org.niis.xroad.e2e.parity.acl.AclScenario.WaitClass;
import org.niis.xroad.e2e.parity.acl.AclScenario.WsdlService;
import org.niis.xroad.e2e.parity.acl.AclScenario.WsdlShape;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.CLIENT_REG;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.CONSUMER;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.GLOBAL_GROUP;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.GROUP;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.MANAGEMENT;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.OTHER_CONSUMER;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.OWNERS_GROUP;
import static org.niis.xroad.e2e.parity.acl.AclParticipants.PROVIDER;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.CONSUMER_ONLY;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.globalGroupGranted;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.groupGranted;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.management;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.openApiGranted;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.provided;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.restToGlobalGroup;

class ScenarioFixtureTest {

    private static final String RUN_TOKEN = "t1";

    private static final String BACKEND = "http://isrest:8080/acl-parity/";

    private static final String SWAP_PATH = "/acl-parity/openapi/pets_then_status_only/swap";

    private static final FixtureSpec TWO_SERVICES = new FixtureSpec(PROVIDER, List.of(
            new RestService(List.of(CONSUMER, OTHER_CONSUMER)),
            new RestService(List.of())));

    private static final FixtureSpec DELETED_GROUP = new FixtureSpec(PROVIDER,
            List.of(new RestService(List.of(), List.of(), List.of(GROUP))),
            List.of(new LocalGroupSpec(PROVIDER, GROUP, List.of(CONSUMER), List.of(CONSUMER), true)));

    private static final String DELETED_GROUP_ID = groupId(PROVIDER, "pt1-v63-partners");

    private final AdminGateway gateway = mock(AdminGateway.class);

    @BeforeEach
    void stubGatewayAnswers() {
        when(gateway.addRestDescription(any(), any(), any())).thenAnswer(call -> "id-" + call.getArgument(2));
        when(gateway.addWsdlDescription(any(), any())).thenReturn("id-wsdl");
        when(gateway.addOpenApiDescription(any(), any(), any())).thenAnswer(call -> "id-" + call.getArgument(2));
        when(gateway.grantAccess(any(), any(), any())).thenReturn(true);
        when(gateway.swapBackendSpec(any())).thenReturn(200);
        when(gateway.addEndpoint(any(), any(), any(), any())).thenAnswer(call ->
                "ep-%s-%s".formatted(call.getArgument(2), call.<String>getArgument(3).replace('/', '_')));
        when(gateway.findEndpointId(any(), any(), any(), any())).thenAnswer(call ->
                "gen-%s-%s".formatted(call.getArgument(2), call.<String>getArgument(3).replace('/', '_')));
        when(gateway.deleteEndpoint(any())).thenReturn(204);
        when(gateway.deleteDescription(any())).thenReturn(204);
        when(gateway.createLocalGroup(any(), any())).thenAnswer(call ->
                groupId(call.getArgument(0), call.getArgument(1)));
        when(gateway.deleteLocalGroup(any())).thenReturn(204);
        when(gateway.deleteGlobalGroup(any())).thenReturn(204);
    }

    @Test
    void setupAddsEnablesAndGrantsEachServiceInOrder() {
        var order = inOrder(gateway);

        try (var ignored = open("V09", TWO_SERVICES)) {
            order.verify(gateway).addRestDescription(PROVIDER, BACKEND + "pt1-v09", "pt1-v09");
            order.verify(gateway).enableDescription("id-pt1-v09");
            order.verify(gateway).grantAccess(PROVIDER, CONSUMER, "pt1-v09");
            order.verify(gateway).grantAccess(PROVIDER, OTHER_CONSUMER, "pt1-v09");
            order.verify(gateway).addRestDescription(PROVIDER, BACKEND + "pt1-v09-2", "pt1-v09-2");
            order.verify(gateway).enableDescription("id-pt1-v09-2");
            verifyNoMoreInteractions(gateway);
        }
    }

    @Test
    void namesCarryTheRunTokenAndScenarioIdSoRunsAndScenariosNeverCollide() {
        var firstRun = ScenarioFixture.open(gateway, "run1", scenario("V01", TWO_SERVICES)).serviceCodes();
        var secondRun = ScenarioFixture.open(gateway, "run2", scenario("V01", TWO_SERVICES)).serviceCodes();
        var otherScenario = ScenarioFixture.open(gateway, "run1", scenario("V02", TWO_SERVICES)).serviceCodes();

        assertThat(firstRun).doesNotContainAnyElementsOf(secondRun).doesNotContainAnyElementsOf(otherScenario);
        assertThat(firstRun).allMatch(code -> code.contains("run1") && code.contains("v01"));
        assertThat(firstRun).noneMatch(code -> code.equals("mock1") || code.equals("restapi"));
    }

    @Test
    void groupCodesCarryTheRunTokenSoRunsNeverCollide() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of())),
                List.of(new LocalGroupSpec(PROVIDER, GROUP, List.of())));

        ScenarioFixture.open(gateway, "run1", scenario("V65", spec));
        ScenarioFixture.open(gateway, "run2", scenario("V65", spec));

        verify(gateway).createLocalGroup(PROVIDER, "prun1-v65-partners");
        verify(gateway).createLocalGroup(PROVIDER, "prun2-v65-partners");
    }

    @Test
    void closeDeletesInReverseCreationOrder() {
        var spec = new FixtureSpec(PROVIDER,
                List.of(new RestService(List.of(), List.of(new EndpointSpec("GET", "/foo", List.of())))),
                List.of(new LocalGroupSpec(PROVIDER, GROUP, List.of())), List.of(),
                List.of(new GlobalGroupSpec(GLOBAL_GROUP)));
        var fixture = open("V01", spec);
        clearInvocations(gateway);

        fixture.close();

        var order = inOrder(gateway);
        order.verify(gateway).deleteEndpoint("ep-GET-_foo");
        order.verify(gateway).deleteDescription("id-pt1-v01");
        order.verify(gateway).deleteLocalGroup(groupId(PROVIDER, "pt1-v01-partners"));
        order.verify(gateway).deleteGlobalGroup("pt1-v01-everyone");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void closeDeletesEachDescriptionOfTwoServicesNewestFirst() {
        var fixture = open("V09", TWO_SERVICES);

        fixture.close();

        var order = inOrder(gateway);
        order.verify(gateway).deleteDescription("id-pt1-v09-2");
        order.verify(gateway).deleteDescription("id-pt1-v09");
    }

    @Test
    void closeToleratesAnObjectThatIsAlreadyGone() {
        doReturn(404).when(gateway).deleteDescription("id-pt1-v09-2");
        var fixture = open("V09", TWO_SERVICES);

        assertThatCode(fixture::close).doesNotThrowAnyException();

        verify(gateway).deleteDescription("id-pt1-v09-2");
        verify(gateway).deleteDescription("id-pt1-v09");
    }

    @Test
    void closeKeepsGoingAfterAnAnsweredErrorThenReportsIt() {
        doReturn(500).when(gateway).deleteDescription("id-pt1-v09-2");
        var fixture = open("V09", TWO_SERVICES);

        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pt1-v09-2")
                .hasMessageContaining("500");
        verify(gateway).deleteDescription("id-pt1-v09");
    }

    @Test
    void closeKeepsGoingAfterAThrownFailureThenReportsIt() {
        doThrow(new IllegalStateException("connection reset")).when(gateway).deleteDescription("id-pt1-v09-2");
        var fixture = open("V09", TWO_SERVICES);

        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pt1-v09-2")
                .hasMessageContaining("connection reset");
        verify(gateway).deleteDescription("id-pt1-v09");
    }

    @Test
    void closeTwiceDeletesOnce() {
        var fixture = open("V09", TWO_SERVICES);

        fixture.close();
        fixture.close();

        verify(gateway, times(2)).deleteDescription(any());
        verifyNoEndpointOrGroupDeleted();
    }

    @Test
    void singleWsdlAppendsTheCodeTailAndGrantsTheCode() {
        var order = inOrder(gateway);

        try (var fixture = open("V129", wsdlSpec(WsdlShape.SINGLE, ".x", List.of(CONSUMER)))) {
            assertThat(fixture.serviceCodes()).containsExactly("pt1-v129.x");
            order.verify(gateway).addWsdlDescription(PROVIDER, BACKEND + "wsdl/single.wsdl?code=pt1-v129.x");
            order.verify(gateway).enableDescription("id-wsdl");
            order.verify(gateway).grantAccess(PROVIDER, CONSUMER, "pt1-v129.x");
            verifyNoMoreInteractions(gateway);
        }

        verify(gateway).deleteDescription("id-wsdl");
    }

    @Test
    void pairWsdlOffersTwoCodesAndGrantsEachOnItsOwn() {
        var spec = new FixtureSpec(PROVIDER,
                List.of(new WsdlService(WsdlShape.PAIR, "", List.of(List.of(CONSUMER), List.of()))));

        try (var fixture = open("V04", spec)) {
            assertThat(fixture.serviceCodes()).containsExactly("pt1-v04", "pt1-v04-b");
            verify(gateway).addWsdlDescription(PROVIDER, BACKEND + "wsdl/pair.wsdl?code1=pt1-v04&code2=pt1-v04-b");
            verify(gateway).enableDescription("id-wsdl");
            verify(gateway).grantAccess(PROVIDER, CONSUMER, "pt1-v04");
            verify(gateway, times(1)).grantAccess(any(), any(), any());
        }

        verify(gateway).deleteDescription("id-wsdl");
    }

    @Test
    void versionsWsdlOffersOneCodeFromTheVersionsStub() {
        try (var fixture = open("V128", wsdlSpec(WsdlShape.VERSIONS, "", List.of(CONSUMER)))) {
            assertThat(fixture.serviceCodes()).containsExactly("pt1-v128");
            verify(gateway).addWsdlDescription(PROVIDER, BACKEND + "wsdl/versions.wsdl?code=pt1-v128");
        }
    }

    @Test
    void unversionedWsdlOffersOneCodeFromTheUnversionedStub() {
        try (var fixture = open("V132a", wsdlSpec(WsdlShape.SINGLE_UNVERSIONED, "", List.of(CONSUMER)))) {
            assertThat(fixture.serviceCodes()).containsExactly("pt1-v132a");
            verify(gateway).addWsdlDescription(PROVIDER, BACKEND + "wsdl/single_unversioned.wsdl?code=pt1-v132a");
        }
    }

    @Test
    void restAndWsdlMixKeepsCodeOrderAndDeletesEachDescriptionOnceNewestFirst() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of()),
                new WsdlService(WsdlShape.PAIR, "", List.of(List.of(), List.of()))));
        var fixture = open("V09", spec);
        clearInvocations(gateway);

        assertThat(fixture.serviceCodes()).containsExactly("pt1-v09", "pt1-v09-2", "pt1-v09-2-b");
        fixture.close();

        var order = inOrder(gateway);
        order.verify(gateway).deleteDescription("id-wsdl");
        order.verify(gateway).deleteDescription("id-pt1-v09");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void wsdlServiceNeedsOneGrantListPerShapeCode() {
        assertThatThrownBy(() -> new WsdlService(WsdlShape.PAIR, "", List.of(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAIR");
    }

    @Test
    void endpointsAreCreatedAfterTheBaseGrantsGrantedAndDeletedNewestFirstBeforeTheirDescription() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of(OTHER_CONSUMER),
                List.of(new EndpointSpec("GET", "/foo", List.of(CONSUMER)), new EndpointSpec("*", "/**", List.of())))));
        var order = inOrder(gateway);

        try (var ignored = open("V22", spec)) {
            order.verify(gateway).addRestDescription(PROVIDER, BACKEND + "pt1-v22", "pt1-v22");
            order.verify(gateway).enableDescription("id-pt1-v22");
            order.verify(gateway).grantAccess(PROVIDER, OTHER_CONSUMER, "pt1-v22");
            order.verify(gateway).addEndpoint("id-pt1-v22", "pt1-v22", "GET", "/foo");
            order.verify(gateway).grantEndpointAccess("ep-GET-_foo", CONSUMER);
            order.verify(gateway).addEndpoint("id-pt1-v22", "pt1-v22", "*", "/**");
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).deleteEndpoint("ep-*-_**");
        order.verify(gateway).deleteEndpoint("ep-GET-_foo");
        order.verify(gateway).deleteDescription("id-pt1-v22");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void failedEndpointDeleteIsReportedAndTheDescriptionIsStillDeleted() {
        doReturn(500).when(gateway).deleteEndpoint("ep-GET-_foo");
        var spec = new FixtureSpec(PROVIDER,
                List.of(new RestService(List.of(), List.of(new EndpointSpec("GET", "/foo", List.of())))));
        var fixture = open("V22", spec);

        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint GET /foo")
                .hasMessageContaining("500");
        var order = inOrder(gateway);
        order.verify(gateway).deleteEndpoint("ep-GET-_foo");
        order.verify(gateway).deleteDescription("id-pt1-v22");
    }

    @Test
    void endpointDeletedInSetupIsDeletedOnceBeforeTheNextEndpointAndNotAgainOnClose() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(
                new EndpointSpec("GET", "/foo", List.of(CONSUMER), new Deleted()),
                new EndpointSpec("GET", "/bar", List.of())))));
        var order = inOrder(gateway);

        try (var ignored = open("V36", spec)) {
            order.verify(gateway).addEndpoint("id-pt1-v36", "pt1-v36", "GET", "/foo");
            order.verify(gateway).grantEndpointAccess("ep-GET-_foo", CONSUMER);
            order.verify(gateway).deleteEndpoint("ep-GET-_foo");
            order.verify(gateway).addEndpoint("id-pt1-v36", "pt1-v36", "GET", "/bar");
        }

        verify(gateway, times(1)).deleteEndpoint("ep-GET-_foo");
        verify(gateway).deleteEndpoint("ep-GET-_bar");
        verify(gateway).deleteDescription("id-pt1-v36");
    }

    @Test
    void endpointDeleteRefusedDuringSetupFailsTheSetupAndIsRetriedBeforeTheDescriptionIsRemoved() {
        doReturn(400).when(gateway).deleteEndpoint("ep-GET-_foo");
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of(),
                List.of(new EndpointSpec("GET", "/foo", List.of(), new Deleted())))));

        assertThatThrownBy(() -> open("V36", spec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint GET /foo")
                .hasMessageContaining("400");
        verify(gateway, times(2)).deleteEndpoint("ep-GET-_foo");
        verify(gateway).deleteDescription("id-pt1-v36");
    }

    @Test
    void editedEndpointKeepsItsIdAndIsDeletedUnderItAfterTheEdit() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of(),
                List.of(new EndpointSpec("GET", "/foo", List.of(CONSUMER), new EditedTo("POST", "/bar"))))));
        var order = inOrder(gateway);

        try (var ignored = open("V37", spec)) {
            order.verify(gateway).addEndpoint("id-pt1-v37", "pt1-v37", "GET", "/foo");
            order.verify(gateway).grantEndpointAccess("ep-GET-_foo", CONSUMER);
            order.verify(gateway).updateEndpoint("ep-GET-_foo", "POST", "/bar");
        }

        verify(gateway).deleteEndpoint("ep-GET-_foo");
        verify(gateway).deleteDescription("id-pt1-v37");
    }

    @Test
    void grantOnAGeneratedEndpointLooksItUpByMethodAndPath() {
        var spec = new FixtureSpec(PROVIDER, List.of(new OpenApiService(OpenApiSpec.PETS, List.of(),
                List.of(new GeneratedEndpointGrant("GET", "/pets/*", List.of(CONSUMER))), null)));
        var order = inOrder(gateway);

        try (var ignored = open("V38", spec)) {
            order.verify(gateway).addOpenApiDescription(PROVIDER, BACKEND + "openapi/pets.yaml?code=pt1-v38",
                    "pt1-v38");
            order.verify(gateway).enableDescription("id-pt1-v38");
            order.verify(gateway).findEndpointId("id-pt1-v38", "pt1-v38", "GET", "/pets/*");
            order.verify(gateway).grantEndpointAccess("gen-GET-_pets_*", CONSUMER);
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).deleteDescription("id-pt1-v38");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void descriptionEditRunsAfterTheManualEndpointIsGrantedWithoutLookingItUp() {
        var spec = new FixtureSpec(PROVIDER, List.of(new OpenApiService(OpenApiSpec.STATUS_ONLY,
                List.of(new EndpointSpec("GET", "/pets/*", List.of(CONSUMER))), List.of(), OpenApiSpec.PETS)));
        var order = inOrder(gateway);

        try (var ignored = open("V40", spec)) {
            order.verify(gateway).addOpenApiDescription(PROVIDER, BACKEND + "openapi/status_only.yaml?code=pt1-v40",
                    "pt1-v40");
            order.verify(gateway).enableDescription("id-pt1-v40");
            order.verify(gateway).addEndpoint("id-pt1-v40", "pt1-v40", "GET", "/pets/*");
            order.verify(gateway).grantEndpointAccess("ep-GET-_pets_*", CONSUMER);
            order.verify(gateway).updateOpenApiDescription("id-pt1-v40", BACKEND + "openapi/pets.yaml?code=pt1-v40",
                    "pt1-v40");
            verifyNoMoreInteractions(gateway);
        }

        verify(gateway, never()).deleteEndpoint(any());
    }

    @Test
    void groupIsCreatedFilledAndGrantedByItsIdThenDeletedAfterTheDescription() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(), List.of(GROUP))),
                List.of(new LocalGroupSpec(PROVIDER, GROUP, List.of(CONSUMER))));
        var groupId = groupId(PROVIDER, "pt1-v60-partners");
        var order = inOrder(gateway);

        try (var ignored = open("V60", spec)) {
            order.verify(gateway).createLocalGroup(PROVIDER, "pt1-v60-partners");
            order.verify(gateway).addLocalGroupMember(groupId, CONSUMER);
            order.verify(gateway).addRestDescription(PROVIDER, BACKEND + "pt1-v60", "pt1-v60");
            order.verify(gateway).enableDescription("id-pt1-v60");
            order.verify(gateway).grantAccess(PROVIDER, groupId, "pt1-v60");
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).deleteDescription("id-pt1-v60");
        order.verify(gateway).deleteLocalGroup(groupId);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void groupWithRemovedMembersAndDeletedInSetupIsDeletedOnceAfterTheGrantsAndNotAgainOnClose() {
        var order = inOrder(gateway);

        try (var ignored = open("V63", DELETED_GROUP)) {
            order.verify(gateway).createLocalGroup(PROVIDER, "pt1-v63-partners");
            order.verify(gateway).addLocalGroupMember(DELETED_GROUP_ID, CONSUMER);
            order.verify(gateway).addRestDescription(PROVIDER, BACKEND + "pt1-v63", "pt1-v63");
            order.verify(gateway).enableDescription("id-pt1-v63");
            order.verify(gateway).grantAccess(PROVIDER, DELETED_GROUP_ID, "pt1-v63");
            order.verify(gateway).removeLocalGroupMember(DELETED_GROUP_ID, CONSUMER);
            order.verify(gateway).deleteLocalGroup(DELETED_GROUP_ID);
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).deleteDescription("id-pt1-v63");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void sameGroupCodeUnderTwoOwnersIsCreatedForBothAndOnlyTheProvidersIsGranted() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(), List.of(GROUP))),
                List.of(new LocalGroupSpec(PROVIDER, GROUP, List.of(OTHER_CONSUMER)),
                        new LocalGroupSpec(SubsystemPool.SECOND_OWNER, GROUP, List.of(CONSUMER))));
        var providerGroup = groupId(PROVIDER, "pt1-v65-partners");
        var otherGroup = groupId(SubsystemPool.SECOND_OWNER, "pt1-v65-partners");

        var fixture = open("V65", spec);

        verify(gateway).createLocalGroup(PROVIDER, "pt1-v65-partners");
        verify(gateway).createLocalGroup(SubsystemPool.SECOND_OWNER, "pt1-v65-partners");
        verify(gateway).grantAccess(PROVIDER, providerGroup, "pt1-v65");
        verify(gateway, times(1)).grantAccess(any(), any(), any());
        clearInvocations(gateway);
        fixture.close();
        var order = inOrder(gateway);
        order.verify(gateway).deleteDescription("id-pt1-v65");
        order.verify(gateway).deleteLocalGroup(otherGroup);
        order.verify(gateway).deleteLocalGroup(providerGroup);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void endpointGrantedToAGroupLooksTheGroupUpByOwnerAndCode() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(
                new EndpointSpec("GET", "/a", List.of(CONSUMER)),
                new EndpointSpec("GET", "/b", List.of(), List.of(GROUP))))),
                List.of(new LocalGroupSpec(PROVIDER, GROUP, List.of(CONSUMER))));
        var order = inOrder(gateway);

        try (var ignored = open("V66", spec)) {
            order.verify(gateway).grantEndpointAccess("ep-GET-_a", CONSUMER);
            order.verify(gateway).grantEndpointAccess("ep-GET-_b", groupId(PROVIDER, "pt1-v66-partners"));
        }
    }

    @Test
    void grantToAGroupTheProviderDoesNotOwnIsRejectedAndWhatWasCreatedIsRemoved() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(), List.of(GROUP))),
                List.of(new LocalGroupSpec(SubsystemPool.SECOND_OWNER, GROUP, List.of())));

        assertThatThrownBy(() -> open("V65", spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partners");
        var order = inOrder(gateway);
        order.verify(gateway).deleteDescription("id-pt1-v65");
        order.verify(gateway).deleteLocalGroup(groupId(SubsystemPool.SECOND_OWNER, "pt1-v65-partners"));
    }

    @Test
    void failedGroupCleanupIsReportedAndTheOtherGroupIsStillDeleted() {
        var secondGroup = groupId(SubsystemPool.SECOND_OWNER, "pt1-v65-partners");
        doReturn(500).when(gateway).deleteLocalGroup(secondGroup);
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of())),
                List.of(new LocalGroupSpec(PROVIDER, GROUP, List.of()),
                        new LocalGroupSpec(SubsystemPool.SECOND_OWNER, GROUP, List.of())));
        var fixture = open("V65", spec);

        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local group pt1-v65-partners of " + SubsystemPool.SECOND_OWNER)
                .hasMessageContaining("500");
        var order = inOrder(gateway);
        order.verify(gateway).deleteLocalGroup(secondGroup);
        order.verify(gateway).deleteLocalGroup(groupId(PROVIDER, "pt1-v65-partners"));
    }

    @Test
    void localGroupWhoseSetupDeleteAnswersAnErrorIsDeletedAgainInCleanup() {
        doReturn(500).when(gateway).deleteLocalGroup(DELETED_GROUP_ID);

        assertThatThrownBy(() -> open("V63", DELETED_GROUP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delete of local group pt1-v63-partners")
                .hasMessageContaining("500");

        verify(gateway, times(2)).deleteLocalGroup(DELETED_GROUP_ID);
    }

    @Test
    void localGroupWhoseSetupDeleteThrowsIsDeletedAgainInCleanup() {
        doThrow(new IllegalStateException("connection reset")).doReturn(204)
                .when(gateway).deleteLocalGroup(DELETED_GROUP_ID);

        assertThatThrownBy(() -> open("V63", DELETED_GROUP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("connection reset")
                .hasNoSuppressedExceptions();

        verify(gateway, times(2)).deleteLocalGroup(DELETED_GROUP_ID);
    }

    @Test
    void disableAndEnableRunAfterTheGrantsInTheOrderOfTheChangesAndTheDescriptionIsStillDeleted() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of(CONSUMER))), List.of(),
                List.of(new Disable(0, null), new Disable(0, "later"), new Enable(0)));
        var order = inOrder(gateway);

        try (var ignored = open("V124", spec)) {
            order.verify(gateway).addRestDescription(PROVIDER, BACKEND + "pt1-v124", "pt1-v124");
            order.verify(gateway).enableDescription("id-pt1-v124");
            order.verify(gateway).grantAccess(PROVIDER, CONSUMER, "pt1-v124");
            order.verify(gateway).disableDescription(eq("id-pt1-v124"), isNull());
            order.verify(gateway).disableDescription("id-pt1-v124", "later");
            order.verify(gateway).enableDescription("id-pt1-v124");
            verifyNoMoreInteractions(gateway);
        }

        verify(gateway).deleteDescription("id-pt1-v124");
    }

    @Test
    void renameRunsOnTheDescriptionOfTheOldCodeAppendsTheNewCodeAndCleanupStillDeletesThatDescription() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of())), List.of(),
                List.of(new RenameService(0)));
        var order = inOrder(gateway);

        try (var fixture = open("V127", spec)) {
            assertThat(fixture.serviceCodes()).containsExactly("pt1-v127", "pt1-v127-renamed");
            order.verify(gateway).renameRestService("id-pt1-v127", BACKEND + "pt1-v127", "pt1-v127",
                    "pt1-v127-renamed");
            order.verifyNoMoreInteractions();
        }

        order.verify(gateway).deleteDescription("id-pt1-v127");
        order.verifyNoMoreInteractions();
    }

    @Test
    void descriptionDeletedInSetupIsDeletedOnceAndOnlyTheOtherOneIsDeletedOnClose() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of()), new RestService(List.of())),
                List.of(), List.of(new Delete(0)));
        var order = inOrder(gateway);

        try (var ignored = open("V125", spec)) {
            order.verify(gateway).deleteDescription("id-pt1-v125");
            order.verifyNoMoreInteractions();
        }

        verify(gateway, times(1)).deleteDescription("id-pt1-v125");
        verify(gateway).deleteDescription("id-pt1-v125-2");
        verifyNoEndpointOrGroupDeleted();
    }

    @Test
    void failedGrantBeforeTheSetupDeleteStillDeletesTheDescriptionInCleanup() {
        doThrow(new AssertionError("grant answered 500")).when(gateway).grantAccess(any(), any(), eq("pt1-v125"));
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of(CONSUMER))), List.of(),
                List.of(new Delete(0)));

        assertThatThrownBy(() -> open("V125", spec))
                .isInstanceOf(AssertionError.class)
                .hasMessage("grant answered 500");
        var order = inOrder(gateway);
        order.verify(gateway).addRestDescription(PROVIDER, BACKEND + "pt1-v125", "pt1-v125");
        order.verify(gateway).enableDescription("id-pt1-v125");
        order.verify(gateway).grantAccess(PROVIDER, CONSUMER, "pt1-v125");
        order.verify(gateway).deleteDescription("id-pt1-v125");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void descriptionWhoseSetupDeleteAnswersAnErrorIsDeletedAgainInCleanup() {
        doReturn(500).when(gateway).deleteDescription("id-pt1-v125");

        assertThatThrownBy(() -> open("V125", deletedDescription()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pt1-v125 answered 500");

        verify(gateway, times(2)).deleteDescription("id-pt1-v125");
    }

    @Test
    void descriptionWhoseSetupDeleteThrowsIsDeletedAgainInCleanup() {
        doThrow(new IllegalStateException("connection reset")).doReturn(204)
                .when(gateway).deleteDescription("id-pt1-v125");

        assertThatThrownBy(() -> open("V125", deletedDescription()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("connection reset")
                .hasNoSuppressedExceptions();

        verify(gateway, times(2)).deleteDescription("id-pt1-v125");
    }

    @Test
    void undefinedServiceReservesACodeAndCreatesNothing() {
        var spec = new FixtureSpec(PROVIDER, List.of(new UndefinedService(), new RestService(List.of())));

        var fixture = open("V133", spec);

        assertThat(fixture.serviceCodes()).containsExactly("pt1-v133", "pt1-v133-2");
        verify(gateway).addRestDescription(PROVIDER, BACKEND + "pt1-v133-2", "pt1-v133-2");
        verify(gateway, times(1)).addRestDescription(any(), any(), any());
        fixture.close();
        verify(gateway, times(1)).deleteDescription(any());
        verify(gateway).deleteDescription("id-pt1-v133-2");
    }

    @Test
    void changeOfAnUndefinedServiceIsRejected() {
        var spec = new FixtureSpec(PROVIDER, List.of(new UndefinedService()), List.of(), List.of(new Enable(0)));

        assertThatThrownBy(() -> open("V133", spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no description");
    }

    @Test
    void renameOfAWsdlServiceIsRejectedAndTheDescriptionIsRemoved() {
        var spec = new FixtureSpec(PROVIDER, List.of(new WsdlService(WsdlShape.SINGLE, "", List.of(List.of()))),
                List.of(), List.of(new RenameService(0)));

        assertThatThrownBy(() -> open("V127", spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a REST service");
        verify(gateway).deleteDescription("id-wsdl");
    }

    @Test
    void phasesAreNotRunByOpen() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of(CONSUMER))));

        try (var ignored = open("V11", spec, phase(new Revoke(0, CONSUMER)))) {
            verify(gateway, never()).revokeAccess(any(), any(), any());
        }
    }

    @Test
    void phaseGrantsAndRevokesOnTheServiceCode() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of(CONSUMER)), new RestService(List.of())));

        try (var fixture = open("V10", spec, phase(new Grant(1, OTHER_CONSUMER)), phase(new Revoke(0, CONSUMER)))) {
            fixture.applyPhase(0);
            verify(gateway).grantAccess(PROVIDER, OTHER_CONSUMER, "pt1-v10-2");
            fixture.applyPhase(1);
            verify(gateway).revokeAccess(PROVIDER, CONSUMER, "pt1-v10");
        }
    }

    @Test
    void applyPhaseRunsOnlyTheChangesOfThatPhaseInTheirOrder() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of())));
        var order = inOrder(gateway);

        try (var fixture = open("V12", spec,
                phase(new Grant(0, CONSUMER), new Revoke(0, CONSUMER)), phase(new Grant(0, OTHER_CONSUMER)))) {
            clearInvocations(gateway);

            fixture.applyPhase(1);
            order.verify(gateway).grantAccess(PROVIDER, OTHER_CONSUMER, "pt1-v12");
            verifyNoMoreInteractions(gateway);

            fixture.applyPhase(0);
            order.verify(gateway).grantAccess(PROVIDER, CONSUMER, "pt1-v12");
            order.verify(gateway).revokeAccess(PROVIDER, CONSUMER, "pt1-v12");
            verifyNoMoreInteractions(gateway);
        }
    }

    @Test
    void phaseSwapsTheBackendSpecBeforeTheRefresh() {
        var spec = openApiGranted(OpenApiSpec.PETS_THEN_STATUS_ONLY, "GET", "/pets/*");
        var order = inOrder(gateway);

        try (var fixture = open("V39", spec, phase(new SwapBackendSpec(0), new Refresh(0)))) {
            assertThat(fixture.serviceCodes()).containsExactly("pt1-v39");
            verify(gateway).addOpenApiDescription(PROVIDER, BACKEND + "openapi/pets_then_status_only.yaml?code=pt1-v39",
                    "pt1-v39");
            clearInvocations(gateway);

            fixture.applyPhase(0);
            order.verify(gateway).swapBackendSpec(SWAP_PATH);
            order.verify(gateway).refreshDescription("id-pt1-v39");
            verifyNoMoreInteractions(gateway);
        }

        verify(gateway).deleteDescription("id-pt1-v39");
    }

    @Test
    void refusedBackendSwapStopsThePhaseBeforeTheRefresh() {
        doReturn(404).when(gateway).swapBackendSpec(SWAP_PATH);
        var spec = openApiGranted(OpenApiSpec.PETS_THEN_STATUS_ONLY, "GET", "/pets/*");
        var fixture = open("V39", spec, phase(new SwapBackendSpec(0), new Refresh(0)));

        assertThatThrownBy(() -> fixture.applyPhase(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SWAP_PATH).hasMessageContaining("404");
        verify(gateway, never()).refreshDescription(any());
    }

    @Test
    void swapOfAServiceThatIsNotTheSwappableOpenApiSpecIsRejectedAndRemovesWhatWasCreated() {
        var rest = new FixtureSpec(PROVIDER, List.of(new RestService(List.of())));
        var pets = openApiGranted(OpenApiSpec.PETS, "GET", "/pets/*");

        assertThatThrownBy(() -> open("VX", rest, phase(new SwapBackendSpec(0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a swappable OpenAPI service");
        verify(gateway).deleteDescription("id-pt1-vx");
        assertThatThrownBy(() -> open("VY", pets, phase(new SwapBackendSpec(0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a swappable OpenAPI service");
        verify(gateway).deleteDescription("id-pt1-vy");
    }

    @Test
    void globalGroupIsNamespacedCreatedFilledAndGrantedThenDeletedInCleanup() {
        var spec = globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP, CONSUMER_ONLY));
        var order = inOrder(gateway);

        try (var ignored = open("V68", spec)) {
            order.verify(gateway).createGlobalGroup("pt1-v68-everyone");
            order.verify(gateway).addGlobalGroupMember("pt1-v68-everyone", CONSUMER);
            order.verify(gateway).grantGlobalGroupAccess(PROVIDER, "DEV:pt1-v68-everyone", "pt1-v68");
        }

        order.verify(gateway).deleteGlobalGroup("pt1-v68-everyone");
        order.verifyNoMoreInteractions();
    }

    @Test
    void globalGroupGrantedOnAnEndpointUsesTheNamespacedGroup() {
        var spec = new FixtureSpec(PROVIDER,
                List.of(new RestService(List.of(),
                        List.of(EndpointSpec.forGlobalGroups("GET", "/g", List.of(GLOBAL_GROUP))))),
                List.of(), List.of(), List.of(new GlobalGroupSpec(GLOBAL_GROUP)));

        try (var ignored = open("V69", spec)) {
            verify(gateway).grantGlobalGroupEndpointAccess("ep-GET-_g", "DEV:pt1-v69-everyone");
        }
    }

    @Test
    void groupDeletedByAPhaseIsNotDeletedAgainInCleanup() {
        try (var fixture = open("V74", deletedGlobalGroupSpec(), phase(new DeleteGlobalGroup(GLOBAL_GROUP)))) {
            fixture.applyPhase(0);
            verify(gateway).deleteGlobalGroup("pt1-v74-everyone");
        }

        verify(gateway, times(1)).deleteGlobalGroup("pt1-v74-everyone");
    }

    @Test
    void globalGroupWhoseDeleteInAPhaseAnswersAnErrorIsDeletedAgainInCleanup() {
        doReturn(500).when(gateway).deleteGlobalGroup("pt1-v74-everyone");

        try (var fixture = open("V74", deletedGlobalGroupSpec(), phase(new DeleteGlobalGroup(GLOBAL_GROUP)))) {
            assertThatThrownBy(() -> fixture.applyPhase(0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("delete of global group pt1-v74-everyone")
                    .hasMessageContaining("500");
            assertThatThrownBy(fixture::close).isInstanceOf(IllegalStateException.class);
        }

        verify(gateway, times(2)).deleteGlobalGroup("pt1-v74-everyone");
    }

    @Test
    void globalGroupWhoseDeleteInAPhaseThrowsIsDeletedAgainInCleanup() {
        doThrow(new IllegalStateException("connection reset")).doReturn(204)
                .when(gateway).deleteGlobalGroup("pt1-v74-everyone");
        var fixture = open("V74", deletedGlobalGroupSpec(), phase(new DeleteGlobalGroup(GLOBAL_GROUP)));

        assertThatThrownBy(() -> fixture.applyPhase(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("connection reset");
        assertThatCode(fixture::close).doesNotThrowAnyException();

        verify(gateway, times(2)).deleteGlobalGroup("pt1-v74-everyone");
    }

    @Test
    void deleteOfAGlobalGroupTheFixtureDidNotCreateIsRejected() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of())));

        assertThatThrownBy(() -> open("V74", spec, phase(new DeleteGlobalGroup(GLOBAL_GROUP))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not created");
        verify(gateway).deleteDescription("id-pt1-v74");
    }

    @Test
    void ownersGroupIsGrantedUnderItsOwnCodeButNeverCreatedOrDeleted() {
        var spec = provided(restToGlobalGroup(GlobalGroupSpec.SECURITY_SERVER_OWNERS));

        try (var ignored = open("V72", spec)) {
            verify(gateway).grantGlobalGroupAccess(PROVIDER, "DEV:security-server-owners", "pt1-v72");
        }

        verify(gateway, never()).createGlobalGroup(any());
        verify(gateway, never()).deleteGlobalGroup(any());
    }

    @Test
    void ownersGroupCannotBeDeclaredAndAnUndeclaredGroupCannotBeGranted() {
        var owners = new FixtureSpec(PROVIDER, List.of(new RestService(List.of())), List.of(), List.of(),
                List.of(new GlobalGroupSpec(GlobalGroupSpec.SECURITY_SERVER_OWNERS)));
        var undeclared = new FixtureSpec(PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of(), List.of("nobody"))));

        assertThatThrownBy(() -> open("V1", owners))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("never created");
        verify(gateway, never()).createGlobalGroup(any());
        assertThatThrownBy(() -> open("V2", undeclared))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not created");
        verify(gateway).deleteDescription("id-pt1-v2");
    }

    @Test
    void globalGroupPhaseChangesUseTheNamespacedCode() {
        var spec = globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP));

        try (var fixture = open("V75", spec, phase(new AddGlobalGroupMember(GLOBAL_GROUP, CONSUMER)),
                phase(new RemoveGlobalGroupMember(GLOBAL_GROUP, CONSUMER)))) {
            fixture.applyPhase(0);
            verify(gateway).addGlobalGroupMember("pt1-v75-everyone", CONSUMER);
            fixture.applyPhase(1);
            verify(gateway).removeGlobalGroupMember("pt1-v75-everyone", CONSUMER);
        }
    }

    @Test
    void missingGlobalGroupIsToleratedInCleanupAndOtherFailuresAreReported() {
        var spec = globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP, CONSUMER_ONLY));
        doReturn(404).when(gateway).deleteGlobalGroup("pt1-v68-everyone");
        assertThatCode(() -> open("V68", spec).close()).doesNotThrowAnyException();

        doReturn(500).when(gateway).deleteGlobalGroup("pt1-v68-everyone");
        var fixture = open("V68", spec);
        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("global group pt1-v68-everyone")
                .hasMessageContaining("500");
    }

    @Test
    void managementServiceKeepsItsCodeRevokesThenGrantsInSetupAndCreatesNothing() {
        var spec = new FixtureSpec(PROVIDER, List.of(new ManagementService(CLIENT_REG, List.of(OWNERS_GROUP),
                List.of(CONSUMER))));
        var order = inOrder(gateway);

        try (var fixture = open("V106a", spec)) {
            assertThat(fixture.serviceCodes()).containsExactly(CLIENT_REG);
            order.verify(gateway).revokeAccess(PROVIDER, OWNERS_GROUP, CLIENT_REG);
            order.verify(gateway).grantAccess(PROVIDER, CONSUMER, CLIENT_REG);
            verifyNoMoreInteractions(gateway);
        }
    }

    @Test
    void managementGrantsChangedInSetupAreChangedBackNewestFirstOnClose() {
        var spec = new FixtureSpec(PROVIDER, List.of(new ManagementService(CLIENT_REG, List.of(OWNERS_GROUP),
                List.of(CONSUMER))));
        var order = inOrder(gateway);

        try (var ignored = open("VX", spec)) {
            order.verify(gateway).revokeAccess(PROVIDER, OWNERS_GROUP, CLIENT_REG);
            order.verify(gateway).grantAccess(PROVIDER, CONSUMER, CLIENT_REG);
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).revokeAccess(PROVIDER, CONSUMER, CLIENT_REG);
        order.verify(gateway).grantAccess(PROVIDER, OWNERS_GROUP, CLIENT_REG);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void managementGrantThatAlreadyExistedIsNotRevokedOnClose() {
        doReturn(false).when(gateway).grantAccess(any(), eq(CONSUMER), any());
        var spec = new FixtureSpec(PROVIDER, List.of(new ManagementService(CLIENT_REG, List.of(), List.of(CONSUMER))));

        try (var ignored = open("VX", spec)) {
            verify(gateway).grantAccess(PROVIDER, CONSUMER, CLIENT_REG);
            verifyNoMoreInteractions(gateway);
        }

        verifyNoMoreInteractions(gateway);
    }

    @Test
    void managementGrantsChangedByAPhaseAreChangedBackNewestFirstOnClose() {
        var spec = new FixtureSpec(PROVIDER,
                List.of(new ManagementService(CLIENT_REG, List.of(), List.of()), new RestService(List.of())));
        var order = inOrder(gateway);

        try (var fixture = open("VX", spec,
                phase(new Grant(0, CONSUMER), new Revoke(0, CONSUMER), new Grant(1, CONSUMER)))) {
            clearInvocations(gateway);

            fixture.applyPhase(0);
            order.verify(gateway).grantAccess(PROVIDER, CONSUMER, CLIENT_REG);
            order.verify(gateway).revokeAccess(PROVIDER, CONSUMER, CLIENT_REG);
            order.verify(gateway).grantAccess(PROVIDER, CONSUMER, "pt1-vx-2");
            verifyNoMoreInteractions(gateway);
            clearInvocations(gateway);
        }

        order.verify(gateway).grantAccess(PROVIDER, CONSUMER, CLIENT_REG);
        order.verify(gateway).revokeAccess(PROVIDER, CONSUMER, CLIENT_REG);
        order.verify(gateway).deleteDescription("id-pt1-vx-2");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void managementGrantChangedInASetupThatFailsLaterIsChangedBackBeforeTheFailureIsRethrown() {
        doThrow(new IllegalStateException("add refused")).when(gateway).addRestDescription(any(), any(), eq("pt1-vx-2"));
        var spec = new FixtureSpec(PROVIDER, List.of(
                new ManagementService(CLIENT_REG, List.of(OWNERS_GROUP), List.of()), new RestService(List.of())));

        assertThatThrownBy(() -> open("VX", spec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("add refused");
        var order = inOrder(gateway);
        order.verify(gateway).revokeAccess(PROVIDER, OWNERS_GROUP, CLIENT_REG);
        order.verify(gateway).addRestDescription(PROVIDER, BACKEND + "pt1-vx-2", "pt1-vx-2");
        order.verify(gateway).grantAccess(PROVIDER, OWNERS_GROUP, CLIENT_REG);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void failedManagementRestoreIsReportedAndTheRestOfTheCleanupStillRuns() {
        doThrow(new AssertionError("grant answered 500")).when(gateway).grantAccess(any(), any(), eq(CLIENT_REG));
        var spec = new FixtureSpec(PROVIDER, List.of(
                new ManagementService(CLIENT_REG, List.of(OWNERS_GROUP, CONSUMER), List.of()),
                new RestService(List.of())));
        var fixture = open("VX", spec);

        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fixture cleanup incomplete")
                .hasMessageContaining("restore of")
                .hasMessageContaining(OWNERS_GROUP)
                .hasMessageContaining(CONSUMER)
                .hasMessageContaining("grant answered 500");
        verify(gateway).deleteDescription("id-pt1-vx-2");
    }

    @Test
    void managementRowMovesTheGrantFromTheOwnersGroupToTheSubsystemInItsPhaseAndChangesNothingInSetup() {
        var spec = management(new ManagementService(CLIENT_REG, List.of(), List.of()));

        try (var fixture = open("V106a", spec,
                phase(new Revoke(0, OWNERS_GROUP), new Grant(0, CONSUMER)))) {
            verifyNoMoreInteractions(gateway);
            var order = inOrder(gateway);

            fixture.applyPhase(0);
            order.verify(gateway).revokeAccess(MANAGEMENT, OWNERS_GROUP, CLIENT_REG);
            order.verify(gateway).grantAccess(MANAGEMENT, CONSUMER, CLIENT_REG);
        }
    }

    @Test
    void managementRowGrantsItsLocalGroupByTheNamespacedCodeInThePhaseAndDeletesTheGroupOnClose() {
        var spec = management(new ManagementService(CLIENT_REG, List.of(OWNERS_GROUP), List.of()),
                new LocalGroupSpec(MANAGEMENT, GROUP, List.of(AclParticipants.MEMBER_OF_CONSUMER)));
        var groupId = groupId(MANAGEMENT, "pt1-v106b-partners");

        try (var fixture = open("V106b", spec, phase(new GrantGroup(0, GROUP)))) {
            verify(gateway).createLocalGroup(MANAGEMENT, "pt1-v106b-partners");
            fixture.applyPhase(0);
            verify(gateway).grantAccess(MANAGEMENT, groupId, CLIENT_REG);
        }

        var order = inOrder(gateway);
        order.verify(gateway).grantAccess(MANAGEMENT, OWNERS_GROUP, CLIENT_REG);
        order.verify(gateway).deleteLocalGroup(groupId);
    }

    @Test
    void failedSetupRemovesWhatItCreatedAndRethrows() {
        doThrow(new IllegalStateException("add refused"))
                .when(gateway).addRestDescription(any(), any(), eq("pt1-v09-2"));

        assertThatThrownBy(() -> open("V09", TWO_SERVICES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("add refused");
        var order = inOrder(gateway);
        order.verify(gateway).addRestDescription(any(), any(), eq("pt1-v09-2"));
        order.verify(gateway).deleteDescription("id-pt1-v09");
        order.verifyNoMoreInteractions();
        verify(gateway, never()).deleteDescription("id-pt1-v09-2");
    }

    @Test
    void failedAdminAssertionDuringSetupRemovesWhatItCreatedAndRethrows() {
        doThrow(new AssertionError("grant answered 500")).when(gateway).grantAccess(any(), any(), eq("pt1-v09"));

        assertThatThrownBy(() -> open("V09", TWO_SERVICES))
                .isInstanceOf(AssertionError.class)
                .hasMessage("grant answered 500");
        var order = inOrder(gateway);
        order.verify(gateway).grantAccess(any(), any(), eq("pt1-v09"));
        order.verify(gateway).deleteDescription("id-pt1-v09");
        order.verifyNoMoreInteractions();
    }

    @Test
    void failedSetupAfterGroupCreationDeletesTheGroup() {
        doThrow(new IllegalStateException("add refused")).when(gateway).addRestDescription(any(), any(), eq("pt1-v60"));
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of(), List.of(), List.of(GROUP))),
                List.of(new LocalGroupSpec(PROVIDER, GROUP, List.of())));

        assertThatThrownBy(() -> open("V60", spec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("add refused");
        var order = inOrder(gateway);
        order.verify(gateway).createLocalGroup(PROVIDER, "pt1-v60-partners");
        order.verify(gateway).deleteLocalGroup(groupId(PROVIDER, "pt1-v60-partners"));
        order.verifyNoMoreInteractions();
    }

    @Test
    void failedSetupAfterGlobalGroupCreationDeletesTheGroup() {
        doThrow(new IllegalStateException("add refused")).when(gateway).addRestDescription(any(), any(), eq("pt1-v68"));
        var spec = globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP, CONSUMER_ONLY));

        assertThatThrownBy(() -> open("V68", spec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("add refused");
        verify(gateway).deleteGlobalGroup("pt1-v68-everyone");
    }

    @Test
    void failedRollbackIsAddedAsSuppressedToTheSetupFailure() {
        doThrow(new IllegalStateException("add refused"))
                .when(gateway).addRestDescription(any(), any(), eq("pt1-v09-2"));
        doReturn(500).when(gateway).deleteDescription("id-pt1-v09");

        assertThatThrownBy(() -> open("V09", TWO_SERVICES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("add refused")
                .hasSuppressedException(new IllegalStateException(
                        "fixture cleanup incomplete: delete of service description pt1-v09 answered 500"));
    }

    @Test
    void descriptionDeletedInAPhaseIsDeletedWhenThePhaseIsAppliedAndNotAgainOnClose() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(CONSUMER_ONLY)));
        try (var fixture = open("V125", spec, phase(new DeleteDescription(0)))) {
            verify(gateway, never()).deleteDescription(any());
            fixture.applyPhase(0);
            verify(gateway).deleteDescription("id-pt1-v125");
        }
        verify(gateway, times(1)).deleteDescription("id-pt1-v125");
    }

    @Test
    void descriptionWhoseDeleteInAPhaseAnswersAnErrorIsDeletedAgainInCleanup() {
        doReturn(500).doReturn(204).when(gateway).deleteDescription("id-pt1-v125");
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(CONSUMER_ONLY)));
        try (var fixture = open("V125", spec, phase(new DeleteDescription(0)))) {
            assertThatThrownBy(() -> fixture.applyPhase(0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("pt1-v125 answered 500");
        }
        verify(gateway, times(2)).deleteDescription("id-pt1-v125");
    }

    @Test
    void deleteOfADescriptionThatDoesNotExistIsRejected() {
        var spec = new FixtureSpec(PROVIDER, List.of(new UndefinedService()));
        assertThatThrownBy(() -> open("V125", spec, phase(new DeleteDescription(0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service 0 has no description");
    }

    @Test
    void memberRemovedFromTheProvidersLocalGroupInAPhaseIsRemovedWhenThePhaseIsApplied() {
        var spec = groupGranted(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY));
        try (var fixture = open("V62", spec, phase(new RemoveLocalGroupMember(GROUP, CONSUMER)))) {
            verify(gateway, never()).removeLocalGroupMember(any(), any());
            fixture.applyPhase(0);
            verify(gateway).removeLocalGroupMember(groupId(PROVIDER, "pt1-v62-partners"), CONSUMER);
        }
    }

    @Test
    void localGroupDeletedInAPhaseIsDeletedWhenThePhaseIsAppliedAndNotAgainOnClose() {
        var spec = groupGranted(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY));
        var groupId = groupId(PROVIDER, "pt1-v63-partners");
        try (var fixture = open("V63", spec, phase(new DeleteLocalGroup(GROUP)))) {
            verify(gateway, never()).deleteLocalGroup(any());
            fixture.applyPhase(0);
            verify(gateway).deleteLocalGroup(groupId);
        }
        verify(gateway, times(1)).deleteLocalGroup(groupId);
    }

    @Test
    void localGroupWhoseDeleteInAPhaseAnswersAnErrorIsDeletedAgainInCleanup() {
        var groupId = groupId(PROVIDER, "pt1-v63-partners");
        doReturn(500).doReturn(204).when(gateway).deleteLocalGroup(groupId);
        var spec = groupGranted(new LocalGroupSpec(PROVIDER, GROUP, CONSUMER_ONLY));
        try (var fixture = open("V63", spec, phase(new DeleteLocalGroup(GROUP)))) {
            assertThatThrownBy(() -> fixture.applyPhase(0)).isInstanceOf(IllegalStateException.class);
        }
        verify(gateway, times(2)).deleteLocalGroup(groupId);
    }

    @Test
    void phaseChangeOfALocalGroupTheProviderDoesNotHaveIsRejected() {
        var spec = new FixtureSpec(PROVIDER, List.of(new RestService(List.of())));
        assertThatThrownBy(() -> open("V63", spec, phase(new DeleteLocalGroup(GROUP))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not created under");
        assertThatThrownBy(() -> open("V62", spec, phase(new RemoveLocalGroupMember(GROUP, CONSUMER))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is granted but not created");
    }

    private ScenarioFixture open(String id, FixtureSpec spec, Phase... phases) {
        return ScenarioFixture.open(gateway, RUN_TOKEN, scenario(id, spec, phases));
    }

    private void verifyNoEndpointOrGroupDeleted() {
        verify(gateway, never()).deleteEndpoint(any());
        verify(gateway, never()).deleteLocalGroup(any());
        verify(gateway, never()).deleteGlobalGroup(any());
    }

    private static AclScenario scenario(String id, FixtureSpec spec, Phase... phases) {
        return new AclScenario(id, "test row", spec, List.of(), List.of(phases), WaitClass.ACL_CACHE);
    }

    private static Phase phase(PhaseChange... changes) {
        return new Phase(List.of(changes), List.of());
    }

    private static String groupId(String ownerClientId, String code) {
        return "grp-%s-%s".formatted(ownerClientId, code);
    }

    private static FixtureSpec wsdlSpec(WsdlShape shape, String codeTail, List<String> granted) {
        return new FixtureSpec(PROVIDER, List.of(new WsdlService(shape, codeTail, List.of(granted))));
    }

    private static FixtureSpec deletedDescription() {
        return new FixtureSpec(PROVIDER, List.of(new RestService(List.of())), List.of(), List.of(new Delete(0)));
    }

    private static FixtureSpec deletedGlobalGroupSpec() {
        return globalGroupGranted(new GlobalGroupSpec(GLOBAL_GROUP, CONSUMER_ONLY));
    }
}
