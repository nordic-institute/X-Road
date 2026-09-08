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

package org.niis.xroad.proxy.core.addon.opmonitoring;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.util.Optional;

import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.ADDON_OP_MONITOR_CONNECTION_CLIENT_TLS_CERTIFICATE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.ADDON_OP_MONITOR_CONNECTION_CONNECTION_TIMEOUT_SECONDS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.ADDON_OP_MONITOR_CONNECTION_HOST;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.ADDON_OP_MONITOR_CONNECTION_PORT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.ADDON_OP_MONITOR_CONNECTION_SCHEME;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.ADDON_OP_MONITOR_CONNECTION_SOCKET_TIMEOUT_SECONDS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.ADDON_OP_MONITOR_CONNECTION_TLS_CERTIFICATE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.ADDON_OP_MONITOR_CONNECTION_XROAD_TLS_CIPHERS;

@RequiredArgsConstructor
public class OpMonitorConnectionProperties {

    private final XRoadConfig xroadConfig;

    /**
     * Address of operational monitoring daemon.
     * @return host address
     */
    public String host() {
        return xroadConfig.value(ADDON_OP_MONITOR_CONNECTION_HOST);
    }

    /**
     * Listen port of operational monitoring daemon.
     * @return port number
     */
    public int port() {
        return xroadConfig.value(ADDON_OP_MONITOR_CONNECTION_PORT);
    }

    /**
     * URI scheme name determining the used connection type.
     * @return http or https
     */
    public String scheme() {
        return xroadConfig.value(ADDON_OP_MONITOR_CONNECTION_SCHEME);
    }

    /**
     * Property name of the path to the location of the TLS certificate used by the HTTP client sending requests to the
     * operational data daemon.
     */
    @Deprecated
    //TODO In X-Road 8 we store TLS certificates in OpenBao - only needed for 7 -> 8 migration, can be removed otherwise
    public Optional<String> clientTlsCertificate() {
        return xroadConfig.valueOpt(ADDON_OP_MONITOR_CONNECTION_CLIENT_TLS_CERTIFICATE);
    }

    /**
     * @return the path to the location of the operational monitoring daemon TLS certificate,
     * If not explicitly specified, certificate from Vault will be used.
     *
     */
    @Deprecated
    //TODO In X-Road 8 we store TLS certificates in OpenBao - only needed for 7 -> 8 migration, can be removed otherwise
    public Optional<String> tlsCertificate() {
        return xroadConfig.valueOpt(ADDON_OP_MONITOR_CONNECTION_TLS_CERTIFICATE);
    }


    public int socketTimeoutSeconds() {
        return xroadConfig.value(ADDON_OP_MONITOR_CONNECTION_SOCKET_TIMEOUT_SECONDS);
    }

    public int connectionTimeoutSeconds() {
        return xroadConfig.value(ADDON_OP_MONITOR_CONNECTION_CONNECTION_TIMEOUT_SECONDS);
    }

    public String[] xroadTlsCiphers() {
        return xroadConfig.value(ADDON_OP_MONITOR_CONNECTION_XROAD_TLS_CIPHERS);
    }

}
