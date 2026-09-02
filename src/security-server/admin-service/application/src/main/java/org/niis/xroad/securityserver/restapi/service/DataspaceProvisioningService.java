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
import org.apache.commons.lang3.EnumUtils;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.serverconf.impl.participant.ParticipantPinningCheck;
import org.niis.xroad.serverconf.model.Client;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_DID_DRIFT;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_IDENTIFIER_MISMATCH;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED;
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
 *       and per-member contexts to provision, member-level identity from the registered clients in
 *       {@link ClientRepository#getAllLocalClients()} plus the SS owner unconditionally.</li>
 *   <li>{@link #ensureParticipantContext(String, ParticipantKind, ClientId)} — idempotent context
 *       creation for one participant (IH + CP). For a {@link ParticipantKind#MEMBER} context with a
 *       pinned {@code ds_participant} row, the row is verified against a fresh derivation and its
 *       DID is used; without a row the DID is derived on the fly. This service never writes pins —
 *       pin creation belongs to an explicit, auditable action outside the reconciler.</li>
 *   <li>{@link #ensureMembershipCredential(String)} — leaves an active (PENDING or ISSUED) request
 *       alone or submits a new one into the next available slot, in a single slot scan; advances
 *       past slots in terminal ERROR.</li>
 *   <li>{@link #readCredentialStatus(String)} — returns the current credential status
 *       ({@code ISSUED}, {@code PENDING}, {@code ERROR}, or {@code null} when none is active) without polling.</li>
 * </ul>
 *
 * <p>Slot semantics: each participant holds up to {@code maxHolderPidSlots} sequentially named holder-request
 * ids. A slot in terminal ERROR is skipped and the next slot is used on the following submit; a slot in
 * PENDING is reused; a slot in ISSUED is the terminal success state. All 20 slots exhausted in ERROR logs a
 * warning and returns without submitting. When all queried slots are in ERROR, {@link #readCredentialStatus}
 * returns {@link CredentialStatus#ERROR} so the status path can distinguish "failing" from "never requested".</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataspaceProvisioningService {

    /**
     * Membership credential state of one participant context. {@code UNKNOWN} covers both an
     * unreadable identity hub and a hub state outside this set.
     */
    public enum CredentialStatus { ISSUED, PENDING, ERROR, ABSENT, UNKNOWN }

    /**
     * Pinned-identity state of one MEMBER participant context. {@code DRIFTED} means the identity
     * hub serves a different DID than this server intends to publish.
     */
    public enum IdentityStatus { OK, MISMATCH, VERSION_UNSUPPORTED, UNPINNED, DRIFTED, UNKNOWN }

    public enum ParticipantKind { HOST, MANAGEMENT, MEMBER }

    /**
     * One participant context to provision or report on.
     *
     * @param participantId the participant context id
     * @param kind          HOST, MANAGEMENT or MEMBER
     * @param memberId      the X-Road member this context's credential is issued to; {@code null} only
     *                      when the SS owner is not yet known (HOST/MANAGEMENT — a MEMBER context always
     *                      carries its member)
     */
    public record ParticipantContext(String participantId, ParticipantKind kind, @Nullable ClientId memberId) {
        public ParticipantContext {
            if (kind == ParticipantKind.MEMBER && memberId == null) {
                throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                        "MEMBER participant context %s requires a member id", participantId);
            }
        }
    }

    /**
     * Read-only snapshot of one participant context's provisioning state.
     *
     * @param participantId    the participant context id
     * @param kind             HOST, MANAGEMENT or MEMBER
     * @param contextCreated   whether the participant context exists in IdentityHub
     * @param credentialStatus the membership credential state
     * @param identityStatus   the pinned-identity state for a MEMBER context; {@code null} for HOST
     *                         and MANAGEMENT
     */
    public record ParticipantContextStatus(
            String participantId,
            ParticipantKind kind,
            boolean contextCreated,
            CredentialStatus credentialStatus,
            @Nullable IdentityStatus identityStatus
    ) {
    }

    private static final String HOLDER_PID_BASE = "xroad-membership-credential-request";
    private static final String MANAGEMENT_CONTEXT_SUFFIX = "-mgmt";
    private static final String CREDENTIAL_FORMAT = "VC1_0_JWT";
    private static final String CREDENTIAL_TYPE = "XRoadMembershipCredential";

    private final AdminServiceProperties adminServiceProperties;
    private final IdentityHubProvisioningClient identityHubClient;
    private final ControlPlaneProvisioningClient controlPlaneClient;
    private final ClientRepository clientRepository;
    private final ServerConfRepository serverConfRepository;
    private final DsParticipantRepository dsParticipantRepository;

    /**
     * Creates (idempotently) the IdentityHub and Control Plane participant context for a single participant.
     *
     * <p>For a {@link ParticipantKind#MEMBER} context with a pinned {@code ds_participant} row, the
     * row is verified against a fresh derivation and its DID is used — the pinned row is never
     * written or overwritten here. Without a row the DID is derived on the fly (unpinned).
     *
     * @param participantId the participant context id
     * @param kind          HOST, MANAGEMENT or MEMBER
     * @param memberId      the X-Road member this context's credential is issued to
     */
    public void ensureParticipantContext(String participantId, ParticipantKind kind, ClientId memberId) {
        var ds = adminServiceProperties.getDataspace();
        var identityHubHost = hostOf(ds.getIdentityHubUrl());

        var did = didFor(identityHubHost, kind, memberId);
        requireNoHubDidDrift(participantId, did);

        createIdentityHubContext(participantId, did, identityHubHost, memberId);
        controlPlaneClient.createParticipantContext(participantId, did);
        controlPlaneClient.putParticipantContextConfig(participantId, did, stsTokenUrl(identityHubHost));
    }

    private void requireNoHubDidDrift(String participantId, String intendedDid) {
        identityHubClient.contextDid(participantId)
                .filter(hubDid -> !hubDid.equals(intendedDid))
                .ifPresent(hubDid -> {
                    throw XrdRuntimeException.systemException(DSP_PARTICIPANT_DID_DRIFT)
                            .metadataItems(hubDid, intendedDid)
                            .details(("identity hub serves DID '%s' for participant context '%s', but the DID to "
                                    + "provision is '%s'; refusing to touch the context until the drift is resolved")
                                    .formatted(hubDid, participantId, intendedDid))
                            .build();
                });
    }

    /**
     * Ensures an active membership credential request exists for the given participant context,
     * in a single holder-pid slot scan: an existing ISSUED or PENDING request is left alone, a new
     * request is submitted into the first free slot otherwise. Advances past slots in terminal
     * ERROR state. Returns immediately — does not poll for the outcome.
     *
     * @param participantId the participant context id
     * @return {@code ISSUED} for a terminally issued credential, {@code PENDING} when a request is
     *         active or was just submitted, {@code ERROR} when all slots are exhausted
     */
    public CredentialStatus ensureMembershipCredential(String participantId) {
        var ds = adminServiceProperties.getDataspace();
        for (int slot = 0; slot < ds.getMaxHolderPidSlots(); slot++) {
            var holderPid = holderPid(participantId, slot);
            var state = identityHubClient.getCredentialRequestState(participantId, holderPid);
            if (state == null) {
                log.info("Data space provisioning: submitting credential request for participant {}", participantId);
                identityHubClient.requestMembershipCredential(participantId, ds.getIssuerDid(), holderPid,
                        ds.getCredentialDefinitionId(), CREDENTIAL_TYPE, CREDENTIAL_FORMAT);
                return CredentialStatus.PENDING;
            }
            var status = hubCredentialState(state);
            if (status == CredentialStatus.ISSUED || status == CredentialStatus.PENDING) {
                return status;
            }
            // ERROR — advance to next slot
        }
        log.warn("Data space credential for participant {} exhausted {} holder-pid slots (all in ERROR); no request submitted",
                participantId, ds.getMaxHolderPidSlots());
        return CredentialStatus.ERROR;
    }

    /**
     * Returns the current credential status for the given participant context without polling.
     *
     * <p>Scans holder-pid slots in order; ERROR slots are skipped. Returns {@code ISSUED} or {@code PENDING}
     * for the first active slot found, {@code ERROR} when all queried slots are in terminal ERROR, or
     * {@code null} when no request has been submitted yet (all slots absent).</p>
     *
     * @param participantId the participant context id
     * @return {@code ISSUED}, {@code PENDING}, {@code ERROR}, {@code UNKNOWN}, or {@code null}
     */
    @Nullable
    public CredentialStatus readCredentialStatus(String participantId) {
        var ds = adminServiceProperties.getDataspace();
        boolean anyError = false;
        for (int slot = 0; slot < ds.getMaxHolderPidSlots(); slot++) {
            var holderPid = holderPid(participantId, slot);
            var state = identityHubClient.getCredentialRequestState(participantId, holderPid);
            if (state == null) {
                continue;
            }
            var status = hubCredentialState(state);
            if (status == CredentialStatus.ERROR) {
                anyError = true;
                continue;
            }
            return status;
        }
        return anyError ? CredentialStatus.ERROR : null;
    }

    private static CredentialStatus hubCredentialState(String state) {
        return EnumUtils.getEnum(CredentialStatus.class, state, CredentialStatus.UNKNOWN);
    }

    /**
     * Enumerates the participant contexts to provision or report on: the host context, the management
     * context when {@code managementRegistered}, and one member context per distinct X-Road member
     * (subsystems collapsed) hosted on this Security Server — the SS owner unconditionally, other
     * members as soon as they have a registered local client. Member ctx-ids follow the v1 scheme
     * ({@link ParticipantIdentifierScheme}); they are derived, not read from {@code ds_participant}.
     *
     * @param managementRegistered whether the MANAGEMENT subsystem is registered on this security server
     */
    @Transactional(readOnly = true)
    public List<ParticipantContext> participantContexts(boolean managementRegistered) {
        var ds = adminServiceProperties.getDataspace();
        var hostParticipantId = ds.getParticipantId();

        var ownerId = ownerId();
        var owner = ownerId.orElse(null);

        List<ParticipantContext> contexts = new ArrayList<>();
        contexts.add(new ParticipantContext(hostParticipantId, ParticipantKind.HOST, owner));
        if (managementRegistered) {
            contexts.add(new ParticipantContext(hostParticipantId + MANAGEMENT_CONTEXT_SUFFIX, ParticipantKind.MANAGEMENT, owner));
        }

        ownerId.ifPresent(id -> hostedMembers(id).forEach(member ->
                contexts.add(new ParticipantContext(ParticipantIdentifierScheme.memberCtxId(member), ParticipantKind.MEMBER, member))));

        return contexts;
    }

    private Optional<ClientId> ownerId() {
        try {
            return Optional.ofNullable(serverConfRepository.getServerConf().getOwner())
                    .map(owner -> (ClientId) owner.getIdentifier());
        } catch (XrdRuntimeException e) {
            if (MALFORMED_SERVERCONF.code().equals(e.getErrorCode())) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private Set<ClientId> hostedMembers(ClientId owner) {
        Set<ClientId> members = new LinkedHashSet<>();
        members.add(owner);
        for (var client : clientRepository.getAllLocalClients()) {
            if (Client.STATUS_REGISTERED.equals(client.getClientStatus())) {
                members.add(client.getIdentifier().getMemberId());
            }
        }
        return members;
    }

    /**
     * Returns a read-only snapshot of one participant context's provisioning status. The gRPC reads
     * hold no database connection; for a MEMBER context the identity-pinning state is read afterwards
     * in the repository's own short transaction. Does not trigger provisioning, poll, or sleep.
     * Tolerates backend unavailability — errors are reported as {@code UNKNOWN} status rather than thrown.
     *
     * @param context the participant context to report on
     */
    public ParticipantContextStatus readContextStatus(ParticipantContext context) {
        var participantId = context.participantId();
        var assessment = context.kind() == ParticipantKind.MEMBER ? assessMemberIdentity(context.memberId()) : null;
        try {
            var hubDid = identityHubClient.contextDid(participantId);
            var contextCreated = hubDid.isPresent();
            var credentialStatus = resolveCredentialStatus(participantId, contextCreated);
            return new ParticipantContextStatus(participantId, context.kind(), contextCreated, credentialStatus,
                    identityStatusOf(assessment, hubDid));
        } catch (Exception e) {
            log.warn("Data space: could not read provisioning status for participant {}", participantId, e);
            return new ParticipantContextStatus(participantId, context.kind(), false, CredentialStatus.UNKNOWN,
                    identityStatusOf(assessment, Optional.empty()));
        }
    }

    @Nullable
    private static IdentityStatus identityStatusOf(@Nullable MemberIdentity assessment, Optional<String> hubDid) {
        if (assessment == null) {
            return null;
        }
        if (assessment.intendedDid() != null && hubDid.isPresent() && !hubDid.get().equals(assessment.intendedDid())) {
            return IdentityStatus.DRIFTED;
        }
        return assessment.status();
    }

    private CredentialStatus resolveCredentialStatus(String participantId, boolean contextCreated) {
        if (!contextCreated) {
            return CredentialStatus.ABSENT;
        }
        return Optional.ofNullable(readCredentialStatus(participantId)).orElse(CredentialStatus.ABSENT);
    }

    private String didFor(String identityHubHost, ParticipantKind kind, ClientId memberId) {
        if (kind == ParticipantKind.MEMBER) {
            return memberDid(memberId, didAuthority(identityHubHost));
        }
        var did = "did:web:" + didAuthority(identityHubHost).replace(":", "%3A");
        return kind == ParticipantKind.MANAGEMENT ? did + ":mgmt" : did;
    }

    /**
     * The authority (host:port) embedded in derived DIDs. Interim source: the identity-hub host
     * plus its DID-serving port, because that is where DID documents are actually served. Target
     * source, once registered-address DID serving exists: the GlobalConf-registered security
     * server address ({@code GlobalConfProvider#getSecurityServerAddress}), with no port.
     *
     * <p>The port must match the identity hub's own {@code web.http.did.port}. It is part of every
     * DID pinned in {@code ds_participant}, so changing it after a member has been pinned makes
     * that pin fail verification.</p>
     */
    private String didAuthority(String identityHubHost) {
        return identityHubHost + ":" + adminServiceProperties.getDataspace().getIdentityHubDidPort();
    }

    private String memberDid(ClientId member, String ssHost) {
        var pinned = dsParticipantRepository.findByMemberIdentifier(member);
        if (pinned.isPresent()) {
            ParticipantPinningCheck.verify(pinned.get(), ssHost);
            return pinned.get().getDid();
        }
        return ParticipantIdentifierScheme.memberDid(member, ssHost);
    }

    /**
     * Reports the identity-pinning state of one member's participant context without provisioning
     * anything. Tolerates backend unavailability — errors are reported as
     * {@link IdentityStatus#UNKNOWN} rather than thrown.
     *
     * @param memberId the member whose pinned identity to check
     * @return {@code OK}, {@code MISMATCH}, {@code VERSION_UNSUPPORTED}, {@code UNPINNED} or {@code UNKNOWN}
     */
    public IdentityStatus readIdentityStatus(ClientId memberId) {
        return assessMemberIdentity(memberId).status();
    }

    /**
     * Pin assessment for one member: the status string plus the DID this server intends to publish
     * (the pinned DID, or the fresh derivation when unpinned; {@code null} when the pin itself is in
     * an error state and no intended DID can be stated).
     */
    private record MemberIdentity(IdentityStatus status, @Nullable String intendedDid) {
    }

    private MemberIdentity assessMemberIdentity(ClientId memberId) {
        try {
            var ssHost = didAuthority(hostOf(adminServiceProperties.getDataspace().getIdentityHubUrl()));
            var pinned = dsParticipantRepository.findByMemberIdentifier(memberId);
            if (pinned.isEmpty()) {
                return new MemberIdentity(IdentityStatus.UNPINNED, ParticipantIdentifierScheme.memberDid(memberId, ssHost));
            }
            ParticipantPinningCheck.verify(pinned.get(), ssHost);
            return new MemberIdentity(IdentityStatus.OK, pinned.get().getDid());
        } catch (XrdRuntimeException e) {
            if (DSP_PARTICIPANT_IDENTIFIER_MISMATCH.code().equals(e.getErrorCode())) {
                return new MemberIdentity(IdentityStatus.MISMATCH, null);
            }
            if (DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED.code().equals(e.getErrorCode())) {
                return new MemberIdentity(IdentityStatus.VERSION_UNSUPPORTED, null);
            }
            log.warn("Data space: could not read identity status for member {}", memberId, e);
            return new MemberIdentity(IdentityStatus.UNKNOWN, null);
        } catch (Exception e) {
            log.warn("Data space: could not read identity status for member {}", memberId, e);
            return new MemberIdentity(IdentityStatus.UNKNOWN, null);
        }
    }

    private void createIdentityHubContext(String participantId, String did, String identityHubHost, ClientId memberId) {
        var credentialServiceUrl = "https://%s:%d/api/credentials/v1/participants/%s".formatted(identityHubHost,
                adminServiceProperties.getDataspace().getIdentityHubCredentialsPort(),
                UriUtils.encodePathSegment(participantId, StandardCharsets.UTF_8));
        var keyId = did + "#key-1";
        var privateKeyAlias = participantId + "-key";
        identityHubClient.createParticipantContext(participantId, did, memberId == null ? null : slashForm(memberId),
                credentialServiceUrl, keyId, privateKeyAlias);
    }

    private String stsTokenUrl(String identityHubHost) {
        return "https://%s:%d/api/sts/token"
                .formatted(identityHubHost, adminServiceProperties.getDataspace().getIdentityHubStsPort());
    }

    private String hostOf(String url) {
        var host = URI.create(url).getHost();
        if (host == null || host.isBlank()) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                    "dataspace identity-hub URL '%s' has no resolvable host", url);
        }
        return host;
    }

    private String holderPid(String participantId, int slot) {
        var base = participantId + "-" + HOLDER_PID_BASE;
        return slot == 0 ? base : base + "-" + slot;
    }

    private static String slashForm(ClientId id) {
        return "%s/%s/%s".formatted(id.getXRoadInstance(), id.getMemberClass(), id.getMemberCode());
    }

}
