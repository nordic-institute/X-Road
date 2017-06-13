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
package ee.ria.xroad.common.util.healthcheck;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.*;
import org.junit.rules.ExpectedException;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


/**
 * Tests setting {@link HealthCheckPort} on and off maintenance mode
 */
public class MaintenanceModeTest {

    private HealthCheckPort testPort;
    private StoppableHealthCheckProvider testProvider;
    private static HttpGet healthCheckGet;
    private static CloseableHttpClient testClient;

    private static final int testPortNumber = 8555;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @BeforeClass
    public static void setUpBeforeClass() throws URISyntaxException {
        URI healthCheckURI = new URIBuilder()
                .setScheme("http")
                .setHost("localhost")
                .setPort(testPortNumber)
                .build();

        healthCheckGet = new HttpGet(healthCheckURI);
        testClient = HttpClients.createDefault();
    }

    @Before
    public void setUp() {
        testProvider = mock(StoppableCombinationHealthCheckProvider.class);
        testPort = new HealthCheckPort(testProvider, testPortNumber);
    }

    /**
     * Tests {@link HealthCheckPort} operation when it's in maintenance mode
     * - verifies that internal provider does not get called during maintenance mode
     * - verifies that HTTP status code 503 is returned due to maintenance mode
     * @throws IOException client and response operation can throw an IOException that should fail the test
     */
    @Test
    public void maintenanceModeOnShouldPreventProviderCalls() throws IOException {
        try {
            testPort.start();
        } catch (Exception e) {
            fail("HealthCheckPort start() failed: " + e.getMessage());
        }
        testPort.setMaintenanceMode(true);
        try (CloseableHttpResponse response = testClient.execute(healthCheckGet)) {
            verify(testProvider, times(0)).get();
            assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatusLine().getStatusCode());
        }

    }

    /**
     * Tests {@link HealthCheckPort} operation when it's not in maintenance mode
     * - verifies that internal provider does get called when not in maintenance mode
     * - verifies that mock provider's OK result gets through HealthCheckPort as HTTP OK when not in maintenance mode
     * @throws IOException client and response operation can throw an IOException that should fail the test
     */
    @Test
    public void maintenanceModeOffShouldNotAffectProviderCalls() throws IOException {

        when(testProvider.get()).thenReturn(HealthCheckResult.OK);

        try {
            testPort.start();
        } catch (Exception e) {
            fail("HealthCheckPort start() failed: " + e.getMessage());
        }
        testPort.setMaintenanceMode(false);
        try (CloseableHttpResponse response = testClient.execute(healthCheckGet)) {
            verify(testProvider, times(1)).get();
            assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

    @After
    public void tearDown() throws IOException {
        if (testPort != null) {
            try {
                testPort.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @AfterClass
    public static void afterClassTearDown() {
        if (testClient != null) {
            try {
                testClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
