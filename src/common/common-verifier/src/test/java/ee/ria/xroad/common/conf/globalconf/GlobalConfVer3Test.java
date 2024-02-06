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

import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GlobalConfVer3Test {
    private static final String GOOD_CONF_DIR = "../common-util/src/test/resources/globalconf_good_v3";
    private static final Path GOOD_CONF_FILES = Paths.get(GOOD_CONF_DIR, "files");

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        GlobalConf.reset();
        System.setProperty(SystemProperties.CONFIGURATION_PATH, GOOD_CONF_DIR);

        createConfigurationFiles();

        GlobalConf.reload();
    }

    private static void createConfigurationFiles() throws IOException {
        List<String> confFiles = new ArrayList<>();
        File files = GOOD_CONF_FILES.toFile();

        confFiles.add(getConfFileName("bar", "shared-params.xml"));
        confFiles.add(getConfFileName("EE", "private-params.xml"));
        confFiles.add(getConfFileName("EE", "shared-params.xml"));
        confFiles.add(getConfFileName("foo_ver2", "private-params.xml"));
        confFiles.add(getConfFileName("foo_ver", "shared-params.xml"));

        FileUtils.writeLines(files, StandardCharsets.UTF_8.name(), confFiles);
    }

    private static String getConfFileName(String instanceIdentifier, String fileName) {
        return Paths.get(GOOD_CONF_DIR, instanceIdentifier, fileName).toAbsolutePath().normalize().toString();
    }

    @AfterClass
    public static void cleanUpAfterClass() {
        deleteConfigurationFiles();
    }

    private static void deleteConfigurationFiles() {
        try {
            Files.delete(GlobalConfVer3Test.GOOD_CONF_FILES);
        } catch (IOException e) {
            // Ignore.
        }
    }

    @Test
    public void isSubjectInGlobalGroup() {
        assertTrue(GlobalConf.isSubjectInGlobalGroup(
                ClientId.Conf.create("EE", "BUSINESS", "member1", "subsys"),
                GlobalGroupId.Conf.create("EE", "Test group"))
        );
        assertTrue(GlobalConf.isSubjectInGlobalGroup(
                ClientId.Conf.create("EE", "BUSINESS", "member2"),
                GlobalGroupId.Conf.create("EE", "Test group"))
        );
        assertFalse(GlobalConf.isSubjectInGlobalGroup(
                ClientId.Conf.create("EE", "BUSINESS", "member2", "subsys"),
                GlobalGroupId.Conf.create("EE", "Test group"))
        );
        assertFalse(GlobalConf.isSubjectInGlobalGroup(
                ClientId.Conf.create("EE", "BUSINESS", "member2"),
                GlobalGroupId.Conf.create("non-existent-instance", "non-existent-group"))
        );
    }
}
