/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.util.process;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExternalProcessRunnerTest {

    public static final String MOCK_SUCCESS_SCRIPT = "src/test/resources/script/success.sh";
    public static final String MOCK_FAIL_SCRIPT = "src/test/resources/script/fail.sh";
    public static final String NON_EXISTING_SCRIPT = "/path/to/non/existing/script.sh";
    public static final String SCRIPT_ARGS = "args";

    private final ExternalProcessRunner externalProcessRunner = new ExternalProcessRunner();

    @Test
    public void executeAndThrowOnFailureSuccess() {
        try {
            ExternalProcessRunner.ProcessResult processResult =
                    externalProcessRunner.executeAndThrowOnFailure(MOCK_SUCCESS_SCRIPT, SCRIPT_ARGS);
            assertEquals(MOCK_SUCCESS_SCRIPT + " " + SCRIPT_ARGS, processResult.getCommandWithArgs());
            assertEquals(0, processResult.getExitCode());
            assertEquals("SUCCESS", processResult.getProcessOutput().get(0));
        } catch (Exception e) {
            fail("should not throw exceptions");
        }
    }

    @Test
    public void executeAndThrowOnFailureScriptFail() throws Exception {
        try {
            externalProcessRunner.executeAndThrowOnFailure(MOCK_FAIL_SCRIPT, SCRIPT_ARGS);
        } catch (ProcessFailedException e) {
            Assert.assertEquals("Failed to run command 'src/test/resources/script/fail.sh args' with output: \n FAIL", e.getMessage());
        }
    }

    @Test
    public void executeAndThrowOnFailureError() throws Exception {
        try {
            externalProcessRunner.executeAndThrowOnFailure(NON_EXISTING_SCRIPT, SCRIPT_ARGS);
        } catch (ProcessNotExecutableException e) {
            assertTrue(e.getCause() instanceof IOException);
        }
    }

    @Test
    public void executeSuccess() {
        try {
            ExternalProcessRunner.ProcessResult processResult =
                    externalProcessRunner.execute(MOCK_SUCCESS_SCRIPT, SCRIPT_ARGS);
            assertEquals(MOCK_SUCCESS_SCRIPT + " " + SCRIPT_ARGS, processResult.getCommandWithArgs());
            assertEquals(0, processResult.getExitCode());
            assertEquals("SUCCESS", processResult.getProcessOutput().get(0));
        } catch (Exception e) {
            fail("should not throw exceptions");
        }
    }

    @Test
    public void executeScriptFail() throws Exception {
        ExternalProcessRunner.ProcessResult processResult =
                externalProcessRunner.execute(MOCK_FAIL_SCRIPT, SCRIPT_ARGS);
        assertEquals(MOCK_FAIL_SCRIPT + " " + SCRIPT_ARGS, processResult.getCommandWithArgs());
        assertEquals(1, processResult.getExitCode());
        assertEquals("FAIL", processResult.getProcessOutput().get(0));
    }

    @Test
    public void executeError() throws Exception {
        try {
            externalProcessRunner.execute(NON_EXISTING_SCRIPT, SCRIPT_ARGS);
        } catch (ProcessNotExecutableException e) {
            assertTrue(e.getCause() instanceof IOException);
        }
    }
}
