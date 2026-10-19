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
import org.niis.xroad.e2e.parity.Scenario.EndpointSpec;
import org.niis.xroad.e2e.parity.Scenario.GeneratedEndpointGrant;
import org.niis.xroad.e2e.parity.Scenario.OpenApiService;
import org.niis.xroad.e2e.parity.Scenario.OpenApiSpec;
import org.niis.xroad.e2e.parity.Scenario.RestRequest;
import org.niis.xroad.e2e.parity.Scenario.RestService;
import org.niis.xroad.e2e.parity.Scenario.SoapRequest;
import org.niis.xroad.e2e.parity.Scenario.WsdlService;
import org.niis.xroad.e2e.parity.Scenario.WsdlShape;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ScenarioCatalogueTest {

    @Test
    void idsAreUnique() {
        assertThat(ScenarioCatalogue.all()).extracting(Scenario::id).doesNotHaveDuplicates();
    }

    @Test
    void rowsCarryTheirLegacyVerdicts() {
        assertThat(ScenarioCatalogue.all()).extracting(Scenario::id, ScenarioCatalogueTest::expectedVerdicts)
                .containsExactly(
                        tuple("V01", List.of(Verdict.ALLOWED)),
                        tuple("V02", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V03", List.of(Verdict.ALLOWED)),
                        tuple("V04", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V20", List.of(Verdict.ALLOWED, Verdict.ALLOWED)),
                        tuple("V21", List.of(Verdict.ALLOWED)),
                        tuple("V22", List.of(Verdict.ALLOWED)),
                        tuple("V23", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V24", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V25", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V26", List.of(Verdict.ALLOWED, Verdict.ALLOWED, Verdict.ACCESS_DENIED,
                                Verdict.ACCESS_DENIED)),
                        tuple("V27", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED, Verdict.ACCESS_DENIED)),
                        tuple("V28", List.of(Verdict.ACCESS_DENIED, Verdict.ALLOWED, Verdict.ALLOWED)),
                        tuple("V29", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V30", List.of(Verdict.ALLOWED, Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V31", List.of(Verdict.ALLOWED, Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V32", List.of(Verdict.ALLOWED, Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V33", List.of(Verdict.ALLOWED, Verdict.ALLOWED)),
                        tuple("V34", List.of(Verdict.ALLOWED, Verdict.ALLOWED, Verdict.ACCESS_DENIED,
                                Verdict.ACCESS_DENIED)),
                        tuple("V35", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V36", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V37", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V38", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V40", List.of(Verdict.ALLOWED)),
                        tuple("V128", List.of(Verdict.ALLOWED)),
                        tuple("V129", List.of(Verdict.ALLOWED)),
                        tuple("V131", List.of(Verdict.INVALID_SECURITY_SERVER)),
                        tuple("V132a", List.of(Verdict.INVALID_SERVICE_TYPE)),
                        tuple("V132b", List.of(Verdict.INVALID_SERVICE_TYPE)));
    }

    @Test
    void endpointRowsGrantOnlyTheCreatedEndpointAndRequestTheBareRootWithAnEmptyPath() {
        var row = scenario("V27");

        var service = (RestService) row.fixture().services().get(0);

        assertThat(service.grantedConsumerIds()).isEmpty();
        assertThat(service.endpoints()).containsExactly(
                new EndpointSpec("GET", "/foo/**", List.of(ScenarioCatalogue.CONSUMER)));
        assertThat(row.checks()).extracting(check -> ((RestRequest) check.request()).path())
                .containsExactly("/foo/x/y", "/foo", "");
    }

    @Test
    void wildcardMethodAndNonGetRequestsAreExercised() {
        var service = (RestService) scenario("V28").fixture().services().get(0);

        assertThat(service.endpoints()).extracting(EndpointSpec::method, EndpointSpec::path)
                .containsExactly(tuple("*", "/**"));
        assertThat(scenario("V20").checks()).extracting(check -> ((RestRequest) check.request()).method())
                .contains("POST");
    }

    @Test
    void baseAndEndpointGrantsOfV33GoToTheSameConsumer() {
        var service = (RestService) scenario("V33").fixture().services().get(0);

        assertThat(service.grantedConsumerIds()).containsExactly(ScenarioCatalogue.CONSUMER);
        assertThat(service.endpoints()).containsExactly(
                new EndpointSpec("GET", "/foo", List.of(ScenarioCatalogue.CONSUMER)));
    }

    @Test
    void perSubjectRowGrantsEachConsumerItsOwnPathAndCallsAsBoth() {
        var row = scenario("V34");
        var service = (RestService) row.fixture().services().get(0);

        assertThat(service.endpoints()).extracting(EndpointSpec::path, EndpointSpec::grantedConsumerIds)
                .containsExactly(tuple("/a", List.of(ScenarioCatalogue.CONSUMER)),
                        tuple("/b", List.of(ScenarioCatalogue.OTHER_CONSUMER)));
        assertThat(row.checks()).extracting(check -> check.request().consumerClientId())
                .containsExactly(ScenarioCatalogue.CONSUMER, ScenarioCatalogue.OTHER_CONSUMER,
                        ScenarioCatalogue.CONSUMER, ScenarioCatalogue.OTHER_CONSUMER);
    }

    @Test
    void ungrantedEndpointRowHoldsNoBaseGrantAndAGrantedControlEndpoint() {
        var service = (RestService) scenario("V35").fixture().services().get(0);

        assertThat(service.grantedConsumerIds()).isEmpty();
        assertThat(service.endpoints()).extracting(EndpointSpec::path, EndpointSpec::grantedConsumerIds)
                .containsExactly(tuple("/foo", List.of()), tuple("/bar", List.of(ScenarioCatalogue.CONSUMER)));
    }

    @Test
    void deletedAndEditedRowsGrantTheEndpointBeforeChangingIt() {
        var deleted = (RestService) scenario("V36").fixture().services().get(0);
        var edited = (RestService) scenario("V37").fixture().services().get(0);

        assertThat(deleted.endpoints().get(0).followUp()).isEqualTo(new Scenario.Deleted());
        assertThat(deleted.endpoints().get(0).grantedConsumerIds()).containsExactly(ScenarioCatalogue.CONSUMER);
        assertThat(edited.endpoints().get(0).followUp()).isEqualTo(new Scenario.EditedTo("GET", "/bar"));
        assertThat(edited.endpoints().get(0).grantedConsumerIds()).containsExactly(ScenarioCatalogue.CONSUMER);
    }

    @Test
    void openApiRowsGrantNoBaseEndpointAndCallAPathTheSpecGenerates() {
        var imported = (OpenApiService) scenario("V38").fixture().services().get(0);
        var equalToParsed = (OpenApiService) scenario("V40").fixture().services().get(0);

        assertThat(imported.spec()).isEqualTo(OpenApiSpec.PETS);
        assertThat(imported.generatedGrants()).containsExactly(new GeneratedEndpointGrant("GET", "/pets/*",
                List.of(ScenarioCatalogue.CONSUMER)));
        assertThat(equalToParsed.spec()).isEqualTo(OpenApiSpec.STATUS_ONLY);
        assertThat(equalToParsed.editedToSpec()).isEqualTo(OpenApiSpec.PETS);
        assertThat(equalToParsed.manualEndpoints()).containsExactly(
                new EndpointSpec("GET", "/pets/*", List.of(ScenarioCatalogue.CONSUMER)));
        assertThat(scenario("V40").checks()).extracting(check -> ((RestRequest) check.request()).path())
                .containsExactly("/pets/123");
    }

    @Test
    void deniedScenarioCallsAsAConsumerWithoutAGrant() {
        var denied = scenario("V02");

        var service = (RestService) denied.fixture().services().get(0);

        assertThat(service.grantedConsumerIds()).doesNotContain(denied.checks().get(0).request().consumerClientId());
    }

    @Test
    void wsdlWithTwoServicesGrantsOnlyTheCodeThatIsNotCalled() {
        var row = scenario("V04");

        var wsdl = (WsdlService) row.fixture().services().get(0);

        assertThat(wsdl.shape()).isEqualTo(WsdlShape.PAIR);
        assertThat(wsdl.grantedConsumerIdsByCode().get(0)).contains(row.checks().get(0).request().consumerClientId());
        assertThat(wsdl.grantedConsumerIdsByCode().get(row.checks().get(0).request().serviceIndex())).isEmpty();
    }

    @Test
    void versionRowCallsTheSecondVersionOfAGrantedCode() {
        var row = scenario("V128");

        var wsdl = (WsdlService) row.fixture().services().get(0);

        assertThat(wsdl.shape()).isEqualTo(WsdlShape.VERSIONS);
        assertThat(((SoapRequest) row.checks().get(0).request()).serviceVersion()).isEqualTo("v2");
    }

    @Test
    void dottedCodeRowAppendsADotToTheCode() {
        var wsdl = (WsdlService) scenario("V129").fixture().services().get(0);

        assertThat(wsdl.codeTail()).contains(".");
    }

    @Test
    void securityServerRowNamesAServerInTheRequestHeader() {
        var request = (SoapRequest) scenario("V131").checks().get(0).request();

        assertThat(request.securityServerHint()).isEqualTo(ScenarioCatalogue.UNREGISTERED_SECURITY_SERVER);
    }

    @Test
    void serviceTypeRowsCallEachServiceWithTheOtherProtocol() {
        var restToSoap = scenario("V132a");
        var soapToRest = scenario("V132b");

        assertThat(restToSoap.fixture().services().get(0)).isInstanceOf(WsdlService.class);
        assertThat(restToSoap.checks().get(0).request()).isInstanceOf(RestRequest.class);
        assertThat(soapToRest.fixture().services().get(0)).isInstanceOf(RestService.class);
        assertThat(soapToRest.checks().get(0).request()).isInstanceOf(SoapRequest.class);
    }

    @Test
    void restCallToASoapServiceUsesAnUnversionedWsdl() {
        var wsdl = (WsdlService) scenario("V132a").fixture().services().get(0);

        assertThat(wsdl.shape()).isEqualTo(WsdlShape.SINGLE_UNVERSIONED);
    }

    @Test
    void soapCallToARestServiceNamesNoVersion() {
        var request = (SoapRequest) scenario("V132b").checks().get(0).request();

        assertThat(request.serviceVersion()).isNull();
    }

    private static List<Verdict> expectedVerdicts(Scenario scenario) {
        return scenario.checks().stream().map(Scenario.Check::expected).toList();
    }

    private static Scenario scenario(String id) {
        return ScenarioCatalogue.all().stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
    }
}
