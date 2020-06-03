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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.util.ExpectedCodedException;

import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Paths;

import static ee.ria.xroad.common.ErrorCodes.X_OUTDATED_GLOBALCONF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests to verify configuration directories are read correctly.
 */
public class ConfigurationDirectoryTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Test to ensure a correct configuration directory is read properly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readDirectoryV2() throws Exception {
        ConfigurationDirectoryV2 dir = new ConfigurationDirectoryV2("src/test/resources/globalconf_good_v2");

        assertEquals("EE", dir.getInstanceIdentifier());

        PrivateParametersV2 p = dir.getPrivate("foo");

        assertNotNull(p);
        assertEquals("foo", p.getInstanceIdentifier());

        SharedParametersV2 s = dir.getShared("foo");

        assertNotNull(s);
        assertEquals("foo", s.getInstanceIdentifier());

        dir.getShared("foo"); // intentional

        assertNull(dir.getPrivate("bar"));
        assertNotNull(dir.getShared("bar"));
        assertNull(dir.getShared("xxx"));
    }

    /**
     * Test to ensure an empty configuration directory is read properly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readEmptyDirectoryV2() throws Exception {
        ConfigurationDirectoryV2 dir = new ConfigurationDirectoryV2("src/test/resources/globalconf_empty");

        assertNull(dir.getPrivate("foo"));
        assertNull(dir.getShared("foo"));
    }

    /**
     * Test to ensure that reading of a malformed configuration fails.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readMalformedDirectoryV2() throws Exception {
        ConfigurationDirectoryV2 dir = new ConfigurationDirectoryV2("src/test/resources/globalconf_malformed");

        assertNull(dir.getPrivate("foo"));
        assertNull(dir.getShared("foo"));
    }

    /**
     * Test to ensure that reading of an outdated configuration fails.
     */
    @Test
    public void readExpiredDirectoryV2() {
        thrown.expectError(X_OUTDATED_GLOBALCONF);

        ConfigurationDirectoryV2.verifyUpToDate(Paths.get("src/test/resources/globalconf_expired/foo/"
                + ConfigurationDirectoryV2.PRIVATE_PARAMETERS_XML));
    }
}
