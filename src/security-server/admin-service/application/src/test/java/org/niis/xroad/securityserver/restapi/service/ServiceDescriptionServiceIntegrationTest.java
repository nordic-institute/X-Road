/**
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.AccessRightType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.repository.ServiceDescriptionRepository;
import org.niis.xroad.securityserver.restapi.util.DeviationTestUtils;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.securityserver.restapi.wsdl.OpenApiParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.conf.serverconf.model.DescriptionType.WSDL;
import static ee.ria.xroad.common.conf.serverconf.model.EndpointType.ANY_METHOD;
import static ee.ria.xroad.common.conf.serverconf.model.EndpointType.ANY_PATH;
import static java.util.Collections.singleton;
import static java.util.function.Predicate.isEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * test ServiceDescription service.
 * Use SpyBean to override parseWsdl, so that we can use WSDL urls that
 * are independent of the files we actually read.
 */
public class ServiceDescriptionServiceIntegrationTest extends AbstractServiceIntegrationTestContext {

    @Autowired
    ServiceDescriptionService serviceDescriptionService;

    @Autowired
    ClientService clientService;

    @Autowired
    AccessRightService accessRightService;

    @Autowired
    ServiceDescriptionRepository serviceDescriptionRepository;

    public static final String BIG_ATTACHMENT_V1_SERVICECODE = "xroadBigAttachment.v1";
    public static final String SMALL_ATTACHMENT_V1_SERVICECODE = "xroadSmallAttachment.v1";
    public static final String GET_RANDOM_V1_SERVICECODE = "xroadGetRandom.v1";
    public static final String BIG_ATTACHMENT_SERVICECODE = "xroadBigAttachment";
    public static final String SMALL_ATTACHMENT_SERVICECODE = "xroadSmallAttachment";
    public static final String XROAD_GET_RANDOM_SERVICECODE = "xroadGetRandom";
    public static final String GET_RANDOM_SERVICECODE = "getRandom";
    public static final String CALCULATE_PRIME = "calculatePrime";
    public static final String HELLO_SERVICE = "helloService";
    public static final String BMI_SERVICE = "bodyMassIndex";
    public static final String SOAPSERVICEDESCRIPTION_URL = "https://soapservice.com/v1/Endpoint?wsdl";
    public static final String OAS3_SERVICE_URL = "https://example.org/api";

    public static final int SS1_ENDPOINTS = 7;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final ClientId.Conf CLIENT_ID_SS1 = ClientId.Conf.create("FI", "GOV", "M1", "SS1");
    private static final ClientId.Conf CLIENT_ID_SS6 = ClientId.Conf.create("FI", "GOV", "M2", "SS6");

    @Before
    public void setup() {
        when(urlValidator.isValidUrl(any())).thenReturn(true);
        when(openApiParser.allowProtocol(any())).thenReturn(true);
    }

    @Test
    @WithMockUser(authorities = "REFRESH_WSDL")
    public void refreshServiceDetectsAddedService() throws Exception {
        File testServiceWsdl = tempFolder.newFile("test.wsdl");
        File getRandomWsdl = TestUtils.getTestResourceFile("wsdl/valid-getrandom.wsdl");
        File threeServicesWsdl = TestUtils.getTestResourceFile("wsdl/valid.wsdl");
        FileUtils.copyFile(getRandomWsdl, testServiceWsdl);
        String url = testServiceWsdl.toURI().toURL().toString();
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1, url, false);

        // update wsdl to one with 3 services
        FileUtils.copyFile(threeServicesWsdl, testServiceWsdl);
        ClientType clientType = clientService.getLocalClient(CLIENT_ID_SS1);
        ServiceDescriptionType serviceDescriptionType = getServiceDescription(url, clientType);

        try {
            serviceDescriptionService.refreshServiceDescription(serviceDescriptionType.getId(),
                    false);
            fail("should throw exception warning about service addition");
        } catch (UnhandledWarningsException expected) {
            Assert.assertEquals(1, expected.getWarningDeviations().size());
            DeviationTestUtils.assertWarning(DeviationCodes.WARNING_ADDING_SERVICES, expected,
                    BIG_ATTACHMENT_V1_SERVICECODE, SMALL_ATTACHMENT_V1_SERVICECODE);
        }

