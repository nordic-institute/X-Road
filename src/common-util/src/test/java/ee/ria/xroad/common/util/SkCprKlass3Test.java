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
package ee.ria.xroad.common.util;

import org.bouncycastle.asn1.x500.X500Name;
import org.junit.Test;

import static ee.ria.xroad.common.util.SkCprKlass3.getSubjectIdentifier;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests the name extractor.
 */
public class SkCprKlass3Test {

    /**
     * Tests whether subject name parts are read correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readPartsSuccessfully() throws Exception {
        String[] parts;

        parts = parts("SERIALNUMBER=76543212");
        assertEquals("GOV", parts[0]);
        assertEquals("76543212", parts[1]);

        parts = parts("SERIALNUMBER=16543212");
        assertEquals("COM", parts[0]);
        assertEquals("16543212", parts[1]);

        parts = parts("SERIALNUMBER=86543212");
        assertEquals("NGO", parts[0]);
        assertEquals("86543212", parts[1]);

        parts = parts("SERIALNUMBER=96543212");
        assertEquals("NGO", parts[0]);
        assertEquals("96543212", parts[1]);
    }

    /**
     * Tests whether a invalid serial number length is detected.
     * @throws Exception in case of an expected error
     */
    @Test(expected = Exception.class)
    public void invalidLength() throws Exception {
        parts("SERIALNUMBER=123");
        fail();
    }

    /**
     * Tests whether a missing serial number is detected.
     * @throws Exception in case of an expected error
     */
    @Test(expected = Exception.class)
    public void missingSerialNumber() throws Exception {
        parts("S=7654321");
        fail();
    }

    /**
     * Tests whether an unknown serial number is detected.
     * @throws Exception in case of an expected error
     */
    @Test(expected = Exception.class)
    public void unknownSerialNumber() throws Exception {
        parts("SERIALNUMBER=s1234567");
        fail();
    }

    private static String[] parts(String dirName) throws Exception {
        return getSubjectIdentifier(new X500Name(dirName));
    }
}
