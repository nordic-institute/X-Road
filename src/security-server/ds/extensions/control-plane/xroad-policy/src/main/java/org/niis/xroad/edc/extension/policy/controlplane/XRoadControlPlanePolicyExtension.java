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

package org.niis.xroad.edc.extension.policy.controlplane;

import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;

import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.niis.xroad.edc.extension.policy.controlplane.XRoadControlPlanePolicyExtension.NAME;

@Extension(value = NAME)
public class XRoadControlPlanePolicyExtension implements ServiceExtension {

    public static final String NAME = "X-Road Control Plane Policy extension";

    @Inject
    private RuleBindingRegistry ruleBindingRegistry;

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private ContractNegotiationStore contractNegotiationStore;

    @Inject
    private TypeManager typeManager;

    @Inject
    private GlobalConfProvider globalConfProvider;

    @Inject
    private ServerConfProvider serverConfProvider;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        bindPermissionFunction(new XRoadClientIdConstraintFunction<>(monitor), CatalogPolicyContext.class, CatalogPolicyContext.CATALOG_SCOPE, XRoadClientIdConstraintFunction.KEY);
        bindPermissionFunction(new XRoadClientIdConstraintFunction<>(monitor), ContractNegotiationPolicyContext.class, ContractNegotiationPolicyContext.NEGOTIATION_SCOPE, XRoadClientIdConstraintFunction.KEY);
        bindPermissionFunction(new XRoadClientIdConstraintFunction<>(monitor), TransferProcessPolicyContext.class, TransferProcessPolicyContext.TRANSFER_SCOPE, XRoadClientIdConstraintFunction.KEY);

        bindPermissionFunction(new XRoadLocalGroupMemberConstraintFunction<>(serverConfProvider, monitor), CatalogPolicyContext.class, CatalogPolicyContext.CATALOG_SCOPE, XRoadLocalGroupMemberConstraintFunction.KEY);
        bindPermissionFunction(new XRoadLocalGroupMemberConstraintFunction<>(serverConfProvider, monitor), ContractNegotiationPolicyContext.class, ContractNegotiationPolicyContext.NEGOTIATION_SCOPE, XRoadLocalGroupMemberConstraintFunction.KEY);
        bindPermissionFunction(new XRoadLocalGroupMemberConstraintFunction<>(serverConfProvider, monitor), TransferProcessPolicyContext.class, TransferProcessPolicyContext.TRANSFER_SCOPE, XRoadLocalGroupMemberConstraintFunction.KEY);

        bindPermissionFunction(new XRoadGlobalGroupMemberConstraintFunction<>(globalConfProvider, monitor), CatalogPolicyContext.class, CatalogPolicyContext.CATALOG_SCOPE, XRoadGlobalGroupMemberConstraintFunction.KEY);
        bindPermissionFunction(new XRoadGlobalGroupMemberConstraintFunction<>(globalConfProvider, monitor), ContractNegotiationPolicyContext.class, ContractNegotiationPolicyContext.NEGOTIATION_SCOPE, XRoadGlobalGroupMemberConstraintFunction.KEY);
        bindPermissionFunction(new XRoadGlobalGroupMemberConstraintFunction<>(globalConfProvider, monitor), TransferProcessPolicyContext.class, TransferProcessPolicyContext.TRANSFER_SCOPE, XRoadGlobalGroupMemberConstraintFunction.KEY);
    }

    private <C extends PolicyContext> void bindPermissionFunction(AtomicConstraintRuleFunction<Permission, C> function, Class<C> contextClass, String scope, String constraintType) {
        ruleBindingRegistry.bind("use", scope);
        ruleBindingRegistry.bind(ODRL_SCHEMA + "use", scope);
        ruleBindingRegistry.bind(constraintType, scope);

        policyEngine.registerFunction(contextClass, Permission.class, constraintType, function);
    }
}
