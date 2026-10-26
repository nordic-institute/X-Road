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

package org.niis.xroad.edc.extension.assetaccess.token;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import org.eclipse.edc.controlplane.DataAddress;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * Reads the expiry of the agreement token carried as the {@code authorization} property of a
 * {@link DataAddress}. The token is a JWT; only its unsigned {@code exp} claim is read, without any
 * signature validation. A missing, non-JWT or otherwise unparseable token yields an empty result.
 */
public final class AgreementTokenExpiry {

    private static final String AUTHORIZATION_KEY = EDC_NAMESPACE + "authorization";

    private AgreementTokenExpiry() {
    }

    public static Optional<Instant> expiryOf(DataAddress dataAddress) {
        if (dataAddress == null) {
            return Optional.empty();
        }
        return expiryOf((String) dataAddress.getProperties().get(AUTHORIZATION_KEY));
    }

    private static Optional<Instant> expiryOf(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return Optional.empty();
        }
        try {
            var parts = authorization.split("\\.");
            if (parts.length < 2) {
                return Optional.empty();
            }
            var payload = Base64.getUrlDecoder().decode(parts[1]);
            try (var reader = Json.createReader(new ByteArrayInputStream(payload))) {
                return (reader.readObject().get("exp") instanceof JsonNumber number)
                        ? Optional.of(Instant.ofEpochSecond(number.longValueExact()))
                        : Optional.empty();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
