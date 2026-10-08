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

package org.niis.xroad.edc.extension.assetaccess.listener;

import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessListener;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * Notifies the local data plane when a CONSUMER-type transfer completes. Stock EDC only calls
 * {@link DataFlowController#completed(TransferProcess)} on the side that received a completion
 * request from its counterparty ({@code TransferProcessorsImpl#processCompleting}); the consumer
 * side, which actively drives completion of a superseded transfer, never signals its own data plane.
 */
public class ConsumerDataFlowCompletionListener implements TransferProcessListener {

    private final DataFlowController dataFlowController;
    private final Monitor monitor;

    public ConsumerDataFlowCompletionListener(DataFlowController dataFlowController, Monitor monitor) {
        this.dataFlowController = dataFlowController;
        this.monitor = monitor;
    }

    @Override
    public void completed(TransferProcess process) {
        if (process.getType() != TransferProcess.Type.CONSUMER) {
            return;
        }
        if (process.getDataPlaneId() == null) {
            return;
        }
        try {
            var result = dataFlowController.completed(process);
            if (result.failed()) {
                monitor.warning("consumer data-plane completion signal failed: transferProcessId=%s detail=%s"
                        .formatted(process.getId(), result.getFailureDetail()));
            }
        } catch (Exception e) {
            monitor.warning("consumer data-plane completion signal failed: transferProcessId=%s"
                    .formatted(process.getId()), e);
        }
    }
}
