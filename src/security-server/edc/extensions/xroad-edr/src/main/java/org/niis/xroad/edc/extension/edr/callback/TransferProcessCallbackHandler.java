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
import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.edc.connector.controlplane.services.spi.callback.CallbackEventRemoteMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessStarted;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.niis.xroad.edc.extension.edr.service.AssetInProgressRegistry;
import org.niis.xroad.edc.extension.edr.service.AuthorizedAssetRegistry;

@RequiredArgsConstructor
public class TransferProcessCallbackHandler implements LocalCallbackHandler {

    private final AuthorizedAssetRegistry authorizedAssetRegistry;
    private final AssetInProgressRegistry inProgressRegistry;
    private final EndpointDataReferenceStore edrStore;
    private final Monitor monitor;

    @Override
    public <T extends Event> Result<Void> handle(CallbackEventRemoteMessage<T> message) {
        if (message.getEventEnvelope().getPayload() instanceof TransferProcessStarted transferProcessStarted) {
            return saveEdr(transferProcessStarted);
        }
        return Result.success();
    }

    private Result<Void> saveEdr(TransferProcessStarted processCompleted) {
        var start = StopWatch.createStarted();
        try {
            AssetInProgressRegistry.AssetInProgress assetInProgress = inProgressRegistry
                    .getByTransferProcessId(processCompleted.getTransferProcessId())
                    .orElseThrow(() -> new EdcException("AssetInProgress not found for transferProcessId: "
                            + processCompleted.getTransferProcessId()));

            var edrDataAddress = edrStore.resolveByTransferProcess(processCompleted.getTransferProcessId())
                    .orElseThrow(f -> new EdcException("Failed to resolve EDR data address: " + f.getFailureDetail()));

            authorizedAssetRegistry.registerAsset(
                    assetInProgress.getClientId(),
                    assetInProgress.getServiceId(),
                    edrDataAddress);

            assetInProgress.complete();
        } catch (Exception exception) {
            monitor.severe("Failed to save EDR", exception);
            return Result.failure(exception.getMessage());
        } finally {
            monitor.debug("EDR save took %s ms".formatted(start.getTime()));
        }

        return Result.success();
    }

}
