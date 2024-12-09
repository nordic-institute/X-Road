/*
 *  Copyright (c) 2022 Amadeus
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus - initial API and implementation
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - make it a PULL request by default
 *
 */

package org.niis.xroad.edc.extension.dataplane.api;

import org.eclipse.edc.connector.dataplane.util.sink.AsyncStreamingDataSink;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_ASSET_ID;
import static org.eclipse.edc.connector.dataplane.spi.schema.DataFlowRequestSchema.BODY;
import static org.eclipse.edc.connector.dataplane.spi.schema.DataFlowRequestSchema.MEDIA_TYPE;
import static org.eclipse.edc.connector.dataplane.spi.schema.DataFlowRequestSchema.METHOD;
import static org.eclipse.edc.connector.dataplane.spi.schema.DataFlowRequestSchema.PATH;
import static org.eclipse.edc.connector.dataplane.spi.schema.DataFlowRequestSchema.QUERY_PARAMS;

public class DataFlowRequestSupplier implements BiFunction<ContainerRequestContextApi, DataAddress, DataFlowStartMessage> {

    /**
     * Put all properties of the incoming request (method, request body, query params...) into a map.
     */
    private static Map<String, String> createProps(ContainerRequestContextApi contextApi) {
        var props = new HashMap<String, String>();
        props.put(METHOD, contextApi.method());
        props.put(QUERY_PARAMS, contextApi.queryParams());
        props.put(PATH, contextApi.path());
        Optional.ofNullable(contextApi.mediaType())
                .ifPresent(mediaType -> {
                    props.put(MEDIA_TYPE, mediaType);
                    props.put(BODY, contextApi.body());
                });
        return props;
    }

    /**
     * Create a {@link DataFlowStartMessage} based on incoming request and claims decoded from the access token.
     *
     * @param contextApi  Api for accessing request properties.
     * @param dataAddress Source data address.
     * @return DataFlowRequest
     */
    @Override
    public DataFlowStartMessage apply(ContainerRequestContextApi contextApi, DataAddress dataAddress) {
        var props = createProps(contextApi);
        return DataFlowStartMessage.Builder.newInstance()
                .processId(UUID.randomUUID().toString())
                .sourceDataAddress(dataAddress)
                .assetId(dataAddress.getStringProperty(TRANSFER_REQUEST_ASSET_ID))
                .flowType(FlowType.PULL) // if a request hits the public DP API, we can assume a PULL transfer
                .destinationDataAddress(DataAddress.Builder.newInstance()
                        .type(AsyncStreamingDataSink.TYPE)
                        .build())
                .id(UUID.randomUUID().toString())
                .properties(props)
                .build();
    }
}
