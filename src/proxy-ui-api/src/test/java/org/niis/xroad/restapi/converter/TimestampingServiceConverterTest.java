/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalMatchers;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.openapi.model.TimestampingService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Test TimestampingServiceConverter
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class TimestampingServiceConverterTest {

    @MockBean
    GlobalConfFacade globalConfFacade;

    private TimestampingServiceConverter timestampingServiceConverter;

    private static final String TSA_URL = "https://tsa.example.com";

    private static final String TSA_NAME = "Test TSA";

    private static final String INSTANCE_IDENTIFIER = "TEST";

    @Before
    public void setup() {
        when(globalConfFacade.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfFacade.getApprovedTspName(INSTANCE_IDENTIFIER, TSA_URL)).thenReturn(TSA_NAME);
        when(globalConfFacade.getApprovedTspName(eq(INSTANCE_IDENTIFIER), AdditionalMatchers.not(eq(TSA_URL))))
                .thenReturn(null);

        timestampingServiceConverter = new TimestampingServiceConverter(globalConfFacade);
    }

    @Test
    public void convertWithCorrectUrl() {
        TimestampingService timestampingService = timestampingServiceConverter.convert(TSA_URL);

        assertEquals(TSA_URL, timestampingService.getUrl());
        assertEquals(TSA_NAME, timestampingService.getName());
    }

    @Test
    public void convertWithIncorrectUrl() {
        String url = "https://example.com";
        TimestampingService timestampingService = timestampingServiceConverter.convert(url);

        assertEquals(url, timestampingService.getUrl());
        assertEquals(null, timestampingService.getName());
    }

    @Test
    public void convertEmptyList() {
        List<String> urls = new ArrayList<>();

        List<TimestampingService> timestampingService = timestampingServiceConverter.convert(urls);

        assertEquals(0, timestampingService.size());
    }

    @Test
    public void convertMultiple() {
        List<String> urls = new ArrayList<>(Arrays.asList(TSA_URL, "https://example.com"));

        List<TimestampingService> timestampingService = timestampingServiceConverter.convert(urls);

        assertEquals(2, timestampingService.size());
    }
}
