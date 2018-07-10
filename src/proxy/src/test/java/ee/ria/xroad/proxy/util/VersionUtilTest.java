package ee.ria.xroad.proxy.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link VersionUtilTest}
 */
public class VersionUtilTest {

    @Test
    public void testParseVersion() {
        assertEquals("unknown", VersionUtil.parseVersion(""));
        assertEquals("unknown", VersionUtil.parseVersion("foobar"));
        assertEquals("unknown", VersionUtil.parseVersion("111"));
        assertEquals("unknown",
                VersionUtil.parseVersion("error: cannot open Packages index using db5 - Permission denied (13)\n"
                        + "error: cannot open Packages database in /var/lib/rpm\n"
                        + "error: cannot open Packages database in /var/lib/rpm\n"
                        + "package xroad-proxy is not installed"));
        assertEquals("6.19.0-1", VersionUtil.parseVersion("6.19.0-1"));
        assertEquals("6.19.0-0.20180709122743git861f417",
                VersionUtil.parseVersion("6.19.0-0.20180709122743git861f417"));
    }
}