        // with ignorewarnings, should succeed
        serviceDescriptionService.refreshServiceDescription(serviceDescriptionType.getId(),
                true);
        serviceDescriptionType = getServiceDescription(url, clientType);
        assertServiceCodes(serviceDescriptionType,
                BIG_ATTACHMENT_SERVICECODE, SMALL_ATTACHMENT_SERVICECODE, XROAD_GET_RANDOM_SERVICECODE);
    }

    @Test
    @WithMockUser(authorities = "REFRESH_WSDL")
    public void refreshServiceDetectsRemovedService() throws Exception {
        File testServiceWsdl = tempFolder.newFile("test.wsdl");
        File getRandomWsdl = TestUtils.getTestResourceFile("wsdl/valid-getrandom.wsdl");
        File threeServicesWsdl = TestUtils.getTestResourceFile("wsdl/valid.wsdl");
        FileUtils.copyFile(threeServicesWsdl, testServiceWsdl);
        String url = testServiceWsdl.toURI().toURL().toString();
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1,
                url,
                false);

        // update wsdl to one with just one service
        FileUtils.copyFile(getRandomWsdl, testServiceWsdl);
        ClientType clientType = clientService.getLocalClient(CLIENT_ID_SS1);
        ServiceDescriptionType serviceDescriptionType = getServiceDescription(url, clientType);

        try {
            serviceDescriptionService.refreshServiceDescription(serviceDescriptionType.getId(),
                    false);
            fail("should throw exception warning about service addition");
        } catch (UnhandledWarningsException expected) {
            Assert.assertEquals(1, expected.getWarningDeviations().size());
            DeviationTestUtils.assertWarning(DeviationCodes.WARNING_DELETING_SERVICES, expected,
                    BIG_ATTACHMENT_V1_SERVICECODE, SMALL_ATTACHMENT_V1_SERVICECODE);
        }

        // with ignorewarnings, should succeed
        serviceDescriptionService.refreshServiceDescription(serviceDescriptionType.getId(),
                true);
        serviceDescriptionType = getServiceDescription(url, clientType);
        assertServiceCodes(serviceDescriptionType,
                XROAD_GET_RANDOM_SERVICECODE);
    }

    @Test
    @WithMockUser(authorities = "REFRESH_WSDL")
    public void refreshServiceDetectsAllWarnings() throws Exception {
        // show warningDeviations about
        // - add service
        // - remove service
        // - validation warningDeviations

        // start with wsdl containing getrandom
        // then switch to one with smallattachment
        // and mock some warningDeviations
        File testServiceWsdl = tempFolder.newFile("test.wsdl");
        File getRandomWsdl = TestUtils.getTestResourceFile("wsdl/valid-getrandom.wsdl");
        File smallWsdl = TestUtils.getTestResourceFile("wsdl/valid-smallattachment.wsdl");
        FileUtils.copyFile(getRandomWsdl, testServiceWsdl);
        String url = testServiceWsdl.toURI().toURL().toString();
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1,
                url, false);
        ClientType clientType = clientService.getLocalClient(CLIENT_ID_SS1);
        ServiceDescriptionType serviceDescriptionType = getServiceDescription(url, clientType);

        // start mocking validation failures, when ignoreFailures = false
        List<String> mockValidationFailures = Arrays.asList("mock warning", "mock warning 2");
        doReturn(mockValidationFailures)
                .when(wsdlValidator).executeValidator(anyString());

        FileUtils.copyFile(smallWsdl, testServiceWsdl);

        try {
            serviceDescriptionService.refreshServiceDescription(serviceDescriptionType.getId(),
                    false);
            fail("should get warningDeviations");
        } catch (UnhandledWarningsException expected) {
            // we should get 3 warningDeviations
            Assert.assertEquals(3, expected.getWarningDeviations().size());
            DeviationTestUtils.assertWarning(DeviationCodes.WARNING_ADDING_SERVICES, expected,
                    SMALL_ATTACHMENT_V1_SERVICECODE);
            DeviationTestUtils.assertWarning(DeviationCodes.WARNING_DELETING_SERVICES, expected,
                    GET_RANDOM_V1_SERVICECODE);
            DeviationTestUtils.assertWarning(DeviationCodes.WARNING_WSDL_VALIDATION_WARNINGS, expected,
                    "mock warning", "mock warning 2");
        }

        // should be able to ignore them all
        serviceDescriptionService.refreshServiceDescription(serviceDescriptionType.getId(),
                true);
        serviceDescriptionType = getServiceDescription(url, clientType);
        assertServiceCodes(serviceDescriptionType,
                SMALL_ATTACHMENT_SERVICECODE);
    }

    @Test
    public void addWsdlServiceDescription() throws Exception {
        // check that validation warningDeviations work for adding, too
        File testServiceWsdl = tempFolder.newFile("test.wsdl");
        File getRandomWsdl = TestUtils.getTestResourceFile("wsdl/valid-getrandom.wsdl");
        FileUtils.copyFile(getRandomWsdl, testServiceWsdl);
        String url = testServiceWsdl.toURI().toURL().toString();
        // start mocking validation failures, when ignoreFailures = false
        List<String> mockValidationFailures = Arrays.asList("mock warning", "mock warning 2");
        doReturn(mockValidationFailures)
                .when(wsdlValidator).executeValidator(anyString());

        try {
            serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1,
                    url, false);
            fail("should get warningDeviations");
        } catch (UnhandledWarningsException expected) {
            // we should get 1 warning
            Assert.assertEquals(1, expected.getWarningDeviations().size());
            DeviationTestUtils.assertWarning(DeviationCodes.WARNING_WSDL_VALIDATION_WARNINGS, expected,
                    "mock warning", "mock warning 2");
        }
        // can be ignored
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1,
                url, true);
        ClientType clientType = clientService.getLocalClient(CLIENT_ID_SS1);
        ServiceDescriptionType serviceDescriptionType = getServiceDescription(url, clientType);
        assertServiceCodes(serviceDescriptionType, XROAD_GET_RANDOM_SERVICECODE);
    }

    @Test
    public void addWsdlServiceDescriptionWithIllegalServiceCode() throws Exception {
        try {
            serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1,
                    getTempWsdlFileUrl("wsdl/invalid-servicecode-colon.wsdl"), false);
            fail("should throw exception");
        } catch (ServiceDescriptionService.InvalidServiceIdentifierException expected) {
            Assert.assertEquals(Collections.singletonList("xroadGetRandom:aa"),
                    expected.getErrorDeviation().getMetadata());
        }
    }

    @Test
    public void addWsdlServiceDescriptionWithIllegalServiceCodeAll() throws Exception {
        try {
            serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1,
                    getTempWsdlFileUrl("wsdl/invalid-servicecode-all.wsdl"), false);
            fail("should throw exception");
        } catch (ServiceDescriptionService.InvalidServiceIdentifierException expected) {
            Set<String> invalidIdentifiers = new HashSet<>(Arrays.asList("xroadGetRandom:aa", "xroadGetRandom;aa",
                    "xroadGetRandom\\aa", "xroadGetRandom/aa", "xroadGetRandom%aa", "xroadGetRandom/../aa"));
            assertEquals(invalidIdentifiers, new HashSet(expected.getErrorDeviation().getMetadata()));
            Assert.assertEquals(invalidIdentifiers.size(), expected.getErrorDeviation().getMetadata().size());
        }
    }

    @Test
    public void addWsdlServiceDescriptionWithIllegalServiceVersion() throws Exception {
        try {
            serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1,
                    getTempWsdlFileUrl("wsdl/invalid-serviceversion-percent.wsdl"), false);
            fail("should throw exception");
        } catch (ServiceDescriptionService.InvalidServiceIdentifierException expected) {
            Assert.assertEquals(Collections.singletonList("v1%234"), expected.getErrorDeviation().getMetadata());
        }
    }

    /**
     * Copy test resource into temp file and return url
     */
    private String getTempWsdlFileUrl(String testResourceFile) throws IOException {
        File testServiceWsdl = tempFolder.newFile("test.wsdl");
        File getRandomWsdl = TestUtils.getTestResourceFile(testResourceFile);
        FileUtils.copyFile(getRandomWsdl, testServiceWsdl);
        return testServiceWsdl.toURI().toURL().toString();
    }

    /**
     * Same tests as {@link #refreshServiceDetectsAllWarnings()}, but triggered by update wsdl url
     */
    @Test
    public void updateWsdlUrlWithWarnings() throws Exception {
        // start with wsdl containing getrandom
        // then switch to one with smallattachment
        // and mock some warningDeviations
        File oldTestServiceWsdl = tempFolder.newFile("old-test.wsdl");
        File newTestServiceWsdl = tempFolder.newFile("new-test.wsdl");
        File getRandomWsdl = TestUtils.getTestResourceFile("wsdl/valid-getrandom.wsdl");
        File smallWsdl = TestUtils.getTestResourceFile("wsdl/valid-smallattachment.wsdl");
        FileUtils.copyFile(getRandomWsdl, oldTestServiceWsdl);
        FileUtils.copyFile(smallWsdl, newTestServiceWsdl);
        String oldUrl = oldTestServiceWsdl.toURI().toURL().toString();
        String newUrl = newTestServiceWsdl.toURI().toURL().toString();
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1,
                oldUrl, false);
        ClientType clientType = clientService.getLocalClient(CLIENT_ID_SS1);
        ServiceDescriptionType serviceDescriptionType = getServiceDescription(oldUrl, clientType);

        // start mocking validation failures, when ignoreFailures = false
        List<String> mockValidationFailures = Arrays.asList("mock warning", "mock warning 2");
        doReturn(mockValidationFailures)
                .when(wsdlValidator).executeValidator(anyString());

        try {
            serviceDescriptionService.updateWsdlUrl(serviceDescriptionType.getId(),
                    newUrl, false);
            fail("should get warningDeviations");
        } catch (UnhandledWarningsException expected) {
            // we should get 3 warningDeviations
            Assert.assertEquals(3, expected.getWarningDeviations().size());
            DeviationTestUtils.assertWarning(DeviationCodes.WARNING_ADDING_SERVICES, expected,
                    SMALL_ATTACHMENT_V1_SERVICECODE);
            DeviationTestUtils.assertWarning(DeviationCodes.WARNING_DELETING_SERVICES, expected,
                    GET_RANDOM_V1_SERVICECODE);
            DeviationTestUtils.assertWarning(DeviationCodes.WARNING_WSDL_VALIDATION_WARNINGS, expected,
                    "mock warning", "mock warning 2");
        }

        // ignore warningDeviations is tested with updateWsdlUrlAndIgnoreWarnings
    }

    /**
     * Separate from {@link #updateWsdlUrlWithWarnings()}, since the failed update prevents
     * next update (running inside same transaction, no rollback)
     */
    @Test
    public void updateWsdlUrlAndIgnoreWarnings() throws Exception {
        // start with wsdl containing getrandom
        // then switch to one with smallattachment
        // and mock some warningDeviations
        File oldTestServiceWsdl = tempFolder.newFile("old-test.wsdl");
        File newTestServiceWsdl = tempFolder.newFile("new-test.wsdl");
        File getRandomWsdl = TestUtils.getTestResourceFile("wsdl/valid-getrandom.wsdl");
        File smallWsdl = TestUtils.getTestResourceFile("wsdl/valid-smallattachment.wsdl");
        FileUtils.copyFile(getRandomWsdl, oldTestServiceWsdl);
        FileUtils.copyFile(smallWsdl, newTestServiceWsdl);
        String oldUrl = oldTestServiceWsdl.toURI().toURL().toString();
        String newUrl = newTestServiceWsdl.toURI().toURL().toString();
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1,
                oldUrl, false);
        ClientType clientType = clientService.getLocalClient(CLIENT_ID_SS1);
        ServiceDescriptionType serviceDescriptionType = getServiceDescription(oldUrl, clientType);

        // start mocking validation failures, when ignoreFailures = false
        List<String> mockValidationFailures = Arrays.asList("mock warning", "mock warning 2");
        doReturn(mockValidationFailures)
                .when(wsdlValidator).executeValidator(anyString());

        // should be able to ignore them all
        serviceDescriptionService.updateWsdlUrl(serviceDescriptionType.getId(),
                newUrl, true);
        serviceDescriptionType = getServiceDescription(newUrl, clientType);
        assertServiceCodes(serviceDescriptionType,
                SMALL_ATTACHMENT_SERVICECODE);

    }

    /**
     * Assert servicedescription contains the given codes. Checks codes only, no versions
     * @param serviceDescriptionType
     */
    private void assertServiceCodes(ServiceDescriptionType serviceDescriptionType, String... expectedCodes) {
        List<String> serviceCodes = serviceDescriptionType.getService()
                .stream()
                .map(service -> service.getServiceCode())
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(expectedCodes), serviceCodes);
    }

    private ServiceDescriptionType getServiceDescription(String url, ClientType clientType) {
        return clientType.getServiceDescription()
                .stream()
                .filter(sd -> sd.getUrl().equals(url))
                .findFirst().get();
    }

    @Test
    public void addWsdlServiceDescriptionAndCheckEndpoints() throws Exception {
        ClientType clientType = clientService.getLocalClient(CLIENT_ID_SS1);

        // 2 as set in data.sql
        assertEquals(SS1_ENDPOINTS, clientType.getEndpoint().size());
        assertTrue(clientType.getEndpoint()
                .stream()
                .map(EndpointType::getServiceCode)
                .collect(Collectors.toList())
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, CALCULATE_PRIME)));

        // add 3 more services
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1, "file:src/test/resources/wsdl/valid.wsdl",
                true);

        clientType = clientService.getLocalClient(CLIENT_ID_SS1);

        // 3 new endpoints saved: xroadSmallAttachment and xroadBigAttachment and xroadGetRandom
        assertEquals(SS1_ENDPOINTS + 3, clientType.getEndpoint().size());
        assertTrue(clientType.getEndpoint()
                .stream()
                .map(EndpointType::getServiceCode)
                .collect(Collectors.toList())
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, CALCULATE_PRIME, XROAD_GET_RANDOM_SERVICECODE,
                        BIG_ATTACHMENT_SERVICECODE, SMALL_ATTACHMENT_SERVICECODE)));
    }

    @Test
    public void updateWsdlServiceDescriptionAndCheckEndpoints() throws Exception {
        ClientType clientType = clientService.getLocalClient(CLIENT_ID_SS1);

        assertEquals(SS1_ENDPOINTS, clientType.getEndpoint().size());
        assertTrue(clientType.getEndpoint()
                .stream()
                .map(EndpointType::getServiceCode)
                .collect(Collectors.toList())
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, CALCULATE_PRIME)));

        ServiceDescriptionType serviceDescription = getServiceDescription(SOAPSERVICEDESCRIPTION_URL, clientType);

        serviceDescriptionService.updateWsdlUrl(serviceDescription.getId(),
                "file:src/test/resources/wsdl/valid-additional-services.wsdl", true);

        clientType = clientService.getLocalClient(CLIENT_ID_SS1);

        assertEquals(6, clientType.getEndpoint().size());
        assertTrue(clientType.getEndpoint()
                .stream()
                .map(EndpointType::getServiceCode)
                .collect(Collectors.toList())
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, HELLO_SERVICE)));
    }

    @Test
    public void removeWsdlServiceDescriptionAndCheckEndpoints() throws Exception {
        ClientType clientType = clientService.getLocalClient(CLIENT_ID_SS1);

        assertEquals(SS1_ENDPOINTS, clientType.getEndpoint().size());
        assertTrue(clientType.getEndpoint()
                .stream()
                .map(EndpointType::getServiceCode)
                .collect(Collectors.toList())
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, CALCULATE_PRIME)));

        ServiceDescriptionType serviceDescription = getServiceDescription(SOAPSERVICEDESCRIPTION_URL, clientType);

        serviceDescriptionService.deleteServiceDescription(serviceDescription.getId());

        clientType = clientService.getLocalClient(CLIENT_ID_SS1);

        assertEquals(4, clientType.getEndpoint().size());
    }

    @Test
    public void removeWsdlServiceDescriptionRetainsEndointsAndAccessRightsIfDifferentVersionExists() throws Exception {
        ClientType clientType = clientService.getLocalClient(CLIENT_ID_SS1);

        ServiceDescriptionType serviceDescription1 = createServiceDescription(clientType, "wsdl1");
        ServiceType serviceV1 = createServiceType("foo-service", "foo", "v1");
        serviceDescription1.getService().add(serviceV1);
        serviceDescriptionRepository.saveOrUpdate(serviceDescription1);

        ServiceDescriptionType serviceDescription2 = createServiceDescription(clientType, "wsdl2");
        ServiceType serviceV2 = createServiceType("foo-service", "foo", "v2");
        serviceDescription2.getService().add(serviceV2);
        serviceDescriptionRepository.saveOrUpdate(serviceDescription2);

        EndpointType endpointType = new EndpointType("foo", ANY_METHOD, ANY_PATH, true);
        clientType.getEndpoint().add(endpointType);

        doReturn(true).when(globalConfService).clientsExist(any());
        accessRightService.addServiceClientAccessRights(
                clientType.getIdentifier(),
                singleton("foo"),
                CLIENT_ID_SS6);


        clientType = clientService.getLocalClient(CLIENT_ID_SS1);

        assertTrue("Expecting endpoint for service 'foo' to be added",
                   clientType.getEndpoint().stream()
                           .map(EndpointType::getServiceCode)
                           .anyMatch(isEqual("foo")));
        assertTrue("Expecting access rights for service 'foo' not to added",
                   clientType.getAcl().stream()
                           .map(AccessRightType::getEndpoint)
                           .map(EndpointType::getServiceCode)
                           .anyMatch(isEqual("foo")));

        serviceDescriptionService.deleteServiceDescription(serviceDescription1.getId());

        clientType = clientService.getLocalClient(CLIENT_ID_SS1);
        assertTrue("Expecting endpoint for service 'foo' not to be removed as it's still referenced by v2",
                   clientType.getEndpoint().stream()
                           .map(EndpointType::getServiceCode)
                           .anyMatch(isEqual("foo")));
        assertTrue("Expecting access rights for service 'foo' not to be removed as it's still referenced by v2",
                   clientType.getAcl().stream()
                           .map(AccessRightType::getEndpoint)
                           .map(EndpointType::getServiceCode)
                           .anyMatch(isEqual("foo")));
    }

    private ServiceDescriptionType createServiceDescription(ClientType clientType, String wsdl) {
        ServiceDescriptionType serviceDescription1 = new ServiceDescriptionType();
        serviceDescription1.setClient(clientType);
        serviceDescription1.setUrl(wsdl);
        serviceDescription1.setType(WSDL);
        return serviceDescription1;
    }

    @Test
    @WithMockUser(authorities = "REFRESH_WSDL")
    public void refreshWsdlServiceDescriptionAndCheckEndpoints() throws Exception {
        ClientType clientType = clientService.getLocalClient(CLIENT_ID_SS1);

        assertEquals(SS1_ENDPOINTS, clientType.getEndpoint().size());
        assertTrue(clientType.getEndpoint()
                .stream()
                .map(EndpointType::getServiceCode)
                .collect(Collectors.toList())
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, CALCULATE_PRIME)));

        File testServiceWsdl = tempFolder.newFile("test.wsdl");
        File getRandomWsdl = TestUtils.getTestResourceFile("wsdl/valid.wsdl");
        File threeServicesWsdl = TestUtils.getTestResourceFile("wsdl/testservice.wsdl");
        FileUtils.copyFile(getRandomWsdl, testServiceWsdl);
        String url = testServiceWsdl.toURI().toURL().toString();
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1, url, true);

        FileUtils.copyFile(threeServicesWsdl, testServiceWsdl);
        clientType = clientService.getLocalClient(CLIENT_ID_SS1);
        ServiceDescriptionType serviceDescription = getServiceDescription(url, clientType);

        serviceDescriptionService.refreshServiceDescription(serviceDescription.getId(), true);

        clientType = clientService.getLocalClient(CLIENT_ID_SS1);

        assertEquals(SS1_ENDPOINTS + 2, clientType.getEndpoint().size());
        assertTrue(clientType.getEndpoint()
                .stream()
                .map(EndpointType::getServiceCode)
                .collect(Collectors.toList())
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, CALCULATE_PRIME, XROAD_GET_RANDOM_SERVICECODE,
                        BMI_SERVICE)));
    }

    @Test
    @WithMockUser(authorities = { "REFRESH_OPENAPI3" })
    public void refreshOpenapi3ServiceDescription() throws Exception {
        ServiceDescriptionType serviceDescriptiontype = serviceDescriptionService.getServiceDescriptiontype(6L);

        ClientType client = serviceDescriptiontype.getClient();

        assertEquals(5, getEndpointCountByServiceCode(client, "openapi3-test"));
        assertEquals(4, client.getAcl().size());
        assertTrue(client.getEndpoint().stream().filter(ep -> ep.getMethod().equals("POST")).count() == 1);

        serviceDescriptiontype.setUrl("file:src/test/resources/openapiparser/valid_modified.yaml");
        serviceDescriptionRepository.saveOrUpdate(serviceDescriptiontype);
        serviceDescriptionService.refreshServiceDescription(6L, false);

        List<EndpointType> endpoints = client.getEndpoint();
        assertEquals(5, getEndpointCountByServiceCode(client, "openapi3-test"));
        assertEquals(3, client.getAcl().size());
        assertFalse(endpoints.stream().anyMatch(ep -> ep.getMethod().equals("POST")));
        assertTrue(endpoints.stream().anyMatch(ep -> ep.getMethod().equals("PATCH")));

        // Assert that the pre-existing, manually added, endpoint is transformed to generated during update
        assertTrue(endpoints.stream()
                .anyMatch(ep -> ep.getServiceCode().equals("openapi3-test")
                        && ep.getMethod().equals("GET")
                        && ep.getPath().equals("/foo")
                        && ep.isGenerated()));

        assertTrue(endpoints.stream()
                .anyMatch(ep -> ep.getServiceCode().equals("openapi3-test")
                        && ep.getMethod().equals("*")
                        && ep.getPath().equals("**")));

        assertTrue(endpoints.stream()
                .anyMatch(ep -> ep.getServiceCode().equals("openapi3-test")
                        && ep.getMethod().equals("PUT")
                        && ep.getPath().equals("/foo")));
    }

    @Test
    @WithMockUser(authorities = { "REFRESH_OPENAPI3" })
    public void refreshOpenApi3ServiceDescriptionUpdatesDate() throws Exception {
        ServiceDescriptionType serviceDescriptiontype = serviceDescriptionService.getServiceDescriptiontype(6L);
        Date originalRefreshedDate = serviceDescriptiontype.getRefreshedDate();
        serviceDescriptionService.refreshServiceDescription(6L, false);
        assertTrue(originalRefreshedDate.compareTo(serviceDescriptiontype.getRefreshedDate()) < 0);
    }

    @Test
    @WithMockUser(authorities = "ADD_OPENAPI3")
    public void addRestEndpointServiceDescriptionSuccess() throws Exception {
        ClientType client = clientService.getLocalClient(CLIENT_ID_SS1);
        assertEquals(7, client.getEndpoint().size());
        serviceDescriptionService.addRestEndpointServiceDescription(CLIENT_ID_SS1, "http://testurl.com", "testcode");
        client = clientService.getLocalClient(CLIENT_ID_SS1);
        assertEquals(8, client.getEndpoint().size());
        assertTrue(client.getEndpoint().stream()
                .map(EndpointType::getServiceCode)
                .collect(Collectors.toList())
                .contains("testcode"));
        Boolean sslAuthentication = client.getServiceDescription().stream()
                .flatMap(sd -> sd.getService().stream())
                .filter(s -> s.getServiceCode().equals("testcode")).findFirst().get().getSslAuthentication();
        assertNull(sslAuthentication);
    }

    @Test
    @WithMockUser(authorities = "ADD_OPENAPI3")
    public void addRestEndpointServiceDescriptionWithHttpsUrl() throws Exception {
        ClientType client = clientService.getLocalClient(CLIENT_ID_SS1);
        assertEquals(7, client.getEndpoint().size());
        serviceDescriptionService.addRestEndpointServiceDescription(CLIENT_ID_SS1, "https://testurl.com", "testcode");
        Boolean sslAuthentication = client.getServiceDescription().stream()
                .flatMap(sd -> sd.getService().stream())
                .filter(s -> s.getServiceCode().equals("testcode")).findFirst().get().getSslAuthentication();
        assertFalse(Boolean.FALSE.equals(sslAuthentication));
    }

    @Test
    @WithMockUser(authorities = "ADD_OPENAPI3")
    public void addRestEndpoinServiceDescriptionWithDuplicateServiceCodes() throws Exception {
        ClientType client = clientService.getLocalClient(CLIENT_ID_SS1);
        assertTrue(client.getServiceDescription().stream()
                .flatMap(sd -> sd.getService().stream())
                .anyMatch(service -> service.getServiceCode().equals("getRandom")
                        && service.getServiceVersion().equals("v1")));

        // Test adding service with duplicate service code
        try {
            serviceDescriptionService.addRestEndpointServiceDescription(CLIENT_ID_SS1,
                    "http://testurl.com", "getRandom");
            throw new Exception("Should have thrown ServiceCodeAlreadyExistsException");
        } catch (ServiceDescriptionService.ServiceCodeAlreadyExistsException e) {
        }

        // Test adding service with duplicate full service code
        try {
            serviceDescriptionService.addRestEndpointServiceDescription(CLIENT_ID_SS1,
                    "http:://testurl.com", "getRandom.v1");
            throw new Exception("Should have thrown ServiceCodeAlreadyExistsException");
        } catch (ServiceDescriptionService.ServiceCodeAlreadyExistsException e) {
        }

    }

    @Test
    @WithMockUser(authorities = "ADD_OPENAPI3")
    public void addOpenApi3ServiceDescriptionSuccess() throws Exception {
        ClientType client = clientService.getLocalClient(CLIENT_ID_SS1);
        assertEquals(SS1_ENDPOINTS, client.getEndpoint().size());
        URL url = getClass().getResource("/openapiparser/valid.yaml");
        String urlString = url.toString();
        serviceDescriptionService.addOpenApi3ServiceDescription(CLIENT_ID_SS1, urlString, "testcode", false);

        client = clientService.getLocalClient(CLIENT_ID_SS1);
        assertEquals(SS1_ENDPOINTS + 3, client.getEndpoint().size());
        assertTrue(client.getEndpoint().stream()
                .map(EndpointType::getServiceCode)
                .filter(s -> "testcode".equals(s))
                .collect(Collectors.toList()).size() == 3);

        OpenApiParser.Result parsedOasResult = openApiParser.parse(urlString);
        assertEquals(OAS3_SERVICE_URL, parsedOasResult.getBaseUrl());
    }

    @Test
    @WithMockUser(authorities = "ADD_OPENAPI3")
    public void addOpenApi3ServiceDescriptionWithWarnings() throws Exception {
        ClientType client = clientService.getLocalClient(CLIENT_ID_SS1);
        assertEquals(SS1_ENDPOINTS, client.getEndpoint().size());
        URL url = getClass().getResource("/openapiparser/warnings.yml");
        boolean foundWarnings = false;
        try {
            serviceDescriptionService.addOpenApi3ServiceDescription(CLIENT_ID_SS1, url.toString(), "testcode", false);
        } catch (UnhandledWarningsException e) {
            foundWarnings = true;
        }
        assertTrue(foundWarnings);

        try {
            serviceDescriptionService.addOpenApi3ServiceDescription(CLIENT_ID_SS1, url.toString(), "testcode", true);
        } catch (UnhandledWarningsException e) {
            fail("Shouldn't throw warnings exception when ignorewarning is true");
        }

        client = clientService.getLocalClient(CLIENT_ID_SS1);
        assertEquals(SS1_ENDPOINTS + 3, client.getEndpoint().size());
    }

    @Test(expected = ServiceDescriptionService.ServiceCodeAlreadyExistsException.class)
    @WithMockUser(authorities = "ADD_OPENAPI3")
    public void addOpenApi3ServiceDescriptionWithDuplicateServiceCode() throws Exception {
        URL url1 = getClass().getResource("/openapiparser/valid.yaml");
        serviceDescriptionService.addOpenApi3ServiceDescription(CLIENT_ID_SS1, url1.toString(), "testcode", false);

        // Should throw ServiceCodeAlreadyExistsException
        URL url2 = getClass().getResource("/openapiparser/warnings.yml");
        serviceDescriptionService.addOpenApi3ServiceDescription(CLIENT_ID_SS1, url2.toString(), "testcode", true);
    }

    @Test(expected = ServiceDescriptionService.UrlAlreadyExistsException.class)
    @WithMockUser(authorities = "ADD_OPENAPI3")
    public void addOpenApi3ServiceDescriptionWithDuplicateUrl() throws Exception {
        URL url = getClass().getResource("/openapiparser/valid.yaml");
        serviceDescriptionService.addOpenApi3ServiceDescription(CLIENT_ID_SS1, url.toString(), "testcode1", false);

        // should throw UrlAlreadyExistsException
        serviceDescriptionService.addOpenApi3ServiceDescription(CLIENT_ID_SS1, url.toString(), "testcode2", false);
    }

    @Test
    @WithMockUser(authorities = "EDIT_REST")
    public void updateRestServiceDescriptionSuccess() throws Exception {
        final String serviceCode = "rest-servicecode";
        final String newServiceCode = "new-rest-servicecode";

        ClientType client = clientService.getLocalClient(CLIENT_ID_SS1);
        ServiceDescriptionType serviceDescription = serviceDescriptionService.getServiceDescriptiontype(5L);

        assertEquals(3, getEndpointCountByServiceCode(client, serviceCode));
        assertTrue(serviceDescriptionContainsServiceWithServiceCode(serviceDescription, serviceCode));

        serviceDescriptionService.updateRestServiceDescription(5L, "https://restservice.com/api/v1/nosuchservice",
                serviceCode, newServiceCode);

        assertEquals(3, getEndpointCountByServiceCode(client, newServiceCode));
        assertTrue(serviceDescriptionContainsServiceWithServiceCode(serviceDescription, newServiceCode));

        assertEquals(0, getEndpointCountByServiceCode(client, serviceCode));
        assertFalse(serviceDescriptionContainsServiceWithServiceCode(serviceDescription, serviceCode));
    }

    private boolean serviceDescriptionContainsServiceWithServiceCode(ServiceDescriptionType serviceDescription,
            String serviceCode) {
        return serviceDescription.getService().stream()
                .map(s -> s.getServiceCode())
                .collect(Collectors.toList())
                .contains(serviceCode);
    }

    private int getEndpointCountByServiceCode(ClientType client, String serviceCode) {
        return client.getEndpoint().stream()
                .map(e -> e.getServiceCode())
                .filter(sc -> serviceCode.equals(sc))
                .collect(Collectors.toList())
                .size();
    }

    @Test
    @WithMockUser(authorities = "EDIT_OPENAPI3")
    public void updateOpenApi3ServiceDescriptionSuccess() throws Exception {
        URL url = getClass().getResource("/openapiparser/valid_modified.yaml");

        ClientType client = clientService.getLocalClient(CLIENT_ID_SS6);
        assertEquals(5, getEndpointCountByServiceCode(client, "openapi3-test"));
        assertEquals(4, client.getAcl().size());
        assertTrue(client.getEndpoint().stream().filter(ep -> ep.getMethod().equals("POST")).count() == 1);

        serviceDescriptionService.updateOpenApi3ServiceDescription(6L, url.toString(), "openapi3-test",
                "openapi3-test", false);

        List<EndpointType> endpoints = client.getEndpoint();
        assertEquals(5, getEndpointCountByServiceCode(client, "openapi3-test"));
        assertEquals(3, client.getAcl().size());
        assertFalse(endpoints.stream().anyMatch(ep -> ep.getMethod().equals("POST")));
        assertTrue(endpoints.stream().anyMatch(ep -> ep.getMethod().equals("PATCH")));

        // Assert that the pre-existing, manually added, endpoint is transformed to generated during update
        assertTrue(endpoints.stream()
                .anyMatch(ep -> ep.getServiceCode().equals("openapi3-test")
                        && ep.getMethod().equals("GET")
                        && ep.getPath().equals("/foo")
                        && ep.isGenerated()));

        assertTrue(endpoints.stream()
                .anyMatch(ep -> ep.getServiceCode().equals("openapi3-test")
                        && ep.getMethod().equals("*")
                        && ep.getPath().equals("**")));

        assertTrue(endpoints.stream()
                .anyMatch(ep -> ep.getServiceCode().equals("openapi3-test")
                        && ep.getMethod().equals("PUT")
                        && ep.getPath().equals("/foo")));

    }

    @Test
    public void getServiceTitle() throws Exception {
        // create a transient client for testing
        ClientType testClient = new ClientType();
        ServiceDescriptionType sd1 = new ServiceDescriptionType();
        testClient.getServiceDescription().add(sd1);

        // backend returns the title of the first matching service (which can be null or empty),
        // or null if no matches
        assertEquals(null, serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getService().add(createServiceType("bar-title", "bar", "v2"));
        assertEquals(null, serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getService().clear();
        sd1.getService().add(createServiceType(null, "foo", "v1"));
        assertEquals(null, serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getService().clear();
        sd1.getService().add(createServiceType("", "foo", "v2"));
        assertEquals("", serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getService().clear();
        sd1.getService().add(createServiceType(null, "foo", "v1"));
        sd1.getService().add(createServiceType("v3-title", "foo", "v3"));
        assertEquals("v3-title", serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getService().clear();
        sd1.getService().add(createServiceType("", "foo", "v2"));
        sd1.getService().add(createServiceType("v3-title", "foo", "v3"));
        assertEquals("v3-title", serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getService().clear();
        sd1.getService().add(createServiceType("v4-title", "foo", "v4"));
        sd1.getService().add(createServiceType("v3-title", "foo", "v3"));
        sd1.getService().add(createServiceType("v5-title", "foo", "v5"));

        assertEquals("v5-title", serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getService().clear();
        sd1.getService().add(createServiceType("AAA-title", "foo", "AAA"));
        sd1.getService().add(createServiceType("XYZ-title", "foo", "XYZ"));
        sd1.getService().add(createServiceType("v1-title", "foo", "v1"));
        sd1.getService().add(createServiceType("V1`-title", "foo", "V1`"));

        assertEquals("XYZ-title", serviceDescriptionService.getServiceTitle(testClient, "foo"));
    }

    private ServiceType createServiceType(String title, String serviceCode, String serviceVersion) {
        ServiceType serviceFooNullTitle = new ServiceType();
        serviceFooNullTitle.setTitle(title);
        serviceFooNullTitle.setServiceCode(serviceCode);
        serviceFooNullTitle.setServiceVersion(serviceVersion);
        return serviceFooNullTitle;
    }

}
