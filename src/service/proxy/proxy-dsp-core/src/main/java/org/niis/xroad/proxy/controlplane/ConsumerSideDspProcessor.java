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
import org.niis.xroad.common.core.ManagementServiceCodes;
import org.niis.xroad.common.core.exception.ClientFacingErrorPolicy;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.proxy.core.dsp.AcquisitionCandidate;
import org.niis.xroad.proxy.core.dsp.AssetAccessAcquisitionService;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver;
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver.ProviderAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATASET_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_OFFERS_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorOrigin.DATASPACE;

/**
 * Consumer-side implementation of {@link DspRequestProcessor}. Resolves candidate provider security
 * servers via {@link ProviderSecurityServerResolver} and invokes
 * {@link AssetAccessAcquisitionService#acquireAssetAccess} once with all of them; the control plane
 * chooses among them.
 *
 * <p>ID construction:
 * <ul>
 *   <li>{@code assetId = serviceId.asEncodedId()} — symmetric with provider
 *       {@code AssetMapper.encodeAssetId}.</li>
 *   <li>{@code counterPartyId} + {@code counterPartyAddress} — derived per candidate from the
 *       candidate's GlobalConf host-address ({@link DspConventions}). Ordinary requests derive the
 *       provider member's target; each {@code member@SS} is a distinct participant (XRDADR-41). A
 *       forced target narrows the candidates to that one server. A request for a service code
 *       SYSTEM actually publishes ({@link ManagementServiceCodes#DSP_NEGOTIATED}) addressed to the
 *       management subsystem, or a builtin-service request, derives the provider's SYSTEM-context
 *       target instead — SYSTEM is a provider-side anchor, one per Security Server regardless of
 *       which members it serves.</li>
 *   <li>{@code participantContextId} — always the sender member's derived context
 *       ({@link ParticipantIdentifierScheme#memberCtxId}); SYSTEM is a provider-side anchor only and
 *       is never the consumer's own negotiation identity.</li>
 * </ul>
 *
 * <p>A failure of the acquisition is sanitized for the consumer at the execute boundary via
 * {@link ClientFacingErrorPolicy#sanitizeForConsumer(XrdRuntimeException)}.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ConsumerSideDspProcessor implements DspRequestProcessor {

    private final AssetAccessAcquisitionService assetAccessAcquisitionService;
    private final ProviderSecurityServerResolver providerSecurityServerResolver;

    @Override
    @WithSpan("dsp-execute")
    @Nonnull
    public AssetAccessResponse execute(DspRequest request) {
        log.trace("execute({})", request);

        try {
            return acquireAssetAccessForService(request, request.serviceId());
        } catch (XrdRuntimeException ex) {
            throw ClientFacingErrorPolicy.sanitizeForConsumer(ex);
        }
    }

    @Override
    public void invalidateAssetAccess(DspRequest request) {
        assetAccessAcquisitionService.invalidateAssetAccess(
                ParticipantIdentifierScheme.memberCtxId(request.sender().getMemberId()),
                request.serviceId().asEncodedId(),
                request.sender());
    }

    private AssetAccessResponse acquireAssetAccessForService(DspRequest request, ServiceId serviceId) {
        var assetId = serviceId.asEncodedId();
        var requestTargetsSystemCtx = isSystemEligibleManagementService(request, serviceId) || isBuiltinService(serviceId);

        var providerAddresses = providerSecurityServerResolver.resolve(serviceId, request.targetSecurityServer());
        log.debug("processing DSP request for service {}, asset {}, management={}. Got {} possible targets",
                serviceId, assetId, request.managementSubsystem(), providerAddresses.size());
        if (providerAddresses.isEmpty()) {
            throw noCandidatesFound(serviceId);
        }
        var participantContextId = ParticipantIdentifierScheme.memberCtxId(request.sender().getMemberId());
        var candidates = deriveCandidates(requestTargetsSystemCtx, serviceId, providerAddresses);
        try {
            return assetAccessAcquisitionService.acquireAssetAccess(
                    participantContextId, assetId, candidates, request.targetSecurityServer(), request.sender());
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

    /**
     * Derives the acquisition candidate of every provider address, skipping an address whose target
     * cannot be derived. When none is left, the first derivation failure is thrown, or the no-candidates
     * error for an empty address list.
     */
    private List<AcquisitionCandidate> deriveCandidates(boolean requestTargetsSystemCtx, ServiceId serviceId,
                                                        List<ProviderAddress> providerAddresses) {
        var candidates = new ArrayList<AcquisitionCandidate>();
        XrdRuntimeException firstFailure = null;
        for (var address : providerAddresses) {
            try {
                candidates.add(candidateFor(requestTargetsSystemCtx, serviceId, address.hostAddress()));
            } catch (XrdRuntimeException ex) {
                log.warn("Skipping SS {} (address {}): acquisition target cannot be derived",
                        address.serverId(), address.hostAddress(), ex);
                if (firstFailure == null) {
                    firstFailure = ex;
                }
            }
        }
        if (candidates.isEmpty()) {
            throw Objects.requireNonNullElseGet(firstFailure, () -> noCandidatesFound(serviceId));
        }
        return candidates;
    }

    /**
     * The acquisition candidate of one serving security server: the provider's SYSTEM-context target
     * for management and builtin-service requests, the provider member's target otherwise. Both are
     * derived, never looked up.
     */
    @Nonnull
    private AcquisitionCandidate candidateFor(boolean requestTargetsSystemCtx, ServiceId serviceId, String hostAddress) {
        var target = targetFor(requestTargetsSystemCtx, serviceId, hostAddress);
        return new AcquisitionCandidate(target.counterPartyId().toString(), target.counterPartyAddress());
    }

    @Nonnull
    private CounterPartyTarget targetFor(boolean requestTargetsSystemCtx, ServiceId serviceId, String hostAddress) {
        if (requestTargetsSystemCtx) {
            return new CounterPartyTarget(
                    DspConventions.systemCounterPartyId(hostAddress),
                    DspConventions.systemCounterPartyAddress(hostAddress));
        }
        var providerMember = serviceId.getClientId().getMemberId();
        return new CounterPartyTarget(
                DspConventions.memberCounterPartyId(providerMember, hostAddress),
                DspConventions.memberCounterPartyAddress(providerMember, hostAddress));
    }

    private static XrdRuntimeException noCandidatesFound(ServiceId serviceId) {
        return XrdRuntimeException.systemException(DSP_DATASET_NOT_FOUND)
                .origin(DATASPACE)
                .details("No candidate security servers found for service %s".formatted(serviceId))
                .build();
    }

    private static boolean isBuiltinService(ServiceId serviceId) {
        return serviceId != null
                && serviceId.getSubsystemCode() == null
                && BuiltinServiceCodes.ALL.contains(serviceId.getServiceCode());
    }

    /**
     * True for a request addressed to the management subsystem's client id whose service code is
     * one the provider's SYSTEM catalog actually publishes; a request merely hosted under that
     * client id (e.g. {@code listMethods} addressed to it) is not management-eligible and resolves
     * to the ordinary member target instead.
     */
    private static boolean isSystemEligibleManagementService(DspRequest request, ServiceId serviceId) {
        return request.managementSubsystem()
                && serviceId.getServiceVersion() == null
                && ManagementServiceCodes.DSP_NEGOTIATED.contains(serviceId.getServiceCode());
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
