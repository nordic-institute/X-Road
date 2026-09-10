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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stateful in-memory fake of {@link IdentityHubProvisioningClient}: tracks created contexts (with
 * their DID and key material) and submitted credential requests, so lifecycle tests can assert on
 * outcome state rather than on call sequences. Failure injection targets one participant context id
 * at a time: it fires on the next matching call for that id, before any state mutation, then clears
 * itself — a tick that also processes other, unrelated contexts (host, management) does not consume it.
 */
final class FakeIdentityHubProvisioningClient implements IdentityHubProvisioningClient {

    private record Context(String did, String privateKeyAlias, int generation) {
    }

    private final Map<String, Context> contexts = new HashMap<>();
    private final Map<String, Map<String, String>> credentialRequests = new HashMap<>();
    private final AtomicInteger generationSeq = new AtomicInteger();

    private final Set<String> failCreateContextFor = new HashSet<>();
    private final Set<String> failDeleteContextFor = new HashSet<>();
    private final Set<String> failRequestCredentialFor = new HashSet<>();

    @Override
    public void createParticipantContext(String participantContextId, String did, String memberId,
                                         String credentialServiceUrl, String keyId, String privateKeyAlias) {
        if (failCreateContextFor.remove(participantContextId)) {
            throw new FakeProvisioningException("identity hub: createParticipantContext " + participantContextId);
        }
        contexts.put(participantContextId, new Context(did, privateKeyAlias, generationSeq.incrementAndGet()));
    }

    @Override
    public void deleteParticipantContext(String participantContextId) {
        if (failDeleteContextFor.remove(participantContextId)) {
            throw new FakeProvisioningException("identity hub: deleteParticipantContext " + participantContextId);
        }
        contexts.remove(participantContextId);
        credentialRequests.remove(participantContextId);
    }

    @Override
    public String requestMembershipCredential(String participantContextId, String issuerDid, String holderPid,
                                              String credentialDefinitionId, String credentialType, String format) {
        if (failRequestCredentialFor.remove(participantContextId)) {
            throw new FakeProvisioningException("identity hub: requestMembershipCredential " + participantContextId);
        }
        credentialRequests.computeIfAbsent(participantContextId, id -> new HashMap<>()).put(holderPid, "REQUESTED");
        return holderPid;
    }

    @Override
    @Nullable
    public String getCredentialRequestState(String participantContextId, String holderPid) {
        var requests = credentialRequests.get(participantContextId);
        return requests == null ? null : requests.get(holderPid);
    }

    @Override
    public Optional<String> contextDid(String participantContextId) {
        return Optional.ofNullable(contexts.get(participantContextId)).map(Context::did);
    }

    boolean hasContext(String participantContextId) {
        return contexts.containsKey(participantContextId);
    }

    boolean hasCredentialRequest(String participantContextId, String holderPid) {
        var requests = credentialRequests.get(participantContextId);
        return requests != null && requests.containsKey(holderPid);
    }

    /**
     * The monotonically increasing generation stamped on the participant's context at its last
     * creation, or empty when no context currently exists. Increments on every (re-)creation, so a
     * teardown followed by a fresh provisioning is visible as a new generation even though the
     * derived DID and key alias are identical.
     */
    Optional<Integer> contextGeneration(String participantContextId) {
        return Optional.ofNullable(contexts.get(participantContextId)).map(Context::generation);
    }

    void failNextCreateContext(String participantContextId) {
        failCreateContextFor.add(participantContextId);
    }

    void failNextDeleteContext(String participantContextId) {
        failDeleteContextFor.add(participantContextId);
    }

    void failNextRequestCredential(String participantContextId) {
        failRequestCredentialFor.add(participantContextId);
    }
}
