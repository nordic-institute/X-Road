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
package ee.ria.xroad.common.message;

import org.apache.http.message.BasicHeader;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Test RestResponse
 */
public class RestResponseTest {

    @Test
    public void shouldParseResponse() throws Exception {
        final RestRequest req = new RestRequest(
                "GET",
                "/r1/Instance/Class/Member/SubSystem/Service%2FCode",
                "foo=bar",
                Arrays.asList(
                        new BasicHeader("X-Road-Client", "Instance/Class/Member/SubSystem"),
                        new BasicHeader("X-Road-Id", "42"),
                        new BasicHeader("X-Road-ServerId", "Instance/Class/Member/ServerCode")),
                "xid"
        );
        final RestResponse resp1 = new RestResponse(
                req.getClientId(),
                req.getQueryId(),
                req.getHash(),
                req.getServiceId(),
                200,
                "OK",
                Arrays.asList(new BasicHeader("Test", "Header")),
                req.getXRequestId());

        final byte[] msg1 = resp1.toByteArray();
        final RestResponse resp2 = RestResponse.of(msg1);

        assertEquals(resp1.getClientId(), resp2.getClientId());
        assertEquals(resp1.getServiceId(), resp2.getServiceId());
        assertArrayEquals(resp1.getRequestHash(), resp2.getRequestHash());
    }


}
