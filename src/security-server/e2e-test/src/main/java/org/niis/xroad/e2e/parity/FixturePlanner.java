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

import org.niis.xroad.e2e.parity.AdminCall.AddEndpoint;
import org.niis.xroad.e2e.parity.AdminCall.AddGlobalGroupMember;
import org.niis.xroad.e2e.parity.AdminCall.AddLocalGroupMember;
import org.niis.xroad.e2e.parity.AdminCall.AddOpenApiDescription;
import org.niis.xroad.e2e.parity.AdminCall.AddRestDescription;
import org.niis.xroad.e2e.parity.AdminCall.AddWsdlDescription;
import org.niis.xroad.e2e.parity.AdminCall.CreateGlobalGroup;
import org.niis.xroad.e2e.parity.AdminCall.CreateLocalGroup;
import org.niis.xroad.e2e.parity.AdminCall.DeleteDescription;
import org.niis.xroad.e2e.parity.AdminCall.DeleteEndpoint;
import org.niis.xroad.e2e.parity.AdminCall.DeleteGlobalGroup;
import org.niis.xroad.e2e.parity.AdminCall.DeleteLocalGroup;
import org.niis.xroad.e2e.parity.AdminCall.DisableDescription;
import org.niis.xroad.e2e.parity.AdminCall.EnableDescription;
import org.niis.xroad.e2e.parity.AdminCall.GrantAccess;
import org.niis.xroad.e2e.parity.AdminCall.GrantEndpointAccess;
import org.niis.xroad.e2e.parity.AdminCall.GrantGlobalGroupAccess;
import org.niis.xroad.e2e.parity.AdminCall.GrantGlobalGroupEndpointAccess;
import org.niis.xroad.e2e.parity.AdminCall.GrantGroupAccess;
import org.niis.xroad.e2e.parity.AdminCall.GrantGroupEndpointAccess;
import org.niis.xroad.e2e.parity.AdminCall.OverrideBackendStub;
import org.niis.xroad.e2e.parity.AdminCall.RefreshDescription;
import org.niis.xroad.e2e.parity.AdminCall.RemoveBackendStubs;
import org.niis.xroad.e2e.parity.AdminCall.RemoveGlobalGroupMember;
import org.niis.xroad.e2e.parity.AdminCall.RemoveLocalGroupMember;
import org.niis.xroad.e2e.parity.AdminCall.RenameRestService;
import org.niis.xroad.e2e.parity.AdminCall.RevokeAccess;
import org.niis.xroad.e2e.parity.AdminCall.UpdateEndpoint;
import org.niis.xroad.e2e.parity.AdminCall.UpdateOpenApiDescription;
import org.niis.xroad.e2e.parity.Scenario.Delete;
import org.niis.xroad.e2e.parity.Scenario.Deleted;
import org.niis.xroad.e2e.parity.Scenario.DescriptionChange;
import org.niis.xroad.e2e.parity.Scenario.Disable;
import org.niis.xroad.e2e.parity.Scenario.EditedTo;
import org.niis.xroad.e2e.parity.Scenario.Enable;
import org.niis.xroad.e2e.parity.Scenario.EndpointSpec;
import org.niis.xroad.e2e.parity.Scenario.FixtureSpec;
import org.niis.xroad.e2e.parity.Scenario.GeneratedEndpointGrant;
import org.niis.xroad.e2e.parity.Scenario.GlobalGroupSpec;
import org.niis.xroad.e2e.parity.Scenario.Grant;
import org.niis.xroad.e2e.parity.Scenario.Kept;
import org.niis.xroad.e2e.parity.Scenario.LocalGroupSpec;
import org.niis.xroad.e2e.parity.Scenario.OpenApiService;
import org.niis.xroad.e2e.parity.Scenario.OpenApiSpec;
import org.niis.xroad.e2e.parity.Scenario.Phase;
import org.niis.xroad.e2e.parity.Scenario.Refresh;
import org.niis.xroad.e2e.parity.Scenario.RenameService;
import org.niis.xroad.e2e.parity.Scenario.ReplaceWsdlSecondService;
import org.niis.xroad.e2e.parity.Scenario.RestService;
import org.niis.xroad.e2e.parity.Scenario.Revoke;
import org.niis.xroad.e2e.parity.Scenario.ServiceSpec;
import org.niis.xroad.e2e.parity.Scenario.SwapOpenApiSpec;
import org.niis.xroad.e2e.parity.Scenario.UndefinedService;
import org.niis.xroad.e2e.parity.Scenario.WsdlService;
import org.niis.xroad.e2e.parity.Scenario.WsdlShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a scenario's fixture spec into admin calls. Every service code carries the run token and the
 * scenario id, so fixtures of different scenarios and of earlier runs never collide, and no shared
 * service description is ever touched. Deleting a service description also removes its access rights,
 * so the cleanup is one delete per created description, preceded by the delete of each REST endpoint the scenario
 * created and left in place. The cleanup also covers a description the scenario deletes itself, so it is still
 * removed when the setup fails before that delete; the executor treats the repeat delete as harmless. The base endpoint
 * and generated endpoints are never deleted on their own, and the endpoints of an OpenAPI description are removed
 * only with the description. Local groups carry the run token and the scenario id in their code too; each group
 * the scenario did not delete itself is deleted in cleanup, after the descriptions. Global groups carry the same
 * namespace and are deleted last; the {@code security-server-owners} group is used under its own code and is never
 * created or deleted.
 */
