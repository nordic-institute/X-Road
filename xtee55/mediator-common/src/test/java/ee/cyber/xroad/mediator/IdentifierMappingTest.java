package ee.cyber.xroad.mediator;

import org.junit.Test;

import ee.ria.xroad.common.identifier.ClientId;

import static ee.cyber.xroad.mediator.IdentifierMapping.getInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests to verify correct identifier mapping parsing behavior.
 */
public class IdentifierMappingTest {

    /**
     * Test to ensure identifier mapping is being read correctly.
     */
    @Test
    public void shouldReadMappingsCorrectly() {
        System.setProperty(MediatorSystemProperties.IDENTIFIER_MAPPING_FILE,
                "src/test/resources/identifiermapping.xml");

        ClientId expectedId =
                ClientId.create("EE", "BUSINESS", "consumer", "ss1");
        ClientId actualId = getInstance().getClientId("consumer");
        assertEquals(expectedId, actualId);

        String expectedShortName = "producer";
        String actualShortName = getInstance().getShortName(
                ClientId.create("EE", "BUSINESS", "producer"));
        assertEquals(expectedShortName, actualShortName);
    }

    /**
     * Test to ensure correct behavior in case of a non-existing identifier mapping.
     */
    @Test
    public void shouldNotFindNonExistingMapping() {
        System.setProperty(MediatorSystemProperties.IDENTIFIER_MAPPING_FILE,
                "src/test/resources/identifiermapping.xml");

        ClientId clientId = getInstance().getClientId("foobarbaz");
        assertNull(clientId);

        String shortName =
                getInstance().getShortName(ClientId.create("a", "b", "c"));
        assertNull(shortName);
    }

}
