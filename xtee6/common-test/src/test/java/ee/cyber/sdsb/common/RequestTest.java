package ee.cyber.sdsb.common;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import ee.cyber.sdsb.common.Request.RequestTag;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static org.junit.Assert.assertEquals;

public class RequestTest {
    private static final String TEMPLATES_DIR = "src/main/resources";

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
                TEMPLATES_DIR + File.separator + "sdsbDoclitRequest.st"));

        Request request =
                new Request(template, client, service, id, content, false);

        // When
        String xmlFromRequest = request.toXml();
        System.out.println("XML from request: " + xmlFromRequest);

        // Then
        String expectedRequest = FileUtils.readFileToString(new File(
                "src/test/resources/sdsbDoclit2.request"));
        assertEquals(expectedRequest, xmlFromRequest);
    }

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
                new Request(template, client, service, id, content, false);

        // When
        String xmlFromRequest = request.toXml();
        System.out.println("XML from request: " + xmlFromRequest);

        // Then
        String expectedRequest = FileUtils.readFileToString(new File(
                "src/test/resources/v5DoclitWithVersion.request"));
        assertEquals(expectedRequest, xmlFromRequest);
    }

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
                new Request(template, client, service, id, content, false);

        // When
        String xmlFromRequest = request.toXml();
        System.out.println("XML from request: " + xmlFromRequest);

        // Then
        String expectedRequest = FileUtils.readFileToString(new File(
                "src/test/resources/v5DoclitWithoutVersion.request"));
        assertEquals(expectedRequest, xmlFromRequest);
    }
}
