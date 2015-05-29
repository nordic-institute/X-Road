package ee.ria.xroad.common.util;

import org.junit.Test;

import static ee.ria.xroad.common.util.PasswordStore.getPassword;
import static ee.ria.xroad.common.util.PasswordStore.storePassword;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests to verify
 */
public class PasswordStoreTest {

    /**
     * Run tests.
     * @throws Exception in case of unexpected errors
     */
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
