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

package org.niis.xroad.edc.extension.store.contractnegotiation;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;

import java.util.List;
import java.util.stream.Stream;

/**
 * Sole {@link ContractNegotiationStore} provider for the control-plane runtime, taking over from the excluded
 * stock SQL store extension. Delegates every method to the wrapped store, and is the seam where the save path
 * diverges from stock to converge same-context self-negotiation onto one agreement row.
 */
public class XRoadContractNegotiationStore implements ContractNegotiationStore {

    private final ContractNegotiationStore delegate;

    public XRoadContractNegotiationStore(ContractNegotiationStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public ContractNegotiation findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public List<ContractNegotiation> nextNotLeased(int max, Criterion... criteria) {
        return delegate.nextNotLeased(max, criteria);
    }

    @Override
    public StoreResult<ContractNegotiation> findByIdAndLease(String id) {
        return delegate.findByIdAndLease(id);
    }

    @Override
    public StoreResult<Void> save(ContractNegotiation negotiation) {
        return delegate.save(negotiation);
    }

    @Override
    public StoreResult<Void> breakLease(ContractNegotiation negotiation) {
        return delegate.breakLease(negotiation);
    }

    @Override
    public ContractAgreement findContractAgreement(String contractId) {
        return delegate.findContractAgreement(contractId);
    }

    @Override
    public StoreResult<Void> deleteById(String negotiationId) {
        return delegate.deleteById(negotiationId);
    }

    @Override
    public Stream<ContractNegotiation> queryNegotiations(QuerySpec querySpec) {
        return delegate.queryNegotiations(querySpec);
    }

    @Override
    public Stream<ContractAgreement> queryAgreements(QuerySpec querySpec) {
        return delegate.queryAgreements(querySpec);
    }
}
