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

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Business configuration for DSP asset access requests.
 */
@ConfigMapping(prefix = "xroad.proxy.dsp")
public interface AssetAccessClientProperties {

    /**
     * The participant context ID used when calling the control plane asset access endpoint.
     *
     * @return participant context ID
     */
    @WithName("participant-context-id")
    @WithDefault("test-part-ctx")
    String participantContextId();

    /**
     * The DSP protocol identifier for negotiation.
     *
     * @return protocol identifier
     */
    @WithDefault("dataspace-protocol-http:2025-1")
    String protocol();

    /**
     * URL scheme for the provider DSP endpoint.
     *
     * <p>Defaults to {@code http} matching the dev-time Control Plane
     * {@code application.yaml}. A TLS-fronted deployment should override to {@code https}
     * via {@code xroad.proxy.dsp.counter-party-url-scheme=https}.
     *
     * @return URL scheme (e.g. {@code "http"} or {@code "https"})
     */
    @WithName("counter-party-url-scheme")
    @WithDefault("http")
    String counterPartyUrlScheme();

    /**
     * Port where the provider Control Plane listens for DSP protocol traffic.
     *
     * <p>Defaults to {@code 8183} matching the dev-time Control Plane
     * {@code web.http.protocol.port} setting in
     * {@code ds-control-plane-application/src/main/resources/application.yaml}.
     *
     * @return DSP port number
     */
    @WithName("counter-party-port")
    @WithDefault("8183")
    int counterPartyPort();

    /**
     * Base path for the DSP protocol endpoint on the provider Control Plane.
     *
     * <p>Defaults to {@code /api/dsp} matching the dev-time Control Plane
     * {@code web.http.protocol.path} setting. EDC appends the protocol version path
     * ({@code /2025-1}) and per-message subpath ({@code /catalog/request}, etc.) at
     * dispatch time — consumers supply ONLY this base prefix.
     *
     * @return DSP base path prefix (starts with {@code /})
     */
    @WithName("counter-party-base-path")
    @WithDefault("/api/dsp")
    String counterPartyBasePath();
}
