/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
                new Request(template, client, service, id, content, false);

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
                new Request(template, client, service, id, content, false);

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
