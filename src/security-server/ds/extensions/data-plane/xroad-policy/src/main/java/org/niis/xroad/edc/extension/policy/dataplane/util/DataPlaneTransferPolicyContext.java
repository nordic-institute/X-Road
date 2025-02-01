package org.niis.xroad.edc.extension.policy.dataplane.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.policy.engine.spi.PolicyContextImpl;
import org.eclipse.edc.policy.engine.spi.PolicyScope;

@Getter
@RequiredArgsConstructor
public class DataPlaneTransferPolicyContext extends PolicyContextImpl {

    @PolicyScope
    public static final String XROAD_DATAPLANE_TRANSFER_SCOPE = "xroad.dataplane.transfer";

    private final String clientId;

    private final Endpoint endpoint;

    @Override
    public String scope() {
        return XROAD_DATAPLANE_TRANSFER_SCOPE;
    }
}
