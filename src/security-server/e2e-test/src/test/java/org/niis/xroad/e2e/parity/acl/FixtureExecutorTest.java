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
import org.niis.xroad.e2e.parity.acl.AclScenario.Delete;
import org.niis.xroad.e2e.parity.acl.AclScenario.Deleted;
import org.niis.xroad.e2e.parity.acl.AclScenario.Disable;
import org.niis.xroad.e2e.parity.acl.AclScenario.EditedTo;
import org.niis.xroad.e2e.parity.acl.AclScenario.EndpointSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.FixtureSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.GeneratedEndpointGrant;
import org.niis.xroad.e2e.parity.acl.AclScenario.LocalGroupSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiService;
import org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.RenameService;
import org.niis.xroad.e2e.parity.acl.AclScenario.RestService;
import org.niis.xroad.e2e.parity.acl.AclScenario.WsdlService;
import org.niis.xroad.e2e.parity.acl.AclScenario.WsdlShape;

import java.util.List;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
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

class FixtureExecutorTest {

    private static final FixtureSpec TWO_SERVICES = new FixtureSpec(AclParticipants.PROVIDER, List.of(
            new RestService(List.of(AclParticipants.CONSUMER)),
            new RestService(List.of())));

    private static final FixturePlan DELETED_GROUP_PLAN = new FixturePlanner("t1").plan("V63",
            new FixtureSpec(AclParticipants.PROVIDER,
                    List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                    List.of(new LocalGroupSpec(AclParticipants.PROVIDER, "partners",
                            List.of(AclParticipants.CONSUMER), List.of(AclParticipants.CONSUMER), true))));

    private static final String DELETED_GROUP_ID = "grp-%s-pt1-v63-partners".formatted(AclParticipants.PROVIDER);

    private static final String SWAP_PATH = "/acl-parity/openapi/pets_then_status_only/swap";

