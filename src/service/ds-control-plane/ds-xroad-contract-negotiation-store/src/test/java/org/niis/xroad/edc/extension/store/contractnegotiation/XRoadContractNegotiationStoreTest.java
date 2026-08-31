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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XRoadContractNegotiationStoreTest {

    @Mock
    private ContractNegotiationStore delegate;

    private XRoadContractNegotiationStore store;

    @BeforeEach
    void setUp() {
        store = new XRoadContractNegotiationStore(delegate);
    }

    @Test
    void findByIdDelegates() {
        var negotiation = mock(ContractNegotiation.class);
        when(delegate.findById("id")).thenReturn(negotiation);

        assertThat(store.findById("id")).isSameAs(negotiation);
        verify(delegate).findById("id");
    }

    @Test
    void nextNotLeasedDelegates() {
        var criterion = mock(Criterion.class);
        var negotiations = List.of(mock(ContractNegotiation.class));
        when(delegate.nextNotLeased(5, criterion)).thenReturn(negotiations);

        assertThat(store.nextNotLeased(5, criterion)).isSameAs(negotiations);
        verify(delegate).nextNotLeased(5, criterion);
    }

    @Test
    void findByIdAndLeaseDelegates() {
        var result = StoreResult.<ContractNegotiation>success(mock(ContractNegotiation.class));
        when(delegate.findByIdAndLease("id")).thenReturn(result);

        assertThat(store.findByIdAndLease("id")).isSameAs(result);
        verify(delegate).findByIdAndLease("id");
    }

    @Test
    void saveDelegates() {
        var negotiation = mock(ContractNegotiation.class);
        var result = StoreResult.<Void>success(null);
        when(delegate.save(negotiation)).thenReturn(result);

        assertThat(store.save(negotiation)).isSameAs(result);
        verify(delegate).save(negotiation);
    }

    @Test
    void breakLeaseDelegates() {
        var negotiation = mock(ContractNegotiation.class);
        var result = StoreResult.<Void>success(null);
        when(delegate.breakLease(negotiation)).thenReturn(result);

        assertThat(store.breakLease(negotiation)).isSameAs(result);
        verify(delegate).breakLease(negotiation);
    }

    @Test
    void findContractAgreementDelegates() {
        var agreement = mock(ContractAgreement.class);
        when(delegate.findContractAgreement("contractId")).thenReturn(agreement);

        assertThat(store.findContractAgreement("contractId")).isSameAs(agreement);
        verify(delegate).findContractAgreement("contractId");
    }

    @Test
    void deleteByIdDelegates() {
        var result = StoreResult.<Void>success(null);
        when(delegate.deleteById("id")).thenReturn(result);

        assertThat(store.deleteById("id")).isSameAs(result);
        verify(delegate).deleteById("id");
    }

    @Test
    void queryNegotiationsDelegates() {
        var querySpec = mock(QuerySpec.class);
        var negotiations = Stream.of(mock(ContractNegotiation.class));
        when(delegate.queryNegotiations(querySpec)).thenReturn(negotiations);

        assertThat(store.queryNegotiations(querySpec)).isSameAs(negotiations);
        verify(delegate).queryNegotiations(querySpec);
    }

    @Test
    void queryAgreementsDelegates() {
        var querySpec = mock(QuerySpec.class);
        var agreements = Stream.of(mock(ContractAgreement.class));
        when(delegate.queryAgreements(querySpec)).thenReturn(agreements);

        assertThat(store.queryAgreements(querySpec)).isSameAs(agreements);
        verify(delegate).queryAgreements(querySpec);
    }
}
