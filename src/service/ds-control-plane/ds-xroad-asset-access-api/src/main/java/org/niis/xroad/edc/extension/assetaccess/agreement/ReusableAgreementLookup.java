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

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.util.List;
import java.util.Optional;

/**
 * Looks up a reusable asset access agreement in the shared EDC agreement store, so that an agreement
 * negotiated through any ds-control-plane instance is reused by every instance sharing the same
 * database, in place of a per-instance agreement registry.
 *
 * <p>Two or more instances may each miss this lookup for the same participant context, consumer, asset
 * and provider at the same moment and negotiate independently; every resulting agreement lands in the
 * shared store, none of them collide, and none are cleaned up here. The next lookup for that key sees
 * all of them and returns the one with the newest contract signing date, so concurrent duplicates are
 * harmless and self-resolving rather than prevented.
 */
public class ReusableAgreementLookup {

    private final ContractNegotiationStore negotiationStore;
    private final TransactionContext transactionContext;

    public ReusableAgreementLookup(ContractNegotiationStore negotiationStore, TransactionContext transactionContext) {
        this.negotiationStore = negotiationStore;
        this.transactionContext = transactionContext;
    }

    /**
     * Returns the newest contract agreement the given participant context negotiated as consumer for the
     * given asset and provider, or empty if none has been negotiated yet.
     *
     * <p>{@code consumerId} is the participant context's own identity. The shared store also holds the
     * agreements this context granted as provider to other consumers of the same asset, and on a self-call
     * those rows carry the same participant context, asset and provider id, because the provider is the
     * context itself. Only an agreement whose consumer is this context can back a transfer it initiates.
     */
    public Optional<ContractAgreement> find(String participantContextId, String consumerId, String assetId,
                                            String providerId) {
        var query = QuerySpec.Builder.newInstance()
                .filter(List.of(
                        Criterion.criterion("participantContextId", "=", participantContextId),
                        Criterion.criterion("consumerId", "=", consumerId),
                        Criterion.criterion("assetId", "=", assetId),
                        Criterion.criterion("providerId", "=", providerId)))
                .sortField("contractSigningDate")
                .sortOrder(SortOrder.DESC)
                .limit(1)
                .build();

        return transactionContext.execute(() -> {
            try (var agreements = negotiationStore.queryAgreements(query)) {
                return agreements.findFirst();
            }
        });
    }
}
