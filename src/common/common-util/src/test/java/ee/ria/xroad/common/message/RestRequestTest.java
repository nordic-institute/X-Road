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
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Test RestRequest
 */
public class RestRequestTest {

    @Test
    public void shouldParseRequest() throws Exception {
        final RestRequest req = new RestRequest(
                "GET",
                String.format("/r%d/Instance/Class/Member/SubSystem/ServiceCode", RestMessage.PROTOCOL_VERSION),
                "foo=bar",
                Arrays.asList(
                        new BasicHeader("X-Road-Client", "Instance/Class/Member/SubSystem"),
                        new BasicHeader("X-Road-Id", "42"),
                        new BasicHeader("X-Road-ServerId", "Instance/Class/Member/ServerCode")),
                "xid"
        );
        final byte[] msg1 = req.toByteArray();
        final RestRequest req2 = new RestRequest(msg1);
        assertArrayEquals(msg1, req2.getMessageBytes());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectWrongProtocolVersion() throws Exception {
        final RestRequest req = new RestRequest(
                "GET",
                String.format("/r%d/Instance/Class/Member/SubSystem/ServiceCode", RestMessage.PROTOCOL_VERSION + 1),
                "foo=bar",
                Collections.emptyList(),
                "xid"
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidURL() {
        RestRequest req = new RestRequest(
                "GET",
                "https://invalid.uri/",
                null,
                Collections.emptyList(),
                "xid"
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidVerb() {
        RestRequest req = new RestRequest(
                "INVALID",
                String.format("/r%d/Instance/Class/Member/SubSystem/ServiceCode", RestMessage.PROTOCOL_VERSION),
                "foo=bar",
                Collections.emptyList(),
                "xid"
        );
    }

    @Test
    public void validRepresentedPartyHeaderWithMember() throws Exception {
        final RestRequest req = new RestRequest(
                "GET",
                String.format("/r%d/Instance/Class/Member/SubSystem/ServiceCode", RestMessage.PROTOCOL_VERSION),
                "foo=bar",
                Arrays.asList(
                        new BasicHeader("X-Road-Client", "Instance/Class/Member/SubSystem"),
                        new BasicHeader("X-Road-Id", "42"),
                        new BasicHeader("X-Road-ServerId", "Instance/Class/Member/ServerCode"),
                        new BasicHeader("X-Road-Represented-Party", "Member")),
                "xid"
        );
        assertEquals(null, req.getRepresentedParty().getPartyClass());
        assertEquals("Member", req.getRepresentedParty().getPartyCode());
    }

    @Test
    public void validRepresentedPartyHeaderWithClassAndMember() throws Exception {
        final RestRequest req = new RestRequest(
                "GET",
                String.format("/r%d/Instance/Class/Member/SubSystem/ServiceCode", RestMessage.PROTOCOL_VERSION),
                "foo=bar",
                Arrays.asList(
                        new BasicHeader("X-Road-Client", "Instance/Class/Member/SubSystem"),
                        new BasicHeader("X-Road-Id", "42"),
                        new BasicHeader("X-Road-ServerId", "Instance/Class/Member/ServerCode"),
                        new BasicHeader("X-Road-Represented-Party", "Class/Member")),
                "xid"
        );
        assertEquals("Class", req.getRepresentedParty().getPartyClass());
        assertEquals("Member", req.getRepresentedParty().getPartyCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidRepresentedPartyHeader() throws Exception {
        final RestRequest req = new RestRequest(
                "GET",
                String.format("/r%d/Instance/Class/Member/SubSystem/ServiceCode", RestMessage.PROTOCOL_VERSION),
                "foo=bar",
                Arrays.asList(
                        new BasicHeader("X-Road-Client", "Instance/Class/Member/SubSystem"),
                        new BasicHeader("X-Road-Id", "42"),
                        new BasicHeader("X-Road-ServerId", "Instance/Class/Member/ServerCode"),
                        new BasicHeader("X-Road-Represented-Party", "Instance/Class/Member")),
                "xid"
        );
    }

}
