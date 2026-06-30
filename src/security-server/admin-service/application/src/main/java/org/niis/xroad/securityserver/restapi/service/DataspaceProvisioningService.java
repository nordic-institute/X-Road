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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties.Dataspace;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;

/**
 * Provisions this security server's data space participant contexts (IdentityHub + Control Plane)
 * and issues their X-Road membership credentials, over X-Road gRPC.
 *
 * <p>For each participant context (the host context, plus the MANAGEMENT context when enabled) the service
 * creates the IdentityHub participant context, the Control Plane participant context and its STS-bound config,
 * then requests the XRoadMembershipCredential. The creates are idempotent (re-runs tolerate conflicts) and the
 * credential is the success gate.</p>
 *
 * <p>The IdentityHub credential request reaches a terminal {@code ERROR} state when its prerequisites
 * (the member SIGN certificate plus a fresh OCSP response, propagated global conf) are not yet ready, and
 * re-submitting the same {@code holderPid} is a no-op. To recover, this service probes a sequence of
 * {@code holderPid} slots and submits a fresh request once the previous one has errored, so that repeated
 * invocations converge to {@code ISSUED} once the prerequisites become observable.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataspaceProvisioningService {

    private static final String HOLDER_PID_BASE = "xroad-membership-credential-request";
    private static final String MANAGEMENT_CONTEXT_SUFFIX = "-mgmt";
    private static final String CREDENTIAL_FORMAT = "VC1_0_JWT";
    private static final String CREDENTIAL_TYPE = "XRoadMembershipCredential";
    private static final int DID_PORT = 7183;
    private static final int STS_PORT = 7184;
    private static final int CREDENTIAL_PORT = 7185;
    private static final String STATE_ISSUED = "ISSUED";
    private static final String STATE_ERROR = "ERROR";
    private static final String STATUS_ISSUED = "ISSUED";
    private static final String STATUS_PENDING = "PENDING";

    private final AdminServiceProperties adminServiceProperties;
    private final ServerConfService serverConfService;
    private final DataspaceProvisioningClient provisioningClient;

    /**
     * Ensures every configured participant context is provisioned and holds a membership credential.
     *
     * @return {@code ISSUED} when all contexts are issued, {@code PENDING} while issuance is still in progress
     */
    public String provision() {
        Dataspace ds = adminServiceProperties.getDataspace();
        if (!ds.isEnabled()) {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                    .details("Data space provisioning is not enabled")
                    .build();
        }

        String memberId = memberIdSlashForm();
        String identityHubHost = hostOf(ds.getIdentityHubUrl());

        boolean allIssued = true;
        for (String participantId : participantContexts(ds)) {
            ensureParticipantContext(ds, participantId, identityHubHost, memberId);
            String status = ensureCredential(ds, participantId);
            log.info("Data space credential status for participant {}: {}", participantId, status);
            if (!STATUS_ISSUED.equals(status)) {
                allIssued = false;
            }
        }
        return allIssued ? STATUS_ISSUED : STATUS_PENDING;
    }

    private List<String> participantContexts(Dataspace ds) {
        List<String> participantIds = new ArrayList<>();
        participantIds.add(ds.getParticipantId());
        if (ds.isManagementContextEnabled()) {
            participantIds.add(ds.getParticipantId() + MANAGEMENT_CONTEXT_SUFFIX);
        }
        return participantIds;
    }

    private void ensureParticipantContext(Dataspace ds, String participantId, String identityHubHost, String memberId) {
        String did = didFor(identityHubHost, participantId);
        createIdentityHubContext(participantId, did, identityHubHost, memberId);
        provisioningClient.createControlPlaneParticipantContext(participantId, did);
        provisioningClient.putControlPlaneParticipantContextConfig(participantId, did, stsTokenUrl(identityHubHost));
    }

    private String didFor(String identityHubHost, String participantId) {
        String did = "did:web:" + identityHubHost + "%3A" + DID_PORT;
        return participantId.endsWith(MANAGEMENT_CONTEXT_SUFFIX) ? did + ":mgmt" : did;
    }

    private void createIdentityHubContext(String participantId, String did, String identityHubHost, String memberId) {
        String credentialServiceUrl = "https://%s:%d/api/credentials/v1/participants/%s"
                .formatted(identityHubHost, CREDENTIAL_PORT, participantId);
        String keyId = did + "#key-1";
        String privateKeyAlias = participantId + "-key";
        provisioningClient.createIdentityHubParticipantContext(participantId, did, memberId, credentialServiceUrl, keyId,
                privateKeyAlias);
    }

    private String stsTokenUrl(String identityHubHost) {
        return "https://%s:%d/api/sts/token".formatted(identityHubHost, STS_PORT);
    }

    private String memberIdSlashForm() {
        ClientId owner = serverConfService.getSecurityServerOwnerId();
        return "%s/%s/%s".formatted(owner.getXRoadInstance(), owner.getMemberClass(), owner.getMemberCode());
    }

    private String hostOf(String url) {
        return URI.create(url).getHost();
    }

    private String ensureCredential(Dataspace ds, String participantId) {
        for (int slot = 0; slot < ds.getMaxHolderPidSlots(); slot++) {
            String holderPid = holderPid(participantId, slot);
            String state = provisioningClient.getCredentialRequestState(participantId, holderPid);
            if (state == null) {
                provisioningClient.requestMembershipCredential(participantId, ds.getIssuerDid(), holderPid,
                        ds.getCredentialDefinitionId(), CREDENTIAL_TYPE, CREDENTIAL_FORMAT);
                state = pollUntilSettled(ds, participantId, holderPid);
            }
            if (STATE_ISSUED.equals(state)) {
                return STATUS_ISSUED;
            }
            if (STATE_ERROR.equals(state)) {
                continue;
            }
            return STATUS_PENDING;
        }
        log.warn("Data space credential for participant {} exhausted {} holder request slots, all in ERROR",
                participantId, ds.getMaxHolderPidSlots());
        return STATUS_PENDING;
    }

    private String holderPid(String participantId, int slot) {
        String base = participantId + "-" + HOLDER_PID_BASE;
        return slot == 0 ? base : base + "-" + slot;
    }

    private String pollUntilSettled(Dataspace ds, String participantId, String holderPid) {
        long deadline = System.currentTimeMillis() + ds.getPollTimeoutMillis();
        String state = provisioningClient.getCredentialRequestState(participantId, holderPid);
        while (!isTerminal(state) && System.currentTimeMillis() < deadline) {
            sleep(ds.getPollIntervalMillis());
            state = provisioningClient.getCredentialRequestState(participantId, holderPid);
        }
        return state;
    }

    private boolean isTerminal(String state) {
        return STATE_ISSUED.equals(state) || STATE_ERROR.equals(state);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                    .cause(e)
                    .details("Interrupted while polling data space credential request")
                    .build();
        }
    }
}
