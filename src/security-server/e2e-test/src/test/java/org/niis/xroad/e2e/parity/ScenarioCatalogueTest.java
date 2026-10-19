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

package org.niis.xroad.e2e.parity;

import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.parity.Scenario.RestRequest;
import org.niis.xroad.e2e.parity.Scenario.RestService;
import org.niis.xroad.e2e.parity.Scenario.SoapRequest;
import org.niis.xroad.e2e.parity.Scenario.WsdlService;
import org.niis.xroad.e2e.parity.Scenario.WsdlShape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ScenarioCatalogueTest {

    @Test
    void idsAreUnique() {
        assertThat(ScenarioCatalogue.all()).extracting(Scenario::id).doesNotHaveDuplicates();
    }

    @Test
    void rowsCarryTheirLegacyVerdicts() {
        assertThat(ScenarioCatalogue.all()).extracting(Scenario::id, Scenario::expected)
                .containsExactly(
                        tuple("V01", Verdict.ALLOWED),
                        tuple("V02", Verdict.ACCESS_DENIED),
                        tuple("V03", Verdict.ALLOWED),
                        tuple("V04", Verdict.ACCESS_DENIED),
                        tuple("V128", Verdict.ALLOWED),
                        tuple("V129", Verdict.ALLOWED),
                        tuple("V131", Verdict.INVALID_SECURITY_SERVER),
                        tuple("V132a", Verdict.INVALID_SERVICE_TYPE),
                        tuple("V132b", Verdict.INVALID_SERVICE_TYPE));
    }

    @Test
    void deniedScenarioCallsAsAConsumerWithoutAGrant() {
        var denied = scenario("V02");

        var service = (RestService) denied.fixture().services().get(0);

        assertThat(service.grantedConsumerIds()).doesNotContain(denied.request().consumerClientId());
    }

    @Test
    void wsdlWithTwoServicesGrantsOnlyTheCodeThatIsNotCalled() {
        var row = scenario("V04");

        var wsdl = (WsdlService) row.fixture().services().get(0);

        assertThat(wsdl.shape()).isEqualTo(WsdlShape.PAIR);
        assertThat(wsdl.grantedConsumerIdsByCode().get(0)).contains(row.request().consumerClientId());
        assertThat(wsdl.grantedConsumerIdsByCode().get(row.request().serviceIndex())).isEmpty();
    }

    @Test
    void versionRowCallsTheSecondVersionOfAGrantedCode() {
        var row = scenario("V128");

        var wsdl = (WsdlService) row.fixture().services().get(0);

        assertThat(wsdl.shape()).isEqualTo(WsdlShape.VERSIONS);
        assertThat(((SoapRequest) row.request()).serviceVersion()).isEqualTo("v2");
    }

    @Test
    void dottedCodeRowAppendsADotToTheCode() {
        var wsdl = (WsdlService) scenario("V129").fixture().services().get(0);

        assertThat(wsdl.codeTail()).contains(".");
    }

    @Test
    void securityServerRowNamesAServerInTheRequestHeader() {
        var request = (SoapRequest) scenario("V131").request();

        assertThat(request.securityServerHint()).isEqualTo(ScenarioCatalogue.UNREGISTERED_SECURITY_SERVER);
    }

    @Test
    void serviceTypeRowsCallEachServiceWithTheOtherProtocol() {
        var restToSoap = scenario("V132a");
        var soapToRest = scenario("V132b");

        assertThat(restToSoap.fixture().services().get(0)).isInstanceOf(WsdlService.class);
        assertThat(restToSoap.request()).isInstanceOf(RestRequest.class);
        assertThat(soapToRest.fixture().services().get(0)).isInstanceOf(RestService.class);
        assertThat(soapToRest.request()).isInstanceOf(SoapRequest.class);
    }

    @Test
    void restCallToASoapServiceUsesAnUnversionedWsdl() {
        var wsdl = (WsdlService) scenario("V132a").fixture().services().get(0);

        assertThat(wsdl.shape()).isEqualTo(WsdlShape.SINGLE_UNVERSIONED);
    }

    @Test
    void soapCallToARestServiceNamesNoVersion() {
        var request = (SoapRequest) scenario("V132b").request();

        assertThat(request.serviceVersion()).isNull();
    }

    private static Scenario scenario(String id) {
        return ScenarioCatalogue.all().stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
    }
}
