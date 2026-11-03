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

package org.niis.xroad.edc.extension.assetaccess.agreement;

import jakarta.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Remembers which agreement worked for which calling subsystem, so that an agreement the shared-store
 * {@link ReusableAgreementLookup} cannot attribute to a subsystem (a group agreement) is reused instead of
 * negotiated again on every acquisition.
 *
 * <p>The note is in memory, private to this instance and lost on restart; another instance or a restart costs
 * one extra agreement per subsystem. It holds at most {@code capacity} entries and drops the least recently
 * used one beyond that. An entry has no age limit: the provider validates the agreement on every transfer.
 *
 * <p>The key carries the participant context's own identity, so a participant that has taken another
 * identity finds no entry. A subsystem-less caller and a service-only agreement are keyed like any other,
 * with a {@code null} calling subsystem in the first case.
 */
public class AgreementReuseNote {

    /**
     * Identifies one remembered agreement.
     *
     * @param participantContextId the consumer participant context
     * @param participantDid       the consumer participant context's own identity
     * @param providerDid          the provider participant identity
     * @param assetId              the asset
     * @param clientId             the calling subsystem; {@code null} when the request states none
     */
    public record Key(String participantContextId, String participantDid, String providerDid, String assetId,
                      @Nullable String clientId) {
    }

    private static final int INITIAL_SIZE = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private final Map<Key, String> agreementIds;

    public AgreementReuseNote(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.agreementIds = new LinkedHashMap<>(INITIAL_SIZE, LOAD_FACTOR, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, String> eldest) {
                return size() > capacity;
            }
        };
    }

    /** Returns the agreement id remembered for the key. */
    public synchronized Optional<String> find(Key key) {
        return Optional.ofNullable(agreementIds.get(key));
    }

    /** Remembers the agreement as the one that worked for the key, replacing any earlier entry. */
    public synchronized void remember(Key key, String agreementId) {
        agreementIds.put(key, agreementId);
    }

    /** Removes the key's entry only if it still holds {@code agreementId}; a newer entry stays. */
    public synchronized void evict(Key key, String agreementId) {
        agreementIds.remove(key, agreementId);
    }
}