    private final AdminGateway gateway = mock(AdminGateway.class);
    private final FixtureExecutor executor = new FixtureExecutor(gateway);
    private final FixturePlan plan = new FixturePlanner("t1").plan("V09", TWO_SERVICES);

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
                "grp-%s-%s".formatted(call.getArgument(0), call.getArgument(1)));
        when(gateway.deleteLocalGroup(any())).thenReturn(204);
        when(gateway.deleteGlobalGroup(any())).thenReturn(204);
    }

    @Test
    void setupRunsInPlanOrderAndCloseDeletesInReverse() {
        var order = inOrder(gateway);

        try (var ignored = executor.open(plan)) {
            order.verify(gateway).addRestDescription(any(), any(), eq("pt1-v09"));
            order.verify(gateway).enableDescription("id-pt1-v09");
            order.verify(gateway).grantAccess(any(), eq(AclParticipants.CONSUMER), eq("pt1-v09"));
            order.verify(gateway).addRestDescription(any(), any(), eq("pt1-v09-2"));
            order.verify(gateway).enableDescription("id-pt1-v09-2");
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).deleteDescription("id-pt1-v09-2");
        order.verify(gateway).deleteDescription("id-pt1-v09");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void cleanupToleratesADescriptionThatIsAlreadyGone() {
        doReturn(404).when(gateway).deleteDescription("id-pt1-v09-2");

        var fixture = executor.open(plan);

        fixture.close();

        verify(gateway).deleteDescription("id-pt1-v09-2");
        verify(gateway).deleteDescription("id-pt1-v09");
    }

    @Test
    void cleanupKeepsGoingAfterAFailedStepThenReportsIt() {
        doReturn(500).when(gateway).deleteDescription("id-pt1-v09-2");
        var fixture = executor.open(plan);

        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pt1-v09-2")
                .hasMessageContaining("500");
        verify(gateway).deleteDescription("id-pt1-v09");
    }

    @Test
    void phaseSwapsTheBackendSpecBeforeTheRefresh() {
        var swapPlan = new FixturePlanner("t1").plan(openApiSwapRow());

        try (var fixture = executor.open(swapPlan)) {
            clearInvocations(gateway);
            fixture.apply(swapPlan.phases().get(0));
            var applied = inOrder(gateway);
            applied.verify(gateway).swapBackendSpec(SWAP_PATH);
            applied.verify(gateway).refreshDescription("id-pt1-v39");
            verifyNoMoreInteractions(gateway);
            clearInvocations(gateway);
        }

        verify(gateway).deleteDescription("id-pt1-v39");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void refusedBackendSwapStopsThePhaseBeforeTheRefresh() {
        var swapPlan = new FixturePlanner("t1").plan(openApiSwapRow());
        doReturn(404).when(gateway).swapBackendSpec(SWAP_PATH);
        var fixture = executor.open(swapPlan);

        assertThatThrownBy(() -> fixture.apply(swapPlan.phases().get(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SWAP_PATH).hasMessageContaining("404");
        verify(gateway, never()).refreshDescription(any());
    }

    @Test
    void revokeIsSentToTheGateway() {
        var revokePlan = new FixturePlanner("t1").plan(revokeRow());

        try (var fixture = executor.open(revokePlan)) {
            fixture.apply(revokePlan.phases().get(0));
            verify(gateway).revokeAccess(any(), eq(AclParticipants.CONSUMER), eq("pt1-v11"));
        }
    }

    @Test
    void failedSetupRemovesWhatItCreatedAndRethrows() {
        doThrow(new IllegalStateException("add refused"))
                .when(gateway).addRestDescription(any(), any(), eq("pt1-v09-2"));

        assertThatThrownBy(() -> executor.open(plan))
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

        assertThatThrownBy(() -> executor.open(plan))
                .isInstanceOf(AssertionError.class)
                .hasMessage("grant answered 500");
        var order = inOrder(gateway);
        order.verify(gateway).grantAccess(any(), any(), eq("pt1-v09"));
        order.verify(gateway).deleteDescription("id-pt1-v09");
        order.verifyNoMoreInteractions();
    }

    @Test
    void wsdlDescriptionIsAddedThroughItsFirstCodeAndDeletedOnClose() {
        var wsdlPlan = new FixturePlanner("t1").plan("V04", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new WsdlService(WsdlShape.PAIR, "", List.of(List.of(AclParticipants.CONSUMER), List.of())))));
        var order = inOrder(gateway);

        try (var ignored = executor.open(wsdlPlan)) {
            order.verify(gateway).addWsdlDescription(any(),
                    eq("http://isrest:8080/acl-parity/wsdl/pair.wsdl?code1=pt1-v04&code2=pt1-v04-b"));
            order.verify(gateway).enableDescription("id-wsdl");
            order.verify(gateway).grantAccess(any(), eq(AclParticipants.CONSUMER), eq("pt1-v04"));
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).deleteDescription("id-wsdl");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void endpointsAreCreatedGrantedAndDeletedBeforeTheirDescription() {
        var endpointPlan = new FixturePlanner("t1").plan("V22", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(),
                        List.of(new EndpointSpec("GET", "/foo", List.of(AclParticipants.CONSUMER)))))));
        var order = inOrder(gateway);

        try (var ignored = executor.open(endpointPlan)) {
            order.verify(gateway).addRestDescription(any(), any(), eq("pt1-v22"));
            order.verify(gateway).enableDescription("id-pt1-v22");
            order.verify(gateway).addEndpoint(eq("id-pt1-v22"), any(), eq("GET"), eq("/foo"));
            order.verify(gateway).grantEndpointAccess("ep-GET-_foo", AclParticipants.CONSUMER);
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).deleteEndpoint("ep-GET-_foo");
        order.verify(gateway).deleteDescription("id-pt1-v22");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void failedEndpointDeleteIsReportedAndTheDescriptionIsStillDeleted() {
        doReturn(500).when(gateway).deleteEndpoint("ep-GET-_foo");
        var endpointPlan = new FixturePlanner("t1").plan("V22", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(), List.of(new EndpointSpec("GET", "/foo", List.of()))))));
        var fixture = executor.open(endpointPlan);

        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint GET /foo")
                .hasMessageContaining("500");
        var order = inOrder(gateway);
        order.verify(gateway).deleteEndpoint("ep-GET-_foo");
        order.verify(gateway).deleteDescription("id-pt1-v22");
        order.verifyNoMoreInteractions();
    }

    @Test
    void grantOnAGeneratedEndpointLooksItUpByMethodAndPath() {
        var openApiPlan = new FixturePlanner("t1").plan("V38", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new OpenApiService(OpenApiSpec.PETS, List.of(),
                        List.of(new GeneratedEndpointGrant("GET", "/pets/*", List.of(AclParticipants.CONSUMER))),
                        null))));
        var order = inOrder(gateway);

        try (var ignored = executor.open(openApiPlan)) {
            order.verify(gateway).addOpenApiDescription(any(),
                    eq("http://isrest:8080/acl-parity/openapi/pets.yaml?code=pt1-v38"), any());
            order.verify(gateway).enableDescription("id-pt1-v38");
            order.verify(gateway).findEndpointId(eq("id-pt1-v38"), any(), eq("GET"), eq("/pets/*"));
            order.verify(gateway).grantEndpointAccess("gen-GET-_pets_*", AclParticipants.CONSUMER);
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).deleteDescription("id-pt1-v38");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void descriptionEditRunsAfterTheManualEndpointIsGrantedWithoutLookingItUp() {
        var openApiPlan = new FixturePlanner("t1").plan("V40", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new OpenApiService(OpenApiSpec.STATUS_ONLY,
                        List.of(new EndpointSpec("GET", "/pets/*", List.of(AclParticipants.CONSUMER))), List.of(),
                        OpenApiSpec.PETS))));
        var order = inOrder(gateway);

        try (var ignored = executor.open(openApiPlan)) {
            order.verify(gateway).addOpenApiDescription(any(),
                    eq("http://isrest:8080/acl-parity/openapi/status_only.yaml?code=pt1-v40"), any());
            order.verify(gateway).enableDescription("id-pt1-v40");
            order.verify(gateway).addEndpoint(eq("id-pt1-v40"), any(), eq("GET"), eq("/pets/*"));
            order.verify(gateway).grantEndpointAccess("ep-GET-_pets_*", AclParticipants.CONSUMER);
            order.verify(gateway).updateOpenApiDescription(eq("id-pt1-v40"),
                    eq("http://isrest:8080/acl-parity/openapi/pets.yaml?code=pt1-v40"), any());
            verifyNoMoreInteractions(gateway);
        }

        verify(gateway, never()).deleteEndpoint(any());
    }

    @Test
    void endpointDeletedInSetupIsDeletedOnceBeforeTheRequestsAndNotAgainOnClose() {
        var deletePlan = new FixturePlanner("t1").plan("V36", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(), List.of(
                        new EndpointSpec("GET", "/foo", List.of(AclParticipants.CONSUMER), new Deleted()))))));
        var order = inOrder(gateway);

        try (var ignored = executor.open(deletePlan)) {
            order.verify(gateway).addRestDescription(any(), any(), eq("pt1-v36"));
            order.verify(gateway).enableDescription("id-pt1-v36");
            order.verify(gateway).addEndpoint(eq("id-pt1-v36"), any(), eq("GET"), eq("/foo"));
            order.verify(gateway).grantEndpointAccess("ep-GET-_foo", AclParticipants.CONSUMER);
            order.verify(gateway).deleteEndpoint("ep-GET-_foo");
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).deleteDescription("id-pt1-v36");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void endpointDeleteRefusedDuringSetupFailsTheSetupAndStillRemovesTheDescription() {
        doReturn(400).when(gateway).deleteEndpoint("ep-GET-_foo");
        var deletePlan = new FixturePlanner("t1").plan("V36", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(), List.of(new EndpointSpec("GET", "/foo", List.of(), new Deleted()))))));

        assertThatThrownBy(() -> executor.open(deletePlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint GET /foo")
                .hasMessageContaining("400");
        var order = inOrder(gateway);
        order.verify(gateway).deleteEndpoint("ep-GET-_foo");
        order.verify(gateway).deleteDescription("id-pt1-v36");
        order.verifyNoMoreInteractions();
    }

    @Test
    void editedEndpointKeepsItsIdAndIsDeletedUnderItAfterTheEdit() {
        var editPlan = new FixturePlanner("t1").plan("V37", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(), List.of(new EndpointSpec("GET", "/foo",
                        List.of(AclParticipants.CONSUMER), new EditedTo("GET", "/bar")))))));
        var order = inOrder(gateway);

        try (var ignored = executor.open(editPlan)) {
            order.verify(gateway).addRestDescription(any(), any(), eq("pt1-v37"));
            order.verify(gateway).enableDescription("id-pt1-v37");
            order.verify(gateway).addEndpoint(eq("id-pt1-v37"), any(), eq("GET"), eq("/foo"));
            order.verify(gateway).grantEndpointAccess("ep-GET-_foo", AclParticipants.CONSUMER);
            order.verify(gateway).updateEndpoint("ep-GET-_foo", "GET", "/bar");
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).deleteEndpoint("ep-GET-_foo");
        order.verify(gateway).deleteDescription("id-pt1-v37");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void groupIsCreatedFilledAndGrantedByItsIdThenDeletedAfterTheDescription() {
        var groupPlan = new FixturePlanner("t1").plan("V60", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(AclParticipants.PROVIDER, "partners", List.of(AclParticipants.CONSUMER)))));
        var groupId = "grp-%s-pt1-v60-partners".formatted(AclParticipants.PROVIDER);
        var order = inOrder(gateway);

        try (var ignored = executor.open(groupPlan)) {
            order.verify(gateway).createLocalGroup(AclParticipants.PROVIDER, "pt1-v60-partners");
            order.verify(gateway).addLocalGroupMember(groupId, AclParticipants.CONSUMER);
            order.verify(gateway).addRestDescription(any(), any(), eq("pt1-v60"));
            order.verify(gateway).enableDescription("id-pt1-v60");
            order.verify(gateway).grantAccess(any(), eq(groupId), eq("pt1-v60"));
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).deleteDescription("id-pt1-v60");
        order.verify(gateway).deleteLocalGroup(groupId);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void groupDeletedInSetupIsDeletedOnceAndNotAgainOnClose() {
        var groupPlan = new FixturePlanner("t1").plan("V63", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(AclParticipants.PROVIDER, "partners", List.of(AclParticipants.CONSUMER),
                        List.of(AclParticipants.CONSUMER), true))));
        var groupId = "grp-%s-pt1-v63-partners".formatted(AclParticipants.PROVIDER);
        var order = inOrder(gateway);

        try (var ignored = executor.open(groupPlan)) {
            order.verify(gateway).createLocalGroup(AclParticipants.PROVIDER, "pt1-v63-partners");
            order.verify(gateway).addLocalGroupMember(groupId, AclParticipants.CONSUMER);
            order.verify(gateway).addRestDescription(any(), any(), eq("pt1-v63"));
            order.verify(gateway).enableDescription("id-pt1-v63");
            order.verify(gateway).grantAccess(any(), eq(groupId), eq("pt1-v63"));
            order.verify(gateway).removeLocalGroupMember(groupId, AclParticipants.CONSUMER);
            order.verify(gateway).deleteLocalGroup(groupId);
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).deleteDescription("id-pt1-v63");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void localGroupWhoseSetupDeleteAnswersAnErrorIsDeletedAgainInCleanup() {
        doReturn(500).when(gateway).deleteLocalGroup(DELETED_GROUP_ID);

        assertThatThrownBy(() -> executor.open(DELETED_GROUP_PLAN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delete of local group pt1-v63-partners")
                .hasMessageContaining("500");

        verify(gateway, times(2)).deleteLocalGroup(DELETED_GROUP_ID);
    }

    @Test
    void localGroupWhoseSetupDeleteThrowsIsDeletedAgainInCleanup() {
        doThrow(new IllegalStateException("gateway down")).when(gateway).deleteLocalGroup(DELETED_GROUP_ID);

        assertThatThrownBy(() -> executor.open(DELETED_GROUP_PLAN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("gateway down");

        verify(gateway, times(2)).deleteLocalGroup(DELETED_GROUP_ID);
    }

    @Test
    void globalGroupWhoseDeleteAnswersAnErrorIsDeletedAgainInCleanup() {
        doReturn(500).when(gateway).deleteGlobalGroup("pt1-v74-everyone");
        var globalPlan = new FixturePlanner("t1").plan(groupDeletedRow());

        try (var fixture = executor.open(globalPlan)) {
            assertThatThrownBy(() -> fixture.apply(globalPlan.phases().get(0)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("delete of global group pt1-v74-everyone")
                    .hasMessageContaining("500");
            assertThatThrownBy(fixture::close).isInstanceOf(IllegalStateException.class);
        }

        verify(gateway, times(2)).deleteGlobalGroup("pt1-v74-everyone");
    }

    @Test
    void globalGroupWhoseDeleteThrowsIsDeletedAgainInCleanup() {
        doThrow(new IllegalStateException("gateway down")).when(gateway).deleteGlobalGroup("pt1-v74-everyone");
        var globalPlan = new FixturePlanner("t1").plan(groupDeletedRow());

        try (var fixture = executor.open(globalPlan)) {
            assertThatThrownBy(() -> fixture.apply(globalPlan.phases().get(0)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("gateway down");
            assertThatThrownBy(fixture::close).isInstanceOf(IllegalStateException.class);
        }

        verify(gateway, times(2)).deleteGlobalGroup("pt1-v74-everyone");
    }

    @Test
    void groupGrantOnAnEndpointLooksTheGroupUpByOwnerAndCode() {
        var groupPlan = new FixturePlanner("t1").plan("V66", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(), List.of(new EndpointSpec("GET", "/b", List.of(), List.of("partners"))))),
                List.of(new LocalGroupSpec(AclParticipants.PROVIDER, "partners", List.of()))));
        var order = inOrder(gateway);

        try (var ignored = executor.open(groupPlan)) {
            order.verify(gateway).grantEndpointAccess("ep-GET-_b", "grp-%s-pt1-v66-partners"
                    .formatted(AclParticipants.PROVIDER));
            order.verifyNoMoreInteractions();
        }
    }

    @Test
    void failedGroupCleanupIsReportedAndTheOtherGroupIsStillDeleted() {
        doReturn(500).when(gateway).deleteLocalGroup("grp-%s-pt1-v65-partners".formatted(SubsystemPool.SECOND_OWNER));
        var groupPlan = new FixturePlanner("t1").plan("V65", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of())),
                List.of(new LocalGroupSpec(AclParticipants.PROVIDER, "partners", List.of()),
                        new LocalGroupSpec(SubsystemPool.SECOND_OWNER, "partners", List.of()))));
        var fixture = executor.open(groupPlan);
        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local group pt1-v65-partners of " + SubsystemPool.SECOND_OWNER)
                .hasMessageContaining("500");
        var order = inOrder(gateway);
        order.verify(gateway).deleteLocalGroup("grp-%s-pt1-v65-partners".formatted(SubsystemPool.SECOND_OWNER));
        order.verify(gateway).deleteLocalGroup("grp-%s-pt1-v65-partners".formatted(AclParticipants.PROVIDER));
        order.verifyNoMoreInteractions();
    }

    @Test
    void failedSetupAfterGroupCreationDeletesTheGroup() {
        doThrow(new IllegalStateException("add refused")).when(gateway).addRestDescription(any(), any(), eq("pt1-v60"));
        var groupPlan = new FixturePlanner("t1").plan("V60", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(), List.of(), List.of("partners"))),
                List.of(new LocalGroupSpec(AclParticipants.PROVIDER, "partners", List.of()))));
        assertThatThrownBy(() -> executor.open(groupPlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("add refused");
        var order = inOrder(gateway);
        order.verify(gateway).createLocalGroup(AclParticipants.PROVIDER, "pt1-v60-partners");
        order.verify(gateway).deleteLocalGroup("grp-%s-pt1-v60-partners".formatted(AclParticipants.PROVIDER));
        order.verifyNoMoreInteractions();
    }

    @Test
    void closeTwiceDeletesOnce() {
        var fixture = executor.open(plan);

        fixture.close();
        fixture.close();

        verify(gateway).deleteDescription("id-pt1-v09-2");
        verify(gateway).deleteDescription("id-pt1-v09");
        verify(gateway, times(2)).deleteDescription(any());
        verifyNoEndpointOrGroupDeleted();
    }

    @Test
    void disableSendsTheNoticeOrNoneAtAll() {
        var disablePlan = new FixturePlanner("t1").plan("V123", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of())), List.of(), List.of(new Disable(0, null))));
        var order = inOrder(gateway);

        try (var ignored = executor.open(disablePlan)) {
            order.verify(gateway).disableDescription(eq("id-pt1-v123"), isNull());
            order.verifyNoMoreInteractions();
        }
    }

    @Test
    void renameRunsOnTheDescriptionIdOfTheOldCodeAndCleanupStillDeletesThatDescription() {
        var renamePlan = new FixturePlanner("t1").plan("V127", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of())), List.of(), List.of(new RenameService(0))));
        var order = inOrder(gateway);

        try (var ignored = executor.open(renamePlan)) {
            order.verify(gateway).renameRestService(eq("id-pt1-v127"), any(), eq("pt1-v127"), eq("pt1-v127-renamed"));
            order.verifyNoMoreInteractions();
        }

        order.verify(gateway).deleteDescription("id-pt1-v127");
        order.verifyNoMoreInteractions();
    }

    @Test
    void descriptionDeletedInSetupIsDeletedOnceAndNotAgainOnClose() {
        var deletePlan = new FixturePlanner("t1").plan("V125", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of())), List.of(), List.of(new Delete(0))));
        var order = inOrder(gateway);

        try (var ignored = executor.open(deletePlan)) {
            order.verify(gateway).deleteDescription("id-pt1-v125");
            order.verifyNoMoreInteractions();
        }

        verify(gateway, times(1)).deleteDescription(any());
        verifyNoEndpointOrGroupDeleted();
    }

    @Test
    void failedGrantBeforeTheSetupDeleteStillDeletesTheDescriptionInCleanup() {
        doThrow(new AssertionError("grant answered 500")).when(gateway).grantAccess(any(), any(), eq("pt1-v125"));
        var deletePlan = new FixturePlanner("t1").plan("V125", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(AclParticipants.CONSUMER))), List.of(), List.of(new Delete(0))));

        assertThatThrownBy(() -> executor.open(deletePlan))
                .isInstanceOf(AssertionError.class)
                .hasMessage("grant answered 500");
        var order = inOrder(gateway);
        order.verify(gateway).addRestDescription(any(), any(), eq("pt1-v125"));
        order.verify(gateway).enableDescription("id-pt1-v125");
        order.verify(gateway).grantAccess(any(), eq(AclParticipants.CONSUMER), eq("pt1-v125"));
        order.verify(gateway).deleteDescription("id-pt1-v125");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void cleanupDeleteOfADescriptionAlreadyGoneIsNotAFailure() {
        doThrow(new AssertionError("grant answered 500")).when(gateway).grantAccess(any(), any(), eq("pt1-v125"));
        doReturn(404).when(gateway).deleteDescription("id-pt1-v125");
        var deletePlan = new FixturePlanner("t1").plan("V125", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of(AclParticipants.CONSUMER))), List.of(), List.of(new Delete(0))));

        assertThatThrownBy(() -> executor.open(deletePlan))
                .isInstanceOf(AssertionError.class)
                .hasMessage("grant answered 500")
                .hasNoSuppressedExceptions();
        var order = inOrder(gateway);
        order.verify(gateway).deleteDescription("id-pt1-v125");
        order.verifyNoMoreInteractions();
    }

    @Test
    void closeAfterASuccessfulSetupDeleteDoesNotDeleteAgainAndDoesNotFail() {
        var deletePlan = new FixturePlanner("t1").plan("V125", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of())), List.of(), List.of(new Delete(0))));
        var fixture = executor.open(deletePlan);

        assertThatCode(fixture::close).doesNotThrowAnyException();
        verify(gateway, times(1)).deleteDescription(any());
        verifyNoEndpointOrGroupDeleted();
    }

    @Test
    void descriptionDeleteRefusedDuringSetupKeepsTheIdSoCleanupDeletesIt() {
        doReturn(500).when(gateway).deleteDescription("id-pt1-v125");
        var deletePlan = new FixturePlanner("t1").plan("V125", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of())), List.of(), List.of(new Delete(0))));

        assertThatThrownBy(() -> executor.open(deletePlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pt1-v125 answered 500");
        verify(gateway, times(2)).deleteDescription("id-pt1-v125");
    }

    @Test
    void descriptionDeleteRefusedDuringSetupFailsTheSetup() {
        doReturn(500).when(gateway).deleteDescription("id-pt1-v125");
        var deletePlan = new FixturePlanner("t1").plan("V125", new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new RestService(List.of())), List.of(), List.of(new Delete(0))));

        assertThatThrownBy(() -> executor.open(deletePlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pt1-v125 answered 500");
    }

    @Test
    void globalGroupIsCreatedBeforeItsGrantAndDeletedInCleanup() {
        var globalPlan = new FixturePlanner("t1").plan(globalGroupRow());
        var order = inOrder(gateway);

        try (var ignored = executor.open(globalPlan)) {
            order.verify(gateway).createGlobalGroup("pt1-v68-everyone");
            order.verify(gateway).addGlobalGroupMember("pt1-v68-everyone", AclParticipants.CONSUMER);
            order.verify(gateway).grantGlobalGroupAccess(any(), eq("DEV:pt1-v68-everyone"), eq("pt1-v68"));
        }

        order.verify(gateway).deleteGlobalGroup("pt1-v68-everyone");
        order.verifyNoMoreInteractions();
    }

    @Test
    void groupDeletedByAPhaseIsNotDeletedAgainInCleanup() {
        var globalPlan = new FixturePlanner("t1").plan(groupDeletedRow());

        try (var fixture = executor.open(globalPlan)) {
            fixture.apply(globalPlan.phases().get(0));
        }

        verify(gateway, times(1)).deleteGlobalGroup("pt1-v74-everyone");
    }

    @Test
    void ownersGroupIsGrantedButNeverCreatedOrDeleted() {
        var ownersPlan = new FixturePlanner("t1").plan(ownersGroupRow());

        try (var ignored = executor.open(ownersPlan)) {
            verify(gateway).grantGlobalGroupAccess(any(), eq("DEV:security-server-owners"), eq("pt1-v72"));
        }

        verify(gateway, never()).createGlobalGroup(any());
        verify(gateway, never()).deleteGlobalGroup(any());
    }

    @Test
    void missingGlobalGroupIsToleratedInCleanupAndOtherFailuresAreReported() {
        doReturn(404).when(gateway).deleteGlobalGroup("pt1-v68-everyone");
        var globalPlan = new FixturePlanner("t1").plan(globalGroupRow());
        assertThatCode(() -> executor.open(globalPlan).close()).doesNotThrowAnyException();

        doReturn(500).when(gateway).deleteGlobalGroup("pt1-v68-everyone");
        var fixture = executor.open(globalPlan);
        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("global group pt1-v68-everyone")
                .hasMessageContaining("500");
    }

    @Test
    void managementGrantsChangedInSetupAreChangedBackNewestFirstOnClose() {
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new AclScenario.ManagementService("clientReg",
                List.of("DEV:security-server-owners"), List.of(AclParticipants.CONSUMER))));
        var managementPlan = new FixturePlanner("t1").plan("VX", spec);
        var order = inOrder(gateway);

        try (var ignored = executor.open(managementPlan)) {
            order.verify(gateway).revokeAccess(any(), eq("DEV:security-server-owners"), eq("clientReg"));
            order.verify(gateway).grantAccess(any(), eq(AclParticipants.CONSUMER), eq("clientReg"));
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).revokeAccess(any(), eq(AclParticipants.CONSUMER), eq("clientReg"));
        order.verify(gateway).grantAccess(any(), eq("DEV:security-server-owners"), eq("clientReg"));
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void managementRowThatMovesTheGrantInAPhaseGivesTheOwnersGroupItsGrantBackOnClose() {
        var phasePlan = new FixturePlanner("t1").plan(managementMoveRow());
        var order = inOrder(gateway);

        try (var fixture = executor.open(phasePlan)) {
            verifyNoInteractions(gateway);
            fixture.apply(phasePlan.phases().get(0));
            order.verify(gateway).revokeAccess(any(), eq("DEV:security-server-owners"), eq("clientReg"));
            order.verify(gateway).grantAccess(any(), eq(AclParticipants.CONSUMER), eq("clientReg"));
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).revokeAccess(any(), eq(AclParticipants.CONSUMER), eq("clientReg"));
        order.verify(gateway).grantAccess(any(), eq("DEV:security-server-owners"), eq("clientReg"));
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void managementGrantThatAlreadyExistedIsNotRevokedOnClose() {
        doReturn(false).when(gateway).grantAccess(any(), eq(AclParticipants.CONSUMER), any());
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(new AclScenario.ManagementService("clientReg",
                List.of(), List.of(AclParticipants.CONSUMER))));
        var managementPlan = new FixturePlanner("t1").plan("VX", spec);

        try (var ignored = executor.open(managementPlan)) {
            verify(gateway).grantAccess(any(), eq(AclParticipants.CONSUMER), eq("clientReg"));
            verifyNoMoreInteractions(gateway);
        }

        verifyNoMoreInteractions(gateway);
    }

    @Test
    void managementGrantChangedByAPhaseIsChangedBackOnClose() {
        var fixtureSpec = new FixtureSpec(AclParticipants.PROVIDER,
                List.of(new AclScenario.ManagementService("clientReg", List.of(), List.of())));
        var row = new AclScenario("VX", "test row", fixtureSpec, List.of(), List.of(new AclScenario.Phase(
                List.of(new AclScenario.Revoke(0, AclParticipants.CONSUMER)), List.of())),
                AclScenario.WaitClass.ACL_CACHE);
        var phasePlan = new FixturePlanner("t1").plan(row);
        var order = inOrder(gateway);

        try (var fixture = executor.open(phasePlan)) {
            fixture.apply(phasePlan.phases().get(0));
            order.verify(gateway).revokeAccess(any(), eq(AclParticipants.CONSUMER), eq("clientReg"));
            verifyNoMoreInteractions(gateway);
        }

        order.verify(gateway).grantAccess(any(), eq(AclParticipants.CONSUMER), eq("clientReg"));
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void managementGrantChangedInASetupThatFailsLaterIsChangedBackBeforeTheFailureIsRethrown() {
        doThrow(new IllegalStateException("add refused")).when(gateway).addRestDescription(any(), any(), eq("pt1-vx-2"));
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(
                new AclScenario.ManagementService("clientReg", List.of("DEV:security-server-owners"), List.of()),
                new RestService(List.of())));
        var failingPlan = new FixturePlanner("t1").plan("VX", spec);

        assertThatThrownBy(() -> executor.open(failingPlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("add refused");
        var order = inOrder(gateway);
        order.verify(gateway).revokeAccess(any(), eq("DEV:security-server-owners"), eq("clientReg"));
        order.verify(gateway).addRestDescription(any(), any(), eq("pt1-vx-2"));
        order.verify(gateway).grantAccess(any(), eq("DEV:security-server-owners"), eq("clientReg"));
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void failedManagementRestoreIsReportedAndTheRestOfTheCleanupStillRuns() {
        doThrow(new AssertionError("grant answered 500")).when(gateway).grantAccess(any(), any(), eq("clientReg"));
        var spec = new FixtureSpec(AclParticipants.PROVIDER, List.of(
                new AclScenario.ManagementService("clientReg",
                        List.of("DEV:security-server-owners", AclParticipants.CONSUMER), List.of()),
                new RestService(List.of())));
        var restorePlan = new FixturePlanner("t1").plan("VX", spec);
        var fixture = executor.open(restorePlan);

        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fixture cleanup incomplete")
                .hasMessageContaining("restore of")
                .hasMessageContaining("DEV:security-server-owners")
                .hasMessageContaining(AclParticipants.CONSUMER)
                .hasMessageContaining("grant answered 500");
        var order = inOrder(gateway);
        order.verify(gateway).deleteDescription("id-pt1-vx-2");
        order.verifyNoMoreInteractions();
    }

    private void verifyNoEndpointOrGroupDeleted() {
        verify(gateway, never()).deleteEndpoint(any());
        verify(gateway, never()).deleteLocalGroup(any());
        verify(gateway, never()).deleteGlobalGroup(any());
    }

    private static AclScenario openApiSwapRow() {
        return phased("V39", openApiGranted(AclScenario.OpenApiSpec.PETS_THEN_STATUS_ONLY, "GET", "/pets/*"),
                List.of(), phase(List.of(new AclScenario.SwapBackendSpec(0),
                        new AclScenario.Refresh(0)))).as("swap row");
    }

    private static AclScenario revokeRow() {
        return phased("V11", restService(AclScenarioBuilders.CONSUMER_ONLY), List.of(),
                phase(new AclScenario.Revoke(0, AclParticipants.CONSUMER))).as("revoke row");
    }

    private static AclScenario globalGroupRow() {
        return globalconfRow("V68", globalGroupGranted(
                new AclScenario.GlobalGroupSpec(AclParticipants.GLOBAL_GROUP, AclScenarioBuilders.CONSUMER_ONLY)))
                .as("global group row");
    }

    private static AclScenario groupDeletedRow() {
        return globalconfPhased("V74", globalGroupGranted(
                new AclScenario.GlobalGroupSpec(AclParticipants.GLOBAL_GROUP, AclScenarioBuilders.CONSUMER_ONLY)),
                List.of(), phase(new AclScenario.DeleteGlobalGroup(AclParticipants.GLOBAL_GROUP)))
                .as("group deleted row");
    }

    private static AclScenario ownersGroupRow() {
        return globalconfRow("V72",
                provided(restToGlobalGroup(AclScenario.GlobalGroupSpec.SECURITY_SERVER_OWNERS)))
                .as("owners group row");
    }

    private static AclScenario managementMoveRow() {
        return phased("V106a",
                management(new AclScenario.ManagementService(AclParticipants.CLIENT_REG, List.of(), List.of())),
                List.of(),
                phase(List.of(new AclScenario.Revoke(0, AclParticipants.OWNERS_GROUP),
                        new AclScenario.Grant(0, AclParticipants.CONSUMER)))).as("management move row");
    }
}
