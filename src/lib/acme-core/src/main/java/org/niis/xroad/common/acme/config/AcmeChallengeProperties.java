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

package org.niis.xroad.common.acme.config;

/**
 * The subset of ACME configuration the shared HTTP-01 challenge listener ({@code AcmeChallengerConfig},
 * {@code AcmeChallengeFilter}) consumes, independent of which certificate flow is being renewed or whether
 * a scheduler is even involved. {@link AcmeConfig} extends this with the full auth/sign-member-certificate
 * configuration surface; a consumer that only serves challenges can implement this narrower interface alone.
 */
public interface AcmeChallengeProperties {

    /**
     * whether the service should listen on acme challenge port (default 80) for incoming requests
     */
    boolean isAcmeChallengePortEnabled();

    int getAcmeChallengePort();

    /**
     * @return the network address the ACME challenge listener binds to, or {@code null} to bind every
     *     interface (the default). Bind every interface when this listener is itself the public-facing
     *     HTTP-01 endpoint; a product that fronts it with its own reverse proxy should return a loopback
     *     address instead, so the port is reachable only through that proxy, never directly.
     */
    default String getAcmeChallengeBindAddress() {
        return null;
    }
}
