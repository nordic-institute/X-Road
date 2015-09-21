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
