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
package ee.ria.xroad.common.messagelog;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests to verify correct hash string parsing behavior.
 */
public class HashTest {

    /**
     * Test to ensure hash string object is created correctly.
     */
    @Test
    public void parseSuccessfully() {
        String hashString = "SHA-256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4"
                + "649b934ca495991b7852b855";

        Hash h = new Hash(hashString);
        assertEquals("SHA-256", h.getAlgoId());
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4"
                + "649b934ca495991b7852b855", h.getHashValue());
        assertEquals(hashString, h.toString());
    }

    /**
     * Test to ensure malformed hash strings result in an exception.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void shouldNotParseMalformedHashStrings() throws Exception {
        assertParseFailed(null);
        assertParseFailed("foobar");
        assertParseFailed(":");
        assertParseFailed(":foobar");
        assertParseFailed("foobar:");
    }

    private static void assertParseFailed(String hashString) {
        try {
            new Hash(hashString);
            fail("Should fail to parse " + hashString);
        } catch (IllegalArgumentException expectedException) {
            System.out.println("Expected exception received "
                    + expectedException.getClass());
        }
    }
}
