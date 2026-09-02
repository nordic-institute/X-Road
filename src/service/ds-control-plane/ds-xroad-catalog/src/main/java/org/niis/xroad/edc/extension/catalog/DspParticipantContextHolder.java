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
package org.niis.xroad.edc.extension.catalog;

import jakarta.annotation.Nullable;

/**
 * Propagates the participant context targeted by the in-flight DSP HTTP request from
 * {@link DspParticipantContextRequestFilter} to the ServerConf-backed catalog stores.
 *
 * <p>EDC's by-id store SPIs ({@code AssetIndex#findById}, {@code PolicyDefinitionStore#findById},
 * {@code ContractDefinitionStore#findById}) take no participant-context parameter, yet EDC's own
 * negotiation and transfer services validate the returned row's {@code participantContextId}
 * against the context the DSP request was addressed to. A computed, multi-context store — ours —
 * cannot satisfy that check by always tagging the row with the same default context, because the
 * same id is legitimately published under several contexts (host/mgmt plus, when provisioned, the
 * owning member's). This holder is the side channel that lets a by-id lookup pick the row for the
 * context actually being validated instead of guessing.
 *
 * <p>Backed by a {@link ThreadLocal}: EDC's DSP request pipeline runs synchronously end-to-end on
 * one thread, from the JAX-RS resource method down through the store lookup, so a value set by the
 * filter at request entry is visible to the store call made later on the same thread. The filter
 * clears it unconditionally when the request completes, so a request that carries no participant
 * context (or isn't a DSP request at all) leaves the holder empty and stores fall back to their
 * pre-existing default resolution.
 */
class DspParticipantContextHolder {

    private final ThreadLocal<String> current = new ThreadLocal<>();

    void set(String participantContextId) {
        current.set(participantContextId);
    }

    void clear() {
        current.remove();
    }

    @Nullable
    String get() {
        return current.get();
    }
}
