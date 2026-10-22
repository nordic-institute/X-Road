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

import org.niis.xroad.e2e.parity.acl.AdminCall.AddEndpoint;
import org.niis.xroad.e2e.parity.acl.AdminCall.AddGlobalGroupMember;
import org.niis.xroad.e2e.parity.acl.AdminCall.AddLocalGroupMember;
import org.niis.xroad.e2e.parity.acl.AdminCall.AddOpenApiDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.AddRestDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.AddWsdlDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.CreateGlobalGroup;
import org.niis.xroad.e2e.parity.acl.AdminCall.CreateLocalGroup;
import org.niis.xroad.e2e.parity.acl.AdminCall.DeleteDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.DeleteEndpoint;
import org.niis.xroad.e2e.parity.acl.AdminCall.DeleteGlobalGroup;
import org.niis.xroad.e2e.parity.acl.AdminCall.DeleteLocalGroup;
import org.niis.xroad.e2e.parity.acl.AdminCall.DisableDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.EnableDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.GrantAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.GrantEndpointAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.GrantGlobalGroupAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.GrantGlobalGroupEndpointAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.GrantGroupAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.GrantGroupEndpointAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.GrantManagementAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.OverrideBackendStub;
import org.niis.xroad.e2e.parity.acl.AdminCall.RefreshDescription;
import org.niis.xroad.e2e.parity.acl.AdminCall.RemoveBackendStubs;
import org.niis.xroad.e2e.parity.acl.AdminCall.RemoveGlobalGroupMember;
import org.niis.xroad.e2e.parity.acl.AdminCall.RemoveLocalGroupMember;
import org.niis.xroad.e2e.parity.acl.AdminCall.RenameRestService;
import org.niis.xroad.e2e.parity.acl.AdminCall.RevokeAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.RevokeManagementAccess;
import org.niis.xroad.e2e.parity.acl.AdminCall.UpdateEndpoint;
import org.niis.xroad.e2e.parity.acl.AdminCall.UpdateOpenApiDescription;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Runs a {@link FixturePlan} against the provider's admin API. A setup that fails part way removes what it
 * created before the failure is rethrown. A grant on a generated endpoint looks the endpoint up by method and
 * path. Cleanup treats a missing service description (HTTP 404) as already
 * removed and keeps going when a step fails. Every grant of a management service the fixture changes is changed
 * back first, newest change first, so the provider ends with the grants it started with.
 */
public final class FixtureExecutor {

    private static final int HTTP_OK = 200;
    private static final int HTTP_LAST_SUCCESS = 299;
    private static final int HTTP_NOT_FOUND = 404;

    private final AdminGateway gateway;

    public FixtureExecutor(AdminGateway gateway) {
        this.gateway = gateway;
    }

