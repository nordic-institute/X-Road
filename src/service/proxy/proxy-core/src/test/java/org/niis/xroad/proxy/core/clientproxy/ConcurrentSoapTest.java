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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.util.ClientSoapRequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies that concurrent SOAP requests to the same CDI singleton
 * {@link ClientSoapMessageProcessor} each receive independent per-request context.
 */
@Slf4j
class ConcurrentSoapTest {

    /**
     * Verifies that two concurrent ClientSoapRequestContext instances are fully independent.
     * Each context creates its own latches and piped streams internally — no shared mutable state.
     */
    @Test
    @Timeout(10)
    void soapRequestContextsAreIndependent() throws Exception {
        var request1 = mock(RequestWrapper.class);
        var response1 = mock(ResponseWrapper.class);
        var request2 = mock(RequestWrapper.class);
        var response2 = mock(ResponseWrapper.class);

        try (var ctx1 = new ClientSoapRequestContext(request1, response1, mock(OpMonitoringData.class));
             var ctx2 = new ClientSoapRequestContext(request2, response2, mock(OpMonitoringData.class))) {

            assertThat(ctx1.request()).isSameAs(request1);
            assertThat(ctx2.request()).isSameAs(request2);
            assertThat(ctx1.request()).isNotSameAs(ctx2.request());

            assertThat(ctx1.response()).isSameAs(response1);
            assertThat(ctx2.response()).isSameAs(response2);
            assertThat(ctx1.response()).isNotSameAs(ctx2.response());

            assertThat(ctx1.requestHandlerGate()).isNotSameAs(ctx2.requestHandlerGate());
            assertThat(ctx1.httpSenderGate()).isNotSameAs(ctx2.httpSenderGate());
            assertThat(ctx1.reqIns()).isNotSameAs(ctx2.reqIns());
            assertThat(ctx1.reqOuts()).isNotSameAs(ctx2.reqOuts());

            assertThat(ctx1.requestHandlerGate().getCount()).isEqualTo(1L);
            assertThat(ctx2.requestHandlerGate().getCount()).isEqualTo(1L);
        }
    }

}
