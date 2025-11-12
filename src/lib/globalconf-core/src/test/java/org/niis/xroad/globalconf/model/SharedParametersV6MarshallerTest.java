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
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedParametersV6MarshallerTest {

    private final SharedParametersV6Marshaller marshaller = new SharedParametersV6Marshaller();

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

        SharedParameters.ApprovedCA approvedCA = new SharedParameters.ApprovedCA();
        approvedCA.setName("Test CA");
        approvedCA.setTopCA(new SharedParameters.CaInfo("ca-cert".getBytes(UTF_8), List.of(
                new SharedParameters.OcspInfo("ocsp:url", "ocsp-cert".getBytes(UTF_8), CostType.FREE))));
        approvedCA.setIntermediateCas(List.of(
                new SharedParameters.CaInfo("intermediate-ca-cert".getBytes(UTF_8), List.of(
                        new SharedParameters.OcspInfo("intermediate-ocsp:url", "intermediate-ocsp-cert".getBytes(UTF_8), CostType.UNDEFINED)
                ))
        ));
        approvedCA.setCertificateProfileInfo("certProfileInfo");
        approvedCA.setDefaultCsrFormat(CsrFormat.DER);
        sharedParamsBuilder.approvedCAs(List.of(approvedCA));

        SharedParameters.ApprovedTSA approvedTSA =
                new SharedParameters.ApprovedTSA("Test TSA", "http://tsa.url", "tsa-cert".getBytes(UTF_8), CostType.PAID);
        sharedParamsBuilder.approvedTSAs(List.of(approvedTSA));

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