    /** Runs the plan's setup calls and returns the handle that removes the fixtures on {@link Fixture#close()}. */
    public Fixture open(FixturePlan plan) {
        var fixture = new Fixture(plan);
        try {
            for (var call : plan.setup()) {
                fixture.apply(call);
            }
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

    /** Created fixtures of one scenario. */
    public final class Fixture implements AutoCloseable {

        private final FixturePlan plan;
        private final Map<String, String> descriptionIds = new HashMap<>();
        private final Map<String, String> endpointIds = new HashMap<>();
        private final Map<String, String> groupIds = new HashMap<>();
        private final Set<String> globalGroupCodes = new HashSet<>();
        private final List<String> backendStubIds = new ArrayList<>();
        private final Deque<AdminCall> restores = new ArrayDeque<>();

        private Fixture(FixturePlan plan) {
            this.plan = plan;
        }

        private static String endpointKey(String serviceCode, String method, String path) {
            return "%s %s %s".formatted(serviceCode, method, path);
        }

        private static String groupKey(String ownerClientId, String code) {
            return ownerClientId + " " + code;
        }

        private static String globalGroupId(String providerClientId, String groupCode) {
            return providerClientId.substring(0, providerClientId.indexOf(':')) + ":" + groupCode;
        }

        /** Runs the calls in order, for the changes a scenario makes while its fixtures are in place. */
        public void apply(List<AdminCall> calls) {
            calls.forEach(this::apply);
        }

        private void apply(AdminCall call) {
            switch (call) {
                case AddRestDescription add -> descriptionIds.put(add.serviceCode(),
                        gateway.addRestDescription(add.providerClientId(), add.backendUrl(), add.serviceCode()));
                case AddWsdlDescription add -> descriptionIds.put(add.serviceCode(),
                        gateway.addWsdlDescription(add.providerClientId(), add.wsdlUrl()));
                case AddOpenApiDescription add -> descriptionIds.put(add.serviceCode(),
                        gateway.addOpenApiDescription(add.providerClientId(), add.specUrl(), add.serviceCode()));
                case UpdateOpenApiDescription update -> gateway.updateOpenApiDescription(
                        descriptionIds.get(update.serviceCode()), update.specUrl(), update.serviceCode());
                case EnableDescription enable -> gateway.enableDescription(descriptionIds.get(enable.serviceCode()));
                case DisableDescription disable ->
                        gateway.disableDescription(descriptionIds.get(disable.serviceCode()), disable.notice());
                case RenameRestService rename -> gateway.renameRestService(descriptionIds.get(rename.serviceCode()),
                        rename.backendUrl(), rename.serviceCode(), rename.newServiceCode());
                case GrantAccess grant -> gateway.grantAccess(
                        grant.providerClientId(), grant.consumerClientId(), grant.serviceCode());
                case RevokeAccess revoke -> gateway.revokeAccess(
                        revoke.providerClientId(), revoke.consumerClientId(), revoke.serviceCode());
                case GrantManagementAccess grant -> {
                    if (gateway.grantAccess(grant.providerClientId(), grant.consumerClientId(), grant.serviceCode())) {
                        restores.push(new RevokeManagementAccess(
                                grant.providerClientId(), grant.consumerClientId(), grant.serviceCode()));
                    }
                }
                case RevokeManagementAccess revoke -> {
                    gateway.revokeAccess(revoke.providerClientId(), revoke.consumerClientId(), revoke.serviceCode());
                    restores.push(new GrantManagementAccess(
                            revoke.providerClientId(), revoke.consumerClientId(), revoke.serviceCode()));
                }
                case RefreshDescription refresh ->
                        gateway.refreshDescription(descriptionIds.get(refresh.serviceCode()));
                case OverrideBackendStub override -> backendStubIds.add(gateway.overrideBackendStub(override));
                case RemoveBackendStubs remove ->
                        throw new IllegalArgumentException("not a setup call: " + remove);
                case AddEndpoint add -> endpointIds.put(endpointKey(add.serviceCode(), add.method(), add.path()),
                        gateway.addEndpoint(descriptionIds.get(add.serviceCode()), add.serviceCode(), add.method(),
                                add.path()));
                case GrantEndpointAccess grant -> gateway.grantEndpointAccess(
                        endpointIds.computeIfAbsent(endpointKey(grant.serviceCode(), grant.method(), grant.path()),
                                key -> gateway.findEndpointId(descriptionIds.get(grant.serviceCode()),
                                        grant.serviceCode(), grant.method(), grant.path())),
                        grant.consumerClientId());
                case UpdateEndpoint update -> {
                    var id = endpointIds.remove(endpointKey(update.serviceCode(), update.method(), update.path()));
                    gateway.updateEndpoint(id, update.newMethod(), update.newPath());
                    endpointIds.put(endpointKey(update.serviceCode(), update.newMethod(), update.newPath()), id);
                }
                case DeleteEndpoint delete -> {
                    var id = endpointIds.remove(endpointKey(delete.serviceCode(), delete.method(), delete.path()));
                    var status = gateway.deleteEndpoint(id);
                    if (!isSuccess(status)) {
                        throw new IllegalStateException("delete of endpoint %s %s of %s answered %d"
                                .formatted(delete.method(), delete.path(), delete.serviceCode(), status));
                    }
                }
                case CreateLocalGroup create -> groupIds.put(groupKey(create.ownerClientId(), create.code()),
                        gateway.createLocalGroup(create.ownerClientId(), create.code()));
                case AddLocalGroupMember add -> gateway.addLocalGroupMember(
                        groupIds.get(groupKey(add.ownerClientId(), add.code())), add.memberId());
                case RemoveLocalGroupMember remove -> gateway.removeLocalGroupMember(
                        groupIds.get(groupKey(remove.ownerClientId(), remove.code())), remove.memberId());
                case GrantGroupAccess grant -> gateway.grantAccess(grant.providerClientId(),
                        groupIds.get(groupKey(grant.providerClientId(), grant.groupCode())), grant.serviceCode());
                case GrantGroupEndpointAccess grant -> gateway.grantEndpointAccess(
                        endpointIds.computeIfAbsent(endpointKey(grant.serviceCode(), grant.method(), grant.path()),
                                key -> gateway.findEndpointId(descriptionIds.get(grant.serviceCode()),
                                        grant.serviceCode(), grant.method(), grant.path())),
                        groupIds.get(groupKey(grant.providerClientId(), grant.groupCode())));
                case CreateGlobalGroup create -> {
                    gateway.createGlobalGroup(create.code());
                    globalGroupCodes.add(create.code());
                }
                case AddGlobalGroupMember add -> gateway.addGlobalGroupMember(add.code(), add.memberId());
                case RemoveGlobalGroupMember remove ->
                        gateway.removeGlobalGroupMember(remove.code(), remove.memberId());
                case GrantGlobalGroupAccess grant -> gateway.grantGlobalGroupAccess(grant.providerClientId(),
                        globalGroupId(grant.providerClientId(), grant.groupCode()), grant.serviceCode());
                case GrantGlobalGroupEndpointAccess grant -> gateway.grantGlobalGroupEndpointAccess(
                        endpointIds.computeIfAbsent(endpointKey(grant.serviceCode(), grant.method(), grant.path()),
                                key -> gateway.findEndpointId(descriptionIds.get(grant.serviceCode()),
                                        grant.serviceCode(), grant.method(), grant.path())),
                        globalGroupId(grant.providerClientId(), grant.groupCode()));
                case DeleteGlobalGroup delete -> {
                    var status = gateway.deleteGlobalGroup(delete.code());
                    if (!isSuccess(status)) {
                        throw new IllegalStateException(
                                "delete of global group %s answered %d".formatted(delete.code(), status));
                    }
                    globalGroupCodes.remove(delete.code());
                }
                case DeleteLocalGroup delete -> {
                    var key = groupKey(delete.ownerClientId(), delete.code());
                    var status = gateway.deleteLocalGroup(groupIds.get(key));
                    if (!isSuccess(status)) {
                        throw new IllegalStateException("delete of local group %s of %s answered %d"
                                .formatted(delete.code(), delete.ownerClientId(), status));
                    }
                    groupIds.remove(key);
                }
                case DeleteDescription delete -> {
                    var status = gateway.deleteDescription(descriptionIds.get(delete.serviceCode()));
                    if (!isSuccess(status)) {
                        throw new IllegalStateException("delete of service description %s answered %d"
                                .formatted(delete.serviceCode(), status));
                    }
                    descriptionIds.remove(delete.serviceCode());
                }
            }
        }

        /** Runs the plan's cleanup calls for the endpoints, descriptions and groups that were created. */
        @Override
        public void close() {
            var failures = new ArrayList<String>();
            restoreManagementGrants(failures);
            for (var call : plan.cleanup()) {
                switch (call) {
                    case DeleteEndpoint delete -> {
                        var id = endpointIds.remove(endpointKey(delete.serviceCode(), delete.method(), delete.path()));
                        remove(id, "endpoint %s %s of %s".formatted(delete.method(), delete.path(),
                                delete.serviceCode()), gateway::deleteEndpoint, failures);
                    }
                    case DeleteDescription delete -> remove(descriptionIds.remove(delete.serviceCode()),
                            delete.serviceCode(), gateway::deleteDescription, failures);
                    case RemoveBackendStubs removeStubs -> {
                        backendStubIds.forEach(id -> remove(id, "backend override " + id,
                                gateway::removeBackendStub, failures));
                        backendStubIds.clear();
                    }
                    case DeleteLocalGroup delete -> remove(groupIds.remove(groupKey(delete.ownerClientId(),
                                    delete.code())), "local group %s of %s".formatted(delete.code(),
                            delete.ownerClientId()), gateway::deleteLocalGroup, failures);
                    case DeleteGlobalGroup delete -> remove(globalGroupCodes.remove(delete.code()) ? delete.code() : null,
                            "global group " + delete.code(), gateway::deleteGlobalGroup, failures);
                    default -> throw new IllegalArgumentException("not a cleanup call: " + call);
                }
            }
            if (!failures.isEmpty()) {
                throw new IllegalStateException("fixture cleanup incomplete: " + String.join("; ", failures));
            }
        }

        private void restoreManagementGrants(List<String> failures) {
            while (!restores.isEmpty()) {
                var call = restores.pop();
                try {
                    switch (call) {
                        case GrantManagementAccess grant -> gateway.grantAccess(
                                grant.providerClientId(), grant.consumerClientId(), grant.serviceCode());
                        case RevokeManagementAccess revoke -> gateway.revokeAccess(
                                revoke.providerClientId(), revoke.consumerClientId(), revoke.serviceCode());
                        default -> throw new IllegalArgumentException("not a restore call: " + call);
                    }
                } catch (RuntimeException | AssertionError e) {
                    failures.add("restore of %s failed: %s".formatted(call, e.getMessage()));
                }
            }
        }

        private static boolean isSuccess(int status) {
            return status >= HTTP_OK && status <= HTTP_LAST_SUCCESS;
        }

        private static void remove(String id, String name, ToIntFunction<String> delete, List<String> failures) {
            if (id == null) {
                return;
            }
            try {
                var status = delete.applyAsInt(id);
                if (!isSuccess(status) && status != HTTP_NOT_FOUND) {
                    failures.add("delete of %s answered %d".formatted(name, status));
                }
            } catch (RuntimeException e) {
                failures.add("delete of %s failed: %s".formatted(name, e.getMessage()));
            }
        }
    }
}
