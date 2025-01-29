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
package org.niis.xroad.proxy.core.opmonitoring;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.metadata.Endpoint;

import org.junit.Test;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.CLIENT;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.PRODUCER;

public class SavedServiceEndpointTest {

    private final ServerConfProvider serverConfProvider = mock(ServerConfProvider .class);
    private final SavedServiceEndpoint savedServiceEndpoint = new SavedServiceEndpoint(serverConfProvider);

    @Test
    public void verifyInProducerSideSavedEndpointPathIsReturned() {
        assertEquals("/pets/*/cat", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/pets/123/cat")));
        assertEquals("/pets/*/cat", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/pets/999/cat")));
        assertEquals("/pets/*/cat/*", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/pets/123/cat/1")));
        assertEquals("/pets/*/cat/*", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/pets/123/cat/9")));
    }

    @Test
    public void verifyInClientSideThenQueriedEndpointPathIsReturned() {
        assertEquals("/pets/123/cat", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(CLIENT, "/pets/123/cat")));
        assertEquals("/pets/999/cat", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(CLIENT, "/pets/999/cat")));
        assertEquals("/pets/123/cat/1", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(CLIENT, "/pets/123/cat/1")));
        assertEquals("/pets/123/cat/9", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(CLIENT, "/pets/123/cat/9")));
    }

    @Test
    public void whenSavedEndpointNotExistsThenQueriedPathIsReturned() {
        assertEquals("/pets", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/pets")));
        assertEquals("/pets/cat/1", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/pets/cat/1")));
        assertEquals("/docs/1", savedServiceEndpoint.getPathIfExists(getOpMonitoringData(PRODUCER, "/docs/1")));
    }

    @Test
    public void whenQueriedPathIsNullThenReturnedPathIsAlsoNull() {
        assertNull(savedServiceEndpoint.getPathIfExists(new OpMonitoringData(PRODUCER, 100)));
    }

    @Test
    public void whenQueriedPathIsNotNullButExceptionIsCatchedThenQueriedPathIsReturned() {
        var opMonitoringData = new OpMonitoringData(PRODUCER, 100);
        opMonitoringData.setRestPath("/path");
        assertEquals("/path", savedServiceEndpoint.getPathIfExists(opMonitoringData));
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
        when(serverConfProvider.getServiceEndpoints(opMonitoringData.getServiceId())).thenReturn(List.of(endpoint1, endpoint2, endpoint3));
    }
}
