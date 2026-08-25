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
package org.niis.xroad.globalconf.model;

import ee.ria.xroad.common.TestCertUtil;

import org.junit.jupiter.api.Test;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class SharedParametersCacheTest {

    @Test
    void shouldNotCacheApprovedDsTlsCaData() throws Exception {
        var memberCaCert = TestCertUtil.getCaCert();

        var approvedCa = new SharedParameters.ApprovedCA();
        approvedCa.setName("Member CA");
        approvedCa.setCertificateProfileInfo("certificateProfileInfo");
        approvedCa.setTopCA(new SharedParameters.CaInfo(memberCaCert.getEncoded(), List.of()));
        approvedCa.setIntermediateCas(List.of());

        var dsTlsCa = new SharedParameters.ApprovedDsTlsCa();
        dsTlsCa.setName("DS TLS CA");
        dsTlsCa.setTopCA(new SharedParameters.CaInfo("not a real certificate".getBytes(UTF_8), List.of()));
        dsTlsCa.setIntermediateCas(List.of(
                new SharedParameters.CaInfo("not a real intermediate certificate either".getBytes(UTF_8), List.of())));

        var sharedParameters = SharedParameters.builder()
                .instanceIdentifier("CS")
                .approvedCAs(List.of(approvedCa))
                .approvedDsTlsCas(List.of(dsTlsCa))
                .securityServers(List.of())
                .build();

        var cache = new SharedParametersCache(sharedParameters);

        assertThat(cache.getVerificationCaCerts()).containsExactly(memberCaCert);
        assertThat(cache.getCaCertsAndApprovedCAData()).containsOnlyKeys(memberCaCert);
        assertThat(cache.getCaCertsAndOcspData()).containsOnlyKeys(memberCaCert);
        assertThat(cache.getSubjectsAndCaCerts()).containsValue(memberCaCert);
    }

}
