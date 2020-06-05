/*
 *  The MIT License
 *  Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 *  Copyright (c) 2018 Estonian Information System Authority (RIA),
 *  Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 *  Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.niis.xroad.restapi.util;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test FormatUtils
 */
public class FormatUtilsTest {
    private static final String VALID_HTTP_URL = "http://foo.bar:8080/baz";
    private static final String VALID_HTTPS_URL = "https://foo.bar:8080/baz";
    private static final String INVALID_HOST = "https://foobar.:8080/baz";
    private static final String INVALID_PROTOCOL = "file:///tmp/localfile.wsdl";
    private static final String MALFORMED_PROTOCOL = "htps://foo.bar:8080/baz";
    private static final String NON_ASCII_HOST = "https://テスト.ホスト:8080/baz";

    @Test
    public void validHttpUrl() {
        assertTrue(FormatUtils.isValidUrl(VALID_HTTP_URL));
    }

    @Test
    public void validHttpsUrl() {
        assertTrue(FormatUtils.isValidUrl(VALID_HTTPS_URL));
    }

    @Test
    public void validNonAsciiUrl() {
        assertTrue(FormatUtils.isValidUrl(NON_ASCII_HOST));
    }

    @Test
    public void invalidUrlHost() {
        assertFalse(FormatUtils.isValidUrl(INVALID_HOST));
    }

    @Test
    public void invalidUrlProtocol() {
        assertFalse(FormatUtils.isValidUrl(INVALID_PROTOCOL));
    }

    @Test
    public void malformedUrlProtocol() {
        assertFalse(FormatUtils.isValidUrl(MALFORMED_PROTOCOL));
    }

    @Test
    public void offsetDateTimeConversion() {
        Date now = new Date();
        assertEquals(now, FormatUtils.fromOffsetDateTimeToDate(FormatUtils.fromDateToOffsetDateTime(now)));
    }
}
