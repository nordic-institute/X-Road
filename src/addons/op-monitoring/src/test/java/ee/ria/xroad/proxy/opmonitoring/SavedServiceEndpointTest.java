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
package ee.ria.xroad.proxy.opmonitoring;

import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.metadata.Endpoint;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;

import org.junit.Test;

import java.util.List;

import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.SecurityServerType.CLIENT;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.SecurityServerType.PRODUCER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SavedServiceEndpointTest {

    private final ServerConfProvider serverConfProvider = mock(ServerConfProvider .class);
    private final SavedServiceEndpoint savedServiceEndpoint = new SavedServiceEndpoint(serverConfProvider);

    @Test
    public void verifyInProducerSideSavedEndpointPathIsReturned() {
        assertEquals("/pets/*/cat", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/pets/123/cat")));
        assertEquals("/pets/*/cat", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/pets/999/cat")));
        assertEquals("/pets/*/cat/*", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/pets/123/cat/1")));
        assertEquals("/pets/*/cat/small", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/pets/123/cat/small")));
        assertEquals("/pets/first/cat/small", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/pets/first/cat/small")));
    }

    @Test
    public void verifyInClientSideThenNullPathIsReturned() {
        assertNull(savedServiceEndpoint.getPathIfExists(getOpMonitoringData(CLIENT, "/pets/123/cat")));
        assertNull(savedServiceEndpoint.getPathIfExists(getOpMonitoringData(CLIENT, "/pets/999/cat")));
        assertNull(savedServiceEndpoint.getPathIfExists(getOpMonitoringData(CLIENT, "/pets/123/cat/1")));
        assertNull(savedServiceEndpoint.getPathIfExists(getOpMonitoringData(CLIENT, "/pets/123/cat/9")));
    }

    @Test
    public void whenSavedEndpointNotExistsThenNullPathIsReturned() {
        assertNull(savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/pets")));
        assertNull(savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/pets/cat/1")));
        assertNull(savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/docs/1")));
    }

    @Test
    public void whenQueriedPathIsNullThenReturnedPathIsAlsoNull() {
        assertNull(savedServiceEndpoint.getPathIfExists(new OpMonitoringData(PRODUCER, 100)));
    }

    @Test
    public void whenQueriedPathIsNotNullButExceptionIsCaughtThenNullPathIsReturned() {
        var opMonitoringData = new OpMonitoringData(PRODUCER, 100);
        opMonitoringData.setRestPath("/path");
        assertNull(savedServiceEndpoint.getPathIfExists(opMonitoringData));
    }

    private OpMonitoringData getOpMonitoringData(OpMonitoringData.SecurityServerType type, String restPath) {
        var opMonitoringData = new OpMonitoringData(type, 100);
        opMonitoringData.setClientId(ClientId.Conf
                .create("TEST", "COV", "4321", "TestClient"));
        opMonitoringData.setServiceId(ServiceId.Conf
                .create("TEST", "COV", "1234", "TestService", "pets"));
        opMonitoringData.setRestMethod("GET");
        opMonitoringData.setRestPath(restPath);
        mockSavedEndpoints(opMonitoringData);
        return opMonitoringData;
    }

    private void mockSavedEndpoints(OpMonitoringData opMonitoringData) {
        var endpoint1 = new Endpoint();
        endpoint1.setMethod("GET");
        endpoint1.setPath("/pets/*/cat/*");
        var endpoint2 = new Endpoint();
        endpoint2.setMethod("POST");
        endpoint2.setPath("/pets/*/cat");
        var endpoint3 = new Endpoint();
        endpoint3.setMethod("GET");
        endpoint3.setPath("/pets/*/cat");
        var endpoint4 = new Endpoint();
        endpoint4.setMethod("GET");
        endpoint4.setPath("/pets/*/cat/small");
        var endpoint5 = new Endpoint();
        endpoint5.setMethod("GET");
        endpoint5.setPath("/pets/first/cat/small");
        var endpoint6 = new Endpoint();
        endpoint6.setMethod("*");
        endpoint6.setPath("/pets/first/cat/small");
        when(serverConfProvider.getServiceEndpoints(
                opMonitoringData.getServiceId())).thenReturn(List.of(endpoint1, endpoint2, endpoint3, endpoint4, endpoint5, endpoint6));
    }
}
