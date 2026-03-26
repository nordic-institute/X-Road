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

package org.niis.xroad.edc.extension.edr.listener;

import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.EdcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NegotiationCompletionListenerTest {

    NegotiationCompletionListener listener;

    @BeforeEach
    void setUp() {
        listener = new NegotiationCompletionListener();
    }

    @Test
    void registerBeforeFinalizedCompletsFutureWithAgreement() throws Exception {
        var agreement = buildAgreement("agreement-1");
        var negotiation = buildFinalizedNegotiation("neg-1", agreement);
        var future = new CompletableFuture<ContractAgreement>();

        listener.register("neg-1", future);
        listener.finalized(negotiation);

        assertThat(future.get(1, TimeUnit.SECONDS)).isSameAs(agreement);
        assertThat(listener.activeWaiters()).isZero();
    }

    @Test
    void registerBeforeTerminatedFailsFutureWithEdcException() {
        var negotiation = buildNegotiation("neg-1");
        var future = new CompletableFuture<ContractAgreement>();

        listener.register("neg-1", future);
        listener.terminated(negotiation);

        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class);
        assertThat(listener.activeWaiters()).isZero();
    }

    @Test
    void finalizedBeforeRegisterCompletesViaDeadLetter() throws Exception {
        var agreement = buildAgreement("agreement-1");
        var negotiation = buildFinalizedNegotiation("neg-1", agreement);
        var future = new CompletableFuture<ContractAgreement>();

        listener.finalized(negotiation);
        listener.register("neg-1", future);

        assertThat(future.isDone()).isTrue();
        assertThat(future.get(1, TimeUnit.SECONDS)).isSameAs(agreement);
        assertThat(listener.activeWaiters()).isZero();
    }

    @Test
    void terminatedBeforeRegisterFailsViaDeadLetter() {
        var negotiation = buildNegotiation("neg-1");
        var future = new CompletableFuture<ContractAgreement>();

        listener.terminated(negotiation);
        listener.register("neg-1", future);

        assertThat(future.isCompletedExceptionally()).isTrue();
        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class);
        assertThat(listener.activeWaiters()).isZero();
    }

    @Test
    void deregisterCleansUpAllMaps() {
        var agreement = buildAgreement("agreement-1");
        var negotiation = buildFinalizedNegotiation("neg-1", agreement);
        var future = new CompletableFuture<ContractAgreement>();

        listener.register("neg-1", future);
        assertThat(listener.activeWaiters()).isEqualTo(1);

        listener.deregister("neg-1");
        assertThat(listener.activeWaiters()).isZero();

        listener.finalized(negotiation);
        assertThat(future.isDone()).isFalse();
    }

    @Test
    void unmatchedEventIsNoOp() {
        var agreement = buildAgreement("agreement-1");
        var negotiation = buildFinalizedNegotiation("unknown-id", agreement);

        listener.finalized(negotiation);

        assertThat(listener.activeWaiters()).isZero();
    }

    private ContractAgreement buildAgreement(String id) {
        return ContractAgreement.Builder.newInstance()
                .id(id)
                .providerId("provider-1")
                .consumerId("consumer-1")
                .contractSigningDate(0L)
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .build();
    }

    private ContractNegotiation buildNegotiation(String id) {
        return ContractNegotiation.Builder.newInstance()
                .id(id)
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://addr")
                .build();
    }

    private ContractNegotiation buildFinalizedNegotiation(String id, ContractAgreement agreement) {
        return ContractNegotiation.Builder.newInstance()
                .id(id)
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://addr")
                .contractAgreement(agreement)
                .build();
    }
}
