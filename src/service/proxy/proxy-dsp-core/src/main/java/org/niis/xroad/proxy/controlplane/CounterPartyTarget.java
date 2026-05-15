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

import java.util.HashMap;
import java.util.Map;

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
 * <p>This is hardcoded today, keyed by globalconf-resolved provider host-address.
 * Future work moves the lookup into globalconf-published shared parameters
 * (see {@code .scratch/dsp-counterparty-cleanup/issues/04-followup-globalconf-dsp-endpoint.md}).
 *
 * @param counterPartyId      URL-encoded participant DID (e.g. {@code did:web:xrd-ss0%3A7183})
 * @param counterPartyAddress full DSP base URL (e.g. {@code https://xrd-ss0:8183/api/dsp})
 */
public record CounterPartyTarget(String counterPartyId, String counterPartyAddress) {

    /**
     * Default per-host map used when no provider-specific source is configured.
     *
     * <p>Covers known dev/test candidates: LXD + k8s ({@code xrd-ss0/1/2}) and Docker
     * compose ({@code ss0/1}). Keys are the values returned by globalconf for each
     * provider security-server host-address.
     *
     * <p>The {@code counterPartyAddress} bakes in BOTH the provider's local participant
     * context id AND the DSP protocol version segment ({@code /2025-1}) because the
     * EDC v2025/1 catalog/negotiation/transfer APIs are scoped under
     * {@code /api/dsp/<participantContextId>/2025-1/...} and the {@code DspHttpDispatcherV2025}
     * does not auto-prepend either segment — it concatenates the per-message subpath
     * (e.g. {@code /catalog/request}) onto whatever address it is given. Verified against
     * a running provider: unversioned/unscoped paths return 404, the scoped path returns
     * 401 (handler reached). Two provider participant context ids in dev align with the
     * SS hostname, so the segment appears twice in the URL.
     *
     * @return immutable map keyed by host-address
     */
    public static Map<String, CounterPartyTarget> defaultMap() {
        return Map.of(
                "xrd-ss0", new CounterPartyTarget("did:web:xrd-ss0%3A7183", "https://xrd-ss0:8183/api/dsp/xrd-ss0/2025-1"),
                "xrd-ss1", new CounterPartyTarget("did:web:xrd-ss1%3A7183", "https://xrd-ss1:8183/api/dsp/xrd-ss1/2025-1"),
                "xrd-ss2", new CounterPartyTarget("did:web:xrd-ss2%3A7183", "https://xrd-ss2:8183/api/dsp/xrd-ss2/2025-1"),
                "ss0", new CounterPartyTarget("did:web:ss0%3A7183", "https://ss0:8183/api/dsp/ss0/2025-1"),
                "ss1", new CounterPartyTarget("did:web:ss1%3A7183", "https://ss1:8183/api/dsp/ss1/2025-1"));
    }

    /**
     * Management-context variant of {@link #defaultMap()}.
     *
     * <p>Each entry is derived from the host-ctx entry by appending {@code -mgmt} to the
     * participant context ID in both the URL path and the DID path segment. No new hosts
     * are introduced — the map keys are identical to those in {@link #defaultMap()}.
     *
     * <p>DID convention: {@code did:web:<host>%3A<port>:mgmt} — path-form DID, same
     * resolution base as the host ctx but scoped to the {@code :mgmt} sub-path.
     *
     * @return immutable map keyed by host-address, targeting the mgmt participant context
     */
    public static Map<String, CounterPartyTarget> managementMap() {
        var base = defaultMap();
        var result = new HashMap<String, CounterPartyTarget>(base.size() * 2);
        for (var entry : base.entrySet()) {
            var host = entry.getKey();
            var hostTarget = entry.getValue();
            // Append ":mgmt" to the DID path and "-mgmt" to the ctx ID path in the URL.
            var mgmtDid = hostTarget.counterPartyId() + ":mgmt";
            var mgmtUrl = hostTarget.counterPartyAddress().replaceFirst("/" + host + "/", "/" + host + "-mgmt/");
            result.put(host, new CounterPartyTarget(mgmtDid, mgmtUrl));
        }
        return Map.copyOf(result);
    }
}
