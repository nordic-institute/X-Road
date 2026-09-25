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
package org.niis.xroad.common.agreementtoken;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The full claim set an agreement token carries on the wire: the grant ({@code agreementId}, {@code client},
 * {@code service}, {@code scope}) plus the protocol envelope ({@code issuer}, {@code audience},
 * {@code expiresAt}) that {@link AgreementTokenVerifier} checked before returning this value. Produced only by
 * a successful {@link AgreementTokenVerifier#verify} call — nothing else in the codebase constructs one from
 * untrusted input.
 */
public record AgreementTokenClaims(
        String agreementId,
        ClientId client,
        ServiceId service,
        List<AgreementTokenScope> scope,
        String issuer,
        String audience,
        Instant expiresAt) {

    public AgreementTokenClaims {
        if (agreementId == null || agreementId.isBlank()) {
            throw new IllegalArgumentException("agreementId must not be blank");
        }
        Objects.requireNonNull(client, "client must not be null");
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        if (scope.isEmpty()) {
            throw new IllegalArgumentException("scope must not be empty");
        }
        Objects.requireNonNull(issuer, "issuer must not be null");
        Objects.requireNonNull(audience, "audience must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        scope = List.copyOf(scope);
    }
}
