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
package org.niis.xroad.proxy.core.addon.opmonitoring;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.niis.xroad.opmonitor.api.OperationalDataRecordProto;
import org.niis.xroad.opmonitor.client.OpMonitorClient;

import java.util.List;
import java.util.function.Supplier;

/**
 * Stores operational data records through op-monitor's gRPC service. The op-monitor client and its channel are
 * created on the first store, so nothing is created while no operational data is sent.
 */
public class OpMonitorStoreClient implements OperationalDataStoreClient {
    /**
     * Part of the status description grpc-java uses when a message exceeds the receiver's maximum inbound message size.
     */
    private static final String INBOUND_MESSAGE_SIZE_EXCEEDED = "gRPC message exceeds maximum size";

    private final Supplier<OpMonitorClient> opMonitorClientFactory;
    private OpMonitorClient opMonitorClient;

    public OpMonitorStoreClient(Supplier<OpMonitorClient> opMonitorClientFactory) {
        this.opMonitorClientFactory = opMonitorClientFactory;
    }

    @Override
    public Result store(List<OperationalDataRecordProto> records) {
        try {
            client().storeOperationalData(records);
            return Result.STORED;
        } catch (StatusRuntimeException e) {
            if (isInboundMessageSizeRejection(e.getStatus())) {
                return Result.TOO_LARGE;
            }
            throw e;
        }
    }

    private static boolean isInboundMessageSizeRejection(Status status) {
        return status.getCode() == Status.Code.RESOURCE_EXHAUSTED
                && status.getDescription() != null
                && status.getDescription().contains(INBOUND_MESSAGE_SIZE_EXCEEDED);
    }

    @Override
    public synchronized void close() {
        if (opMonitorClient != null) {
            opMonitorClient.close();
            opMonitorClient = null;
        }
    }

    private synchronized OpMonitorClient client() {
        if (opMonitorClient == null) {
            opMonitorClient = opMonitorClientFactory.get();
        }
        return opMonitorClient;
    }
}
