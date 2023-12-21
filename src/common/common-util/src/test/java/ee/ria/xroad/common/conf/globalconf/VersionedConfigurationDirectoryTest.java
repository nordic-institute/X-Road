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

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests to verify configuration directories are read correctly.
 */
public class VersionedConfigurationDirectoryTest {

    /**
     * Test to ensure a correct configuration directory is read properly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readDirectoryV2() throws Exception {
        VersionedConfigurationDirectory dir = new VersionedConfigurationDirectory("src/test/resources/globalconf_good_v2");

        assertEquals("EE", dir.getInstanceIdentifier());

        PrivateParameters p = dir.findPrivate("foo").orElseThrow();

        assertEquals("foo", p.getInstanceIdentifier());

        SharedParameters s = dir.findShared("foo").orElseThrow();

        assertEquals("foo", s.getInstanceIdentifier());

        assertTrue(dir.findPrivate("bar").isEmpty());
        assertTrue(dir.findShared("bar").isPresent());
        assertTrue(dir.findShared("xxx").isEmpty());
    }

    /**
     * Test to ensure that the list of available configuration files excluding metadata and directories
     * is read properly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readConfigurationFilesV2() throws Exception {
        String rootDir = "src/test/resources/globalconf_good_v2";
        VersionedConfigurationDirectory dir = new VersionedConfigurationDirectory(rootDir);

        List<Path> configurationFiles = dir.getConfigurationFiles();

        assertFalse(pathExists(configurationFiles, rootDir + "/instance-identifier"));

        assertTrue(pathExists(configurationFiles, rootDir + "/bar/shared-params.xml"));
        assertFalse(pathExists(configurationFiles, rootDir + "/bar/shared-params.xml.metadata"));
        assertFalse(pathExists(configurationFiles, rootDir + "/bar/private-params.xml"));
        assertFalse(pathExists(configurationFiles, rootDir + "/bar/private-params.xml.metadata"));

        assertTrue(pathExists(configurationFiles, rootDir + "/EE/shared-params.xml"));
        assertFalse(pathExists(configurationFiles, rootDir + "/EE/shared-params.xml.metadata"));
        assertTrue(pathExists(configurationFiles, rootDir + "/EE/private-params.xml"));
        assertFalse(pathExists(configurationFiles, rootDir + "/EE/private-params.xml.metadata"));

        assertTrue(pathExists(configurationFiles, rootDir + "/foo/shared-params.xml"));
        assertFalse(pathExists(configurationFiles, rootDir + "/foo/shared-params.xml.metadata"));
        assertTrue(pathExists(configurationFiles, rootDir + "/foo/private-params.xml"));
        assertFalse(pathExists(configurationFiles, rootDir + "/foo/private-params.xml.metadata"));
    }

    /**
     * Test to ensure a correct configuration directory is read properly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readDirectoryContainingBothV3AndV2Configurations() throws Exception {
        VersionedConfigurationDirectory dir = new VersionedConfigurationDirectory("src/test/resources/globalconf_good_v3");

        assertEquals("EE", dir.getInstanceIdentifier());

        PrivateParameters p = dir.findPrivate("foo_v2").orElseThrow();

        assertEquals("foo_v2", p.getInstanceIdentifier());

        SharedParameters s = dir.findShared("foo_v2").orElseThrow();

        assertEquals("foo_v2", s.getInstanceIdentifier());

        assertTrue(dir.findPrivate("bar").isEmpty());
        assertTrue(dir.findShared("bar").isPresent());
        assertTrue(dir.findShared("xxx").isEmpty());
    }

    /**
     * Test to ensure that the list of available configuration files excluding metadata and directories
     * is read properly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readConfigurationFilesContainingBothV3AndV2() throws Exception {
        String rootDir = "src/test/resources/globalconf_good_v3";
        VersionedConfigurationDirectory dir = new VersionedConfigurationDirectory(rootDir);

        List<Path> configurationFiles = dir.getConfigurationFiles();

        assertFalse(pathExists(configurationFiles, rootDir + "/instance-identifier"));

        assertTrue(pathExists(configurationFiles, rootDir + "/bar/shared-params.xml"));
        assertFalse(pathExists(configurationFiles, rootDir + "/bar/shared-params.xml.metadata"));
        assertFalse(pathExists(configurationFiles, rootDir + "/bar/private-params.xml"));
        assertFalse(pathExists(configurationFiles, rootDir + "/bar/private-params.xml.metadata"));

        assertTrue(pathExists(configurationFiles, rootDir + "/EE/shared-params.xml"));
        assertFalse(pathExists(configurationFiles, rootDir + "/EE/shared-params.xml.metadata"));
        assertTrue(pathExists(configurationFiles, rootDir + "/EE/private-params.xml"));
        assertFalse(pathExists(configurationFiles, rootDir + "/EE/private-params.xml.metadata"));

        assertTrue(pathExists(configurationFiles, rootDir + "/foo_v2/shared-params.xml"));
        assertFalse(pathExists(configurationFiles, rootDir + "/foo_v2/shared-params.xml.metadata"));
        assertTrue(pathExists(configurationFiles, rootDir + "/foo_v2/private-params.xml"));
        assertFalse(pathExists(configurationFiles, rootDir + "/foo_v2/private-params.xml.metadata"));
    }

    /**
     * Test to ensure an empty configuration directory is read properly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readEmptyDirectory() throws Exception {
        VersionedConfigurationDirectory dir = new VersionedConfigurationDirectory("src/test/resources/globalconf_empty");

        assertTrue(dir.findPrivate("foo").isEmpty());
        assertTrue(dir.findShared("foo").isEmpty());
    }

    /**
     * Test to ensure that reading of a malformed configuration fails.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readMalformedDirectory() throws Exception {
        VersionedConfigurationDirectory dir = new VersionedConfigurationDirectory("src/test/resources/globalconf_malformed");

        assertTrue(dir.findPrivate("foo").isEmpty());
        assertTrue(dir.findShared("foo").isEmpty());
    }

    private boolean pathExists(List<Path> paths, String path) {
        return null != paths.stream()
                .filter(p -> (p.getParent() + "/" + p.getFileName()).equals(path))
                .findAny()
                .orElse(null);
    }

}
