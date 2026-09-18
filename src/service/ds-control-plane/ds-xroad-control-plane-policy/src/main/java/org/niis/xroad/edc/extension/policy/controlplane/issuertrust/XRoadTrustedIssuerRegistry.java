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
package org.niis.xroad.edc.extension.policy.controlplane.issuertrust;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@link TrustedIssuerRegistry} backed by an atomically swapped, immutable snapshot rather than the
 * additive-only in-memory map EDC ships by default. {@link #replaceAll} lets a periodic reloader publish a
 * wholesale replacement snapshot, so an issuer DID dropped from globalconf loses trust as soon as the next
 * snapshot is published, instead of surviving until process restart. {@link #register} stays additive into
 * the current snapshot to satisfy the interface contract for any other caller of this registry.
 */
final class XRoadTrustedIssuerRegistry implements TrustedIssuerRegistry {

    private final AtomicReference<Map<String, Set<String>>> supportedTypesByIssuerId = new AtomicReference<>(Map.of());

    @Override
    public void register(Issuer issuer, String credentialType) {
        supportedTypesByIssuerId.updateAndGet(current -> withAddedType(current, issuer.id(), credentialType));
    }

    @Override
    public Set<String> getSupportedTypes(Issuer issuer) {
        return supportedTypesByIssuerId.get().getOrDefault(issuer.id(), Set.of());
    }

    /**
     * Publishes a new snapshot trusting exactly {@code issuerIds} for {@code credentialType}, replacing
     * whatever the previous snapshot trusted. An issuer id missing from {@code issuerIds} is no longer
     * trusted once this call returns; an empty set drops all trust.
     */
    void replaceAll(Set<String> issuerIds, String credentialType) {
        supportedTypesByIssuerId.set(issuerIds.stream()
                .collect(Collectors.toUnmodifiableMap(Function.identity(), id -> Set.of(credentialType))));
    }

    private static Map<String, Set<String>> withAddedType(Map<String, Set<String>> current, String issuerId, String credentialType) {
        var updated = new HashMap<>(current);
        updated.merge(issuerId, Set.of(credentialType),
                (existing, added) -> Stream.concat(existing.stream(), added.stream()).collect(Collectors.toUnmodifiableSet()));
        return Map.copyOf(updated);
    }
}
