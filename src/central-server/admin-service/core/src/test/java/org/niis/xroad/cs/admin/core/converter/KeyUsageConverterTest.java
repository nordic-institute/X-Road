/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.cs.admin.core.converter;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.api.dto.KeyUsageEnum;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.niis.xroad.cs.admin.api.dto.KeyUsageEnum.CRL_SIGN;
import static org.niis.xroad.cs.admin.api.dto.KeyUsageEnum.DATA_ENCIPHERMENT;
import static org.niis.xroad.cs.admin.api.dto.KeyUsageEnum.DECIPHER_ONLY;
import static org.niis.xroad.cs.admin.api.dto.KeyUsageEnum.DIGITAL_SIGNATURE;
import static org.niis.xroad.cs.admin.api.dto.KeyUsageEnum.ENCIPHER_ONLY;
import static org.niis.xroad.cs.admin.api.dto.KeyUsageEnum.KEY_AGREEMENT;
import static org.niis.xroad.cs.admin.api.dto.KeyUsageEnum.KEY_CERT_SIGN;
import static org.niis.xroad.cs.admin.api.dto.KeyUsageEnum.KEY_ENCIPHERMENT;
import static org.niis.xroad.cs.admin.api.dto.KeyUsageEnum.NON_REPUDIATION;

class KeyUsageConverterTest {

    private final KeyUsageConverter keyUsageConverter = new KeyUsageConverter();

    @Test
    void convert() {
        assertNull(keyUsageConverter.convert(null));
        assertTrue(keyUsageConverter.convert(new boolean[0]).isEmpty());

        Set<KeyUsageEnum> result;

        result = convert(false, false, false, false, false, false, false, false, false);
        assertTrue(result.isEmpty());

        result = convert(true, true, true, true, true, true, true, true, true);
        assertThat(result).containsAll(Set.of(KeyUsageEnum.values()));

        result = convert(true, false, false, false, false, false, false, false, false);
        assertThat(result).containsExactly(DIGITAL_SIGNATURE);

        result = convert(false, true, false, false, false, false, false, false, false);
        assertThat(result).containsExactly(NON_REPUDIATION);

        result = convert(false, false, true, false, false, false, false, false, false);
        assertThat(result).containsExactly(KEY_ENCIPHERMENT);

        result = convert(false, false, false, true, false, false, false, false, false);
        assertThat(result).containsExactly(DATA_ENCIPHERMENT);

        result = convert(false, false, false, false, true, false, false, false, false);
        assertThat(result).containsExactly(KEY_AGREEMENT);

        result = convert(false, false, false, false, false, true, false, false, false);
        assertThat(result).containsExactly(KEY_CERT_SIGN);

        result = convert(false, false, false, false, false, false, true, false, false);
        assertThat(result).containsExactly(CRL_SIGN);

        result = convert(false, false, false, false, false, false, false, true, false);
        assertThat(result).containsExactly(ENCIPHER_ONLY);

        result = convert(false, false, false, false, false, false, false, false, true);
        assertThat(result).containsExactly(DECIPHER_ONLY);
    }

    private Set<KeyUsageEnum> convert(boolean... usages) {
        return keyUsageConverter.convert(usages);
    }

}
