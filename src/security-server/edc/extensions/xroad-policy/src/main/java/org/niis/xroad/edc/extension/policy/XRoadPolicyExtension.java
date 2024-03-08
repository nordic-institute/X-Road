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

package org.niis.xroad.edc.extension.policy;

import org.eclipse.edc.policy.engine.spi.AtomicConstraintFunction;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.iam.AudienceResolver;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;

import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.niis.xroad.edc.extension.policy.XRoadPolicyExtension.NAME;

@Extension(value = NAME)
@Provides(IdentityService.class)
public class XRoadPolicyExtension implements ServiceExtension {

    public static final String NAME = "X-Road Policy extension";

    private static final String XROAD_DATAPLANE_TRANSFER_SCOPE = "xroad.dataplane.transfer";

    @Inject
    private RuleBindingRegistry ruleBindingRegistry;

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private TypeManager typeManager;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        // edc scopes:
        // - catalog
        // - request.catalog
        // - contract.negotiation
        // - request.contract.negotiation <- participant agent is not set in policy context.

        registerFunction(XRoadClientIdConstraintFunction.KEY, "catalog",
                new XRoadClientIdConstraintFunction(monitor));
        registerFunction(XRoadClientIdConstraintFunction.KEY, "request.catalog",
                new XRoadClientIdConstraintFunction(monitor));
        registerFunction(XRoadClientIdConstraintFunction.KEY, "contract.negotiation",
                new XRoadClientIdConstraintFunction(monitor));

        registerFunction(XRoadGlobalGroupMemberConstraintFunction.KEY, "catalog",
                new XRoadGlobalGroupMemberConstraintFunction(monitor));
        registerFunction(XRoadGlobalGroupMemberConstraintFunction.KEY, "request.catalog",
                new XRoadGlobalGroupMemberConstraintFunction(monitor));
        registerFunction(XRoadGlobalGroupMemberConstraintFunction.KEY, "contract.negotiation",
                new XRoadGlobalGroupMemberConstraintFunction(monitor));

        registerFunction(XRoadDataPathConstraintFunction.KEY, XROAD_DATAPLANE_TRANSFER_SCOPE,
                new XRoadDataPathConstraintFunction(monitor, typeManager));

        // todo: REMOVE this!!!! Only for testing purposes. Must be replaced with real identity service
//        var defaultXRoadClient = "CS:ORG:my-member:my-subsystem";
        var defaultXRoadClient = "a:b:c:d";
        var xroadClientId = context.getSetting("edc.mock.xroad.clientid", defaultXRoadClient);
        context.registerService(IdentityService.class, new XRoadMockIdentityService(typeManager, context.getParticipantId(),
                xroadClientId));
    }

    private void registerFunction(String key, String scope, AtomicConstraintFunction<Permission> function) {
        ruleBindingRegistry.bind("USE", scope);
        ruleBindingRegistry.bind(ODRL_SCHEMA + "use", scope);
        ruleBindingRegistry.bind(key, scope);

        policyEngine.registerFunction(scope, Permission.class, key, function);
    }

    // todo: REMOVE this!!!! Only for testing purposes
    @Provider
    public AudienceResolver audienceResolver() {
        return RemoteMessage::getCounterPartyAddress;
    }

}
