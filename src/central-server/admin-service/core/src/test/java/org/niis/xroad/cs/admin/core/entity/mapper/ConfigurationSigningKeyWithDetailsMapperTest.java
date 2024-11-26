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
package org.niis.xroad.cs.admin.core.entity.mapper;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.dto.PossibleKeyAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {ConfigurationSigningKeyWithDetailsMapperImpl.class})
class ConfigurationSigningKeyWithDetailsMapperTest {
    private static final String LABEL = "label1";

    @Autowired
    private ConfigurationSigningKeyWithDetailsMapper mapper;

    @Test
    void shouldMapAllFields() {
        var configurationSigningKey = new ConfigurationSigningKey();
        var possibleKeys = List.of(PossibleKeyAction.ACTIVATE, PossibleKeyAction.DELETE);

        var result = mapper.toTarget(configurationSigningKey, possibleKeys, LABEL, true, KeyAlgorithm.RSA);

        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getLabel().getLabel()).isEqualTo(LABEL);
        assertThat(result.getKeyAlgorithm()).isEqualTo(KeyAlgorithm.RSA);
        assertThat(result.getPossibleActions()).containsExactlyInAnyOrder(PossibleKeyAction.ACTIVATE, PossibleKeyAction.DELETE);
    }
}