public final class FixturePlanner {

    /** Origin of the backend stub that answers every path under {@link #BACKEND_PATH_PREFIX}. */
    public static final String BACKEND_ORIGIN = "http://isrest:8080";
    public static final String BACKEND_PATH_PREFIX = "/acl-parity/";
    private static final String OPENAPI_PATH_PREFIX = BACKEND_PATH_PREFIX + "openapi/";
    private static final String RENAMED_SUFFIX = "-renamed";
    private static final String NEW_SUFFIX = "-new";
    private static final String OPENAPI_STUB_PREFIX = "acl_parity_openapi_";
    private static final String WSDL_STUB_PREFIX = "acl_parity_wsdl_";
    private static final String SECOND_CODE_PLACEHOLDER = "{{request.query.code2}}";

    private final String runToken;

    public FixturePlanner(String runToken) {
        this.runToken = runToken.toLowerCase(Locale.ROOT);
    }

    public FixturePlan plan(Scenario scenario) {
        return plan(scenario.id(), scenario.fixture(), scenario.phases());
    }

    public FixturePlan plan(String scenarioId, FixtureSpec spec) {
        return plan(scenarioId, spec, List.of());
    }

    private FixturePlan plan(String scenarioId, FixtureSpec spec, List<Phase> phases) {
        var codes = new ArrayList<String>();
        var descriptionKeys = new ArrayList<String>();
        var serviceKeys = new ArrayList<String>();
        var endpointDeletes = new ArrayList<List<AdminCall>>();
        var setup = new ArrayList<AdminCall>();
        var services = spec.services();
        globalGroupSetup(setup, scenarioId, spec.globalGroups());
        groupSetup(setup, scenarioId, spec.groups());
        for (var index = 0; index < services.size(); index++) {
            var base = serviceCode(scenarioId, index);
            switch (services.get(index)) {
                case RestService rest -> {
                    codes.add(base);
                    descriptionKeys.add(base);
                    serviceKeys.add(base);
                    setup.add(new AddRestDescription(spec.providerClientId(), backendUrl(base), base));
                    setup.add(new EnableDescription(base));
                    grant(setup, spec.providerClientId(), base, rest.grantedConsumerIds());
                    groupGrants(setup, scenarioId, spec, base, rest.grantedGroupCodes());
                    globalGroupGrants(setup, scenarioId, spec, base, rest.grantedGlobalGroupCodes());
                    endpointDeletes.add(endpoints(setup, scenarioId, spec, base, rest.endpoints()));
                }
                case OpenApiService openApi -> {
                    codes.add(base);
                    descriptionKeys.add(base);
                    serviceKeys.add(base);
                    endpointDeletes.add(List.of());
                    setup.add(new AddOpenApiDescription(spec.providerClientId(),
                            openApiUrl(openApi.spec(), base), base));
                    setup.add(new EnableDescription(base));
                    endpoints(setup, scenarioId, spec, base, openApi.manualEndpoints());
                    generatedGrants(setup, base, openApi.generatedGrants());
                    if (openApi.editedToSpec() != null) {
                        setup.add(new UpdateOpenApiDescription(base, openApiUrl(openApi.editedToSpec(), base)));
                    }
                }
                case WsdlService wsdl -> {
                    var wsdlCodes = wsdlCodes(base, wsdl);
                    var key = wsdlCodes.get(0);
                    codes.addAll(wsdlCodes);
                    descriptionKeys.add(key);
                    serviceKeys.add(key);
                    endpointDeletes.add(List.of());
                    setup.add(new AddWsdlDescription(spec.providerClientId(), wsdlUrl(wsdl.shape(), wsdlCodes), key));
                    setup.add(new EnableDescription(key));
                    for (var i = 0; i < wsdlCodes.size(); i++) {
                        grant(setup, spec.providerClientId(), wsdlCodes.get(i), wsdl.grantedConsumerIdsByCode().get(i));
                    }
                }
                case UndefinedService undefined -> {
                    codes.add(base);
                    serviceKeys.add(null);
                }
            }
        }
        descriptionChanges(setup, codes, services, serviceKeys, spec.changes());
        groupFollowUps(setup, scenarioId, spec.groups());
        var phaseCalls = phaseCalls(scenarioId, spec, codes, serviceKeys, phases);
        var cleanup = new ArrayList<AdminCall>();
        if (phaseCalls.stream().flatMap(List::stream).anyMatch(OverrideBackendStub.class::isInstance)) {
            cleanup.add(new RemoveBackendStubs());
        }
        for (var index = descriptionKeys.size() - 1; index >= 0; index--) {
            cleanup.addAll(endpointDeletes.get(index).reversed());
            cleanup.add(new DeleteDescription(descriptionKeys.get(index)));
        }
        for (var group : spec.groups().reversed()) {
            if (!group.deleted()) {
                cleanup.add(new DeleteLocalGroup(group.ownerClientId(), groupCode(scenarioId, group.code())));
            }
        }
        for (var group : spec.globalGroups().reversed()) {
            cleanup.add(new DeleteGlobalGroup(groupCode(scenarioId, group.code())));
        }
        return new FixturePlan(List.copyOf(codes), List.copyOf(setup), List.copyOf(phaseCalls), List.copyOf(cleanup));
    }

