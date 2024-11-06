/*
 * The MIT License
 *
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
package ee.ria.xroad.monitor.executablelister;

import ee.ria.xroad.monitor.JmxStringifiedData;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for package lister
 */
@Slf4j
class PackageListerTest {

    // FIXME: there should be a better way to do this with gradle. Seems to be the norm though elsewhere as well.
    private static final String RESOURCE_PATH = "src/test/resources/";

    private String packageOutputString;

    /**
     * Before test handler
     */
    @BeforeEach
    void setup() throws Exception {

        packageOutputString = FileUtils.readFileToString(new File(RESOURCE_PATH + "ubuntu-packagelist.txt"),
                StandardCharsets.UTF_8.toString());

        log.info("string=" + packageOutputString);
    }

    @Test
    void testProcessList() {
        Assumptions.assumeTrue(SystemUtils.IS_OS_LINUX, "AbstractExecListener does not support other operating systems.");

        PackageLister testPackageLister = new PackageLister() {
            @Override
            ProcessOutputs executeProcess() throws IOException, InterruptedException {
                ProcessOutputs fakeOutputs = new ProcessOutputs();
                fakeOutputs.setOut(packageOutputString);
                return fakeOutputs;
            }
        };
        JmxStringifiedData<PackageInfo> data = testPackageLister.list();
        assertEquals(8, data.getDtoData().size());
        assertEquals(8, data.getJmxStringData().size()); // header row included

        PackageInfo info = data.getDtoData().iterator().next();
        assertEquals("account-plugin-aim", info.getName());
        assertEquals("3.8.6-0ubuntu9.2", info.getVersion());

        String jmxData = data.getJmxStringData().get(0);
        assertEquals("account-plugin-aim/3.8.6-0ubuntu9.2", jmxData);
    }
}
