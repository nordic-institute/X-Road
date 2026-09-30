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

import java.util.List;
import java.util.Objects;

/**
 * The grant a provider states when it mints an agreement token: exactly one consumer subsystem, one service,
 * and the method/path patterns it may call under the given agreement. Protocol envelope details (issuer,
 * audience, expiry) are not part of the grant — {@link AgreementTokenMinter} derives them from its own
 * configuration so that every mint call is bound to the same, non-hardcoded TTL.
 */
public record AgreementTokenGrant(
        String agreementId,
        ClientId client,
        ServiceId service,
        List<AgreementTokenScope> scope) {

    public AgreementTokenGrant {
        if (agreementId == null || agreementId.isBlank()) {
            throw new IllegalArgumentException("agreementId must not be blank");
        }
        Objects.requireNonNull(client, "client must not be null");
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        if (scope.isEmpty()) {
            throw new IllegalArgumentException("scope must not be empty");
        }
        scope = List.copyOf(scope);
    }
}
