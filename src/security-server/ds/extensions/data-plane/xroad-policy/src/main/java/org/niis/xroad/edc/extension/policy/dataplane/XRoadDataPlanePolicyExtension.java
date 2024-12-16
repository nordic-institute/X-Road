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

package org.niis.xroad.edc.extension.policy.dataplane;

import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;

import org.eclipse.edc.connector.controlplane.transform.odrl.OdrlTransformersFactory;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAccessControlService;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintFunction;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.agent.ParticipantIdMapper;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.niis.xroad.edc.extension.policy.dataplane.transform.JsonObjectToContractAgreementTransformer;

import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;
import static org.niis.xroad.edc.extension.policy.dataplane.XRoadDataPlanePolicyExtension.NAME;

@Extension(value = NAME)
@Provides({IdentityService.class, DataPlaneAccessControlService.class})
public class XRoadDataPlanePolicyExtension implements ServiceExtension {

    public static final String NAME = "X-Road Data Plane Policy extension";

    static final String XROAD_DATAPLANE_TRANSFER_SCOPE = "xroad.dataplane.transfer";

    @Setting(value = "DataPlane selector api URL", required = true)
    static final String CONTROL_PLANE_MANAGEMENT_URL_SETTING = "edc.controlplane.management.url";

    @Inject
    private RuleBindingRegistry ruleBindingRegistry;

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private TypeManager typeManager;

    @Inject
    private GlobalConfProvider globalConfProvider;

    @Inject
    private ServerConfProvider serverConfProvider;

    @Inject
    private EdcHttpClient httpClient;

    @Inject
    private TypeTransformerRegistry transformerRegistry;

    @Inject
    private ParticipantIdMapper participantIdMapper;

    @Inject
    private JsonLd jsonLd;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        // register transformers needed to remotely fetch contract agreement & its ODRL policy from control plane
        OdrlTransformersFactory.jsonObjectToOdrlTransformers(participantIdMapper).forEach(transformerRegistry::register);
        transformerRegistry.register(new JsonObjectToContractAgreementTransformer());

        registerFunction(XRoadClientIdConstraintFunction.KEY, XROAD_DATAPLANE_TRANSFER_SCOPE,
                new XRoadClientIdConstraintFunction(globalConfProvider, monitor));
        registerFunction(XRoadLocalGroupMemberConstraintFunction.KEY, XROAD_DATAPLANE_TRANSFER_SCOPE,
                new XRoadLocalGroupMemberConstraintFunction(serverConfProvider, monitor));
        registerFunction(XRoadGlobalGroupMemberConstraintFunction.KEY, XROAD_DATAPLANE_TRANSFER_SCOPE,
                new XRoadGlobalGroupMemberConstraintFunction(globalConfProvider, monitor));
        registerFunction(XRoadDataPathConstraintFunction.KEY, XROAD_DATAPLANE_TRANSFER_SCOPE,
                new XRoadDataPathConstraintFunction(monitor, typeManager));
    }

    private void registerFunction(String key, String scope, AtomicConstraintFunction<Permission> function) {
        ruleBindingRegistry.bind("USE", scope);
        ruleBindingRegistry.bind(ODRL_SCHEMA + "use", scope);
        ruleBindingRegistry.bind(key, scope);

        policyEngine.registerFunction(scope, Permission.class, key, function);
    }

    @Provider
    public DataPlaneAccessControlService xrdDataPlaneAccessControlService(ServiceExtensionContext context) {
        var controlPlaneManagementUrl = context.getConfig().getString(CONTROL_PLANE_MANAGEMENT_URL_SETTING);
        var contractAgreementApiUrl = getContractAgreementApiUrl(controlPlaneManagementUrl);
        return new XrdDataPlaneAccessControlService(httpClient, contractAgreementApiUrl,
                typeManager.getMapper(JSON_LD), transformerRegistry, jsonLd, policyEngine, context.getMonitor());
    }

    private String getContractAgreementApiUrl(String controlPlaneManagementUrl) {
        return controlPlaneManagementUrl + "/v3/contractagreements";
    }
}
