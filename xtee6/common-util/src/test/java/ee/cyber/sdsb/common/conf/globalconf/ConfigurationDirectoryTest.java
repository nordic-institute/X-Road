package ee.cyber.sdsb.common.conf.globalconf;

import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.util.ExpectedCodedException;

import static ee.cyber.sdsb.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static ee.cyber.sdsb.common.ErrorCodes.X_OUTDATED_GLOBALCONF;
import static org.junit.Assert.*;

public class ConfigurationDirectoryTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Test
    public void readDirectory() throws Exception {
        ConfigurationDirectory dir = new ConfigurationDirectory(
                "src/test/resources/globalconf_good");

        assertEquals("EE", dir.getInstanceIdentifier());

        PrivateParameters p = dir.getPrivate("foo");
        assertNotNull(p);
        assertEquals("foo", p.getInstanceIdentifier());

        SharedParameters s = dir.getShared("foo");
        assertNotNull(s);
        assertEquals("foo", s.getInstanceIdentifier());
        dir.getShared("foo"); // intentional

        assertNull(dir.getPrivate("bar"));
        assertNotNull(dir.getShared("bar"));

        assertNull(dir.getShared("xxx"));
    }

    @Test
    public void readEmptyDirectory() throws Exception {
        ConfigurationDirectory dir = new ConfigurationDirectory(
                "src/test/resources/globalconf_empty");
        assertNull(dir.getPrivate("foo"));
        assertNull(dir.getShared("foo"));
    }

    @Test
    public void readMalformedDirectory() throws Exception {
        thrown.expectError(X_MALFORMED_GLOBALCONF);

        ConfigurationDirectory dir = new ConfigurationDirectory(
                "src/test/resources/globalconf_malformed");
        assertNull(dir.getPrivate("foo"));
        assertNull(dir.getShared("foo"));
    }

    @Test
    public void readExpiredDirectory() throws Exception {
        thrown.expectError(X_OUTDATED_GLOBALCONF);

        ConfigurationDirectory dir = new ConfigurationDirectory(
                "src/test/resources/globalconf_expired");
        assertNull(dir.getPrivate("foo"));
    }
}