    private List<AdminCall> endpoints(List<AdminCall> setup, String scenarioId, FixtureSpec spec,
                                      String serviceCode, List<EndpointSpec> endpoints) {
        var deletes = new ArrayList<AdminCall>();
        for (var endpoint : endpoints) {
            setup.add(new AddEndpoint(serviceCode, endpoint.method(), endpoint.path()));
            for (var consumer : endpoint.grantedConsumerIds()) {
                setup.add(new GrantEndpointAccess(serviceCode, endpoint.method(), endpoint.path(), consumer));
            }
            for (var groupCode : endpoint.grantedGroupCodes()) {
                setup.add(new GrantGroupEndpointAccess(spec.providerClientId(),
                        grantedGroupCode(scenarioId, spec, groupCode), serviceCode, endpoint.method(),
                        endpoint.path()));
            }
            for (var groupCode : endpoint.grantedGlobalGroupCodes()) {
                setup.add(new GrantGlobalGroupEndpointAccess(spec.providerClientId(),
                        globalGroupCode(scenarioId, spec, groupCode), serviceCode, endpoint.method(),
                        endpoint.path()));
            }
            switch (endpoint.followUp()) {
                case Kept kept -> deletes.add(new DeleteEndpoint(serviceCode, endpoint.method(), endpoint.path()));
                case Deleted deleted ->
                        setup.add(new DeleteEndpoint(serviceCode, endpoint.method(), endpoint.path()));
                case EditedTo edited -> {
                    setup.add(new UpdateEndpoint(serviceCode, endpoint.method(), endpoint.path(),
                            edited.method(), edited.path()));
                    deletes.add(new DeleteEndpoint(serviceCode, edited.method(), edited.path()));
                }
            }
        }
        return deletes;
    }

    private static void descriptionChanges(List<AdminCall> setup, List<String> codes, List<ServiceSpec> services,
                                           List<String> serviceKeys, List<DescriptionChange> changes) {
        for (var change : changes) {
            var key = serviceKeys.get(change.serviceIndex());
            if (key == null) {
                throw new IllegalArgumentException("service %d has no description to change"
                        .formatted(change.serviceIndex()));
            }
            switch (change) {
                case Disable disable -> setup.add(new DisableDescription(key, disable.notice()));
                case Enable enable -> setup.add(new EnableDescription(key));
                case Delete delete -> setup.add(new DeleteDescription(key));
                case RenameService rename -> {
                    if (!(services.get(rename.serviceIndex()) instanceof RestService)) {
                        throw new IllegalArgumentException(
                                "service %d is not a REST service".formatted(rename.serviceIndex()));
                    }
                    var renamed = key + RENAMED_SUFFIX;
                    setup.add(new RenameRestService(key, backendUrl(key), renamed));
                    codes.add(renamed);
                }
            }
        }
    }

