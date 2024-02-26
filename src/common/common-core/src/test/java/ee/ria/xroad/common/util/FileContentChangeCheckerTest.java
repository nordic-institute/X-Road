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
package ee.ria.xroad.common.util;

import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit test for FileContentChangeChecker.
 */
public class FileContentChangeCheckerTest {

    /**
     * Tests whether the file content changes are detected
     * @throws Exception if error occurs
     */
    @Test
    public void checkChanges() throws Exception {
        FileContentChangeChecker checker = new FileContentChangeChecker("mock") {
            @Override
            protected String calculateConfFileChecksum(File file) throws Exception {
                return "foo";
            }
        };

        FileContentChangeChecker spy = spy(checker);

        assertFalse("Should not have changed yet", spy.hasChanged());

        when(spy.calculateConfFileChecksum(Mockito.any())).thenReturn("bar");

        assertTrue("Should have changed", spy.hasChanged());

        when(spy.calculateConfFileChecksum(Mockito.any())).thenReturn("foo");

        assertTrue("Should have changed", spy.hasChanged());
    }
}
