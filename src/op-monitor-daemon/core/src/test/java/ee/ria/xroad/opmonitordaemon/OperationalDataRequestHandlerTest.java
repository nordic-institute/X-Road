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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.opmonitordaemon.message.GetSecurityServerOperationalDataResponseType;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Tests for verifying query request handler behavior.
 */
public class OperationalDataRequestHandlerTest extends BaseTestUsingDB {

    @Test
    public void checkOkSearchCriteriaOutputFields() {
        OperationalDataRequestHandler.checkOutputFields(Collections.emptySet());
        OperationalDataRequestHandler.checkOutputFields(Sets.newHashSet(
                "monitoringDataTs", "securityServerInternalIp"));
    }

    @Test
    public void checkInvalidSearchCriteriaOutputFields() {
        var err = assertThrows(CodedException.class, () -> OperationalDataRequestHandler.checkOutputFields(Set.of(
                "monitoringDataTs", "UNKNOWN-FIELD")));
        assertEquals("Unknown output field in search criteria: UNKNOWN-FIELD", err.getFaultString());
    }

    @Test
    public void buildOperationalDataResponseWithNotAvailableRecordsTo()
            throws Exception {
        ClientId client = ClientId.Conf.create(
                "XTEE-CI-XM", "00000001", "GOV", "System1");
        OperationalDataRequestHandler handler =
                new OperationalDataRequestHandler(mock(GlobalConfProvider.class), operationalDataRecordManager);
        long recordsAvailableBefore = TimeUtils.getEpochSecond();

        GetSecurityServerOperationalDataResponseType response = handler
                .buildOperationalDataResponse(client, 1474968960L,
                        recordsAvailableBefore + 10, null,
                        Collections.emptySet(), recordsAvailableBefore);

        assertNotNull(response.getNextRecordsFrom());
    }

    @Test
    public void checkNegativeRecordsFromTimestamps() {
        var err = assertThrows(CodedException.class, () -> OperationalDataRequestHandler.checkTimestamps(-10, 10, 10));
        assertEquals("Records from timestamp is a negative number", err.getFaultString());
    }

    @Test
    public void checkNegativeRecordsToTimestamps() {
        var err = assertThrows(CodedException.class, () -> OperationalDataRequestHandler.checkTimestamps(10, -10, 10));
        assertEquals("Records to timestamp is a negative number", err.getFaultString());
    }

    @Test
    public void checkEarlierRecordsToTimestamps() {
        var err = assertThrows(CodedException.class, () -> OperationalDataRequestHandler.checkTimestamps(10, 5, 10));
        assertEquals("Records to timestamp is earlier than records from timestamp", err.getFaultString());
    }

    @Test
    public void checkRecordsNotAvailable() {
        var err = assertThrows(CodedException.class, () -> OperationalDataRequestHandler.checkTimestamps(10, 10, 5));
        assertEquals("Records not available from " + 10 + " yet", err.getFaultString());
    }
}
