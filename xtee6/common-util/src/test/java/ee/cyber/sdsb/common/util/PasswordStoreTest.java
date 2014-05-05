package ee.cyber.sdsb.common.util;

import org.junit.Test;

import static ee.cyber.sdsb.common.util.PasswordStore.getPassword;
import static ee.cyber.sdsb.common.util.PasswordStore.storePassword;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PasswordStoreTest {
    @Test
    public void runTest() throws Exception {
        getPassword("foo"); // Just check if get on empty DB works.

        storePassword("foo", null);
        storePassword("bar", null);

        assertNull(getPassword("foo"));

        storePassword("foo", "fooPwd".toCharArray());
        storePassword("bar", "barPwd".toCharArray());

        assertEquals("fooPwd", new String(getPassword("foo")));
        assertEquals("barPwd", new String(getPassword("bar")));

        storePassword("foo", null);
        assertNull(getPassword("foo"));
        assertEquals("barPwd", new String(getPassword("bar")));
    }
}
