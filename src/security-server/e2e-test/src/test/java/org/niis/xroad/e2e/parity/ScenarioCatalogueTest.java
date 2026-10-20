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
import org.niis.xroad.e2e.parity.Scenario.LocalGroupSpec;
import org.niis.xroad.e2e.parity.Scenario.MetaserviceRequest;
import org.niis.xroad.e2e.parity.Scenario.OpenApiService;
import org.niis.xroad.e2e.parity.Scenario.OpenApiSpec;
import org.niis.xroad.e2e.parity.Scenario.RestRequest;
import org.niis.xroad.e2e.parity.Scenario.RestService;
import org.niis.xroad.e2e.parity.Scenario.SoapRequest;
import org.niis.xroad.e2e.parity.Scenario.WsdlService;
import org.niis.xroad.e2e.parity.Scenario.WsdlShape;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                        tuple("V10", List.of(Verdict.ACCESS_DENIED, Verdict.ALLOWED)),
                        tuple("V11", List.of(Verdict.ALLOWED, Verdict.ALLOWED, Verdict.ACCESS_DENIED,
                                Verdict.ACCESS_DENIED)),
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
                        tuple("V39", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V40", List.of(Verdict.ALLOWED)),
                        tuple("V41", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V42", List.of(Verdict.ALLOWED)),
                        tuple("V43", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V44", List.of(Verdict.ALLOWED)),
                        tuple("V45", List.of(Verdict.ALLOWED, Verdict.CONSUMER_REJECTED, Verdict.ACCESS_DENIED)),
                        tuple("V46", List.of(Verdict.ALLOWED)),
                        tuple("V47", List.of(Verdict.ALLOWED)),
                        tuple("V48", List.of(Verdict.ALLOWED, Verdict.ALLOWED)),
                        tuple("V49", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V50", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V51", List.of(Verdict.ALLOWED, Verdict.ALLOWED)),
                        tuple("V52", List.of(Verdict.ALLOWED, Verdict.ALLOWED, Verdict.ALLOWED, Verdict.ALLOWED,
                                Verdict.ALLOWED, Verdict.ALLOWED, Verdict.ALLOWED, Verdict.ALLOWED,
                                Verdict.ACCESS_DENIED)),
                        tuple("V53", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V54", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V55", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V60", List.of(Verdict.ALLOWED)),
                        tuple("V61", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V62", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V63", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V64", List.of(Verdict.ALLOWED)),
                        tuple("V65", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V66", List.of(Verdict.ALLOWED, Verdict.ALLOWED)),
                        tuple("V67", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V68", List.of(Verdict.ALLOWED)),
                        tuple("V69", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V70", List.of(Verdict.ALLOWED)),
                        tuple("V71", List.of(Verdict.ALLOWED, Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V72", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V73", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V74", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V75", List.of(Verdict.ACCESS_DENIED, Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V77", List.of(Verdict.ALLOWED, Verdict.ALLOWED)),
                        tuple("V90", List.of(Verdict.ALLOWED, Verdict.ALLOWED)),
                        tuple("V91", List.of(Verdict.ALLOWED, Verdict.ALLOWED)),
                        tuple("V92", List.of(Verdict.ALLOWED, Verdict.INVALID_SERVICE_TYPE,
                                Verdict.UNKNOWN_SERVICE)),
                        tuple("V93", List.of(Verdict.ALLOWED)),
                        tuple("V94", List.of(Verdict.UNKNOWN_SERVICE)),
                        tuple("V99", List.of(Verdict.ALLOWED)),
                        tuple("V100", List.of(Verdict.ALLOWED)),
                        tuple("V101", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V106a", List.of(Verdict.ALLOWED, Verdict.ACCESS_DENIED)),
                        tuple("V106b", List.of(Verdict.ACCESS_DENIED, Verdict.ALLOWED)),
                        tuple("V120", List.of(Verdict.SERVICE_DISABLED)),
                        tuple("V121", List.of(Verdict.ACCESS_DENIED)),
                        tuple("V122", List.of(Verdict.SERVICE_DISABLED, Verdict.SERVICE_DISABLED)),
                        tuple("V123", List.of(Verdict.SERVICE_DISABLED)),
                        tuple("V124", List.of(Verdict.ALLOWED)),
                        tuple("V125", List.of(Verdict.UNKNOWN_SERVICE)),
                        tuple("V127", List.of(Verdict.UNKNOWN_SERVICE, Verdict.ALLOWED)),
                        tuple("V128", List.of(Verdict.ALLOWED)),
                        tuple("V129", List.of(Verdict.ALLOWED)),
                        tuple("V131", List.of(Verdict.INVALID_SECURITY_SERVER)),
                        tuple("V132a", List.of(Verdict.INVALID_SERVICE_TYPE)),
                        tuple("V132b", List.of(Verdict.INVALID_SERVICE_TYPE)),
                        tuple("V133", List.of(Verdict.UNKNOWN_SERVICE, Verdict.UNKNOWN_SERVICE)));
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
    void pathRowsSendTheEncodedPathsAsTheCatalogueWritesThem() {
        assertThat(requestPaths("V42")).containsExactly("/foo?x=1");
        assertThat(requestPaths("V44")).containsExactly("/api/t%65st/foo");
        assertThat(requestPaths("V45")).containsExactly("/api/x", "/api/%2e%2e/secret", "/../x");
        assertThat(requestPaths("V46")).containsExactly("/api/test/foo/../bar");
        assertThat(requestPaths("V47")).containsExactly("/api/test%2Dbar");
        assertThat(requestPaths("V48")).containsExactly("/foo/x", "/foo%2Fbar");
        assertThat(requestPaths("V49")).containsExactly("/public%3Fx");
        assertThat(requestPaths("V50")).containsExactly("/files/my-file.pdf", "/files/my%20file.pdf");
        assertThat(requestPaths("V53")).containsExactly("/api/items/1%3F/../../other");
        assertThat(requestPaths("V54")).containsExactly("/%2F../other");
    }

    @Test
    void v54GrantsTheBaseEndpointOnly() {
        var service = (RestService) scenario("V54").fixture().services().get(0);
        assertThat(service.grantedConsumerIds()).isNotEmpty();
        assertThat(service.endpoints()).isEmpty();
    }

    @Test
    void patternShapeRowsGrantTheEndpointPathsAsWritten() {
        assertThat(endpointPaths("V51")).containsExactly("/foo?");
        assertThat(endpointPaths("V55")).containsExactly("/files/*.json", "/files/ok");
        assertThat(requestPaths("V51")).containsExactly("/foo", "/fo");
        assertThat(requestPaths("V55")).containsExactly("/files/ok", "/files/a.json");
    }

    @Test
    void onlyTheRowsHittingAnUnfixedFindingCarryAMarkerNamingJustItsId() {
        assertThat(ScenarioCatalogue.all()).filteredOn(row -> row.knownDefect() != null)
                .extracting(Scenario::id, Scenario::knownDefect)
                .isEmpty();
    }

    @Test
    void markedRowsAssertTheSafeVerdict() {
        assertThat(ScenarioCatalogue.all()).filteredOn(row -> row.knownDefect() != null)
                .allSatisfy(row -> assertThat(expectedVerdicts(row))
                        .isSubsetOf(Verdict.ACCESS_DENIED, Verdict.SERVICE_DISABLED));
    }

    @Test
    void methodRowCallsEachUnusualMethodOnTheAnyMethodEndpointAndOnItsOwn() {
        var row = scenario("V52");
        var service = (RestService) row.fixture().services().get(0);

        assertThat(service.endpoints()).extracting(EndpointSpec::method, EndpointSpec::path)
                .containsExactly(tuple("*", "/any"), tuple("HEAD", "/head"), tuple("OPTIONS", "/options"),
                        tuple("TRACE", "/trace"), tuple("PATCH", "/patch"));
        assertThat(row.checks()).extracting(check -> ((RestRequest) check.request()).method(),
                        check -> ((RestRequest) check.request()).path())
                .containsExactly(tuple("HEAD", "/any"), tuple("OPTIONS", "/any"), tuple("TRACE", "/any"),
                        tuple("PATCH", "/any"), tuple("HEAD", "/head"), tuple("OPTIONS", "/options"),
                        tuple("TRACE", "/trace"), tuple("PATCH", "/patch"), tuple("PATCH", "/head"));
    }

    @Test
    void groupRowsGrantTheBaseEndpointToTheGroupAndCreateItUnderTheProvider() {
        for (var id : List.of("V60", "V61", "V62", "V63", "V64")) {
            var fixture = scenario(id).fixture();
            var service = (RestService) fixture.services().get(0);
            assertThat(service.grantedConsumerIds()).as(id).isEmpty();
            assertThat(service.grantedGroupCodes()).as(id).hasSize(1);
            assertThat(fixture.groups()).as(id).extracting(LocalGroupSpec::ownerClientId)
                    .containsExactly(ScenarioCatalogue.PROVIDER);
        }
    }

    @Test
    void groupMembershipChangesAreAppliedOnlyByTheRemovalAndDeletionRows() {
        assertThat(scenario("V60").fixture().groups().get(0).removedMemberIds()).isEmpty();
        assertThat(scenario("V60").fixture().groups().get(0).deleted()).isFalse();
        assertThat(scenario("V62").fixture().groups().get(0).removedMemberIds())
                .containsExactly(ScenarioCatalogue.CONSUMER);
        assertThat(scenario("V63").fixture().groups().get(0).deleted()).isTrue();
    }

    @Test
    void memberShapedGroupRowSendsAsTheMemberThatIsInTheGroup() {
        var row = scenario("V64");
        assertThat(row.fixture().groups().get(0).memberIds()).containsExactly(ScenarioCatalogue.MEMBER_OF_CONSUMER);
        assertThat(row.checks().get(0).request().consumerClientId()).isEqualTo(ScenarioCatalogue.MEMBER_OF_CONSUMER);
    }

    @Test
    void sharedGroupCodeRowHasTheConsumerOnlyInTheOtherOwnersGroup() {
        var groups = scenario("V65").fixture().groups();
        assertThat(groups).extracting(LocalGroupSpec::code).containsOnly("partners");
        assertThat(groups).extracting(LocalGroupSpec::ownerClientId)
                .containsExactly(ScenarioCatalogue.PROVIDER, SubsystemPool.SECOND_OWNER);
        assertThat(groups.get(0).memberIds()).doesNotContain(ScenarioCatalogue.CONSUMER);
        assertThat(groups.get(1).memberIds()).containsExactly(ScenarioCatalogue.CONSUMER);
    }

    @Test
    void directAndGroupGrantRowGrantsEachEndpointThroughADifferentSubject() {
        var service = (RestService) scenario("V66").fixture().services().get(0);
        assertThat(service.endpoints()).extracting(EndpointSpec::path, EndpointSpec::grantedConsumerIds,
                EndpointSpec::grantedGroupCodes).containsExactly(
                tuple("/a", List.of(ScenarioCatalogue.CONSUMER), List.of()),
                tuple("/b", List.of(), List.of("partners")));
    }

    @Test
    void groupOnOneServiceRowCallsTheGrantedAndTheUngrantedService() {
        var row = scenario("V67");
        var granted = (RestService) row.fixture().services().get(0);
        var other = (RestService) row.fixture().services().get(1);
        assertThat(granted.grantedGroupCodes()).containsExactly("partners");
        assertThat(other.grantedGroupCodes()).isEmpty();
        assertThat(row.checks()).extracting(check -> check.request().serviceIndex()).containsExactly(0, 1);
    }

    @Test
    void stateRowsApplyTheirChangesToTheOnlyDescription() {
        assertThat(scenario("V120").fixture().changes())
                .containsExactly(new Scenario.Disable(0, ScenarioCatalogue.DISABLED_NOTICE));
        assertThat(scenario("V123").fixture().changes()).containsExactly(new Scenario.Disable(0, null));
        assertThat(scenario("V124").fixture().changes())
                .containsExactly(new Scenario.Disable(0, ScenarioCatalogue.DISABLED_NOTICE), new Scenario.Enable(0));
        assertThat(scenario("V125").fixture().changes()).containsExactly(new Scenario.Delete(0));
        assertThat(scenario("V127").fixture().changes()).containsExactly(new Scenario.RenameService(0));
    }

    @Test
    void onlyTheNoticeRowChecksTheNoticeText() {
        assertThat(scenario("V120").checks()).extracting(Scenario.Check::notice)
                .containsExactly(ScenarioCatalogue.DISABLED_NOTICE);
        assertThat(ScenarioCatalogue.all()).filteredOn(row -> !row.id().equals("V120"))
                .flatExtracting(Scenario::checks).extracting(Scenario.Check::notice).containsOnlyNulls();
    }

    @Test
    void disabledServiceRowWithoutAGrantCallsAsAnotherConsumer() {
        var row = scenario("V121");
        var service = (RestService) row.fixture().services().get(0);

        assertThat(service.grantedConsumerIds()).doesNotContain(row.checks().get(0).request().consumerClientId());
    }

    @Test
    void disabledWsdlRowCallsBothServicesOfTheDescription() {
        var row = scenario("V122");
        var wsdl = (WsdlService) row.fixture().services().get(0);

        assertThat(wsdl.shape()).isEqualTo(WsdlShape.PAIR);
        assertThat(row.checks()).extracting(check -> check.request().serviceIndex()).containsExactly(0, 1);
    }

    @Test
    void renameRowCallsTheOldCodeAndTheNewCode() {
        assertThat(scenario("V127").checks()).extracting(check -> check.request().serviceIndex())
                .containsExactly(0, 1);
    }

    @Test
    void undefinedServiceRowCallsOverRestAndSoap() {
        var row = scenario("V133");

        assertThat(row.fixture().services()).containsExactly(new Scenario.UndefinedService());
        assertThat(row.checks().get(0).request()).isInstanceOf(RestRequest.class);
        assertThat(row.checks().get(1).request()).isInstanceOf(SoapRequest.class);
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

    @Test
    void everyAllowedMetaserviceCheckStatesWhatTheReplyHasToSay() {
        assertThat(ScenarioCatalogue.all()).flatExtracting(Scenario::checks)
                .filteredOn(check -> check.request() instanceof MetaserviceRequest && check.expected() == Verdict.ALLOWED)
                .isNotEmpty()
                .allSatisfy(check -> assertThat(((MetaserviceRequest) check.request()).expectation().isEmpty())
                        .isFalse());
    }

    @Test
    void metaserviceIndicesPointAtAServiceCodeOfTheFixtureOrNone() {
        var metaserviceRows = ScenarioCatalogue.all().stream()
                .filter(row -> row.checks().stream().anyMatch(check -> check.request() instanceof MetaserviceRequest))
                .toList();

        assertThat(metaserviceRows).extracting(Scenario::id).containsExactly("V90", "V91", "V92", "V93", "V94", "V99");
        for (var row : metaserviceRows) {
            var codes = row.fixture().services().stream()
                    .mapToInt(service -> service instanceof WsdlService wsdl ? wsdl.shape().codeCount() : 1).sum();
            for (var check : row.checks()) {
                var request = (MetaserviceRequest) check.request();
                var positions = Stream.of(request.expectation().listed(), request.expectation().unlisted())
                        .flatMap(List::stream).collect(Collectors.toList());
                positions.add(request.serviceIndex());
                assertThat(positions).as(row.id()).allSatisfy(position -> assertThat(position)
                        .isBetween(MetaserviceRequest.NO_SERVICE, codes - 1));
                assertThat(request.expectation().listed()).as(row.id()).doesNotContain(MetaserviceRequest.NO_SERVICE);
                assertThat(request.expectation().unlisted()).as(row.id()).doesNotContain(MetaserviceRequest.NO_SERVICE);
            }
        }
    }

    @Test
    void metaserviceRowsAreUnmarkedPlainRows() {
        for (var id : List.of("V90", "V91", "V92", "V93", "V94", "V99")) {
            assertThat(scenario(id).knownDefect()).as(id).isNull();
        }
    }

    @Test
    void soapMetaserviceCallsAreSoapPostsAndRestOnesAreGets() {
        assertThat(ScenarioCatalogue.all()).flatExtracting(Scenario::checks)
                .map(Scenario.Check::request).filteredOn(MetaserviceRequest.class::isInstance)
                .map(MetaserviceRequest.class::cast)
                .filteredOn(request -> request.transport() == Scenario.Transport.SOAP)
                .isNotEmpty()
                .allSatisfy(request -> assertThat(request.method()).isEqualTo("POST"));
    }

    @Test
    void soap12RowReadsTheWsdlOfAGrantlessSoap12Service() {
        var row = scenario("V99");
        var wsdl = (WsdlService) row.fixture().services().get(0);
        var request = (MetaserviceRequest) row.checks().get(0).request();

        assertThat(wsdl.shape()).isEqualTo(WsdlShape.SOAP12);
        assertThat(wsdl.grantedConsumerIdsByCode()).containsExactly(List.of());
        assertThat(request.transport()).isEqualTo(Scenario.Transport.SOAP);
        assertThat(request.code()).isEqualTo("getWsdl");
        assertThat(request.expectation().contains()).containsExactly("example.org/xroad-endpoint");
        assertThat(request.expectation().without()).containsExactly("isrest:8080");
    }

    @Test
    void getWsdlOfAWsdlServiceNamesTheStubVersionAndTheOtherV92CallsNameNone() {
        assertThat(targetVersions("V92")).containsExactly("v1", null, null);
        assertThat(targetVersions("V99")).containsExactly("v1");
    }

    private static List<String> targetVersions(String id) {
        return scenario(id).checks().stream().map(check -> ((MetaserviceRequest) check.request()).targetVersion())
                .toList();
    }

    private static List<String> requestPaths(String id) {
        return scenario(id).checks().stream().map(check -> ((RestRequest) check.request()).path()).toList();
    }

    private static List<String> endpointPaths(String id) {
        return ((RestService) scenario(id).fixture().services().get(0)).endpoints().stream()
                .map(EndpointSpec::path).toList();
    }

    @Test
    void onlyTheFlipRowsHavePhasesAndEachPhaseMakesItsOwnChange() {
        assertThat(ScenarioCatalogue.all()).filteredOn(row -> !row.phases().isEmpty()).extracting(Scenario::id)
                .containsExactly("V10", "V11", "V39", "V74", "V75", "V106a", "V106b");
        assertThat(scenario("V106a").phases()).extracting(Scenario.Phase::changes)
                .containsExactly(List.of(new Scenario.Revoke(0, "DEV:security-server-owners"),
                        new Scenario.Grant(0, ScenarioCatalogue.CONSUMER)));
        assertThat(scenario("V106b").phases()).extracting(Scenario.Phase::changes)
                .containsExactly(List.of(new Scenario.GrantGroup(0, "partners")));
        assertThat(scenario("V74").phases()).extracting(Scenario.Phase::changes)
                .containsExactly(List.of(new Scenario.DeleteGlobalGroup("everyone")));
        assertThat(scenario("V75").phases()).extracting(Scenario.Phase::changes)
                .containsExactly(List.of(new Scenario.AddGlobalGroupMember("everyone", ScenarioCatalogue.CONSUMER)),
                        List.of(new Scenario.RemoveGlobalGroupMember("everyone", ScenarioCatalogue.CONSUMER)));
        assertThat(scenario("V10").phases()).extracting(Scenario.Phase::changes)
                .containsExactly(List.of(new Scenario.Grant(0, ScenarioCatalogue.CONSUMER)));
        assertThat(scenario("V11").phases()).extracting(Scenario.Phase::changes)
                .containsExactly(List.of(new Scenario.Revoke(0, ScenarioCatalogue.CONSUMER)));
        assertThat(scenario("V39").phases()).extracting(Scenario.Phase::changes)
                .containsExactly(List.of(new Scenario.SwapOpenApiSpec(0, OpenApiSpec.STATUS_ONLY),
                        new Scenario.Refresh(0)));
    }

    @Test
    void managementRowsSendARegistrationAsTheOwnerToTheManagementServiceOfTheManagementClient() {
        var ids = List.of("V100", "V101", "V106a", "V106b");

        assertThat(ScenarioCatalogue.all()).filteredOn(row -> ids.contains(row.id())).hasSameSizeAs(ids)
                .allSatisfy(row -> {
                    assertThat(row.fixture().providerClientId()).isEqualTo("DEV:COM:1234:MANAGEMENT");
                    assertThat(row.fixture().services()).singleElement().isInstanceOf(Scenario.ManagementService.class);
                    assertThat(allChecks(row).map(Scenario.Check::request))
                            .containsOnly(new Scenario.ManagementRequest(ScenarioCatalogue.MEMBER_OF_CONSUMER, 0));
                });
        assertThat(managementService("V100")).isEqualTo(new Scenario.ManagementService("clientReg", List.of(), List.of()));
        assertThat(managementService("V101").revokedConsumerIds()).containsExactly("DEV:security-server-owners");
        assertThat(managementService("V101").grantedConsumerIds()).isEmpty();
        assertThat(managementService("V106a")).isEqualTo(new Scenario.ManagementService("clientReg", List.of(), List.of()));
        assertThat(managementService("V106b").grantedConsumerIds()).isEmpty();
        assertThat(scenario("V106b").fixture().groups()).containsExactly(new LocalGroupSpec(
                "DEV:COM:1234:MANAGEMENT", "partners", List.of(ScenarioCatalogue.MEMBER_OF_CONSUMER)));
    }

    private static Scenario.ManagementService managementService(String id) {
        return (Scenario.ManagementService) scenario(id).fixture().services().get(0);
    }

    @Test
    void flipRowsCheckBothSidesOfTheFlip() {
        assertThat(scenario("V10").checks()).extracting(Scenario.Check::expected)
                .containsExactly(Verdict.ACCESS_DENIED);
        assertThat(scenario("V10").phases().get(0).checks()).extracting(Scenario.Check::expected)
                .containsExactly(Verdict.ALLOWED);
        assertThat(scenario("V11").checks()).extracting(Scenario.Check::expected)
                .containsOnly(Verdict.ALLOWED);
        assertThat(scenario("V11").phases().get(0).checks()).extracting(Scenario.Check::expected)
                .containsOnly(Verdict.ACCESS_DENIED);
    }

    @Test
    void globalGroupRowsWaitForGlobalConfiguration() {
        var ids = List.of("V68", "V69", "V70", "V71", "V72", "V73", "V74", "V75", "V77");

        assertThat(ScenarioCatalogue.all()).filteredOn(row -> ids.contains(row.id()))
                .hasSameSizeAs(ids)
                .allMatch(row -> row.waitClass() == Scenario.WaitClass.GLOBALCONF);
    }

    @Test
    void globalConfigurationBudgetCoversPropagationPlusTheAclCache() {
        var propagation = Duration.ofSeconds(30);
        var aclCache = Duration.ofSeconds(60);

        assertThat(Scenario.WaitClass.GLOBALCONF.timeout()).isGreaterThan(propagation.plus(aclCache))
                .isGreaterThan(Scenario.WaitClass.ACL_CACHE.timeout());
        assertThat(Scenario.WaitClass.ACL_CACHE.timeout()).isGreaterThan(aclCache);
    }

    private static List<Verdict> expectedVerdicts(Scenario scenario) {
        return allChecks(scenario).map(Scenario.Check::expected).toList();
    }

    private static Stream<Scenario.Check> allChecks(Scenario scenario) {
        return Stream.concat(scenario.checks().stream(),
                scenario.phases().stream().flatMap(phase -> phase.checks().stream()));
    }

    private static Scenario scenario(String id) {
        return ScenarioCatalogue.all().stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
    }
}
