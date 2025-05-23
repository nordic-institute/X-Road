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

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageDto;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * test KeyUsageConverter
 */
public class KeyUsageConverterTest {

    private KeyUsageConverter keyUsageConverter = new KeyUsageConverter();

    @Test
    public void convert() {
        boolean[] bits;
        bits = new boolean[]{false, false, false, false, false, false, false, false, false};
        Set<KeyUsageDto> usages = keyUsageConverter.convert(bits);
        assertEquals(new HashSet<>(usages), new HashSet<>());

        bits = new boolean[]{true, false, false, false, false, false, false, false, true};
        usages = keyUsageConverter.convert(bits);
        assertEquals(new HashSet<>(usages), new HashSet<>(Arrays.asList(
                KeyUsageDto.DIGITAL_SIGNATURE,
                KeyUsageDto.DECIPHER_ONLY)));

        bits = new boolean[]{false, true, false, false, false, false, false, true, false};
        usages = keyUsageConverter.convert(bits);
        assertEquals(new HashSet<>(usages), new HashSet<>(Arrays.asList(
                KeyUsageDto.NON_REPUDIATION,
                KeyUsageDto.ENCIPHER_ONLY)));

        bits = new boolean[]{false, false, false, false, true, false, false, false, false};
        usages = keyUsageConverter.convert(bits);
        assertEquals(new HashSet<>(usages), new HashSet<>(Arrays.asList(
                KeyUsageDto.KEY_AGREEMENT)));

        bits = new boolean[]{false, false, false, false, true};
        usages = keyUsageConverter.convert(bits);
        assertEquals(new HashSet<>(usages), new HashSet<>(Arrays.asList(
                KeyUsageDto.KEY_AGREEMENT)));

        bits = new boolean[]{};
        usages = keyUsageConverter.convert(bits);
        assertEquals(new HashSet<>(usages), new HashSet<>());

        bits = new boolean[]{false, false, false, false, false, false, false, false, true, true, true, true};
        usages = keyUsageConverter.convert(bits);
        assertEquals(new HashSet<>(usages), new HashSet<>(Arrays.asList(
                KeyUsageDto.DECIPHER_ONLY)));
    }
}
