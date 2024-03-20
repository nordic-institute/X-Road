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

package org.niis.xroad.edc.extension.edr.callback;

import lombok.RequiredArgsConstructor;
import org.eclipse.edc.connector.contract.spi.event.contractnegotiation.ContractNegotiationFinalized;
import org.eclipse.edc.connector.spi.callback.CallbackEventRemoteMessage;
import org.eclipse.edc.connector.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.transfer.spi.types.TransferRequest;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.callback.CallbackAddress;
import org.niis.xroad.edc.extension.edr.service.AssetInProgressRegistry;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
public class ContractNegotiationCallbackHandler implements LocalCallbackHandler {

    private final TransferProcessService transferProcessService;
    private final AssetInProgressRegistry inProgressRegistry;
    private final Monitor monitor;

    private final CallbackAddress transferProcessCallbackAddress = CallbackAddress.Builder.newInstance()
            .uri("local://x-road")
            .events(Set.of("transfer.process.started"))
            .build();

    @Override
    public <T extends Event> Result<Void> handle(CallbackEventRemoteMessage<T> message) {
        if (message.getEventEnvelope().getPayload() instanceof ContractNegotiationFinalized contractNegotiationFinalized) {
            return initiateTransferProcess(contractNegotiationFinalized);
        }
        return Result.success();
    }

    private Result<Void> initiateTransferProcess(ContractNegotiationFinalized contractNegotiationFinalized) {
        Optional<AssetInProgressRegistry.AssetInProgress> assetInProgress = inProgressRegistry
                .get(contractNegotiationFinalized.getContractNegotiationId());
        if (assetInProgress.isEmpty()) {
            return Result.failure("No asset in progress for contract negotiation %s"
                    .formatted(contractNegotiationFinalized.getContractNegotiationId()));
        }

        var contractAgreement = contractNegotiationFinalized.getContractAgreement();
        monitor.debug("Initiating transfer process for contract agreement %s".formatted(contractAgreement.getId()));

        TransferRequest initTransferRequest = TransferRequest.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .assetId(contractAgreement.getAssetId())
                .contractId(contractAgreement.getId())
                .counterPartyAddress(contractNegotiationFinalized.getCounterPartyAddress())
                .protocol(contractNegotiationFinalized.getProtocol())
                .dataDestination(DataAddress.Builder.newInstance().type("HttpProxy").build())
                .callbackAddresses(List.of(transferProcessCallbackAddress))
                .build();

        ServiceResult<TransferProcess> result =
                transferProcessService.initiateTransfer(initTransferRequest);

        if (result.failed()) {
            monitor.severe("Failed to initiate transfer process for contract agreement %s"
                    .formatted(contractAgreement.getId()));
            return Result.failure(result.getFailureDetail());
        }

        assetInProgress.get().setTransferProcessId(result.getContent().getId());

        monitor.debug("Transfer process %s initialized".formatted(result.getContent().getId()));
        return Result.success();
    }
}
