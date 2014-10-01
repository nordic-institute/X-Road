package ee.cyber.sdsb.common.util;

import java.io.StringReader;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PrefixedPropertiesTest {

    @Test
    public void readPrefixedProperties() throws Exception {
        String properties = "foo.bar.a1 = bar1\nfoo.bar.a2 = bar2\n"
                + "bar.xxx = ignoreme";

        PrefixedProperties p = new PrefixedProperties("foo.");
        p.load(new StringReader(properties));

        assertEquals("bar1", p.get("bar.a1"));
        assertEquals("bar2", p.get("bar.a2"));
        assertNull(p.get("bar.xxx"));
    }

}
