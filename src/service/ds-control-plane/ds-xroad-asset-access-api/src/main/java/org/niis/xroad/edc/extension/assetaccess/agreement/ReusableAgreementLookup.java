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
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.niis.xroad.edc.extension.assetaccess.policy.PolicySubjectMatcher;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Looks up a reusable asset access agreement in the shared EDC agreement store, so that an agreement
 * negotiated through any ds-control-plane instance is reused by every instance sharing the same
 * database, in place of a per-instance agreement registry.
 *
 * <p>The provider publishes one offer per ACL subject, so an agreement negotiated on a subsystem's
 * behalf carries a policy naming only that subsystem. For an <b>Asset access acquisition</b> with a
 * client id, this lookup honours the same subsystem scoping: among the agreements matching participant
 * context, consumer, asset and provider, it returns the newest one whose policy names that client, else
 * the newest unrestricted one (no permissions at all, negotiated from the offer the provider publishes
 * for builtin and unrestricted-SYSTEM services), else empty. An unrestricted agreement applies to any
 * caller, so reusing it isolates nothing less than negotiating a fresh one. There is no fallback to an
 * agreement applying to the member as a whole or to a group here; that fallback exists only in offer
 * selection, because the consumer cannot verify a subsystem's grant on such an agreement and a request
 * under it fails the provider's own check. With a null client id (the caller states none),
 * <b>Agreement reuse</b> returns the newest candidate regardless of policy.
 *
 * <p>Two or more instances may each miss this lookup for the same participant context, consumer, asset,
 * provider and client at the same moment and negotiate independently; every resulting agreement lands in
 * the shared store, none of them collide, and none are cleaned up here. The next lookup for that key sees
 * all of them and returns the one with the newest contract signing date, so concurrent duplicates are
 * harmless and self-resolving rather than prevented.
 */
public class ReusableAgreementLookup {

    /**
     * Upper bound on candidates fetched per lookup: participant context, consumer, asset and provider
     * narrow the query to one row per ACL subject the provider publishes for the asset, plus any
     * duplicates left behind by concurrent negotiations; this comfortably covers both.
     */
    private static final int MAX_CANDIDATES = 500;

    private final ContractNegotiationStore negotiationStore;
    private final TransactionContext transactionContext;

    public ReusableAgreementLookup(ContractNegotiationStore negotiationStore, TransactionContext transactionContext) {
        this.negotiationStore = negotiationStore;
        this.transactionContext = transactionContext;
    }

    /**
     * Returns the reusable contract agreement the given participant context negotiated as consumer for
     * the given asset and provider, or empty if none applies. See the class description for the
     * client-id precedence.
     *
     * <p>{@code consumerId} is the participant context's own identity. The shared store also holds the
     * agreements this context granted as provider to other consumers of the same asset, and on a self-call
     * those rows carry the same participant context, asset and provider id, because the provider is the
     * context itself. Only an agreement whose consumer is this context can back a transfer it initiates.
     */
    public Optional<ContractAgreement> find(String participantContextId, String consumerId, String assetId,
                                            String providerId, @Nullable String clientId) {
        var query = QuerySpec.Builder.newInstance()
                .filter(List.of(
                        Criterion.criterion("participantContextId", "=", participantContextId),
                        Criterion.criterion("consumerId", "=", consumerId),
                        Criterion.criterion("assetId", "=", assetId),
                        Criterion.criterion("providerId", "=", providerId)))
                .sortField("contractSigningDate")
                .sortOrder(SortOrder.DESC)
                .limit(MAX_CANDIDATES)
                .build();

        var candidates = transactionContext.execute(() -> {
            try (var agreements = negotiationStore.queryAgreements(query)) {
                return agreements.toList();
            }
        });

        if (clientId == null) {
            return candidates.stream().findFirst();
        }
        return firstMatching(candidates, agreement -> PolicySubjectMatcher.namesClient(agreement.getPolicy(), clientId))
                .or(() -> firstMatching(candidates, agreement -> PolicySubjectMatcher.isUnrestricted(agreement.getPolicy())));
    }

    private static Optional<ContractAgreement> firstMatching(List<ContractAgreement> candidates,
                                                             Predicate<ContractAgreement> matches) {
        return candidates.stream().filter(matches).findFirst();
    }
}
