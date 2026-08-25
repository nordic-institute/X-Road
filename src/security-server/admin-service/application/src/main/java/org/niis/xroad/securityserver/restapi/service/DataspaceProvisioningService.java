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

import ee.ria.xroad.common.identifier.ClientId;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.serverconf.impl.participant.ParticipantPinningCheck;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_SERVERCONF;
import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

/**
 * Provisions this security server's data space participant contexts (IdentityHub + Control Plane)
 * and issues their X-Road membership credentials, over X-Road gRPC.
 *
 * <p>Exposes non-blocking, single-step primitives for use by
 * {@link org.niis.xroad.securityserver.restapi.scheduling.DataspaceParticipantProvisioningWorker}:
 * <ul>
 *   <li>{@link #participantContexts(boolean)} — enumerates the host, management (when registered)
 *       and per-member contexts to provision, member-level identity from
 *       {@link ClientRepository#getAllLocalClients()} plus the SS owner unconditionally.</li>
 *   <li>{@link #ensureParticipantContext(String, ParticipantKind, String)} — idempotent context
 *       creation for one participant (IH + CP). For a {@link ParticipantKind#MEMBER} context this
 *       pins the member's ctx-id/DID into {@code ds_participant} on first call and verifies the
 *       pinned row against a fresh derivation on every later call.</li>
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

    public enum ParticipantKind { HOST, MANAGEMENT, MEMBER }

    /**
     * One participant context to provision or report on.
     *
     * @param participantId     the participant context id
     * @param kind              HOST, MANAGEMENT or MEMBER
     * @param memberIdSlashForm the X-Road member id this context's credential is issued to, formatted as
     *                          {@code instance/memberClass/memberCode}; {@code null} when the SS owner is
     *                          not yet known (HOST/MANAGEMENT only — MEMBER contexts never appear in that case)
     */
    public record ParticipantContext(String participantId, ParticipantKind kind, @Nullable String memberIdSlashForm) {
    }

    /**
     * Read-only snapshot of one participant context's provisioning state.
     *
     * @param participantId    the participant context id
     * @param kind             HOST, MANAGEMENT or MEMBER
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
    private static final int MEMBER_ID_SLASH_FORM_SEGMENT_COUNT = 3;

    private final AdminServiceProperties adminServiceProperties;
    private final IdentityHubProvisioningClient identityHubClient;
    private final ControlPlaneProvisioningClient controlPlaneClient;
    private final ClientRepository clientRepository;
    private final ServerConfRepository serverConfRepository;
    private final DsParticipantRepository dsParticipantRepository;

    /**
     * Creates (idempotently) the IdentityHub and Control Plane participant context for a single participant.
     *
     * <p>For a {@link ParticipantKind#MEMBER} context, the member's ctx-id and DID are pinned into
     * {@code ds_participant} on the first call and re-verified against a fresh derivation on every
     * later call — the pinned row is never overwritten.
     *
     * @param participantId      the participant context id
     * @param kind                HOST, MANAGEMENT or MEMBER
     * @param memberIdSlashForm  the member id this context's credential is issued to, formatted as
     *                           {@code instance/memberClass/memberCode}
     */
    public void ensureParticipantContext(String participantId, ParticipantKind kind, String memberIdSlashForm) {
        var ds = adminServiceProperties.getDataspace();
        var identityHubHost = hostOf(ds.getIdentityHubUrl());

        var did = didFor(identityHubHost, kind, memberIdSlashForm);

        createIdentityHubContext(participantId, did, identityHubHost, memberIdSlashForm);
        controlPlaneClient.createParticipantContext(participantId, did);
        controlPlaneClient.putParticipantContextConfig(participantId, did, stsTokenUrl(identityHubHost));
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
            var state = identityHubClient.getCredentialRequestState(participantId, holderPid);
            if (state == null) {
                identityHubClient.requestMembershipCredential(participantId, ds.getIssuerDid(), holderPid,
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
            var state = identityHubClient.getCredentialRequestState(participantId, holderPid);
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
     * Enumerates the participant contexts to provision or report on: the host context, the management
     * context when {@code managementRegistered}, and one member context per distinct X-Road member
     * (subsystems collapsed) hosted on this Security Server — the SS owner unconditionally, other
     * members as soon as they have a local client. Member ctx-ids follow the v1 scheme
     * ({@link ParticipantIdentifierScheme}); they are derived, not read from {@code ds_participant} —
     * pinning happens as a side effect of {@link #ensureParticipantContext(String, ParticipantKind, String)}.
     *
     * @param managementRegistered whether the MANAGEMENT subsystem is registered on this security server
     */
    @Transactional(readOnly = true)
    public List<ParticipantContext> participantContexts(boolean managementRegistered) {
        return enumerateParticipantContexts(managementRegistered);
    }

    private List<ParticipantContext> enumerateParticipantContexts(boolean managementRegistered) {
        var ds = adminServiceProperties.getDataspace();
        var hostParticipantId = ds.getParticipantId();

        var ownerId = ownerIdOrNull();
        var ownerSlashForm = ownerId == null ? null : slashForm(ownerId);

        List<ParticipantContext> contexts = new ArrayList<>();
        contexts.add(new ParticipantContext(hostParticipantId, ParticipantKind.HOST, ownerSlashForm));
        if (managementRegistered) {
            contexts.add(new ParticipantContext(hostParticipantId + MANAGEMENT_CONTEXT_SUFFIX, ParticipantKind.MANAGEMENT, ownerSlashForm));
        }

        if (ownerId != null) {
            for (var member : hostedMembers(ownerId)) {
                contexts.add(new ParticipantContext(ParticipantIdentifierScheme.memberCtxId(member), ParticipantKind.MEMBER,
                        slashForm(member)));
            }
        }

        return contexts;
    }

    /**
     * Returns a read-only snapshot of the provisioning status for each participant context.
     * Does not trigger provisioning, poll, or sleep.
     * Tolerates backend unavailability — errors are reported as {@code UNKNOWN} status rather than thrown.
     *
     * @param managementRegistered whether the MANAGEMENT subsystem is registered on this security server
     */
    @Transactional(readOnly = true)
    public List<ParticipantContextStatus> readParticipantContextStatuses(boolean managementRegistered) {
        return enumerateParticipantContexts(managementRegistered).stream()
                .map(ctx -> readContextStatus(ctx.participantId(), ctx.kind()))
                .toList();
    }

    @Nullable
    private ClientId ownerIdOrNull() {
        try {
            var owner = serverConfRepository.getServerConf().getOwner();
            return owner == null ? null : owner.getIdentifier();
        } catch (XrdRuntimeException e) {
            if (MALFORMED_SERVERCONF.code().equals(e.getErrorCode())) {
                return null;
            }
            throw e;
        }
    }

    private Set<ClientId> hostedMembers(ClientId owner) {
        Set<ClientId> members = new LinkedHashSet<>();
        members.add(owner);
        for (var client : clientRepository.getAllLocalClients()) {
            members.add(memberLevelId(client.getIdentifier()));
        }
        return members;
    }

    private static ClientId memberLevelId(ClientId id) {
        return id.getSubsystemCode() == null
                ? id
                : ClientId.Conf.create(id.getXRoadInstance(), id.getMemberClass(), id.getMemberCode());
    }

    private ParticipantContextStatus readContextStatus(String participantId, ParticipantKind kind) {
        try {
            var contextCreated = identityHubClient.contextExists(participantId);
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

    private String didFor(String identityHubHost, ParticipantKind kind, String memberIdSlashForm) {
        if (kind == ParticipantKind.MEMBER) {
            var ssHost = identityHubHost + ":" + DID_PORT;
            return pinnedMemberDid(parseSlashForm(memberIdSlashForm), ssHost);
        }
        var did = "did:web:" + identityHubHost + "%3A" + DID_PORT;
        return kind == ParticipantKind.MANAGEMENT ? did + ":mgmt" : did;
    }

    private String pinnedMemberDid(ClientId member, String ssHost) {
        var pinned = dsParticipantRepository.findByMemberIdentifier(member);
        if (pinned.isPresent()) {
            ParticipantPinningCheck.verify(pinned.get(), ssHost);
            return pinned.get().getDid();
        }

        var ctxId = ParticipantIdentifierScheme.memberCtxId(member);
        var did = ParticipantIdentifierScheme.memberDid(member, ssHost);
        dsParticipantRepository.pinMemberParticipant(member, ctxId, did);
        return did;
    }

    private void createIdentityHubContext(String participantId, String did, String identityHubHost, String memberId) {
        var credentialServiceUrl = "https://%s:%d/api/credentials/v1/participants/%s"
                .formatted(identityHubHost, CREDENTIAL_PORT, participantId);
        var keyId = did + "#key-1";
        var privateKeyAlias = participantId + "-key";
        identityHubClient.createParticipantContext(participantId, did, memberId, credentialServiceUrl, keyId,
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

    private static String slashForm(ClientId id) {
        return "%s/%s/%s".formatted(id.getXRoadInstance(), id.getMemberClass(), id.getMemberCode());
    }

    private static ClientId parseSlashForm(String memberIdSlashForm) {
        var parts = memberIdSlashForm.split("/", -1);
        if (parts.length != MEMBER_ID_SLASH_FORM_SEGMENT_COUNT) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                    "member id '%s' must have exactly %d slash-separated segments",
                    memberIdSlashForm, MEMBER_ID_SLASH_FORM_SEGMENT_COUNT);
        }
        return ClientId.Conf.create(parts[0], parts[1], parts[2]);
    }

}
