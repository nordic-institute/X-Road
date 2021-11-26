/**
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
package org.niis.xroad.securityserver.restapi.converter;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.dto.AnchorFile;
import org.niis.xroad.securityserver.restapi.openapi.model.Anchor;

import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * Test AnchorConverter
 */
public class AnchorConverterTest {

    private AnchorConverter anchorConverter;

    private static final String ANCHOR_HASH =
            "CE:2C:A4:FB:BB:67:26:0F:6C:E9:7F:9B:CB:73:50:1F:40:43:2A:1A:2C:4E:5D:A6:F9:F5:0D:D1";

    private static final String CREATED_AT = "2019-04-28T09:03:31.841Z";

    private static final Long CREATED_AT_MILLIS = 1556442211841L;

    @Before
    public void setup() {
        anchorConverter = new AnchorConverter();
    }

    @Test
    public void convertAnchor() {
        AnchorFile anchorFile = new AnchorFile(ANCHOR_HASH);
        anchorFile.setCreatedAt(new Date(CREATED_AT_MILLIS).toInstant().atOffset(ZoneOffset.UTC));

        Anchor anchor = anchorConverter.convert(anchorFile);

        assertEquals(ANCHOR_HASH, anchorFile.getHash());
        assertEquals(CREATED_AT, anchorFile.getCreatedAt().toString());
    }
}
