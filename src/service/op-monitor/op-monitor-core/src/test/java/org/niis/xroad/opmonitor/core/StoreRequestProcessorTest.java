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
package org.niis.xroad.opmonitor.core;

import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.common.util.RequestWrapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.StoreOpMonitoringDataRequest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.PRODUCER;

class StoreRequestProcessorTest extends BaseTestUsingDB {

    private final HealthDataMetrics healthDataMetrics = new HealthDataMetrics(OP_MONITOR_PROPERTIES);
    private final HealthDataRegistry healthDataRegistry = new HealthDataRegistry(healthDataMetrics);
    private final OperationalDataStore operationalDataStore =
            new OperationalDataStore(operationalDataRecordManager, healthDataMetrics, healthDataRegistry);

    @BeforeEach
    void cleanDatabase() {
        DATABASE_CTX.doInTransaction(session -> session.createMutationQuery("delete OperationalDataRecordEntity").executeUpdate());
    }

    @Test
    void restStoreRequestPersistsRecordsAndUpdatesHealthCounters() throws Exception {
        var data = OpMonitoringDataFixtures.fullyPopulated(PRODUCER);

        new StoreRequestProcessor(jsonRequest(data), operationalDataStore).process();

        assertThat(operationalDataRecordManager.queryAllRecords().getRecords()).singleElement()
                .usingRecursiveComparison()
                .ignoringFields("id", "monitoringDataTs")
                .isEqualTo(OpMonitoringDataFixtures.viaRestJson(data));
        var serviceId = HealthDataMetricsUtil.getServiceId(OpMonitoringDataFixtures.viaRestJson(data));
        assertThat(HealthDataMetricsUtil.findCounter(healthDataRegistry.getRegistry(),
                HealthDataMetricsUtil.getRequestCounterName(serviceId, true)).getCount()).isEqualTo(1);
    }

    @Test
    void restStoreRequestWithBlankBodyIsRejected() {
        var request = mock(RequestWrapper.class);
        when(request.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));

        assertThatThrownBy(() -> new StoreRequestProcessor(request, operationalDataStore).process())
                .hasMessageContaining("No data was found");
        assertThat(operationalDataRecordManager.queryAllRecords().getRecords()).isEmpty();
    }

    static RequestWrapper jsonRequest(OpMonitoringData data) {
        var body = new StoreOpMonitoringDataRequest();
        body.addRecord(data.getData());
        String json = JsonUtils.getObjectWriter().writeValueAsString(body);
        var request = mock(RequestWrapper.class);
        when(request.getInputStream()).thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        return request;
    }
}
