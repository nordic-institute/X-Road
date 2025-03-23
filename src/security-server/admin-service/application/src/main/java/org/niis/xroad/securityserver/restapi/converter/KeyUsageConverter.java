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

import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageDto;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Convert X598Certificate's public abstract boolean[] getKeyUsage()
 */
@Component
@SuppressWarnings("checkstyle:MagicNumber") // index numbers are most clear way here to represent the issue
public class KeyUsageConverter {
    // maps a X509Certificate.getKeyUsage bit index to corresponding KeyUsagesEnum value
    private static final Map<Integer, KeyUsageDto> BIT_TO_USAGE =
            new HashMap<>();

    static {
        BIT_TO_USAGE.put(0, KeyUsageDto.DIGITAL_SIGNATURE);
        BIT_TO_USAGE.put(1, KeyUsageDto.NON_REPUDIATION);
        BIT_TO_USAGE.put(2, KeyUsageDto.KEY_ENCIPHERMENT);
        BIT_TO_USAGE.put(3, KeyUsageDto.DATA_ENCIPHERMENT);
        BIT_TO_USAGE.put(4, KeyUsageDto.KEY_AGREEMENT);
        BIT_TO_USAGE.put(5, KeyUsageDto.KEY_CERT_SIGN);
        BIT_TO_USAGE.put(6, KeyUsageDto.CRL_SIGN);
        BIT_TO_USAGE.put(7, KeyUsageDto.ENCIPHER_ONLY);
        BIT_TO_USAGE.put(8, KeyUsageDto.DECIPHER_ONLY);
    }

    /**
     * Convert boolean array of key usage bits as returned by
     * https://docs.oracle.com/javase/8/docs/api/java/security/cert/X509Certificate.html#getKeyUsage--
     * into an EnumSet
     * @param keyUsageBits
     * @return
     */
    public EnumSet<KeyUsageDto> convert(boolean[] keyUsageBits) {
        EnumSet<KeyUsageDto> usages = EnumSet.noneOf(KeyUsageDto.class);
        if (keyUsageBits != null) {
            for (int i = 0; i < Math.min(BIT_TO_USAGE.size(), keyUsageBits.length); i++) {
                if (keyUsageBits[i]) {
                    usages.add(BIT_TO_USAGE.get(i));
                }
            }
        }
        return usages;
    }
}
