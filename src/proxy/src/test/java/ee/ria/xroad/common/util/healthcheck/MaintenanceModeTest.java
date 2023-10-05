/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.net.URI;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Tests setting {@link HealthCheckPort} on and off maintenance mode
 */
public class MaintenanceModeTest {

    private static HealthCheckPort testPort;
    private static StoppableHealthCheckProvider testProvider;
    private static HttpGet healthCheckGet;
    private static CloseableHttpClient testClient;

    private static final int TEST_PORT_NUMBER = 23555;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    /**
     * Setup for all tests
     * @throws Exception throws an Exception if unable to setup
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        URI healthCheckURI = new URIBuilder()
                .setScheme("http")
                .setHost("localhost")
                .setPort(TEST_PORT_NUMBER)
                .build();

        healthCheckGet = new HttpGet(healthCheckURI);
        testClient = HttpClients.createDefault();

        testProvider = mock(StoppableCombinationHealthCheckProvider.class);
        testPort = new HealthCheckPort(testProvider, TEST_PORT_NUMBER);
        testPort.start();
    }

    /**
     * Teardown for all tests closing resources initialized prior to running the tests
     */
    @AfterClass
    public static void afterClassTearDown() {
        if (testClient != null) {
            try {
                testClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (testPort != null) {
            try {
                testPort.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Before
    public void setUp() {
        when(testProvider.get()).thenReturn(HealthCheckResult.OK);
    }

    @After
    public void tearDown() {
        reset(testProvider);
    }

    /**
     * Tests {@link HealthCheckPort} operation when it's in maintenance mode
     * - verifies that internal provider does not get called during maintenance mode
     * - verifies that HTTP status code 503 with the maintenance message is returned due to maintenance mode
     * @throws IOException client operation can throw an IOException that should fail the test
     */
    @Test
    public void maintenanceModeOnShouldPreventProviderCalls() throws IOException {
        testPort.setMaintenanceMode(true);
        try (CloseableHttpResponse response = testClient.execute(healthCheckGet)) {
            verify(testProvider, times(0)).get();
            assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatusLine().getStatusCode());
            HttpEntity responseEntity = response.getEntity();
            assertNotNull("HealthCheckPorts's response did not contain a message", responseEntity);
            String responseMessage = IOUtils.toString(responseEntity.getContent());
            assertThat("HealthCheckPorts's response did not contain maintenance message",
                    responseMessage, containsString(HealthCheckPort.MAINTENANCE_MESSAGE));
        }
    }

    /**
     * Tests {@link HealthCheckPort} operation when it's not in maintenance mode
     * - verifies that internal provider does get called when not in maintenance mode
     * - verifies that mock provider's OK result gets through HealthCheckPort as HTTP OK when not in maintenance mode
     * @throws IOException client operation can throw an IOException that should fail the test
     */
    @Test
    public void maintenanceModeOffShouldNotAffectProviderCalls() throws IOException {
        testPort.setMaintenanceMode(false);
        try (CloseableHttpResponse response = testClient.execute(healthCheckGet)) {
            verify(testProvider, times(1)).get();
            assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
        }
    }
}
