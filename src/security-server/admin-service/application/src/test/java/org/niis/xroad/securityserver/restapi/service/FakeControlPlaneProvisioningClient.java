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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Stateful in-memory fake of {@link ControlPlaneProvisioningClient}: tracks created contexts and
 * their STS config, so lifecycle tests can assert on outcome state rather than on call sequences.
 * Failure injection targets one participant context id at a time: it fires on the next matching call
 * for that id, before any state mutation, then clears itself — a tick that also processes other,
 * unrelated contexts (host, management) does not consume it.
 */
final class FakeControlPlaneProvisioningClient implements ControlPlaneProvisioningClient {

    private record Context(String did, boolean configured) {
    }

    private final Map<String, Context> contexts = new HashMap<>();

    private final Set<String> failCreateContextFor = new HashSet<>();
    private final Set<String> failPutConfigFor = new HashSet<>();
    private final Set<String> failDeleteContextFor = new HashSet<>();

    @Override
    public void createParticipantContext(String participantContextId, String did) {
        if (failCreateContextFor.remove(participantContextId)) {
            throw new FakeProvisioningException("control plane: createParticipantContext " + participantContextId);
        }
        contexts.put(participantContextId, new Context(did, false));
    }

    @Override
    public void putParticipantContextConfig(String participantContextId, String did, String stsTokenUrl) {
        if (failPutConfigFor.remove(participantContextId)) {
            throw new FakeProvisioningException("control plane: putParticipantContextConfig " + participantContextId);
        }
        contexts.put(participantContextId, new Context(did, true));
    }

    @Override
    public void deleteParticipantContext(String participantContextId) {
        if (failDeleteContextFor.remove(participantContextId)) {
            throw new FakeProvisioningException("control plane: deleteParticipantContext " + participantContextId);
        }
        contexts.remove(participantContextId);
    }

    @Override
    public void invalidateCatalogCaches() {
    }

    boolean hasContext(String participantContextId) {
        return contexts.containsKey(participantContextId);
    }

    boolean hasConfig(String participantContextId) {
        var context = contexts.get(participantContextId);
        return context != null && context.configured();
    }

    void failNextCreateContext(String participantContextId) {
        failCreateContextFor.add(participantContextId);
    }

    void failNextPutConfig(String participantContextId) {
        failPutConfigFor.add(participantContextId);
    }

    void failNextDeleteContext(String participantContextId) {
        failDeleteContextFor.add(participantContextId);
    }
}
