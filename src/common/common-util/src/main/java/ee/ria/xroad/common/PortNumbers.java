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
package ee.ria.xroad.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * This interface contains global constants, such as port numbers
 * and configuration locations.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PortNumbers {
    /** Client proxy listens for HTTP queries. */
    public static final int CLIENT_HTTP_PORT = 8080;

    /** Client proxy listens for HTTPS queries. */
    public static final int CLIENT_HTTPS_PORT = 8443;

    /** Port for connection between client and server proxy. */
    public static final int PROXY_PORT = 5500;

    /** Server proxy listens for OCSP requests. */
    public static final int PROXY_OCSP_PORT = 5577;

    /** Admin port for proxy. */
    public static final int ADMIN_PORT = 5566;

    /** Signer listens for HTTP queries. */
    public static final int SIGNER_PORT = 5558;

    /** Signer Admin port. */
    public static final int SIGNER_ADMIN_PORT = 5559;

    /**
     * Signer grpc service port.
     */
    public static final int SIGNER_GRPC_PORT = 5560;

    /** Port for Distributed Files Client. */
    public static final int CONFIGURATION_CLIENT_PORT = 5665;

    /** Port for Configuration Admin Port. */
    public static final int CONFIGURATION_CLIENT_ADMIN_PORT = 5675;

    /** Port of the operational monitoring daemon. */
    public static final int OP_MONITOR_DAEMON_PORT = 2080;

    /**
     * Proxy grpc port
     */
    public static final int PROXY_GRPC_PORT = 5567;

}
