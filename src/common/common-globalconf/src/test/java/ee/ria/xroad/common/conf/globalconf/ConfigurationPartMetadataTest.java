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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.util.TimeUtils;

import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertEquals;

/**
 * Tests to verify writing and reading of metadata.
 */
public class ConfigurationPartMetadataTest {

    /**
     * Test that ensures metadata is written and read correctly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void writeReadMetadata() throws Exception {
        ConfigurationPartMetadata write = new ConfigurationPartMetadata();
        write.setContentIdentifier("SHARED-PARAMETERS");
        write.setInstanceIdentifier("FOO");
        write.setExpirationDate(TimeUtils.offsetDateTimeNow());

        final byte[] bytes = write.toJson();
        ConfigurationPartMetadata read = ConfigurationPartMetadata.read(
                new ByteArrayInputStream(bytes));

        assertEquals(write.getContentIdentifier(), read.getContentIdentifier());
        assertEquals(write.getInstanceIdentifier(),
                read.getInstanceIdentifier());
        assertEquals(write.getExpirationDate(), read.getExpirationDate());
    }

}
