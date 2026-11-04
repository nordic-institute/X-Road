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

import java.util.Locale;

/**
 * Tells which {@link AclMode} the consumer Security Server runs in from the value its serverconf stores for
 * {@code xroad.proxy.dsp-enabled}, the only source the proxy reads that key from. Without a stored value the
 * proxy uses its default, which is the data space protocol.
 */
public final class AclModeProbe {

    private AclModeProbe() {
    }

    /**
     * The mode a stored {@code dsp-enabled} value names.
     *
     * @param storedValue the value in serverconf, or {@code null} or blank when none is stored
     * @throws IllegalStateException when the value is neither {@code true} nor {@code false}
     */
    public static AclMode modeOf(String storedValue) {
        var value = storedValue == null ? "" : storedValue.trim();
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "", "true" -> AclMode.DSP;
            case "false" -> AclMode.LEGACY;
            default -> throw new IllegalStateException("unexpected stored value of dsp-enabled: " + value);
        };
    }

    /**
     * Checks that the stored value names the mode the expected mode needs the consumer to run in.
     *
     * @return a line saying which mode the Security Server runs in
     * @throws IllegalStateException when the stored value names another mode
     */
    public static String verify(AclMode expected, String storedValue) {
        var actual = modeOf(storedValue);
        var stored = storedValue == null || storedValue.isBlank() ? "none (default)" : storedValue.trim();
        if (actual != expected.consumerMode()) {
            throw new IllegalStateException(
                    "ACL mode %s was requested but the consumer Security Server runs in %s (stored dsp-enabled: %s)"
                            .formatted(expected, actual, stored));
        }
        return "consumer Security Server runs in ACL mode %s (stored dsp-enabled: %s)".formatted(actual, stored);
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
