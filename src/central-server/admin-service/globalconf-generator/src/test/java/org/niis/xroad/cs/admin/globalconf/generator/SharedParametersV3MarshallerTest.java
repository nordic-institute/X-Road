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
package org.niis.xroad.cs.admin.globalconf.generator;

import jakarta.xml.bind.MarshalException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedParametersV3MarshallerTest {

    private final SharedParametersV3Marshaller marshaller = new SharedParametersV3Marshaller();

    @Test
    void marshall() {
        SharedParameters sharedParams = new SharedParameters();
        sharedParams.setInstanceIdentifier("CS");

        var configurationSource = new SharedParameters.ConfigurationSource();
        configurationSource.setAddress("cs");
        configurationSource.setInternalVerificationCerts(List.of("internal-conf-signing-cert".getBytes(StandardCharsets.UTF_8)));
        configurationSource.setExternalVerificationCerts(List.of("external-conf-signing-cert".getBytes(StandardCharsets.UTF_8)));
        sharedParams.setGlobalSettings(new SharedParameters.GlobalSettings(null, 60));
        sharedParams.setSources(List.of(configurationSource));

        final String result = marshaller.marshall(sharedParams);

        assertThat(result).isNotBlank();
    }

    @Test
    void marshallShouldFailWhenInvalid() {
        SharedParameters sharedParams = new SharedParameters();
        sharedParams.setInstanceIdentifier("CS");
        sharedParams.setSources(List.of(new SharedParameters.ConfigurationSource())); // missing address or cert
        sharedParams.setGlobalSettings(new SharedParameters.GlobalSettings(null, 60));
        assertThrows(MarshalException.class, () -> marshaller.marshall(sharedParams));
    }


}
