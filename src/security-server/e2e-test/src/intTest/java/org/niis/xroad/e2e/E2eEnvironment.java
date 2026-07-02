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
package org.niis.xroad.e2e;

/**
 * Environment abstraction for the e2e test suite.
 */
public interface E2eEnvironment {

    String HURL = "hurl";
    String DS_CONTROL_PLANE = "ds-control-plane";
    String DS_IDENTITY_HUB = "ds-identity-hub";
    String DS_ISSUER_SERVICE = "ds-issuer-service";
    String DB_MESSAGELOG = "db-messagelog";

    /**
     * Resolves a service's reachable host and port from (env, service, port).
     */
    ContainerMapping getContainerMapping(String env, String service, int port);

    /**
     * Returns true when the environment is fully bootstrapped and ready for tests.
     */
    boolean isInitialized();

    /**
     * Returns the host name of the peer dataspace control-plane for the given environment.
     */
    String peerControlPlaneHost(String env);

    /**
     * Resolved address of a service (host + port pair).
     *
     * @param host resolved hostname or IP
     * @param port resolved TCP port
     */
    record ContainerMapping(String host, int port) {
    }

    /**
     * Port constants used across the e2e test suite.
     */
    final class Port {
        public static final int UI = 4000;
        public static final int PROXY = 8080;
        public static final int PROXY_HEALTHCHECK = 5588;
        public static final int CONTROL_PLANE_MANAGEMENT = 8182;
        public static final int CONTROL_PLANE_PROTOCOL = 8183;
        public static final int IDENTITY_HUB_IDENTITY = 7182;
        public static final int IDENTITY_HUB_STS = 7184;
        public static final int ISSUER_SERVICE_IDENTITY = 6182;
        public static final int ISSUER_SERVICE_ADMIN = 6186;

        private Port() {
        }
    }
}
