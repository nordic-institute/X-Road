package org.niis.xroad.edc.extension.messagelog;

import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.Set;

import static org.niis.xroad.edc.extension.messagelog.XrdIatpScopeExtension.NAME;

@Extension(NAME)
public class XrdIatpScopeExtension implements ServiceExtension {

    static final String NAME = "X-Road IATP scope extension";

    public static final String CATALOG_REQUEST_SCOPE = "request.catalog";
    public static final String NEGOTIATION_REQUEST_SCOPE = "request.contract.negotiation";
    public static final String TRANSFER_PROCESS_REQUEST_SCOPE = "request.transfer.process";

    public static final String SCOPE_FORMAT = "%s:%s:read";
    public static final String CREDENTIAL_TYPE_NAMESPACE = "org.eclipse.edc.vc.type";
    public static final String CREDENTIAL_FORMAT = "XRoadCredential";

    @Inject
    private PolicyEngine policyEngine;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var contextMappingFunction = new DefaultScopeExtractor(
                Set.of(SCOPE_FORMAT.formatted(CREDENTIAL_TYPE_NAMESPACE, CREDENTIAL_FORMAT)));
        policyEngine.registerPostValidator(CATALOG_REQUEST_SCOPE, contextMappingFunction);
        policyEngine.registerPostValidator(NEGOTIATION_REQUEST_SCOPE, contextMappingFunction);
        policyEngine.registerPostValidator(TRANSFER_PROCESS_REQUEST_SCOPE, contextMappingFunction);
    }

}
