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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests to verify configuration anchors are read correctly.
 */
public class ConfigurationAnchorTest {

    /**
     * Test to ensure the configuration anchor is read correctly from a file.
     */
    @Test
    public void readAnchorV2() {
        ConfigurationAnchor a = new ConfigurationAnchor(
                "src/test/resources/configuration-anchor1.xml");

        assertEquals("EE", a.getInstanceIdentifier());
        assertEquals(3, a.getLocations().size());

        ConfigurationLocation l = a.getLocations().get(0);
        assertEquals("http://www.bar.com/conf", l.getDownloadURL());

        String hash = "t7+jfR1wnsN1EBtBpCt/q8JIasg=";
        String hashAlgoId = "http://www.w3.org/2000/09/xmldsig#sha1";
        assertNotNull(l.getVerificationCert(hash, hashAlgoId));

        hash = "4HCV0OGOaz4mJauIZvrt7A3RdfM=";
        assertNotNull(l.getVerificationCert(hash, hashAlgoId));
    }

    /**
     * Test to ensure the equals method behaves as expected.
     */
    @Test
    public void equalsAnchorV2() {
        ConfigurationAnchor a = new ConfigurationAnchor(
                "src/test/resources/configuration-anchor1.xml");
        ConfigurationAnchor b = new ConfigurationAnchor(
                "src/test/resources/configuration-anchor1.xml");
        ConfigurationAnchor c = new ConfigurationAnchor(
                "src/test/resources/configuration-anchor2.xml");
        assertEquals(a, a);
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(b, c);
    }
}
