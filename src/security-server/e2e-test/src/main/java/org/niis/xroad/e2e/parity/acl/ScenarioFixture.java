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

import org.niis.xroad.e2e.parity.acl.AclScenario.AddGlobalGroupMember;
import org.niis.xroad.e2e.parity.acl.AclScenario.Delete;
import org.niis.xroad.e2e.parity.acl.AclScenario.DeleteGlobalGroup;
import org.niis.xroad.e2e.parity.acl.AclScenario.Deleted;
import org.niis.xroad.e2e.parity.acl.AclScenario.Disable;
import org.niis.xroad.e2e.parity.acl.AclScenario.EditedTo;
import org.niis.xroad.e2e.parity.acl.AclScenario.Enable;
import org.niis.xroad.e2e.parity.acl.AclScenario.EndpointSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.FixtureSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.GlobalGroupSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.Grant;
import org.niis.xroad.e2e.parity.acl.AclScenario.GrantGroup;
import org.niis.xroad.e2e.parity.acl.AclScenario.Kept;
import org.niis.xroad.e2e.parity.acl.AclScenario.LocalGroupSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.ManagementService;
import org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiService;
import org.niis.xroad.e2e.parity.acl.AclScenario.OpenApiSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.PhaseChange;
import org.niis.xroad.e2e.parity.acl.AclScenario.Refresh;
import org.niis.xroad.e2e.parity.acl.AclScenario.RemoveGlobalGroupMember;
import org.niis.xroad.e2e.parity.acl.AclScenario.RenameService;
import org.niis.xroad.e2e.parity.acl.AclScenario.RestService;
import org.niis.xroad.e2e.parity.acl.AclScenario.Revoke;
import org.niis.xroad.e2e.parity.acl.AclScenario.SwapBackendSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.UndefinedService;
import org.niis.xroad.e2e.parity.acl.AclScenario.WsdlService;
import org.niis.xroad.e2e.parity.acl.AclScenario.WsdlShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * The fixtures of one scenario on the provider's Security Server and the Central Server. Opening walks the
 * scenario's fixture spec through an {@link AdminGateway}, and prepares the scenario's phases without running them.
 * Service and group codes carry the run token and the scenario id, so scenarios and runs never collide.
 *
 * <p>Every object the fixture creates, and every management grant it changes, gets one undo step on a single
 * stack. {@link #close()} runs the steps newest first, keeps going when a step fails, treats HTTP 404 on a delete as
 * done, and reports the failures together. A step that a scenario runs itself, such as the deletion of a group
 * or of a description, leaves the stack only once it succeeded, so close retries it after a failure. A setup that
 * fails part way runs the steps it has before the failure is rethrown. Deleting a description removes its access
 * rights, so grants are not undone; endpoints the scenario leaves in place are deleted before their description.
 * The {@code security-server-owners} group and management services belong to the provider: used under their own
 * code, never created or deleted.
 */
public final class ScenarioFixture implements AutoCloseable {

    private static final String BACKEND_ORIGIN = "http://isrest:8080";
    private static final String BACKEND_PATH_PREFIX = "/acl-parity/";
    private static final String OPENAPI_PATH_PREFIX = BACKEND_PATH_PREFIX + "openapi/";
    private static final String RENAMED_SUFFIX = "-renamed";

    private final AdminGateway gateway;
    private final String runToken;
    private final AclScenario scenario;
    private final FixtureSpec spec;
    private final String providerClientId;

    private final List<String> serviceCodes = new ArrayList<>();
    private final List<Service> services = new ArrayList<>();
    private final Set<String> managementCodes = new HashSet<>();
    private final List<LocalGroup> localGroups = new ArrayList<>();
    private final Map<String, String> providerGroupIds = new HashMap<>();
    private final Map<String, UndoStep> globalGroupSteps = new HashMap<>();
    private final Deque<UndoStep> undoSteps = new ArrayDeque<>();
    private final List<List<Runnable>> phases = new ArrayList<>();

    private ScenarioFixture(AdminGateway gateway, String runToken, AclScenario scenario) {
        this.gateway = gateway;
        this.runToken = runToken.toLowerCase(Locale.ROOT);
        this.scenario = scenario;
        this.spec = scenario.fixture();
        this.providerClientId = spec.providerClientId();
    }

    /** Creates the scenario's fixtures and returns the handle that removes them on {@link #close()}. */
    public static ScenarioFixture open(AdminGateway gateway, String runToken, AclScenario scenario) {
        var fixture = new ScenarioFixture(gateway, runToken, scenario);
        try {
            fixture.setUp();
        } catch (RuntimeException | AssertionError e) {
            try {
                fixture.close();
            } catch (IllegalStateException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
        return fixture;
    }

    /** The namespaced service codes, in the order of the fixture's services. */
    public List<String> serviceCodes() {
        return List.copyOf(serviceCodes);
    }

    /** Makes the changes of the scenario's phase with the given index, in order. */
    public void applyPhase(int index) {
        phases.get(index).forEach(Runnable::run);
    }

    /** Runs the undo steps, newest first, and reports every step that failed. */
    @Override
    public void close() {
        var failures = new ArrayList<String>();
        while (!undoSteps.isEmpty()) {
            var step = undoSteps.pop();
            try {
                var status = step.run().getAsInt();
                if (!Reply.isSuccess(status) && status != HTTP_NOT_FOUND) {
                    failures.add("%s answered %d".formatted(step.name(), status));
                }
            } catch (RuntimeException | AssertionError e) {
                failures.add("%s failed: %s".formatted(step.name(), e.getMessage()));
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("fixture cleanup incomplete: " + String.join("; ", failures));
        }
    }

    private void setUp() {
        createGlobalGroups();
        createLocalGroups();
        createServices();
        changeDescriptions();
        followUpLocalGroups();
        scenario.phases().forEach(phase -> phases.add(phase.changes().stream().map(this::prepare).toList()));
    }

    private void createGlobalGroups() {
        for (var group : spec.globalGroups()) {
            if (group.isOwnersGroup()) {
                throw new IllegalArgumentException(
                        "group %s exists on every Central Server and is never created".formatted(group.code()));
            }
            var code = scoped(group.code());
            gateway.createGlobalGroup(code);
            globalGroupSteps.put(code, pushDelete("global group " + code, gateway::deleteGlobalGroup, code));
            for (var memberId : group.memberIds()) {
                gateway.addGlobalGroupMember(code, memberId);
            }
        }
    }

    private void createLocalGroups() {
        for (var group : spec.groups()) {
            var code = scoped(group.code());
            var id = gateway.createLocalGroup(group.ownerClientId(), code);
            var step = pushDelete("local group %s of %s".formatted(code, group.ownerClientId()),
                    gateway::deleteLocalGroup, id);
            localGroups.add(new LocalGroup(group, id, step));
            if (group.ownerClientId().equals(providerClientId)) {
                providerGroupIds.put(group.code(), id);
            }
            for (var memberId : group.memberIds()) {
                gateway.addLocalGroupMember(id, memberId);
            }
        }
    }

    private void followUpLocalGroups() {
        for (var group : localGroups) {
            for (var memberId : group.spec().removedMemberIds()) {
                gateway.removeLocalGroupMember(group.id(), memberId);
            }
            if (group.spec().deleted()) {
                deleteNow(group.step());
            }
        }
    }

    private void createServices() {
        for (var index = 0; index < spec.services().size(); index++) {
            var code = serviceCode(index);
            switch (spec.services().get(index)) {
                case RestService rest -> addRest(code, rest);
                case OpenApiService openApi -> addOpenApi(code, openApi);
                case WsdlService wsdl -> addWsdl(code, wsdl);
                case UndefinedService undefined -> {
                    serviceCodes.add(code);
                    services.add(Service.NONE);
                }
                case ManagementService management -> changeManagementGrants(management);
            }
        }
    }

    private void addRest(String code, RestService rest) {
        var id = gateway.addRestDescription(providerClientId, backendUrl(code), code);
        register(code, id);
        serviceCodes.add(code);
        gateway.enableDescription(id);
        rest.grantedConsumerIds().forEach(consumerId -> grantAccess(code, consumerId));
        rest.grantedGroupCodes().forEach(groupCode ->
                gateway.grantAccess(providerClientId, providerGroupId(groupCode), code));
        rest.grantedGlobalGroupCodes().forEach(groupCode ->
                gateway.grantGlobalGroupAccess(providerClientId, globalGroupId(groupCode), code));
        addEndpoints(code, id, rest.endpoints(), true);
    }

    private void addOpenApi(String code, OpenApiService openApi) {
        var id = gateway.addOpenApiDescription(providerClientId, openApiUrl(openApi.spec(), code), code);
        register(code, id);
        serviceCodes.add(code);
        gateway.enableDescription(id);
        addEndpoints(code, id, openApi.manualEndpoints(), false);
        for (var generated : openApi.generatedGrants()) {
            var endpointId = gateway.findEndpointId(id, code, generated.method(), generated.path());
            generated.grantedConsumerIds().forEach(consumerId -> gateway.grantEndpointAccess(endpointId, consumerId));
        }
        if (openApi.editedToSpec() != null) {
            gateway.updateOpenApiDescription(id, openApiUrl(openApi.editedToSpec(), code), code);
        }
    }

    private void addWsdl(String base, WsdlService wsdl) {
        var codes = wsdlCodes(base, wsdl);
        var id = gateway.addWsdlDescription(providerClientId, wsdlUrl(wsdl.shape(), codes));
        register(codes.get(0), id);
        serviceCodes.addAll(codes);
        gateway.enableDescription(id);
        for (var i = 0; i < codes.size(); i++) {
            for (var consumerId : wsdl.grantedConsumerIdsByCode().get(i)) {
                grantAccess(codes.get(i), consumerId);
            }
        }
    }

    private void changeManagementGrants(ManagementService management) {
        var code = management.serviceCode();
        serviceCodes.add(code);
        services.add(Service.NONE);
        managementCodes.add(code);
        management.revokedConsumerIds().forEach(consumerId -> revokeAccess(code, consumerId));
        management.grantedConsumerIds().forEach(consumerId -> grantAccess(code, consumerId));
    }

    private void register(String code, String descriptionId) {
        var step = pushDelete("service description " + code, gateway::deleteDescription, descriptionId);
        services.add(new Service(code, descriptionId, step));
    }

    private void addEndpoints(String serviceCode, String descriptionId, List<EndpointSpec> endpoints,
                              boolean deleteOnClose) {
        for (var endpoint : endpoints) {
            var id = gateway.addEndpoint(descriptionId, serviceCode, endpoint.method(), endpoint.path());
            var step = deleteStep("endpoint %s %s of %s".formatted(endpoint.method(), endpoint.path(), serviceCode),
                    gateway::deleteEndpoint, id);
            if (deleteOnClose) {
                undoSteps.push(step);
            }
            endpoint.grantedConsumerIds().forEach(consumerId -> gateway.grantEndpointAccess(id, consumerId));
            endpoint.grantedGroupCodes().forEach(groupCode ->
                    gateway.grantEndpointAccess(id, providerGroupId(groupCode)));
            endpoint.grantedGlobalGroupCodes().forEach(groupCode ->
                    gateway.grantGlobalGroupEndpointAccess(id, globalGroupId(groupCode)));
            switch (endpoint.followUp()) {
                case Kept kept -> { }
                case Deleted deleted -> deleteNow(step);
                case EditedTo edited -> gateway.updateEndpoint(id, edited.method(), edited.path());
            }
        }
    }

    private void changeDescriptions() {
        for (var change : spec.changes()) {
            var service = described(change.serviceIndex());
            switch (change) {
                case Disable disable -> gateway.disableDescription(service.descriptionId(), disable.notice());
                case Enable enable -> gateway.enableDescription(service.descriptionId());
                case Delete delete -> deleteNow(service.undo());
                case RenameService rename -> renameService(rename, service);
            }
        }
    }

    private void renameService(RenameService rename, Service service) {
        if (!(spec.services().get(rename.serviceIndex()) instanceof RestService)) {
            throw new IllegalArgumentException("service %d is not a REST service".formatted(rename.serviceIndex()));
        }
        var renamed = service.code() + RENAMED_SUFFIX;
        gateway.renameRestService(service.descriptionId(), backendUrl(service.code()), service.code(), renamed);
        serviceCodes.add(renamed);
    }

    private Runnable prepare(PhaseChange change) {
        return switch (change) {
            case Grant grant -> {
                var code = serviceCodes.get(grant.serviceIndex());
                yield () -> grantAccess(code, grant.consumerId());
            }
            case Revoke revoke -> {
                var code = serviceCodes.get(revoke.serviceIndex());
                yield () -> revokeAccess(code, revoke.consumerId());
            }
            case GrantGroup grantGroup -> {
                var code = serviceCodes.get(grantGroup.serviceIndex());
                var groupId = providerGroupId(grantGroup.groupCode());
                yield () -> gateway.grantAccess(providerClientId, groupId, code);
            }
            case SwapBackendSpec swap -> prepareSwap(swap);
            case Refresh refresh -> {
                var descriptionId = described(refresh.serviceIndex()).descriptionId();
                yield () -> gateway.refreshDescription(descriptionId);
            }
            case AddGlobalGroupMember add -> {
                var code = globalGroupCode(add.code());
                yield () -> gateway.addGlobalGroupMember(code, add.memberId());
            }
            case RemoveGlobalGroupMember remove -> {
                var code = globalGroupCode(remove.code());
                yield () -> gateway.removeGlobalGroupMember(code, remove.memberId());
            }
            case DeleteGlobalGroup delete -> {
                var step = globalGroupSteps.get(globalGroupCode(delete.code()));
                if (step == null) {
                    throw new IllegalArgumentException(
                            "global group %s is not created by the fixture".formatted(delete.code()));
                }
                yield () -> deleteNow(step);
            }
        };
    }

    private Runnable prepareSwap(SwapBackendSpec swap) {
        if (!(spec.services().get(swap.serviceIndex()) instanceof OpenApiService openApi)
                || openApi.spec() != OpenApiSpec.PETS_THEN_STATUS_ONLY) {
            throw new IllegalArgumentException(
                    "service %d is not a swappable OpenAPI service".formatted(swap.serviceIndex()));
        }
        var path = swapPath(openApi.spec());
        return () -> requireSuccess(gateway.swapBackendSpec(path), "backend swap request " + path);
    }

    private void grantAccess(String serviceCode, String consumerId) {
        var created = gateway.grantAccess(providerClientId, consumerId, serviceCode);
        if (created && managementCodes.contains(serviceCode)) {
            undoSteps.push(restoreStep(serviceCode, consumerId,
                    () -> gateway.revokeAccess(providerClientId, consumerId, serviceCode)));
        }
    }

    private void revokeAccess(String serviceCode, String consumerId) {
        gateway.revokeAccess(providerClientId, consumerId, serviceCode);
        if (managementCodes.contains(serviceCode)) {
            undoSteps.push(restoreStep(serviceCode, consumerId,
                    () -> gateway.grantAccess(providerClientId, consumerId, serviceCode)));
        }
    }

    private static UndoStep restoreStep(String serviceCode, String consumerId, Runnable restore) {
        return new UndoStep("restore of access of %s to %s".formatted(consumerId, serviceCode), () -> {
            restore.run();
            return HTTP_OK;
        });
    }

    private UndoStep pushDelete(String what, ToIntFunction<String> delete, String id) {
        var step = deleteStep(what, delete, id);
        undoSteps.push(step);
        return step;
    }

    private static UndoStep deleteStep(String what, ToIntFunction<String> delete, String id) {
        return new UndoStep("delete of " + what, () -> delete.applyAsInt(id));
    }

    private void deleteNow(UndoStep step) {
        requireSuccess(step.run().getAsInt(), step.name());
        undoSteps.remove(step);
    }

    private static void requireSuccess(int status, String what) {
        if (!Reply.isSuccess(status)) {
            throw new IllegalStateException("%s answered %d".formatted(what, status));
        }
    }

    private Service described(int serviceIndex) {
        var service = services.get(serviceIndex);
        if (service.descriptionId() == null) {
            throw new IllegalArgumentException("service %d has no description".formatted(serviceIndex));
        }
        return service;
    }

    private String providerGroupId(String code) {
        var id = providerGroupIds.get(code);
        if (id == null) {
            throw new IllegalArgumentException(
                    "group %s is granted but not created under %s".formatted(code, providerClientId));
        }
        return id;
    }

    private String globalGroupCode(String code) {
        if (GlobalGroupSpec.SECURITY_SERVER_OWNERS.equals(code)) {
            return code;
        }
        if (spec.globalGroups().stream().noneMatch(group -> group.code().equals(code))) {
            throw new IllegalArgumentException("global group %s is used but not created".formatted(code));
        }
        return scoped(code);
    }

    private String globalGroupId(String code) {
        return providerClientId.substring(0, providerClientId.indexOf(':')) + ":" + globalGroupCode(code);
    }

    private String scoped(String code) {
        return "p%s-%s-%s".formatted(runToken, scenario.id().toLowerCase(Locale.ROOT), code);
    }

    private String serviceCode(int serviceIndex) {
        var base = "p%s-%s".formatted(runToken, scenario.id().toLowerCase(Locale.ROOT));
        return serviceIndex == 0 ? base : base + "-" + (serviceIndex + 1);
    }

    private static List<String> wsdlCodes(String base, WsdlService wsdl) {
        var codes = new ArrayList<String>();
        for (var i = 0; i < wsdl.shape().codeCount(); i++) {
            codes.add(base + (i == 0 ? "" : "-b") + wsdl.codeTail());
        }
        return codes;
    }

    private static String backendUrl(String serviceCode) {
        return BACKEND_ORIGIN + BACKEND_PATH_PREFIX + serviceCode;
    }

    private static String openApiUrl(OpenApiSpec spec, String serviceCode) {
        return "%s%s%s.yaml?code=%s".formatted(BACKEND_ORIGIN, OPENAPI_PATH_PREFIX, specName(spec), serviceCode);
    }

    private static String swapPath(OpenApiSpec spec) {
        return "%s%s/swap".formatted(OPENAPI_PATH_PREFIX, specName(spec));
    }

    private static String wsdlUrl(WsdlShape shape, List<String> codes) {
        var query = shape == WsdlShape.PAIR
                ? "code1=%s&code2=%s".formatted(codes.get(0), codes.get(1))
                : "code=" + codes.get(0);
        return "%s%swsdl/%s.wsdl?%s".formatted(BACKEND_ORIGIN, BACKEND_PATH_PREFIX, specName(shape), query);
    }

    private static String specName(Enum<?> spec) {
        return spec.name().toLowerCase(Locale.ROOT);
    }

    private record Service(String code, String descriptionId, UndoStep undo) {
        static final Service NONE = new Service(null, null, null);
    }

    private record LocalGroup(LocalGroupSpec spec, String id, UndoStep step) {
    }

    /** One undo step; {@code run} returns the HTTP status of the call, or 200 for a step that makes no HTTP call. */
    private record UndoStep(String name, IntSupplier run) {
    }
}
