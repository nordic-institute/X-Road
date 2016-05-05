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
package ee.ria.xroad.monitor.executablelister;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/**
 * Created by janne on 6.11.2015.
 */
@Slf4j
public class ProcessListerTest {

    // FIXME: there should be a better way to do this with gradle. Seems to be the norm though elsewhere as well.
    private static final String RESOURCE_PATH = "src/test/resources/";

    private String processOutputString;

    /**
     * Before test handler
     */
    @Before
    public void setup() throws Exception {

        processOutputString = FileUtils.readFileToString(new File(RESOURCE_PATH + "processlist.txt"),
                StandardCharsets.UTF_8.toString());

        log.info("string=" + processOutputString);
    }

    @Test
    public void testProcessList() throws Exception {
        ProcessLister testProcessLister = new ProcessLister() {


            @Override
            ProcessOutputs executeProcess() throws IOException, InterruptedException {
                ProcessOutputs fakeOutputs = new ProcessOutputs();
                fakeOutputs.setOut(processOutputString);
                return fakeOutputs;
            }
        };
        ListedData<ProcessInfo> data = testProcessLister.list();
        assertEquals(11, data.getParsedData().size()); // no header row
        assertEquals(12, data.getJmxData().size()); // header row included

        ProcessInfo info = data.getParsedData().iterator().next();
        assertEquals("root", info.getUserId());
        assertEquals("7.0", info.getCpuLoad());
        assertEquals("marras05", info.getStartTime());
        assertEquals("0.2", info.getMemUsed());
        assertEquals("1", info.getProcessId());
        assertEquals("init", info.getCommand());

        String jmxData = data.getJmxData().get(1);
        assertEquals("root      7.0 marras05  0.2  1 init", jmxData);
    }
}
