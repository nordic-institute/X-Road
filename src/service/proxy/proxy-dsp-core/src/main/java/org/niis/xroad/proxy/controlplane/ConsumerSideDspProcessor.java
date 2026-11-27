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
package org.niis.xroad.proxy.controlplane;

import ee.ria.xroad.common.identifier.ServiceId;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.BuiltinServiceCodes;
import org.niis.xroad.common.core.exception.ClientFacingErrorPolicy;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.edc.protocol.assetaccess.SecurityServerAuthority;
import org.niis.xroad.proxy.core.dsp.AcquisitionCandidate;
import org.niis.xroad.proxy.core.dsp.AssetAccessAcquisitionService;
import org.niis.xroad.proxy.core.dsp.DspAcquisition;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;

import java.util.List;
import java.util.stream.Collectors;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATASET_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_OFFERS_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVIDER_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorOrigin.DATASPACE;

/**
 * Consumer-side implementation of {@link DspRequestProcessor}. Invokes
 * {@link AssetAccessAcquisitionService#acquireAssetAccess} once with the candidates carried by the request, leaving
 * out the servers the call already tried; the control plane chooses among them.
 *
 * <p>ID construction:
 * <ul>
 *   <li>{@code assetId = serviceId.asEncodedId()} — symmetric with provider
 *       {@code AssetMapper.encodeAssetId}.</li>
 *   <li>{@code counterPartyId} + {@code counterPartyAddress} — taken from the request's candidates, which
 *       the call mode selection builds from the DIDs and DSP base URLs the provider servers published in the
 *       global configuration.</li>
 *   <li>{@code participantContextId} — always the sender member's derived context
 *       ({@link ParticipantIdentifierScheme#memberCtxId}); SYSTEM is a provider-side anchor only and
 *       is never the consumer's own negotiation identity.</li>
 * </ul>
 *
 * <p>A metaservice ({@link BuiltinServiceCodes#METASERVICES}) addressed to a subsystem or to a member
 * bypasses DSP ({@link #bypassesDsp}): metaservices carry no access rights, so the provider's built-in
 * handlers serve them tokenless over the X-Road 7 path. The set is the union of the SOAP and REST handlers'
 * codes; a code the handler for that transport does not serve falls to the provider's access-checked default
 * handler.
 *
 * <p>A failure of the acquisition is sanitized for the consumer at the execute boundary via
 * {@link ClientFacingErrorPolicy#sanitizeForConsumer(XrdRuntimeException)}.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ConsumerSideDspProcessor implements DspRequestProcessor {

    private final AssetAccessAcquisitionService assetAccessAcquisitionService;
    private final AssetAccessClientProperties clientProperties;

    @Override
    @WithSpan("dsp-execute")
    @Nonnull
    public DspAcquisition execute(DspRequest request) {
        log.trace("execute({})", request);

        try {
            return acquireAssetAccessForService(request, request.serviceId());
        } catch (XrdRuntimeException ex) {
            throw ClientFacingErrorPolicy.sanitizeForConsumer(ex);
        }
    }

    @Override
    public boolean bypassesDsp(DspRequest request) {
        var serviceId = request.serviceId();
        return serviceId != null && BuiltinServiceCodes.METASERVICES.contains(serviceId.getServiceCode());
    }

    @Override
    public void invalidateAssetAccess(DspRequest request) {
        assetAccessAcquisitionService.invalidateAssetAccess(
                ParticipantIdentifierScheme.memberCtxId(request.sender().getMemberId()),
                request.serviceId().asEncodedId(),
                request.sender());
    }

    @Override
    public void invalidateAssetAccessWonBy(String counterPartyAddress) {
        assetAccessAcquisitionService.invalidateAssetAccessWonBy(counterPartyAddress);
    }

    @Override
    public void reportCounterPartyFailure(String counterPartyId, String counterPartyAddress) {
        assetAccessAcquisitionService.reportCounterPartyFailure(counterPartyId, counterPartyAddress);
    }

    private DspAcquisition acquireAssetAccessForService(DspRequest request, ServiceId serviceId) {
        var assetId = serviceId.asEncodedId();
        var candidates = untriedCandidates(request);
        log.debug("processing DSP request for service {}, asset {}. Got {} untried candidates",
                serviceId, assetId, candidates.size());
        if (candidates.isEmpty()) {
            throw noCandidatesFound(serviceId);
        }
        var participantContextId = ParticipantIdentifierScheme.memberCtxId(request.sender().getMemberId());
        try {
            var access = assetAccessAcquisitionService.acquireAssetAccess(
                    participantContextId, assetId, candidates, request.sender(),
                    clientProperties.acquisitionDeadline());
            return new DspAcquisition(access, (int) candidates.stream().map(ConsumerSideDspProcessor::authorityOf).distinct().count());
        } catch (XrdRuntimeException ex) {
            throw remapRemoteNotFound(ex, serviceId);
        } catch (RuntimeException ex) {
            throw XrdRuntimeException.systemException(DSP_ACQUISITION_FAILED)
                    .origin(DATASPACE)
                    .cause(ex)
                    .details("Failed to acquire asset access for service %s".formatted(serviceId))
                    .build();
        }
    }

    private static List<AcquisitionCandidate> untriedCandidates(DspRequest request) {
        var triedAuthorities = request.triedServers().stream().map(SecurityServerAuthority::of).collect(Collectors.toSet());
        return request.candidates().stream()
                .filter(candidate -> !triedAuthorities.contains(authorityOf(candidate)))
                .toList();
    }

    private static String authorityOf(AcquisitionCandidate candidate) {
        return SecurityServerAuthority.of(candidate.counterPartyAddress());
    }

    private static XrdRuntimeException noCandidatesFound(ServiceId serviceId) {
        return XrdRuntimeException.systemException(DSP_PROVIDER_NOT_FOUND)
                .origin(DATASPACE)
                .details("No candidate security servers found for service %s".formatted(serviceId))
                .build();
    }

    private static XrdRuntimeException remapRemoteNotFound(XrdRuntimeException failure, ServiceId serviceId) {
        var remoteNotFoundCode = resolveRemoteNotFoundCode(failure.getCode());
        if (remoteNotFoundCode == null) {
            return failure;
        }
        return XrdRuntimeException.systemException(remoteNotFoundCode)
                .origin(DATASPACE)
                .cause(failure)
                .details("No catalog offer found for service %s".formatted(serviceId))
                .build();
    }

    private static ErrorCode resolveRemoteNotFoundCode(String errorCode) {
        if (errorCode == null) {
            return null;
        }
        if (errorCode.endsWith(DSP_DATASET_NOT_FOUND.code())) {
            return DSP_DATASET_NOT_FOUND;
        }
        if (errorCode.endsWith(DSP_OFFERS_NOT_FOUND.code())) {
            return DSP_OFFERS_NOT_FOUND;
        }
        return null;
    }
}
