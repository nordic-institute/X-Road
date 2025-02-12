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

package org.niis.xroad.proxy.core.test;

import ee.ria.xroad.common.SystemProperties;

import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Set;

import static ee.ria.xroad.common.TestPortUtils.findRandomPort;
import static java.lang.String.valueOf;
import static java.util.Optional.ofNullable;

@UtilityClass
public class ProxyTestSuiteHelper {
    public static final int SERVICE_PORT = findRandomPort();
    public static final int SERVICE_SSL_PORT = findRandomPort();
    public static final int PROXY_PORT = findRandomPort();
    public static final int DUMMY_SERVER_PROXY_PORT = findRandomPort();

    public static volatile MessageTestCase currentTestCase;

    private static DummyService dummyService;
    private static DummyServerProxy dummyServerProxy;

    public static void startTestServices() throws Exception {
        dummyService = new DummyService();
        dummyService.start();
    }

    public static void startDummyProxy() throws Exception {
        dummyServerProxy = new DummyServerProxy(DUMMY_SERVER_PROXY_PORT);
        dummyServerProxy.start();
    }

    public static void destroyTestServices() {
        ofNullable(dummyService).ifPresent(DummyService::destroy);
        ofNullable(dummyServerProxy).ifPresent(DummyServerProxy::destroy);
    }

    public static void setPropsIfNotSet(Map<String, String> properties) {
        PropsSolver solver = new PropsSolver();

        int clientHttpPort = findRandomPort();
        properties.putIfAbsent("xroad.proxy.client-proxy.client-http-port", valueOf(clientHttpPort));
        solver.setIfNotSet(SystemProperties.PROXY_CLIENT_HTTP_PORT, valueOf(clientHttpPort));

        int clientHttpsPort = findRandomPort();
        properties.putIfAbsent("xroad.proxy.client-proxy.client-https-port", valueOf(clientHttpsPort));
        solver.setIfNotSet(SystemProperties.PROXY_CLIENT_HTTPS_PORT, valueOf(clientHttpsPort));

        solver.setIfNotSet(SystemProperties.TEMP_FILES_PATH, "build/");
        solver.setIfNotSet(SystemProperties.GRPC_INTERNAL_TLS_ENABLED, Boolean.FALSE.toString());

        properties.putIfAbsent("xroad.proxy.server.listen-address", "127.0.0.1");

        properties.putIfAbsent("xroad.proxy.server.listen-port", valueOf(PROXY_PORT));
        solver.setIfNotSet(SystemProperties.PROXY_SERVER_PORT, valueOf(PROXY_PORT));

        System.setProperty(SystemProperties.PROXY_CLIENT_TIMEOUT, "15000");
    }

    @Deprecated(forRemoval = true)
    private static final class PropsSolver {
        private final Set<String> setProperties = System.getProperties().stringPropertyNames();

        @Deprecated(forRemoval = true)
        void setIfNotSet(String property, String defaultValue) {
            if (!setProperties.contains(property)) {
                System.setProperty(property, defaultValue);
            }
        }
    }

}
