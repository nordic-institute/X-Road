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
package org.niis.xroad.proxy.controlplane;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.BuiltinServiceCodes;
import org.niis.xroad.proxy.core.dsp.AssetAccessAcquisitionService;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.test.StubbableDspRequestProcessor;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * Keeps the metaservice bypass rule that the proxy test fixture copies in step with the production rule.
 */
class StubbableDspRequestProcessorParityTest {

    private static final ClientId SENDER = ClientId.Conf.create("DEV", "COM", "4321", "SenderSub");

    private final StubbableDspRequestProcessor fixture = mock(StubbableDspRequestProcessor.class, CALLS_REAL_METHODS);
    private final ConsumerSideDspProcessor production = new ConsumerSideDspProcessor(
            mock(AssetAccessAcquisitionService.class), mock(AssetAccessClientProperties.class));

    static Stream<String> serviceCodes() {
        return Stream.concat(BuiltinServiceCodes.METASERVICES.stream(), Stream.of("testService"));
    }

    @ParameterizedTest
    @MethodSource("serviceCodes")
    void fixtureBypassesDspExactlyWhenProductionDoes(String serviceCode) {
        var request = new DspRequest(ServiceId.Conf.create("DEV", "COM", "1234", "TestClient", serviceCode), SENDER, null, false);

        assertThat(fixture.bypassesDsp(request)).isEqualTo(production.bypassesDsp(request));
    }
}
