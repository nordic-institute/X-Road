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
package org.niis.xroad.proxy.controlplane;

import com.apicatalog.did.Did;

/**
 * Provider DSP endpoint metadata used by the consumer-side asset-access flow.
 *
 * <p>{@code counterPartyId} is the URL-encoded {@code did:web} of the provider's
 * EDC participant identity (see EDC DR {@code 2023-12-19-token-handling-refactor}:
 * {@code IdentityAndTrustService} maps {@code counterPartyId} → DID → JWT audience).
 * The {@code %3A} in place of {@code :} between host and port is required by
 * {@code did:web} resolution semantics.
 *
 * <p>{@code counterPartyAddress} is the whole DSP base URL of the provider's Control Plane.
 * EDC appends the protocol version path and per-message subpath at dispatch time.
 *
 * <p>Both member and SYSTEM targets are derived from GlobalConf data and ecosystem-wide DSP
 * conventions; no map lookup is involved.
 *
 * @param counterPartyId      URL-encoded participant DID (e.g. {@code did:web:xrd-ss0%3A7183:v1:system})
 * @param counterPartyAddress full DSP base URL (e.g. {@code https://xrd-ss0:8183/api/dsp/system/http-dsp-profile-2025-1})
 */
public record CounterPartyTarget(Did counterPartyId, String counterPartyAddress) {
}
