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
package ee.ria.xroad.proxy.util;

import ee.ria.xroad.common.CodedException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;


/**
 * Test to verify MessageProcessorBase behavior
 */
@RunWith(Parameterized.class)
public class ValidateSoapActionTest {

    /**
     * Test parameters (header value, validity)
     */
    @Parameters(name = "{index}: <{0}>, valid: {1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {null, true},
                {"", true},
                {"\"\"", true},
                {"\"http://example.org/test\"", true},
                {"\"urn:foo\"", true},
                {"http://quotes/missing", false},
                {"\"http://extra/quote\"\"", false},
                {"\"spaces \"", false}
        });
    }

    @Parameter
    public String header;

    @Parameter(1)
    public boolean expected;

    @Test
    public void testValidateSoapAction() throws Exception {
        boolean valid = true;

        try {
            MessageProcessorBase.validateSoapActionHeader(header);
        } catch (CodedException e) {
            valid = false;
        }

        assertEquals(valid, expected);
    }

}
