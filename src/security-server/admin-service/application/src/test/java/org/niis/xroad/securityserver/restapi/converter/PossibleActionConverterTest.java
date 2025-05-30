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
package org.niis.xroad.securityserver.restapi.converter;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.PossibleActionDto;
import org.niis.xroad.securityserver.restapi.service.PossibleActionEnum;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * test StateChangeActionConverter
 */
public class PossibleActionConverterTest {

    @Test
    public void enumsAreInSync() {
        // test to verify openapi and service layer enums contain same number of discreet items
        Set<String> openApiEnumNames = Arrays.stream(PossibleActionDto.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        Set<String> serviceEnumNames = Arrays.stream(PossibleActionEnum.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertEquals(openApiEnumNames.size(), serviceEnumNames.size());
    }

    @Test
    public void convertOne() {
        PossibleActionDto converted = new PossibleActionConverter()
                .convert(PossibleActionEnum.IMPORT_FROM_TOKEN);
        assertEquals(PossibleActionDto.IMPORT_FROM_TOKEN, converted);
    }

    @Test
    public void convertAll() {
        Set<PossibleActionDto> allItemsConverted = new HashSet<>(
                new PossibleActionConverter()
                        .convert(Arrays.asList(PossibleActionEnum.values())));
        Set<PossibleActionDto> allOpenApiValues = new HashSet<>(
                Arrays.asList(PossibleActionDto.values()));
        assertTrue(allOpenApiValues.containsAll(allItemsConverted));
        assertTrue(allItemsConverted.containsAll(allOpenApiValues));
        assertEquals(PossibleActionDto.values().length, allItemsConverted.size());
    }

}
