/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.niis.xroad.restapi.service.ProcessFailedException.PROCESS_FAILED;

public class ExternalProcessRunnerTest {

    public static final String MOCK_SUCCESS_SCRIPT = "src/test/resources/script/success.sh";
    public static final String MOCK_FAIL_SCRIPT = "src/test/resources/script/fail.sh";
    public static final String NON_EXISTING_SCRIPT = "/path/to/non/existing/script.sh";
    public static final String SCRIPT_ARGS = "args";

    private final ExternalProcessRunner externalProcessRunner = new ExternalProcessRunner();

    @Test
    public void executeSuccess() {
        try {
            externalProcessRunner.executeAndThrowOnFailure(MOCK_SUCCESS_SCRIPT, SCRIPT_ARGS);
        } catch (Exception e) {
            fail("should not throw exceptions");
        }
    }

    @Test
    public void executeScriptFail() throws Exception {
        try {
            externalProcessRunner.executeAndThrowOnFailure(MOCK_FAIL_SCRIPT, SCRIPT_ARGS);
        } catch (ProcessFailedException e) {
            assertEquals(PROCESS_FAILED, e.getErrorDeviation().getCode());
        }
    }

    @Test
    public void executeError() throws Exception {
        try {
            externalProcessRunner.executeAndThrowOnFailure(NON_EXISTING_SCRIPT, SCRIPT_ARGS);
        } catch (ProcessNotExecutableException e) {
            assertTrue(e.getCause() instanceof IOException);
        }
    }
}
