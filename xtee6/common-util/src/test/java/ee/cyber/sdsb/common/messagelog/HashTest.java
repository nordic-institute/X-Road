package ee.cyber.sdsb.common.messagelog;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class HashTest {

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
        }
    }
}
