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

import org.niis.xroad.ds.identity.DspConventions;

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
 * <p>Member targets are derived from GlobalConf data ({@link DspConventions}); only the legacy
 * {@code -mgmt} synthetic targets remain map-based ({@link #managementMap()}) until the consumer
 * SYSTEM-routing story replaces them.
 *
 * @param counterPartyId      URL-encoded participant DID (e.g. {@code did:web:xrd-ss0%3A7183:mgmt})
 * @param counterPartyAddress full DSP base URL (e.g. {@code https://xrd-ss0:8183/api/dsp/xrd-ss0-mgmt/…})
 */
public record CounterPartyTarget(String counterPartyId, String counterPartyAddress) {

    /**
     * Targets of the legacy {@code -mgmt} participant contexts, keyed by GlobalConf-registered
     * provider host-address, covering the known dev/test substrates: Docker compose E2E
     * ({@code xrd-ss0/1/2}), LXD ({@code xrd-ss*.lxd}), Docker compose system-test ({@code ss0/1}),
     * and k8s ({@code proxy.ss0/1} — the namespace-qualified proxy Service names each release
     * registers in globalconf).
     *
     * <p>The DID follows the provider's provisioned management DID
     * ({@code did:web:<registered-address>%3A7183:mgmt}). The URL's context segment is the
     * provider's host participant context id plus {@code -mgmt} — provider-local configuration
     * that is not in GlobalConf, which is why these targets stay a map: on k8s the registered
     * address ({@code proxy.ssN}) and the host participant context id ({@code xrd-ssN}) differ.
     *
     * @return immutable map keyed by host-address, targeting the mgmt participant context
     */
    public static Map<String, CounterPartyTarget> managementMap() {
        return Map.ofEntries(
                //For E2E
                mgmtEntry("xrd-ss0"),
                mgmtEntry("xrd-ss1"),
                mgmtEntry("xrd-ss2"),
                //For LXD
                mgmtEntry("xrd-ss0.lxd"),
                mgmtEntry("xrd-ss1.lxd"),
                mgmtEntry("xrd-ss2.lxd"),
                //For docker compose system-test
                mgmtEntry("ss0"),
                mgmtEntry("ss1"),
                //For k8s
                mgmtEntry("proxy.ss0", "xrd-ss0"),
                mgmtEntry("proxy.ss1", "xrd-ss1"));
    }

    private static Map.Entry<String, CounterPartyTarget> mgmtEntry(String hostAddress) {
        return mgmtEntry(hostAddress, hostAddress);
    }

    private static Map.Entry<String, CounterPartyTarget> mgmtEntry(String hostAddress, String hostParticipantId) {
        var url = "https://%s:%d/api/dsp/%s%s/%s".formatted(hostAddress, DspConventions.DSP_PORT,
                hostParticipantId, DspConventions.MANAGEMENT_CONTEXT_SUFFIX, DspConventions.DSP_PROFILE_ID);
        return Map.entry(hostAddress, new CounterPartyTarget(DspConventions.managementDid(hostAddress), url));
    }
}
