/*
 * The MIT License
 *
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
package org.niis.xroad.securityserver.restapi.converter;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.common.CostType;
import org.niis.xroad.securityserver.restapi.openapi.model.CostTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDto;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.serverconf.model.TimestampingService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Test TimestampingServiceConverter
 */
public class TimestampingServiceConverterTest {

    private TimestampingServiceConverter timestampingServiceConverter;

    private static final String TSA_1_URL = "https://tsa.example.com";

    private static final String TSA_1_NAME = "Test TSA";

    private static final String TSA_2_URL = "https://tsa.com";

    private static final String TSA_2_NAME = "TSA 2";

    private static final String INSTANCE_IDENTIFIER = "TEST";

    @Before
    public void setup() {
        timestampingServiceConverter = new TimestampingServiceConverter();
    }

    @Test
    public void convertSingleTspType() {
        TimestampingServiceDto timestampingService = timestampingServiceConverter.convert(
                TestUtils.createTspType(TSA_1_URL, TSA_1_NAME, CostType.FREE));

        assertEquals(TSA_1_URL, timestampingService.getUrl());
        assertEquals(TSA_1_NAME, timestampingService.getName());
        assertEquals(CostTypeDto.FREE, timestampingService.getCostType());
    }

    @Test
    public void convertEmptyTspTypeList() {
        List<TimestampingService> timestampingServices = new ArrayList<>();

        Set<TimestampingServiceDto> timestampingService = timestampingServiceConverter.convert(timestampingServices);

        assertEquals(0, timestampingService.size());
    }

    @Test
    public void convertMultipleTspTypes() {
        List<TimestampingService> tspTypes = new ArrayList<>(Arrays.asList(TestUtils.createTspType(
                TSA_1_URL, TSA_1_NAME, CostType.PAID), TestUtils.createTspType(TSA_2_URL, TSA_2_NAME, CostType.FREE)));

        Set<TimestampingServiceDto> timestampingServices = timestampingServiceConverter.convert(tspTypes);

        assertEquals(2, timestampingServices.size());
    }

    @Test
    public void convertSingleTimestampingService() {
        TimestampingService timestampingService = timestampingServiceConverter.convert(TestUtils
                .createTimestampingService(TSA_1_URL, TSA_1_NAME, CostTypeDto.FREE));

        assertEquals(TSA_1_URL, timestampingService.getUrl());
        assertEquals(TSA_1_NAME, timestampingService.getName());
        assertEquals(CostTypeDto.FREE.name(), timestampingService.getCostType().name());
    }
}
