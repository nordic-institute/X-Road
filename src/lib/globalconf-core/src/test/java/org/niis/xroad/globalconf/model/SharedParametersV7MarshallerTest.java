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
package org.niis.xroad.globalconf.model;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.CostType;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedParametersV7MarshallerTest {

    private final SharedParametersV7Marshaller marshaller = new SharedParametersV7Marshaller();

    @Test
    void marshall() {
        var sharedParamsBuilder = SharedParameters.builder();
        sharedParamsBuilder.instanceIdentifier("CS");

        var configurationSource = new SharedParameters.ConfigurationSource();
        configurationSource.setAddress("cs");
        configurationSource.setInternalVerificationCerts(List.of("internal-conf-signing-cert".getBytes(StandardCharsets.UTF_8)));
        configurationSource.setExternalVerificationCerts(List.of("external-conf-signing-cert".getBytes(StandardCharsets.UTF_8)));
        sharedParamsBuilder.globalSettings(new SharedParameters.GlobalSettings(null, 60));
        sharedParamsBuilder.sources(List.of(configurationSource));

        var approvedDsTlsCa = new SharedParameters.ApprovedDsTlsCa();
        approvedDsTlsCa.setName("Test DS TLS CA");
        approvedDsTlsCa.setTopCA(new SharedParameters.CaInfo("ds-tls-ca-cert".getBytes(UTF_8), List.of(
                new SharedParameters.OcspInfo("ds-tls-ocsp:url", "ds-tls-ocsp-cert".getBytes(UTF_8), CostType.FREE))));
        approvedDsTlsCa.setIntermediateCas(List.of(
                new SharedParameters.CaInfo("ds-tls-intermediate-ca-cert".getBytes(UTF_8), List.of(
                        new SharedParameters.OcspInfo("ds-tls-intermediate-ocsp:url", "ds-tls-intermediate-ocsp-cert".getBytes(UTF_8),
                                CostType.UNDEFINED)
                ))
        ));
        approvedDsTlsCa.setAcmeServer(new SharedParameters.AcmeServer("http://testca.com/acme", "192.99.88.7", null, null,
                "ds-tls-profile"));
        sharedParamsBuilder.approvedDsTlsCas(List.of(approvedDsTlsCa));

        final String result = marshaller.marshall(sharedParamsBuilder.build());

        assertThat(result).isNotBlank();
        System.out.println(result);
    }

    @Test
    void marshallShouldFailWhenInvalid() {
        var sharedParamsBuilder = SharedParameters.builder();
        sharedParamsBuilder.instanceIdentifier("CS");
        sharedParamsBuilder.sources(List.of(new SharedParameters.ConfigurationSource())); // missing address or cert
        sharedParamsBuilder.globalSettings(new SharedParameters.GlobalSettings(null, 60));
        SharedParameters sharedParameters = sharedParamsBuilder.build();
        assertThrows(XrdRuntimeException.class, () -> marshaller.marshall(sharedParameters));
    }


}
