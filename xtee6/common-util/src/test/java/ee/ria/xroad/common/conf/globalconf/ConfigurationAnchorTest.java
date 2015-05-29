package ee.ria.xroad.common.conf.globalconf;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests to verify configuration anchors are read correctly.
 */
public class ConfigurationAnchorTest {

    /**
     * Test to ensure the configuration anchor is read correctly from a file.
     */
    @Test
    public void readFromConf() {
        ConfigurationAnchor a = new ConfigurationAnchor(
                "src/test/resources/configuration-anchor1.xml");

        assertEquals("EE", a.getInstanceIdentifier());
        assertEquals(1, a.getLocations().size());

        ConfigurationLocation l = a.getLocations().get(0);
        assertEquals("http://www.bar.com/conf", l.getDownloadURL());

        String hash = "t7+jfR1wnsN1EBtBpCt/q8JIasg=";
        String hashAlgoId = "http://www.w3.org/2000/09/xmldsig#sha1";
        assertNotNull(l.getVerificationCert(hash, hashAlgoId));

        hash = "4HCV0OGOaz4mJauIZvrt7A3RdfM=";
        assertNotNull(l.getVerificationCert(hash, hashAlgoId));
    }

    /**
     * Test to ensure the equals method behaves as expected.
     */
    @Test
    public void equals() {
        ConfigurationAnchor a = new ConfigurationAnchor(
                "src/test/resources/configuration-anchor1.xml");
        ConfigurationAnchor b = new ConfigurationAnchor(
                "src/test/resources/configuration-anchor1.xml");
        ConfigurationAnchor c = new ConfigurationAnchor(
                "src/test/resources/configuration-anchor2.xml");
        assertEquals(a, a);
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(b, c);
    }
}
