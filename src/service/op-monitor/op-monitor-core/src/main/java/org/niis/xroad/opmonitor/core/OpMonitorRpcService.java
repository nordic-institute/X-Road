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
package org.niis.xroad.opmonitor.core;

import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.common.rpc.mapper.ServiceIdMapper;
import org.niis.xroad.opmonitor.api.GetOperationalDataIntervalsReq;
import org.niis.xroad.opmonitor.api.GetOperationalDataIntervalsResp;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.OperationalDataIntervalProto;
import org.niis.xroad.opmonitor.api.SecurityServerType;

import java.time.Instant;
import java.util.List;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class OpMonitorRpcService extends OpMonitorServiceGrpc.OpMonitorServiceImplBase {
    private final OperationalDataRecordManager operationalDataRecordManager;

    @Override
    public void getOperationalDataIntervals(GetOperationalDataIntervalsReq request,
                                              StreamObserver<GetOperationalDataIntervalsResp> responseObserver) {
        try {
            responseObserver.onNext(handleGetMonitoringDataRequest(request));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("getOperationalDataIntervals failed", e);
            responseObserver.onError(e);
        }
    }

    @ArchUnitSuppressed("NoVanillaExceptions") //TODO XRDDEV-2962 review and refactor if needed
    private GetOperationalDataIntervalsResp handleGetMonitoringDataRequest(GetOperationalDataIntervalsReq request) throws Exception {
        log.debug("Getting operational data in {} minute intervals, between {} and {}",
                request.getIntervalInMinutes(),
                Instant.ofEpochMilli(request.getRecordsFrom()),
                Instant.ofEpochMilli(request.getRecordsTo()));
        List<OperationalDataInTimeInterval> operationalDataInIntervals =
                operationalDataRecordManager.queryRequestMetricsDividedInIntervals(request.getRecordsFrom(),
                        request.getRecordsTo(),
                        request.getIntervalInMinutes(),
                        convertSecurityServerType(request.getSecurityServerType()),
                        request.hasMemberId() ? ClientIdMapper.fromDto(request.getMemberId()) : null,
                        request.hasServiceId() ? ServiceIdMapper.fromDto(request.getServiceId()) : null);

        GetOperationalDataIntervalsResp.Builder getOpDataIntervalsRespBuilder = GetOperationalDataIntervalsResp.newBuilder();
        operationalDataInIntervals.forEach(interval -> {
            OperationalDataIntervalProto.Builder operationalDataIntervalBuilder = OperationalDataIntervalProto.newBuilder();
            Timestamp intervalStartTimestamp = Timestamp.newBuilder()
                    .setSeconds(interval.timeIntervalStart().getEpochSecond())
                    .setNanos(interval.timeIntervalStart().getNano())
                    .build();
            operationalDataIntervalBuilder.setTimeIntervalStart(intervalStartTimestamp);
            operationalDataIntervalBuilder.setSuccessCount(interval.successCount());
            operationalDataIntervalBuilder.setFailureCount(interval.failureCount());
            getOpDataIntervalsRespBuilder.addOperationalDataInterval(operationalDataIntervalBuilder);
        });
        return getOpDataIntervalsRespBuilder.build();
    }

    private OpMonitoringData.SecurityServerType convertSecurityServerType(SecurityServerType securityServerType) {
        return switch (securityServerType) {
            case CLIENT -> OpMonitoringData.SecurityServerType.CLIENT;
            case PRODUCER -> OpMonitoringData.SecurityServerType.PRODUCER;
            case SECURITY_SERVER_TYPE_UNSPECIFIED, UNRECOGNIZED -> null;
        };
    }
}
