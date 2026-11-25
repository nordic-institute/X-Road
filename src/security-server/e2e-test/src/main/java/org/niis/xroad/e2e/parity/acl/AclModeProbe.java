/*
 * The MIT License
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

package org.niis.xroad.e2e.parity.acl;

/**
 * Tells which {@link AclMode} the consumer Security Server runs in from what the Central Server and the Security
 * Servers report about the Issuer Service. Without a bound Issuer Service no Security Server is ready for the data
 * space, no DID is published, and the consumer calls every provider without negotiation.
 */
public final class AclModeProbe {

    private AclModeProbe() {
    }

    /**
     * What the Central Server and the Security Servers report about the data space.
     *
     * @param issuerServiceBound whether the Central Server's Issuer Service identity is bound
     * @param ss0DataspaceReady  whether ss0 reports {@code dataspace_ready}
     * @param ss1DataspaceReady  whether ss1 reports {@code dataspace_ready}
     */
    public record DataspaceState(boolean issuerServiceBound, boolean ss0DataspaceReady, boolean ss1DataspaceReady) {

        private boolean anyPresent() {
            return issuerServiceBound || ss0DataspaceReady || ss1DataspaceReady;
        }

        private boolean allPresent() {
            return issuerServiceBound && ss0DataspaceReady && ss1DataspaceReady;
        }

        String describe() {
            return "Issuer Service bound: %s, ss0 dataspace_ready: %s, ss1 dataspace_ready: %s"
                    .formatted(issuerServiceBound, ss0DataspaceReady, ss1DataspaceReady);
        }
    }

    /**
     * Checks that the reported state matches the expected mode: {@link AclMode#LEGACY} needs no bound Issuer Service
     * and no ready Security Server, every other mode needs all three.
     *
     * @return a line saying which mode the consumer Security Server runs in
     * @throws IllegalStateException when the reported state names another mode
     */
    public static String verify(AclMode expected, DataspaceState state) {
        var matches = expected.consumerMode() == AclMode.LEGACY ? !state.anyPresent() : state.allPresent();
        if (!matches) {
            throw new IllegalStateException("ACL mode %s was requested but the environment is not set up for it (%s)"
                    .formatted(expected, state.describe()));
        }
        return "consumer Security Server runs in ACL mode %s (%s)".formatted(expected.consumerMode(), state.describe());
    }

    /**
     * Checks that the provider Security Server runs a stack the expected mode can be applied to: refusing legacy
     * calls needs a proxy container of its own to restart, which the sidecar stack lacks.
     *
     * @throws IllegalStateException when the mode refuses legacy calls and the provider runs the sidecar stack
     */
    public static void verifyProviderStack(AclMode expected, boolean sidecarStack) {
        if (expected.refusesLegacyCalls() && sidecarStack) {
            throw new IllegalStateException(
                    "ACL mode %s needs the default ss0 stack; drop -Pe2e.ss0-stack=sidecar".formatted(expected));
        }
    }
}
