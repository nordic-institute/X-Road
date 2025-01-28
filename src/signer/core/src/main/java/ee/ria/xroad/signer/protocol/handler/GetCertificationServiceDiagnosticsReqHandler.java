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

package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.common.CertificationServiceDiagnostics;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.signer.certmanager.OcspClientWorker;
import ee.ria.xroad.signer.protocol.AbstractRpcHandler;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.rpc.common.Empty;
import org.niis.xroad.signer.proto.CertificationServiceDiagnosticsResp;
import org.niis.xroad.signer.proto.CertificationServiceStatus;
import org.niis.xroad.signer.proto.OcspResponderStatus;

import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class GetCertificationServiceDiagnosticsReqHandler extends AbstractRpcHandler<Empty, CertificationServiceDiagnosticsResp> {

    private final OcspClientWorker ocspClientWorker;
    private final CertificationServiceDiagnostics diagnosticsDefault = new CertificationServiceDiagnostics();

    @Override
    protected CertificationServiceDiagnosticsResp handle(Empty request) throws Exception {

        CertificationServiceDiagnostics diagnostics = null;
        try {
            diagnostics = ocspClientWorker.getDiagnostics();
            if (diagnostics != null) {
                diagnosticsDefault.update(diagnostics.getCertificationServiceStatusMap());
            }
        } catch (Exception e) {
            log.error("Error getting diagnostics status", e);
        }
        if (diagnostics == null) {
            diagnostics = diagnosticsDefault;
        }

        return toProtoDto(diagnostics);
    }

    private CertificationServiceDiagnosticsResp toProtoDto(CertificationServiceDiagnostics diagnostics) {
        Map<String, CertificationServiceStatus> statusMap =
                diagnostics.getCertificationServiceStatusMap().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> mapServiceStatusToProto(entry.getValue())));

        return CertificationServiceDiagnosticsResp.newBuilder()
                .putAllCertificationServiceStatusMap(statusMap)
                .build();
    }

    private CertificationServiceStatus mapServiceStatusToProto(ee.ria.xroad.common.CertificationServiceStatus status) {
        Map<String, OcspResponderStatus> mappedStatuses = status.getOcspResponderStatusMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                            var prevUpdate = TimeUtils.offsetDateTimeToEpochMillis(entry.getValue().getPrevUpdate());
                            var nextUpdate = TimeUtils.offsetDateTimeToEpochMillis(entry.getValue().getNextUpdate());
                            var builder = OcspResponderStatus.newBuilder()
                                    .setStatus(entry.getValue().getStatus())
                                    .setUrl(entry.getValue().getUrl());
                            ofNullable(prevUpdate).ifPresent(builder::setPrevUpdate);
                            ofNullable(nextUpdate).ifPresent(builder::setNextUpdate);
                            return builder.build();
                        })
                );

        return CertificationServiceStatus.newBuilder()
                .setName(status.getName())
                .putAllOcspResponderStatusMap(mappedStatuses)
                .build();
    }

}
