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
package org.niis.xroad.securityserver.restapi.service;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties.Dataspace;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provisions this security server's data space participant contexts (IdentityHub + Control Plane)
 * and issues their X-Road membership credentials, over X-Road gRPC.
 *
 * <p>Exposes non-blocking, single-step primitives for use by
 * {@link org.niis.xroad.securityserver.restapi.scheduling.DataspaceParticipantProvisioningWorker}:
 * <ul>
 *   <li>{@link #contextExists(String)} — checks whether a participant context exists in IdentityHub.</li>
 *   <li>{@link #ensureParticipantContext(String, String)} — idempotent context creation for one participant (IH + CP).</li>
 *   <li>{@link #submitCredentialRequest(String)} — submits a credential request into the next available
 *       slot only when no active request (PENDING or ISSUED) exists; advances past slots in terminal ERROR.</li>
 *   <li>{@link #readCredentialStatus(String)} — returns the current credential status
 *       ({@code ISSUED}, {@code PENDING}, {@code ERROR}, or {@code null} when none is active) without polling.</li>
 * </ul>
 *
 * <p>Slot semantics: each participant holds up to {@code maxHolderPidSlots} sequentially named holder-request
 * ids. A slot in terminal ERROR is skipped and the next slot is used on the following submit; a slot in
 * PENDING is reused; a slot in ISSUED is the terminal success state. All 20 slots exhausted in ERROR logs a
 * warning and returns without submitting. When all queried slots are in ERROR, {@link #readCredentialStatus}
 * returns {@link #STATUS_ERROR} so the status path can distinguish "failing" from "never requested".</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataspaceProvisioningService {

    public static final String STATUS_ISSUED = "ISSUED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ERROR = "ERROR";
    public static final String STATUS_ABSENT = "ABSENT";
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    public enum ParticipantKind { HOST, MANAGEMENT }

    /**
     * Read-only snapshot of one participant context's provisioning state.
     *
     * @param participantId    the participant context id
     * @param kind             HOST or MANAGEMENT
     * @param contextCreated   whether the participant context exists in IdentityHub
     * @param credentialStatus ISSUED / PENDING / ABSENT / ERROR / UNKNOWN
     */
    public record ParticipantContextStatus(
            String participantId,
            ParticipantKind kind,
            boolean contextCreated,
            String credentialStatus
    ) {
    }

    private static final String HOLDER_PID_BASE = "xroad-membership-credential-request";
    private static final String MANAGEMENT_CONTEXT_SUFFIX = "-mgmt";
    private static final String CREDENTIAL_FORMAT = "VC1_0_JWT";
    private static final String CREDENTIAL_TYPE = "XRoadMembershipCredential";
    private static final int DID_PORT = 7183;
    private static final int STS_PORT = 7184;
    private static final int CREDENTIAL_PORT = 7185;

    private final AdminServiceProperties adminServiceProperties;
    private final DataspaceProvisioningClient provisioningClient;

    /**
     * Returns {@code true} if a participant context with the given id exists in IdentityHub.
     *
     * @param participantId the participant context id
     */
    public boolean contextExists(String participantId) {
        return provisioningClient.contextExists(participantId);
    }

    /**
     * Creates (idempotently) the IdentityHub and Control Plane participant context for a single participant.
     *
     * @param participantId          the participant context id
     * @param ownerMemberIdSlashForm the SS owner member id formatted as {@code instance/memberClass/memberCode}
     */
    public void ensureParticipantContext(String participantId, String ownerMemberIdSlashForm) {
        var ds = adminServiceProperties.getDataspace();
        var identityHubHost = hostOf(ds.getIdentityHubUrl());
        ensureParticipantContext(ds, participantId, identityHubHost, ownerMemberIdSlashForm);
    }

    /**
     * Creates (idempotently) the IdentityHub and Control Plane participant contexts for the host participant
     * and, when {@code managementRegistered} is true, for the management participant context as well.
     *
     * @param managementRegistered   whether the MANAGEMENT subsystem is registered on this security server
     * @param ownerMemberIdSlashForm the SS owner member id formatted as {@code instance/memberClass/memberCode}
     */
    public void ensureParticipantContexts(boolean managementRegistered, String ownerMemberIdSlashForm) {
        var ds = adminServiceProperties.getDataspace();
        var identityHubHost = hostOf(ds.getIdentityHubUrl());
        for (var participantId : participantContextIds(managementRegistered)) {
            ensureParticipantContext(ds, participantId, identityHubHost, ownerMemberIdSlashForm);
        }
    }

    /**
     * Submits a membership credential request into the next available holder-pid slot for the given
     * participant context, unless an active (PENDING or ISSUED) request already exists.
     * Advances past slots that are in terminal ERROR state.
     * Returns immediately — does not poll for the outcome.
     *
     * @param participantId the participant context id
     */
    public void submitCredentialRequest(String participantId) {
        var ds = adminServiceProperties.getDataspace();
        for (int slot = 0; slot < ds.getMaxHolderPidSlots(); slot++) {
            var holderPid = holderPid(participantId, slot);
            var state = provisioningClient.getCredentialRequestState(participantId, holderPid);
            if (state == null) {
                provisioningClient.requestMembershipCredential(participantId, ds.getIssuerDid(), holderPid,
                        ds.getCredentialDefinitionId(), CREDENTIAL_TYPE, CREDENTIAL_FORMAT);
                return;
            }
            if (STATUS_ISSUED.equals(state) || STATUS_PENDING.equals(state)) {
                return;
            }
            // ERROR — advance to next slot
        }
        log.warn("Data space credential for participant {} exhausted {} holder-pid slots (all in ERROR); no request submitted",
                participantId, ds.getMaxHolderPidSlots());
    }

    /**
     * Returns the current credential status for the given participant context without polling.
     *
     * <p>Scans holder-pid slots in order; ERROR slots are skipped. Returns {@code ISSUED} or {@code PENDING}
     * for the first active slot found, {@code ERROR} when all queried slots are in terminal ERROR, or
     * {@code null} when no request has been submitted yet (all slots absent).</p>
     *
     * @param participantId the participant context id
     * @return {@code "ISSUED"}, {@code "PENDING"}, {@code "ERROR"}, or {@code null}
     */
    @Nullable
    public String readCredentialStatus(String participantId) {
        var ds = adminServiceProperties.getDataspace();
        boolean anyError = false;
        for (int slot = 0; slot < ds.getMaxHolderPidSlots(); slot++) {
            var holderPid = holderPid(participantId, slot);
            var state = provisioningClient.getCredentialRequestState(participantId, holderPid);
            if (state == null) {
                continue;
            }
            if (STATUS_ERROR.equals(state)) {
                anyError = true;
                continue;
            }
            return state;
        }
        return anyError ? STATUS_ERROR : null;
    }

    /**
     * Returns the participant context ids for the given management subsystem registration state.
     *
     * @param managementRegistered whether the MANAGEMENT subsystem is registered on this security server
     */
    public List<String> participantContextIds(boolean managementRegistered) {
        var participantId = adminServiceProperties.getDataspace().getParticipantId();
        List<String> ids = new ArrayList<>();
        ids.add(participantId);
        if (managementRegistered) {
            ids.add(participantId + MANAGEMENT_CONTEXT_SUFFIX);
        }
        return ids;
    }

    /**
     * Returns a read-only snapshot of the provisioning status for each participant context.
     * Does not trigger provisioning, poll, or sleep.
     * Tolerates backend unavailability — errors are reported as {@code UNKNOWN} status rather than thrown.
     *
     * @param managementRegistered whether the MANAGEMENT subsystem is registered on this security server
     */
    public List<ParticipantContextStatus> readParticipantContextStatuses(boolean managementRegistered) {
        var hostParticipantId = adminServiceProperties.getDataspace().getParticipantId();
        List<ParticipantContextStatus> result = new ArrayList<>();
        result.add(readContextStatus(hostParticipantId, ParticipantKind.HOST));
        if (managementRegistered) {
            result.add(readContextStatus(hostParticipantId + MANAGEMENT_CONTEXT_SUFFIX, ParticipantKind.MANAGEMENT));
        }
        return result;
    }

    private ParticipantContextStatus readContextStatus(String participantId, ParticipantKind kind) {
        try {
            var contextCreated = provisioningClient.contextExists(participantId);
            var credentialStatus = resolveCredentialStatus(participantId, contextCreated);
            return new ParticipantContextStatus(participantId, kind, contextCreated, credentialStatus);
        } catch (Exception e) {
            log.warn("Data space: could not read provisioning status for participant {}", participantId, e);
            return new ParticipantContextStatus(participantId, kind, false, STATUS_UNKNOWN);
        }
    }

    private String resolveCredentialStatus(String participantId, boolean contextCreated) {
        if (!contextCreated) {
            return STATUS_ABSENT;
        }
        return Optional.ofNullable(readCredentialStatus(participantId)).orElse(STATUS_ABSENT);
    }

    private void ensureParticipantContext(Dataspace ds, String participantId, String identityHubHost, String memberId) {
        var did = didFor(identityHubHost, participantId);
        createIdentityHubContext(participantId, did, identityHubHost, memberId);
        provisioningClient.createControlPlaneParticipantContext(participantId, did);
        provisioningClient.putControlPlaneParticipantContextConfig(participantId, did, stsTokenUrl(identityHubHost));
    }

    private String didFor(String identityHubHost, String participantId) {
        var did = "did:web:" + identityHubHost + "%3A" + DID_PORT;
        return participantId.endsWith(MANAGEMENT_CONTEXT_SUFFIX) ? did + ":mgmt" : did;
    }

    private void createIdentityHubContext(String participantId, String did, String identityHubHost, String memberId) {
        var credentialServiceUrl = "https://%s:%d/api/credentials/v1/participants/%s"
                .formatted(identityHubHost, CREDENTIAL_PORT, participantId);
        var keyId = did + "#key-1";
        var privateKeyAlias = participantId + "-key";
        provisioningClient.createIdentityHubParticipantContext(participantId, did, memberId, credentialServiceUrl, keyId,
                privateKeyAlias);
    }

    private String stsTokenUrl(String identityHubHost) {
        return "https://%s:%d/api/sts/token".formatted(identityHubHost, STS_PORT);
    }

    private String hostOf(String url) {
        return URI.create(url).getHost();
    }

    private String holderPid(String participantId, int slot) {
        var base = participantId + "-" + HOLDER_PID_BASE;
        return slot == 0 ? base : base + "-" + slot;
    }

}
