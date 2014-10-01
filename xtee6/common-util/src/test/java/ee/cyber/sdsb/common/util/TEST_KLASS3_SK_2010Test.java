package ee.cyber.sdsb.common.util;

import org.bouncycastle.asn1.x500.X500Name;
import org.junit.Test;

import static ee.cyber.sdsb.common.util.TEST_KLASS3_SK_2010.getSubjectIdentifier;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TEST_KLASS3_SK_2010Test {

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

    @Test
    public void missingCountryCode() {
        try {
            parts("SERIALNUMBER=7654321");
            fail();
        } catch (Exception expected) {
        }
    }

    @Test
    public void missingSerialNumber() {
        try {
            parts("C=EE, S=7654321");
            fail();
        } catch (Exception expected) {
        }
    }

    @Test
    public void unknownSerialNumber() {
        try {
            parts("C=EE, S=666");
            fail();
        } catch (Exception expected) {
        }
    }

    @Test
    public void wrongCountryCode() {
        try {
            parts("C=SE, SERIALNUMBER=7654321");
            fail();
        } catch (Exception expected) {
        }
    }

    private static String[] parts(String dirName) throws Exception {
        return getSubjectIdentifier(new X500Name(dirName));
    }
}
