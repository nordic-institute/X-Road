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

package org.niis.xroad.edc.extension.assetaccess.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FutureRegistryTest {

    private static final BiFunction<String, String, Throwable> EXCEPTION_FACTORY =
            (id, reason) -> new IllegalStateException("id=" + id + " reason=" + reason);

    FutureRegistry<String> registry;

    @BeforeEach
    void setUp() {
        registry = new FutureRegistry<>(EXCEPTION_FACTORY);
    }

    @Test
    void registerThenDispatchSuccessCompletsFuture() throws Exception {
        var future = new CompletableFuture<String>();
        registry.register("id-1", future);
        registry.dispatchSuccess("id-1", "payload-A");

        assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("payload-A");
        assertThat(registry.activeWaiters()).isZero();
    }

    @Test
    void registerThenDispatchTerminationFailsFuture() {
        var future = new CompletableFuture<String>();
        registry.register("id-1", future);
        registry.dispatchTermination("id-1", "boom");

        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id=id-1")
                .hasMessageContaining("reason=boom");
        assertThat(registry.activeWaiters()).isZero();
    }

    @Test
    void dispatchSuccessBeforeRegisterDeliversViaDeadLetter() throws Exception {
        registry.dispatchSuccess("id-1", "payload-A");

        var future = new CompletableFuture<String>();
        registry.register("id-1", future);

        assertThat(future.isDone()).isTrue();
        assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("payload-A");
        assertThat(registry.activeWaiters()).isZero();
    }

    @Test
    void dispatchTerminationBeforeRegisterDeliversViaDeadLetter() {
        registry.dispatchTermination("id-1", "boom");

        var future = new CompletableFuture<String>();
        registry.register("id-1", future);

        assertThat(future.isCompletedExceptionally()).isTrue();
        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id=id-1")
                .hasMessageContaining("reason=boom");
        assertThat(registry.activeWaiters()).isZero();
    }

    @Test
    void deregisterRemovesFromRegistryAndDeadLetters() {
        var future = new CompletableFuture<String>();
        registry.register("id-1", future);
        registry.deregister("id-1");

        registry.dispatchSuccess("id-1", "payload-A");
        assertThat(future.isDone()).isFalse();
        assertThat(registry.activeWaiters()).isZero();
    }

    @Test
    void deregisterClearsDeadLetterSuccess() {
        registry.dispatchSuccess("id-1", "payload-A");
        registry.deregister("id-1");

        var future = new CompletableFuture<String>();
        registry.register("id-1", future);

        assertThat(future.isDone()).isFalse();
    }

    @Test
    void deregisterClearsDeadLetterTermination() {
        registry.dispatchTermination("id-1", "boom");
        registry.deregister("id-1");

        var future = new CompletableFuture<String>();
        registry.register("id-1", future);

        assertThat(future.isDone()).isFalse();
    }

    @Test
    void unmatchedDispatchSuccessIsNoOp() {
        registry.dispatchSuccess("unknown", "payload-A");
        assertThat(registry.activeWaiters()).isZero();
    }

    @Test
    void successAndTerminationOnDifferentIdsAreIsolated() throws Exception {
        var futureA = new CompletableFuture<String>();
        var futureB = new CompletableFuture<String>();
        registry.register("id-A", futureA);
        registry.register("id-B", futureB);

        registry.dispatchSuccess("id-A", "payload-A");
        registry.dispatchTermination("id-B", "boom-B");

        assertThat(futureA.get(1, TimeUnit.SECONDS)).isEqualTo("payload-A");
        assertThat(futureB.isCompletedExceptionally()).isTrue();
        assertThat(registry.activeWaiters()).isZero();
    }
}
