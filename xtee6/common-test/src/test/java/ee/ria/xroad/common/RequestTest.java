package ee.ria.xroad.common;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import ee.ria.xroad.common.Request.RequestTag;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import static org.junit.Assert.assertEquals;

/**
 * Tests to verify test requests are created as expected.
 */
public class RequestTest {
    private static final String TEMPLATES_DIR = "src/main/resources";

    /**
     * Test to ensure bodymass index test request creation is correct.
     * @throws IOException if request template file could not be read
     */
    @Test
    public void shouldCreateBodyMassIndexDoclitRequest() throws IOException {
        // Given
        ClientId client = ClientId.create(
                "EE",
                "riigiasutus",
                "consumer",
                "subClient");

        ServiceId service = ServiceId.create(
                "EE",
                "riigiasutus",
                "producer",
                "subService",
                "bodyMassIndex",
                "v1");

        String id = "1234567890";

        List<RequestTag> content = Arrays.asList(
                new RequestTag("height", Integer.toString(180)),
                new RequestTag("weight", Double.toString(80.1)));

        String template = FileUtils.readFileToString(new File(
                TEMPLATES_DIR + File.separator + "xroadDoclitRequest.st"));

        Request request =
                new Request(template, client, service, id, content);

        // When
        String xmlFromRequest = request.toXml();
        System.out.println("XML from request: " + xmlFromRequest);

        // Then
        String expectedRequest = FileUtils.readFileToString(new File(
                "src/test/resources/xroadDoclit2.request"));
        assertEquals(expectedRequest, xmlFromRequest);
    }

    /**
     * Test to ensure bodymass index test RPC request (with version) creation is correct.
     * @throws IOException if request template file could not be read
     */
    @Test
    public void shouldCreateBodyMassIndexRpcRequestWithVersion()
            throws IOException {
        // Given
        ClientId client = ClientId.create(
                "EE",
                "riigiasutus",
                "consumer",
                "subClient");

        ServiceId service = ServiceId.create(
                "EE",
                "riigiasutus",
                "producer",
                "subService",
                "bodyMassIndex", "v1");

        String id = "1234567890";

        List<RequestTag> content = Arrays.asList(
                new RequestTag("height", Integer.toString(180)),
                new RequestTag("weight", Double.toString(80.1)));

        String template = FileUtils.readFileToString(new File(
                TEMPLATES_DIR + File.separator + "v5DoclitRequest.st"));

        Request request =
                new Request(template, client, service, id, content);

        // When
        String xmlFromRequest = request.toXml();
        System.out.println("XML from request: " + xmlFromRequest);

        // Then
        String expectedRequest = FileUtils.readFileToString(new File(
                "src/test/resources/v5DoclitWithVersion.request"));
        assertEquals(expectedRequest, xmlFromRequest);
    }

    /**
     * Test to ensure bodymass index test RPC request (without version) creation is correct.
     * @throws IOException if request template file could not be read
     */
    @Test
    public void shouldCreateBodyMassIndexRpcRequestWithoutVersion()
            throws IOException {
        // Given
        ClientId client = ClientId.create(
                "EE",
                "riigiasutus",
                "consumer",
                "subClient");

        ServiceId service = ServiceId.create(
                "EE",
                "riigiasutus",
                "producer",
                "subService",
                "bodyMassIndex");

        String id = "1234567890";

        List<RequestTag> content = Arrays.asList(
                new RequestTag("height", Integer.toString(180)),
                new RequestTag("weight", Double.toString(80.1)));

        String template = FileUtils.readFileToString(new File(
                TEMPLATES_DIR + File.separator + "v5DoclitRequest.st"));

        Request request =
                new Request(template, client, service, id, content);

        // When
        String xmlFromRequest = request.toXml();
        System.out.println("XML from request: " + xmlFromRequest);

        // Then
        String expectedRequest = FileUtils.readFileToString(new File(
                "src/test/resources/v5DoclitWithoutVersion.request"));
        assertEquals(expectedRequest, xmlFromRequest);
    }
}
