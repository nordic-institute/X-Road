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
package org.niis.xroad.opmonitor.api;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

import java.util.Optional;

@ConfigMapping(prefix = "xroad.op-monitor")
public interface OpMonitorCommonProperties {

    @WithParentName
    OpMonitorConnectionProperties connection();

    @WithName("service")
    OpMonitorServiceProperties service();

    @WithName("buffer")
    OpMonitorBufferProperties buffer();

    interface OpMonitorConnectionProperties {
        /**
         * Address of operational monitoring daemon.
         *
         * @return host address
         */
        @WithName("host")
        @WithDefault("localhost")
        String host();

        /**
         * Listen port of operational monitoring daemon.
         *
         * @return port number
         */
        @WithName("port")
        @WithDefault("2080")
        int port();

        /**
         * URI scheme name determining the used connection type.
         *
         * @return http or https
         */
        @WithName("scheme")
        @WithDefault("http")
        String scheme();

        /**
         * Property name of the path to the location of the TLS certificate used by the HTTP client sending requests to the
         * operational data daemon.
         */
        @WithName("client-tls-certificate")
        @Deprecated
        // TODO: In X-Road 8 we store TLS certificates in OpenBao - only needed for 7 -> 8 migration, can be removed otherwise
        Optional<String> clientTlsCertificate();

        /**
         * @return the path to the location of the operational monitoring daemon TLS certificate,
         * If not explicitly specified, certificate from Vault will be used.
         *
         */
        @WithName("tls-certificate")
        @Deprecated
        // TODO: In X-Road 8 we store TLS certificates in OpenBao - only needed for 7 -> 8 migration, can be removed otherwise
        Optional<String> tlsCertificate();

    }

    interface OpMonitorServiceProperties {
        @WithName("socket-timeout-seconds")
        @WithDefault("60")
        int socketTimeoutSeconds();

        @WithName("connection-timeout-seconds")
        @WithDefault("30")
        int connectionTimeoutSeconds();
    }

    interface OpMonitorBufferProperties {

        @WithName("size")
        @WithDefault("20000")
        int size();

        @WithName("max-records-in-message")
        @WithDefault("100")
        int maxRecordsInMessage();

        @WithName("sending-interval-seconds")
        @WithDefault("5")
        long sendingIntervalSeconds();

        @WithName("socket-timeout-seconds")
        @WithDefault("60")
        int socketTimeoutSeconds();

        @WithName("connection-timeout-seconds")
        @WithDefault("50")
        int connectionTimeoutSeconds();

    }

}
