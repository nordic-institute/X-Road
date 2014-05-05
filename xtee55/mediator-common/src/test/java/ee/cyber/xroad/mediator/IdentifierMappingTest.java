package ee.cyber.xroad.mediator;

import org.junit.Test;

import ee.cyber.sdsb.common.identifier.ClientId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class IdentifierMappingTest {

    @Test
    public void shouldReadMappingsCorrectly() {
        System.setProperty(MediatorSystemProperties.IDENTIFIER_MAPPING_FILE,
                "src/test/resources/identifiermapping.xml");

        IdentifierMappingImpl mapping = new IdentifierMappingImpl();

        ClientId expectedId =
                ClientId.create("EE", "BUSINESS", "consumer", "ss1");
        ClientId actualId = mapping.getClientId("consumer");
        assertEquals(expectedId, actualId);

        String expectedShortName = "producer";
        String actualShortName = mapping.getShortName(
                ClientId.create("EE", "BUSINESS", "producer"));
        assertEquals(expectedShortName, actualShortName);
    }

    @Test
    public void shouldNotFindNonExistingMapping() {
        System.setProperty(MediatorSystemProperties.IDENTIFIER_MAPPING_FILE,
                "src/test/resources/identifiermapping.xml");

        IdentifierMappingImpl mapping = new IdentifierMappingImpl();

        ClientId clientId = mapping.getClientId("foobarbaz");
        assertNull(clientId);

        String shortName = mapping.getShortName(ClientId.create("a", "b", "c"));
        assertNull(shortName);
    }

}
