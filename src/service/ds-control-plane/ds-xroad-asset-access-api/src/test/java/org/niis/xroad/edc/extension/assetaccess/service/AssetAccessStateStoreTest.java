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

package org.niis.xroad.edc.extension.assetaccess.service;

import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.protocol.assetaccess.XRoadTransferType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AssetAccessStateStoreTest {

    AssetAccessStateStore store;

    @BeforeEach
    void setUp() {
        store = new AssetAccessStateStore();
    }

    @Test
    void getAgreementReturnsNullWhenEmpty() {
        assertThat(store.getAgreement("any-key")).isNull();
    }

    @Test
    void recordAndRetrieveAgreement() {
        var agreement = buildAgreement("agr-1");
        store.recordAgreement("key-1", agreement, XRoadTransferType.PULL.wireValue());

        var ctx = store.getAgreement("key-1");

        assertThat(ctx).isNotNull();
        assertThat(ctx.agreement()).isSameAs(agreement);
        assertThat(ctx.transferType()).isEqualTo(XRoadTransferType.PULL.wireValue());
    }

    @Test
    void recordAgreementDoesNotAffectOtherKeys() {
        var agreement = buildAgreement("agr-1");
        store.recordAgreement("key-1", agreement, XRoadTransferType.PULL.wireValue());

        assertThat(store.getAgreement("key-2")).isNull();
    }

    @Test
    void loadOrStartInFlightReturnsSameFutureForSameKey() {
        var supplierCallCount = new AtomicInteger(0);

        var future1 = store.loadOrStartInFlight("k", () -> {
            supplierCallCount.incrementAndGet();
            return new CompletableFuture<>();
        });
        var future2 = store.loadOrStartInFlight("k", () -> {
            supplierCallCount.incrementAndGet();
            return new CompletableFuture<>();
        });

        assertThat(future1).isSameAs(future2);
        assertThat(supplierCallCount.get()).isEqualTo(1);
    }

    @Test
    void loadOrStartInFlightRemovesEntryOnCompletion() throws Exception {
        var inner = new CompletableFuture<ServiceResult<DataAddress>>();
        var future = store.loadOrStartInFlight("k", () -> inner);

        assertThat(future).isNotNull();

        inner.complete(ServiceResult.success(DataAddress.Builder.newInstance().type("HttpData").build()));
        future.get();

        // After completion, a new call with the same key should invoke the supplier again
        var supplierCalled = new AtomicInteger(0);
        store.loadOrStartInFlight("k", () -> {
            supplierCalled.incrementAndGet();
            return new CompletableFuture<>();
        });
        assertThat(supplierCalled.get()).isEqualTo(1);
    }

    @Test
    void loadOrStartInFlightDedupUnderConcurrency() throws Exception {
        var latch = new CountDownLatch(1);
        var supplierCallCount = new AtomicInteger(0);

        Runnable task = () -> store.loadOrStartInFlight("concurrent-key", () -> {
            supplierCallCount.incrementAndGet();
            return new CompletableFuture<>();
        });

        var t1 = new Thread(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.run();
        });
        var t2 = new Thread(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.run();
        });

        t1.start();
        t2.start();
        latch.countDown();
        t1.join();
        t2.join();

        assertThat(supplierCallCount.get()).isEqualTo(1);
    }

    @Test
    void loadOrStartInFlightReturnsDifferentFuturesForDifferentKeys() {
        var future1 = store.loadOrStartInFlight("key-a", CompletableFuture::new);
        var future2 = store.loadOrStartInFlight("key-b", CompletableFuture::new);

        assertThat(future1).isNotSameAs(future2);
    }

    private ContractAgreement buildAgreement(String id) {
        return ContractAgreement.Builder.newInstance()
                .id(id)
                .providerId("provider-1")
                .consumerId("consumer-1")
                .contractSigningDate(System.currentTimeMillis())
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .build();
    }
}