    private List<List<AdminCall>> phaseCalls(String scenarioId, FixtureSpec spec, List<String> codes,
                                             List<String> serviceKeys, List<Phase> phases) {
        var result = new ArrayList<List<AdminCall>>();
        for (var phase : phases) {
            var calls = new ArrayList<AdminCall>();
            for (var change : phase.changes()) {
                switch (change) {
                    case Grant grant -> calls.add(new GrantAccess(spec.providerClientId(), grant.consumerId(),
                            codes.get(grant.serviceIndex())));
                    case Revoke revoke -> calls.add(new RevokeAccess(spec.providerClientId(), revoke.consumerId(),
                            codes.get(revoke.serviceIndex())));
                    case Refresh refresh ->
                            calls.add(new RefreshDescription(descriptionKey(serviceKeys, refresh.serviceIndex())));
                    case SwapOpenApiSpec swap -> calls.add(swapOpenApi(spec, serviceKeys, swap));
                    case ReplaceWsdlSecondService replace ->
                            calls.add(replaceWsdlSecondService(scenarioId, spec, codes, replace));
                    case Scenario.AddGlobalGroupMember add -> calls.add(new AddGlobalGroupMember(
                            globalGroupCode(scenarioId, spec, add.code()), add.memberId()));
                    case Scenario.RemoveGlobalGroupMember remove -> calls.add(new RemoveGlobalGroupMember(
                            globalGroupCode(scenarioId, spec, remove.code()), remove.memberId()));
                    case Scenario.DeleteGlobalGroup delete ->
                            calls.add(new DeleteGlobalGroup(globalGroupCode(scenarioId, spec, delete.code())));
                }
            }
            result.add(List.copyOf(calls));
        }
        return result;
    }

    private static AdminCall swapOpenApi(FixtureSpec spec, List<String> serviceKeys, SwapOpenApiSpec swap) {
        if (!(spec.services().get(swap.serviceIndex()) instanceof OpenApiService openApi)) {
            throw new IllegalArgumentException("service %d is not an OpenAPI service".formatted(swap.serviceIndex()));
        }
        var served = openApi.editedToSpec() != null ? openApi.editedToSpec() : openApi.spec();
        return new OverrideBackendStub(OPENAPI_STUB_PREFIX + specName(swap.to()), openApiPath(served),
                Map.of("code", descriptionKey(serviceKeys, swap.serviceIndex())), Map.of());
    }

    private AdminCall replaceWsdlSecondService(String scenarioId, FixtureSpec spec, List<String> codes,
                                               ReplaceWsdlSecondService replace) {
        if (!(spec.services().get(replace.serviceIndex()) instanceof WsdlService wsdl)
                || wsdl.shape() != WsdlShape.PAIR) {
            throw new IllegalArgumentException(
                    "service %d is not a PAIR WSDL service".formatted(replace.serviceIndex()));
        }
        var base = serviceCode(scenarioId, replace.serviceIndex());
        var served = wsdlCodes(base, wsdl);
        codes.add(base + NEW_SUFFIX + wsdl.codeTail());
        return new OverrideBackendStub(WSDL_STUB_PREFIX + specName(WsdlShape.PAIR), wsdlPath(WsdlShape.PAIR),
                Map.of("code1", served.get(0), "code2", served.get(1)),
                Map.of(SECOND_CODE_PLACEHOLDER, codes.getLast()));
    }

    private static String descriptionKey(List<String> serviceKeys, int serviceIndex) {
        var key = serviceKeys.get(serviceIndex);
        if (key == null) {
            throw new IllegalArgumentException("service %d has no description".formatted(serviceIndex));
        }
        return key;
    }

    private static String specName(Enum<?> spec) {
        return spec.name().toLowerCase(Locale.ROOT);
    }

    private void groupSetup(List<AdminCall> setup, String scenarioId, List<LocalGroupSpec> groups) {
        for (var group : groups) {
            var code = groupCode(scenarioId, group.code());
            setup.add(new CreateLocalGroup(group.ownerClientId(), code));
            for (var memberId : group.memberIds()) {
                setup.add(new AddLocalGroupMember(group.ownerClientId(), code, memberId));
            }
        }
    }

    private void globalGroupSetup(List<AdminCall> setup, String scenarioId, List<GlobalGroupSpec> groups) {
        for (var group : groups) {
            if (group.isOwnersGroup()) {
                throw new IllegalArgumentException(
                        "group %s exists on every Central Server and is never created".formatted(group.code()));
            }
            var code = groupCode(scenarioId, group.code());
            setup.add(new CreateGlobalGroup(code));
            for (var memberId : group.memberIds()) {
                setup.add(new AddGlobalGroupMember(code, memberId));
            }
        }
    }

