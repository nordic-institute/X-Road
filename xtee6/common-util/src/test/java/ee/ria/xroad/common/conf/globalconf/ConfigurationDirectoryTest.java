/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.nio.file.Paths;

import org.junit.Rule;
import org.junit.Test;

import ee.ria.xroad.common.util.ExpectedCodedException;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static ee.ria.xroad.common.ErrorCodes.X_OUTDATED_GLOBALCONF;
import static org.junit.Assert.*;

/**
 * Tests to verify configuration directories are read correctly.
 */
public class ConfigurationDirectoryTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Test to ensure a correct configuration directory is read properly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readDirectory() throws Exception {
        ConfigurationDirectory dir = new ConfigurationDirectory(
                "src/test/resources/globalconf_good");

        assertEquals("EE", dir.getInstanceIdentifier());

        PrivateParameters p = dir.getPrivate("foo");
        assertNotNull(p);
        assertEquals("foo", p.getInstanceIdentifier());

        SharedParameters s = dir.getShared("foo");
        assertNotNull(s);
        assertEquals("foo", s.getInstanceIdentifier());
        dir.getShared("foo"); // intentional

        assertNull(dir.getPrivate("bar"));
        assertNotNull(dir.getShared("bar"));

        assertNull(dir.getShared("xxx"));
    }

    /**
     * Test to ensure an empty configuration directory is read properly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readEmptyDirectory() throws Exception {
        ConfigurationDirectory dir = new ConfigurationDirectory(
                "src/test/resources/globalconf_empty");
        assertNull(dir.getPrivate("foo"));
        assertNull(dir.getShared("foo"));
    }

    /**
     * Test to ensure that reading of a malformed configuration fails.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readMalformedDirectory() throws Exception {
        thrown.expectError(X_MALFORMED_GLOBALCONF);

        ConfigurationDirectory dir = new ConfigurationDirectory(
                "src/test/resources/globalconf_malformed");
        assertNull(dir.getPrivate("foo"));
        assertNull(dir.getShared("foo"));
    }

    /**
     * Test to ensure that reading of an outdated configuration fails.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readExpiredDirectory() throws Exception {
        thrown.expectError(X_OUTDATED_GLOBALCONF);

        ConfigurationDirectory.verifyUpToDate(
                Paths.get("src/test/resources/globalconf_expired/foo/"
                        + ConfigurationDirectory.PRIVATE_PARAMETERS_XML));
    }
}
