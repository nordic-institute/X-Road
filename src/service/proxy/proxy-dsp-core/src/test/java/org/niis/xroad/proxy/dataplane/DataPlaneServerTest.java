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
package org.niis.xroad.proxy.dataplane;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPlaneServerTest {

    @Mock
    private ProxyDspProperties properties;

    private DataPlaneServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.destroy();
        }
    }

    @Test
    void init_createsServerWithoutStarting() throws Exception {
        when(properties.listenPort()).thenReturn(0);
        when(properties.listenAddress()).thenReturn("127.0.0.1");
        when(properties.threadPoolMin()).thenReturn(1);
        when(properties.threadPoolMax()).thenReturn(5);
        when(properties.threadPoolIdleTimeout()).thenReturn(1000);

        server = new DataPlaneServer(properties);
        // init() creates the server but does not start it — no exception
        assertThatNoException().isThrownBy(() -> server.init());
    }

    @Test
    void registerJaxRsResource_thenStart_handlerIsSet() throws Exception {
        when(properties.listenPort()).thenReturn(0);
        when(properties.listenAddress()).thenReturn("127.0.0.1");
        when(properties.threadPoolMin()).thenReturn(1);
        when(properties.threadPoolMax()).thenReturn(5);
        when(properties.threadPoolIdleTimeout()).thenReturn(1000);

        server = new DataPlaneServer(properties);
        server.init();
        server.registerJaxRsResource("test/", new Object());
        // start() registers the handler and starts the Jetty server — must not throw
        assertThatNoException().isThrownBy(() -> server.start());
    }
}
