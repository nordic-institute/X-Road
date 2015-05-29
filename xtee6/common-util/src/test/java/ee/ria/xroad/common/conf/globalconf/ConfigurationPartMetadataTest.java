package ee.ria.xroad.common.conf.globalconf;

import java.io.ByteArrayInputStream;

import org.joda.time.DateTime;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests to verify writing and reading of metadata.
 */
public class ConfigurationPartMetadataTest {

    /**
     * Test that ensures metadata is written and read correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void writeReadMetadata() throws Exception {
        ConfigurationPartMetadata write = new ConfigurationPartMetadata();
        write.setContentIdentifier("SHARED-PARAMETERS");
        write.setInstanceIdentifier("FOO");
        write.setExpirationDate(new DateTime());

        ConfigurationPartMetadata read = ConfigurationPartMetadata.read(
                new ByteArrayInputStream(write.toByteArray()));

        assertEquals(write.getContentIdentifier(), read.getContentIdentifier());
        assertEquals(write.getInstanceIdentifier(),
                read.getInstanceIdentifier());
        assertEquals(write.getExpirationDate(), read.getExpirationDate());
    }

}
