package ee.ria.xroad.common.util;

import org.bouncycastle.asn1.x500.X500Name;
import org.junit.Test;

import static ee.ria.xroad.common.util.TestKlass3Sk2010.getSubjectIdentifier;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests the name extractor.
 */
public class TestKlass3Sk2010Test {

    /**
     * Tests whether subject name parts are read correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void readPartsSuccessfully() throws Exception {
        String[] parts;

        parts = parts("C=EE, SERIALNUMBER=7654321");
        assertEquals("GOV", parts[0]);
        assertEquals("7654321", parts[1]);

        parts = parts("C=EE, SERIALNUMBER=1654321");
        assertEquals("COM", parts[0]);
        assertEquals("1654321", parts[1]);

        parts = parts("C=EE, SERIALNUMBER=8654321");
        assertEquals("COM", parts[0]);
        assertEquals("8654321", parts[1]);

        parts = parts("C=EE, SERIALNUMBER=9654321");
        assertEquals("COM", parts[0]);
        assertEquals("9654321", parts[1]);
    }

    /**
     * Tests whether a missing country code is detected.
     * @throws Exception in case of an expected error
     */
    @Test(expected = Exception.class)
    public void missingCountryCode() throws Exception {
        parts("SERIALNUMBER=7654321");
        fail();
    }

    /**
     * Tests whether a missing serial number is detected.
     * @throws Exception in case of an expected error
     */
    @Test(expected = Exception.class)
    public void missingSerialNumber() throws Exception {
        parts("C=EE, S=7654321");
        fail();
    }

    /**
     * Tests whether an unknown serial number is detected.
     * @throws Exception in case of an expected error
     */
    @Test(expected = Exception.class)
    public void unknownSerialNumber() throws Exception {
        parts("C=EE, S=666");
        fail();
    }

    /**
     * Tests whether an unknown country code is detected.
     * @throws Exception in case of an expected error
     */
    @Test(expected = Exception.class)
    public void wrongCountryCode() throws Exception {
        parts("C=SE, SERIALNUMBER=7654321");
        fail();
    }

    private static String[] parts(String dirName) throws Exception {
        return getSubjectIdentifier(new X500Name(dirName));
    }
}
