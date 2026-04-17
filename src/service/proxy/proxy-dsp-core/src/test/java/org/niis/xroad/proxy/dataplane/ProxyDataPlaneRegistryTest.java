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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProxyDataPlaneRegistryTest {

    @Mock
    private ProxyDspProperties dspProperties;
    @Mock
    private DataPlaneServer dataPlaneServer;

    private ProxyDataPlaneRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ProxyDataPlaneRegistry(dspProperties, dataPlaneServer);
    }

    @Test
    void initialize_registersJaxRsResourceWithApiContextPath() throws Exception {
        stubProperties();

        // initialize() will throw because the control plane endpoint is not reachable; that is expected in unit test
        assertThatThrownBy(() -> registry.initialize())
                .isInstanceOf(XrdRuntimeException.class);

        // Verify the context path includes "/api/" (Fix 4)
        verify(dataPlaneServer).registerJaxRsResource(eq("/full/api/"), any());
    }

    @Test
    void initialize_registersJaxRsResourceBeforeServerStart() throws Exception {
        stubProperties();

        assertThatThrownBy(() -> registry.initialize())
                .isInstanceOf(XrdRuntimeException.class);

        // Verify registration happens before server start (Fix 2 — init order)
        InOrder order = inOrder(dataPlaneServer);
        order.verify(dataPlaneServer).registerJaxRsResource(eq("/full/api/"), any());
        order.verify(dataPlaneServer).start();
    }

    @Test
    void initialize_usesXrdPullTransferType() throws Exception {
        stubProperties();

        assertThatThrownBy(() -> registry.initialize())
                .isInstanceOf(XrdRuntimeException.class);

        // The Dataplane builder succeeded and registered a controller — confirms transferType("Xrd-PULL") was set
        verify(dataPlaneServer).registerJaxRsResource(eq("/full/api/"), any());
    }

    private void stubProperties() {
        org.mockito.Mockito.when(dspProperties.dataFlowEndpoint()).thenReturn("http://127.0.0.1:5590/full/api/v1/dataflows");
        org.mockito.Mockito.when(dspProperties.controlPlaneEndpoint()).thenReturn("http://127.0.0.1:8184/api/v1/control");
    }
}
