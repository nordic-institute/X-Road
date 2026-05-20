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
package org.niis.xroad.proxy.dataplane;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration mapping for the data-plane listener.
 */
@ConfigMapping(prefix = "xroad.proxy.dsp")
public interface DataPlaneServerProperties {

    @WithName("listen-address")
    @WithDefault("127.0.0.1")
    String listenAddress();

    @WithName("listen-port")
    @WithDefault("5590")
    int listenPort();

    @WithName("thread-pool-min")
    @WithDefault("10")
    int threadPoolMin();

    @WithName("thread-pool-max")
    @WithDefault("200")
    int threadPoolMax();

    @WithName("thread-pool-idle-timeout")
    @WithDefault("60000")
    int threadPoolIdleTimeout();

    @WithName("control-plane-endpoint")
    @WithDefault("http://127.0.0.1:8184/api/v1/control")
    String controlPlaneEndpoint();

    @WithDefault("http://127.0.0.1:5590/full/api/v1/dataflows")
    String dataFlowEndpoint();

    /**
     * The participant context ID used when registering this proxy as a data-plane instance on the control plane.
     *
     * <p>Must match the {@code ParticipantContext} registered in the Identity Hub for this
     * Security Server (e.g. {@code xrd-ss0}). Set via {@code local-dsp.yaml} in native deployments.
     * No default — must be set explicitly so a misconfigured deployment fails fast.
     *
     * @return participant context ID
     */
    @WithName("participant-context-id")
    String participantContextId();

    /**
     * The participant context ID for the MANAGEMENT subsystem's DSP context.
     *
     * <p>Defaults to {@code <participantContextId>-mgmt} (e.g. {@code xrd-ss0-mgmt}).
     * Override in {@code local-dsp.yaml} only when the mgmt context is provisioned under a
     * non-standard ID.
     *
     * @return management participant context ID
     */
    @WithName("management-participant-context-id")
    @WithDefault("${xroad.proxy.dsp.participant-context-id}-mgmt")
    String managementParticipantContextId();

}