    private void globalGroupGrants(List<AdminCall> setup, String scenarioId, FixtureSpec spec, String serviceCode,
                                   List<String> groupCodes) {
        for (var groupCode : groupCodes) {
            setup.add(new GrantGlobalGroupAccess(spec.providerClientId(),
                    globalGroupCode(scenarioId, spec, groupCode), serviceCode));
        }
    }

    private String globalGroupCode(String scenarioId, FixtureSpec spec, String code) {
        if (GlobalGroupSpec.SECURITY_SERVER_OWNERS.equals(code)) {
            return code;
        }
        if (spec.globalGroups().stream().noneMatch(group -> group.code().equals(code))) {
            throw new IllegalArgumentException("global group %s is used but not created".formatted(code));
        }
        return groupCode(scenarioId, code);
    }

    private void groupFollowUps(List<AdminCall> setup, String scenarioId, List<LocalGroupSpec> groups) {
        for (var group : groups) {
            var code = groupCode(scenarioId, group.code());
            for (var memberId : group.removedMemberIds()) {
                setup.add(new RemoveLocalGroupMember(group.ownerClientId(), code, memberId));
            }
            if (group.deleted()) {
                setup.add(new DeleteLocalGroup(group.ownerClientId(), code));
            }
        }
    }

    private void groupGrants(List<AdminCall> setup, String scenarioId, FixtureSpec spec, String serviceCode,
                             List<String> groupCodes) {
        for (var groupCode : groupCodes) {
            setup.add(new GrantGroupAccess(spec.providerClientId(), grantedGroupCode(scenarioId, spec, groupCode),
                    serviceCode));
        }
    }

    private String grantedGroupCode(String scenarioId, FixtureSpec spec, String code) {
        var owned = spec.groups().stream()
                .anyMatch(group -> group.code().equals(code) && group.ownerClientId().equals(spec.providerClientId()));
        if (!owned) {
            throw new IllegalArgumentException(
                    "group %s is granted but not created under %s".formatted(code, spec.providerClientId()));
        }
        return groupCode(scenarioId, code);
    }

    String groupCode(String scenarioId, String code) {
        return "p%s-%s-%s".formatted(runToken, scenarioId.toLowerCase(Locale.ROOT), code);
    }

    private static void generatedGrants(List<AdminCall> setup, String serviceCode,
                                        List<GeneratedEndpointGrant> grants) {
        for (var endpoint : grants) {
            for (var consumer : endpoint.grantedConsumerIds()) {
                setup.add(new GrantEndpointAccess(serviceCode, endpoint.method(), endpoint.path(), consumer));
            }
        }
    }

    private static void grant(List<AdminCall> setup, String providerClientId, String serviceCode,
                              List<String> consumerIds) {
        for (var consumer : consumerIds) {
            setup.add(new GrantAccess(providerClientId, consumer, serviceCode));
        }
    }

    private static List<String> wsdlCodes(String base, WsdlService wsdl) {
        var codes = new ArrayList<String>();
        for (var i = 0; i < wsdl.shape().codeCount(); i++) {
            codes.add(base + (i == 0 ? "" : "-b") + wsdl.codeTail());
        }
        return codes;
    }

    String serviceCode(String scenarioId, int serviceIndex) {
        var base = "p%s-%s".formatted(runToken, scenarioId.toLowerCase(Locale.ROOT));
        return serviceIndex == 0 ? base : base + "-" + (serviceIndex + 1);
    }

    static String backendUrl(String serviceCode) {
        return BACKEND_ORIGIN + BACKEND_PATH_PREFIX + serviceCode;
    }

    static String openApiUrl(OpenApiSpec spec, String serviceCode) {
        return "%s%s?code=%s".formatted(BACKEND_ORIGIN, openApiPath(spec), serviceCode);
    }

    static String openApiPath(OpenApiSpec spec) {
        return "%s%s.yaml".formatted(OPENAPI_PATH_PREFIX, specName(spec));
    }

    static String wsdlPath(WsdlShape shape) {
        return "%swsdl/%s.wsdl".formatted(BACKEND_PATH_PREFIX, specName(shape));
    }

    static String wsdlUrl(WsdlShape shape, List<String> codes) {
        var query = shape == WsdlShape.PAIR
                ? "code1=%s&code2=%s".formatted(codes.get(0), codes.get(1))
                : "code=" + codes.get(0);
        return "%s%s?%s".formatted(BACKEND_ORIGIN, wsdlPath(shape), query);
    }
}
