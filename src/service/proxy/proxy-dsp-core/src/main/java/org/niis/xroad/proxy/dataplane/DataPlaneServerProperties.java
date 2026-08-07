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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;

import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.DSP_LISTEN_ADDRESS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.DSP_LISTEN_PORT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.DSP_SERVERPROXY_ENDPOINT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.DSP_THREAD_POOL_IDLE_TIMEOUT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.DSP_THREAD_POOL_MAX;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.DSP_THREAD_POOL_MIN;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.SERVER_PORT;

/** Configuration for the data-plane listener ({@code xroad.proxy.dsp.*}). */
@RequiredArgsConstructor
public class DataPlaneServerProperties {

    private final XRoadConfig xRoadConfig;

    /** @return listen address for the data-plane server */
    public String listenAddress() {
        return xRoadConfig.value(DSP_LISTEN_ADDRESS);
    }

    /** @return listen port for the data-plane server */
    public int listenPort() {
        return xRoadConfig.value(DSP_LISTEN_PORT);
    }

    /** @return minimum thread pool size */
    public int threadPoolMin() {
        return xRoadConfig.value(DSP_THREAD_POOL_MIN);
    }

    /** @return maximum thread pool size */
    public int threadPoolMax() {
        return xRoadConfig.value(DSP_THREAD_POOL_MAX);
    }

    /** @return thread pool idle timeout in milliseconds */
    public int threadPoolIdleTimeout() {
        return xRoadConfig.value(DSP_THREAD_POOL_IDLE_TIMEOUT);
    }

    /** @return server-proxy endpoint URL; defaults to localhost on the configured server-proxy port */
    public String serverproxyEndpoint() {
        return xRoadConfig.valueOpt(DSP_SERVERPROXY_ENDPOINT)
                .orElseGet(() -> "https://localhost:" + xRoadConfig.value(SERVER_PORT));
    }
}
