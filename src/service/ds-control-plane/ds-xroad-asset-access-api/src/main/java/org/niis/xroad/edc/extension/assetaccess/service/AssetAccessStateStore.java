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
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Holds in-memory mutable state for the asset access flow: the agreement registry and the in-flight request map.
 */
public class AssetAccessStateStore {

    private final ConcurrentHashMap<String, AgreementContext> agreementRegistry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<ServiceResult<DataAddress>>> inFlightRequests = new ConcurrentHashMap<>();

    /**
     * Returns the cached agreement context for the given key, or {@code null} if none is recorded.
     */
    public AgreementContext getAgreement(String key) {
        return agreementRegistry.get(key);
    }

    /**
     * Stores an agreement context under the given key.
     */
    public void recordAgreement(String key, ContractAgreement agreement, String transferType) {
        agreementRegistry.put(key, new AgreementContext(agreement, transferType));
    }

    /**
     * Returns the existing in-flight future for {@code key} if one exists; otherwise calls {@code supplier}
     * to create a new future, registers it, and returns it. The registered future removes itself from the
     * map on completion.
     *
     * <p>The self-removal callback is attached only after {@code computeIfAbsent} has inserted the entry.
     * A supplier may return an already-completed future (the assembly itself failed synchronously); attaching
     * the callback inside the mapping function would run the removal before the entry exists, leaving the
     * dead future cached under the key forever. Callers that join an existing future attach a redundant
     * callback; the value-conditional remove makes that harmless.
     *
     * <p>The supplier must be non-blocking: it runs under a {@link ConcurrentHashMap} bin lock
     * and is expected only to assemble (not await) the async pipeline.
     */
    public CompletableFuture<ServiceResult<DataAddress>> loadOrStartInFlight(
            String key,
            Supplier<CompletableFuture<ServiceResult<DataAddress>>> supplier) {
        var future = inFlightRequests.computeIfAbsent(key, k -> supplier.get());
        future.whenComplete((result, throwable) -> inFlightRequests.remove(key, future));
        return future;
    }

    /**
     * Holds a negotiated agreement together with the transfer type chosen for the asset.
     */
    public record AgreementContext(ContractAgreement agreement, String transferType) {
    }
}
