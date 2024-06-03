package org.niis.xroad.edc.extension.policy.dataplane;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAccessControlService;
import org.eclipse.edc.policy.engine.spi.PolicyContextImpl;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;

import java.util.Map;

@RequiredArgsConstructor
public class XrdDataPlaneAccessControlService implements DataPlaneAccessControlService {
    private final ContractNegotiationStore contractNegotiationStore;
    private final PolicyEngine policyEngine;
    private final Monitor monitor;

    @Override
    public Result<Void> checkAccess(ClaimToken claimToken, DataAddress address, Map<String, Object> requestData, Map<String, Object> additionalData) {
        String contractId = additionalData.get("agreement_id").toString();
        ContractAgreement contractAgreement = this.contractNegotiationStore.findContractAgreement(contractId);
        if (contractAgreement == null) {
            return Result.failure("Contract agreement %s not authorized".formatted(contractId));
        }

        String dataPath = "%s /%s".formatted(requestData.get("method"), requestData.get("resolvedPath"));
        monitor.debug("Checking access for %s".formatted(dataPath));
        var startTime = StopWatch.createStarted();
        var result = this.policyEngine.evaluate("xroad.dataplane.transfer",
                contractAgreement.getPolicy(),
                PolicyContextImpl.Builder.newInstance()
                        .additional(String.class, dataPath) //todo: xroad8, use dedicated dto?
                        .build());
        monitor.debug("Access check for %s took %s ms".formatted(dataPath, startTime.getTime()));
        return result;
    }


}
