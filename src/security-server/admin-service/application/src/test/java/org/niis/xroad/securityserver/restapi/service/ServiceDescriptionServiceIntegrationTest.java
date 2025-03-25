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
package org.niis.xroad.securityserver.restapi.service;

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
import org.niis.xroad.serverconf.entity.AccessRightEntity;
import org.niis.xroad.serverconf.entity.ClientEntity;
import org.niis.xroad.serverconf.entity.EndpointEntity;
import org.niis.xroad.serverconf.entity.ServiceDescriptionEntity;
import org.niis.xroad.serverconf.entity.ServiceEntity;
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

import static java.util.Collections.singleton;
import static java.util.function.Predicate.isEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.niis.xroad.serverconf.model.BaseEndpoint.ANY_METHOD;
import static org.niis.xroad.serverconf.model.BaseEndpoint.ANY_PATH;
import static org.niis.xroad.serverconf.model.DescriptionType.WSDL;

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
        ClientEntity clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        ServiceDescriptionEntity serviceDescriptionEntity = getServiceDescription(url, clientEntity);

        try {
            serviceDescriptionService.refreshServiceDescription(serviceDescriptionEntity.getId(),
                    false);
            fail("should throw exception warning about service addition");
        } catch (UnhandledWarningsException expected) {
            Assert.assertEquals(1, expected.getWarningDeviations().size());
            DeviationTestUtils.assertWarning(DeviationCodes.WARNING_ADDING_SERVICES, expected,
                    BIG_ATTACHMENT_V1_SERVICECODE, SMALL_ATTACHMENT_V1_SERVICECODE);
        }

        // with ignorewarnings, should succeed
        serviceDescriptionService.refreshServiceDescription(serviceDescriptionEntity.getId(),
                true);
        serviceDescriptionEntity = getServiceDescription(url, clientEntity);
        assertServiceCodes(serviceDescriptionEntity,
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
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1, url, false);

        // update wsdl to one with just one service
        FileUtils.copyFile(getRandomWsdl, testServiceWsdl);
        ClientEntity clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        ServiceDescriptionEntity serviceDescriptionEntity = getServiceDescription(url, clientEntity);

        try {
            serviceDescriptionService.refreshServiceDescription(serviceDescriptionEntity.getId(), false);
            fail("should throw exception warning about service addition");
        } catch (UnhandledWarningsException expected) {
            Assert.assertEquals(1, expected.getWarningDeviations().size());
            DeviationTestUtils.assertWarning(DeviationCodes.WARNING_DELETING_SERVICES, expected,
                    BIG_ATTACHMENT_V1_SERVICECODE, SMALL_ATTACHMENT_V1_SERVICECODE);
        }

        // with ignorewarnings, should succeed
        serviceDescriptionService.refreshServiceDescription(serviceDescriptionEntity.getId(), true);
        serviceDescriptionEntity = getServiceDescription(url, clientEntity);
        assertServiceCodes(serviceDescriptionEntity, XROAD_GET_RANDOM_SERVICECODE);
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
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1, url, false);
        ClientEntity clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        ServiceDescriptionEntity serviceDescriptionEntity = getServiceDescription(url, clientEntity);

        // start mocking validation failures, when ignoreFailures = false
        List<String> mockValidationFailures = Arrays.asList("mock warning", "mock warning 2");
        doReturn(mockValidationFailures)
                .when(wsdlValidator).executeValidator(anyString());

        FileUtils.copyFile(smallWsdl, testServiceWsdl);

        try {
            serviceDescriptionService.refreshServiceDescription(serviceDescriptionEntity.getId(), false);
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
        serviceDescriptionService.refreshServiceDescription(serviceDescriptionEntity.getId(),
                true);
        serviceDescriptionEntity = getServiceDescription(url, clientEntity);
        assertServiceCodes(serviceDescriptionEntity, SMALL_ATTACHMENT_SERVICECODE);
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
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1, url, true);
        ClientEntity clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        ServiceDescriptionEntity serviceDescriptionEntity = getServiceDescription(url, clientEntity);
        assertServiceCodes(serviceDescriptionEntity, XROAD_GET_RANDOM_SERVICECODE);
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
            assertEquals(invalidIdentifiers, new HashSet<>(expected.getErrorDeviation().getMetadata()));
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
        ClientEntity clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        ServiceDescriptionEntity serviceDescriptionEntity = getServiceDescription(oldUrl, clientEntity);

        // start mocking validation failures, when ignoreFailures = false
        List<String> mockValidationFailures = Arrays.asList("mock warning", "mock warning 2");
        doReturn(mockValidationFailures)
                .when(wsdlValidator).executeValidator(anyString());

        try {
            serviceDescriptionService.updateWsdlUrl(serviceDescriptionEntity.getId(),
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
        ClientEntity clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        ServiceDescriptionEntity serviceDescriptionEntity = getServiceDescription(oldUrl, clientEntity);

        // start mocking validation failures, when ignoreFailures = false
        List<String> mockValidationFailures = Arrays.asList("mock warning", "mock warning 2");
        doReturn(mockValidationFailures)
                .when(wsdlValidator).executeValidator(anyString());

        // should be able to ignore them all
        serviceDescriptionService.updateWsdlUrl(serviceDescriptionEntity.getId(), newUrl, true);
        serviceDescriptionEntity = getServiceDescription(newUrl, clientEntity);
        assertServiceCodes(serviceDescriptionEntity, SMALL_ATTACHMENT_SERVICECODE);
    }

    /**
     * Assert service description contains the given codes. Checks codes only, no versions
     * @param serviceDescriptionEntity service description to check
     */
    private void assertServiceCodes(ServiceDescriptionEntity serviceDescriptionEntity, String... expectedCodes) {
        List<String> serviceCodes = serviceDescriptionEntity.getServices()
                .stream()
                .map(org.niis.xroad.serverconf.entity.ServiceEntity::getServiceCode)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(expectedCodes), serviceCodes);
    }

    private ServiceDescriptionEntity getServiceDescription(String url, ClientEntity clientEntity) {
        return clientEntity.getServiceDescriptions()
                .stream()
                .filter(sd -> sd.getUrl().equals(url))
                .findFirst().get();
    }

    @Test
    public void addWsdlServiceDescriptionAndCheckEndpoints() throws Exception {
        ClientEntity clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);

        // 2 as set in data.sql
        assertEquals(SS1_ENDPOINTS, clientEntity.getEndpoints().size());
        assertTrue(clientEntity.getEndpoints()
                .stream()
                .map(EndpointEntity::getServiceCode)
                .toList()
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, CALCULATE_PRIME)));

        // add 3 more services
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1, "file:src/test/resources/wsdl/valid.wsdl",
                true);

        clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);

        // 3 new endpoints saved: xroadSmallAttachment and xroadBigAttachment and xroadGetRandom
        assertEquals(SS1_ENDPOINTS + 3, clientEntity.getEndpoints().size());
        assertTrue(clientEntity.getEndpoints()
                .stream()
                .map(EndpointEntity::getServiceCode)
                .toList()
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, CALCULATE_PRIME, XROAD_GET_RANDOM_SERVICECODE,
                        BIG_ATTACHMENT_SERVICECODE, SMALL_ATTACHMENT_SERVICECODE)));
    }

    @Test
    public void updateWsdlServiceDescriptionAndCheckEndpoints() throws Exception {
        ClientEntity clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);

        assertEquals(SS1_ENDPOINTS, clientEntity.getEndpoints().size());
        assertTrue(clientEntity.getEndpoints()
                .stream()
                .map(EndpointEntity::getServiceCode)
                .toList()
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, CALCULATE_PRIME)));

        ServiceDescriptionEntity serviceDescription = getServiceDescription(SOAPSERVICEDESCRIPTION_URL, clientEntity);

        serviceDescriptionService.updateWsdlUrl(serviceDescription.getId(),
                "file:src/test/resources/wsdl/valid-additional-services.wsdl", true);

        var serviceDescriptionEntity = serviceDescriptionService.getServiceDescriptionEntity(serviceDescription.getId());
        clientEntity = serviceDescriptionEntity.getClient();

        assertEquals(6, clientEntity.getEndpoints().size());
        assertTrue(clientEntity.getEndpoints()
                .stream()
                .map(EndpointEntity::getServiceCode)
                .toList()
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, HELLO_SERVICE)));
    }

    @Test
    public void removeWsdlServiceDescriptionAndCheckEndpoints() throws Exception {
        ClientEntity clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);

        assertEquals(SS1_ENDPOINTS, clientEntity.getEndpoints().size());
        assertTrue(clientEntity.getEndpoints()
                .stream()
                .map(EndpointEntity::getServiceCode)
                .toList()
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, CALCULATE_PRIME)));

        ServiceDescriptionEntity serviceDescription = getServiceDescription(SOAPSERVICEDESCRIPTION_URL, clientEntity);

        serviceDescriptionService.deleteServiceDescription(serviceDescription.getId());

        clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);

        assertEquals(4, clientEntity.getEndpoints().size());
    }

    @Test
    public void removeWsdlServiceDescriptionRetainsEndointsAndAccessRightsIfDifferentVersionExists() throws Exception {
        ClientEntity clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);

        ServiceDescriptionEntity serviceDescription1 = createServiceDescription(clientEntity, "wsdl1");
        ServiceEntity serviceV1 = createServiceType("foo-service", "foo", "v1");
        serviceV1.setServiceDescription(serviceDescription1);
        serviceDescription1.getServices().add(serviceV1);
        serviceDescriptionRepository.persist(serviceDescription1);

        ServiceDescriptionEntity serviceDescription2 = createServiceDescription(clientEntity, "wsdl2");
        ServiceEntity serviceV2 = createServiceType("foo-service", "foo", "v2");
        serviceV2.setServiceDescription(serviceDescription2);
        serviceDescription2.getServices().add(serviceV2);
        serviceDescriptionRepository.persist(serviceDescription2);

        EndpointEntity endpointEntity = EndpointEntity.create("foo", ANY_METHOD, ANY_PATH, true);
        clientEntity.getEndpoints().add(endpointEntity);

        doReturn(true).when(globalConfService).clientsExist(any());
        accessRightService.addServiceClientAccessRights(
                clientEntity.getIdentifier(),
                singleton("foo"),
                CLIENT_ID_SS6);


        clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);

        assertTrue("Expecting endpoint for service 'foo' to be added",
                clientEntity.getEndpoints().stream()
                        .map(EndpointEntity::getServiceCode)
                        .anyMatch(isEqual("foo")));
        assertTrue("Expecting access rights for service 'foo' not to added",
                clientEntity.getAccessRights().stream()
                        .map(AccessRightEntity::getEndpoint)
                        .map(EndpointEntity::getServiceCode)
                        .anyMatch(isEqual("foo")));

        serviceDescriptionService.deleteServiceDescription(serviceDescription1.getId());

        clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        assertTrue("Expecting endpoint for service 'foo' not to be removed as it's still referenced by v2",
                clientEntity.getEndpoints().stream()
                        .map(EndpointEntity::getServiceCode)
                        .anyMatch(isEqual("foo")));
        assertTrue("Expecting access rights for service 'foo' not to be removed as it's still referenced by v2",
                clientEntity.getAccessRights().stream()
                        .map(AccessRightEntity::getEndpoint)
                        .map(EndpointEntity::getServiceCode)
                        .anyMatch(isEqual("foo")));
    }

    private ServiceDescriptionEntity createServiceDescription(ClientEntity clientEntity, String wsdl) {
        ServiceDescriptionEntity serviceDescriptionEntity = new ServiceDescriptionEntity();
        serviceDescriptionEntity.setClient(clientEntity);
        serviceDescriptionEntity.setUrl(wsdl);
        serviceDescriptionEntity.setType(WSDL);
        return serviceDescriptionEntity;
    }

    @Test
    @WithMockUser(authorities = "REFRESH_WSDL")
    public void refreshWsdlServiceDescriptionAndCheckEndpoints() throws Exception {
        ClientEntity clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);

        assertEquals(SS1_ENDPOINTS, clientEntity.getEndpoints().size());
        assertTrue(clientEntity.getEndpoints()
                .stream()
                .map(EndpointEntity::getServiceCode)
                .toList()
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, CALCULATE_PRIME)));

        File testServiceWsdl = tempFolder.newFile("test.wsdl");
        File getRandomWsdl = TestUtils.getTestResourceFile("wsdl/valid.wsdl");
        File threeServicesWsdl = TestUtils.getTestResourceFile("wsdl/testservice.wsdl");
        FileUtils.copyFile(getRandomWsdl, testServiceWsdl);
        String url = testServiceWsdl.toURI().toURL().toString();
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1, url, true);

        FileUtils.copyFile(threeServicesWsdl, testServiceWsdl);
        clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        ServiceDescriptionEntity serviceDescription = getServiceDescription(url, clientEntity);

        serviceDescriptionService.refreshServiceDescription(serviceDescription.getId(), true);

        clientEntity = clientService.getLocalClientEntity(CLIENT_ID_SS1);

        assertEquals(SS1_ENDPOINTS + 2, clientEntity.getEndpoints().size());
        assertTrue(clientEntity.getEndpoints()
                .stream()
                .map(EndpointEntity::getServiceCode)
                .toList()
                .containsAll(Arrays.asList(GET_RANDOM_SERVICECODE, CALCULATE_PRIME, XROAD_GET_RANDOM_SERVICECODE,
                        BMI_SERVICE)));
    }

    @Test
    @WithMockUser(authorities = {"REFRESH_OPENAPI3"})
    public void refreshOpenapi3ServiceDescriptionSuccess() throws Exception {
        ServiceDescriptionEntity serviceDescriptionEntity = serviceDescriptionService.getServiceDescriptionEntity(6L);

        ClientEntity client = serviceDescriptionEntity.getClient();

        assertEquals(5, getEndpointCountByServiceCode(client, "openapi3-test"));
        assertEquals(4, client.getAccessRights().size());
        assertEquals(1, client.getEndpoints().stream().filter(ep -> ep.getMethod().equals("POST")).count());

        serviceDescriptionEntity.setUrl("file:src/test/resources/openapiparser/valid.yaml");
        serviceDescriptionRepository.saveOrUpdate(serviceDescriptionEntity);
        serviceDescriptionService.refreshServiceDescription(6L, false);

        List<EndpointEntity> endpoints = client.getEndpoints();
        assertEquals(5, getEndpointCountByServiceCode(client, "openapi3-test"));
        assertEquals(4, client.getAccessRights().size());

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
    @WithMockUser(authorities = {"REFRESH_OPENAPI3"})
    public void refreshOpenapi3ServiceDescriptionWithWarnings() throws Exception {
        ServiceDescriptionEntity serviceDescriptionEntity = serviceDescriptionService.getServiceDescriptionEntity(6L);

        ClientEntity client = serviceDescriptionEntity.getClient();

        assertEquals(5, getEndpointCountByServiceCode(client, "openapi3-test"));
        assertEquals(4, client.getAccessRights().size());
        assertEquals(1, client.getEndpoints().stream().filter(ep -> ep.getMethod().equals("POST")).count());

        serviceDescriptionEntity.setUrl("file:src/test/resources/openapiparser/valid_modified.yaml");
        serviceDescriptionRepository.saveOrUpdate(serviceDescriptionEntity);
        boolean foundWarnings = false;
        try {
            serviceDescriptionService.refreshServiceDescription(6L, false);
        } catch (UnhandledWarningsException e) {
            foundWarnings = true;
        }
        assertTrue(foundWarnings);

        try {
            serviceDescriptionService.refreshServiceDescription(6L, true);
        } catch (UnhandledWarningsException e) {
            fail("Shouldn't throw warnings exception when ignorewarning is true");
        }

        List<EndpointEntity> endpoints = client.getEndpoints();
        assertEquals(5, getEndpointCountByServiceCode(client, "openapi3-test"));
        assertEquals(3, client.getAccessRights().size());
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
    @WithMockUser(authorities = {"REFRESH_OPENAPI3"})
    public void refreshOpenApi3ServiceDescriptionUpdatesDate() throws Exception {

        ServiceDescriptionEntity serviceDescriptionEntity = serviceDescriptionService.getServiceDescriptionEntity(6L);
        Date originalRefreshedDate = serviceDescriptionEntity.getRefreshedDate();
        serviceDescriptionService.refreshServiceDescription(6L, false);
        assertTrue(originalRefreshedDate.compareTo(serviceDescriptionEntity.getRefreshedDate()) < 0);
    }

    @Test
    @WithMockUser(authorities = "ADD_OPENAPI3")
    public void addRestEndpointServiceDescriptionSuccess() throws Exception {
        ClientEntity client = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        assertEquals(7, client.getEndpoints().size());
        serviceDescriptionService.addRestEndpointServiceDescription(CLIENT_ID_SS1, "http://testurl.com", "testcode");
        client = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        assertEquals(8, client.getEndpoints().size());
        assertTrue(client.getEndpoints().stream()
                .map(EndpointEntity::getServiceCode)
                .toList()
                .contains("testcode"));
        Boolean sslAuthentication = client.getServiceDescriptions().stream()
                .flatMap(sd -> sd.getServices().stream())
                .filter(s -> s.getServiceCode().equals("testcode"))
                .findFirst()
                .get().getSslAuthentication();
        assertNull(sslAuthentication);
    }

    @Test
    @WithMockUser(authorities = "ADD_OPENAPI3")
    public void addRestEndpointServiceDescriptionWithHttpsUrl() throws Exception {
        ClientEntity client = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        assertEquals(7, client.getEndpoints().size());
        serviceDescriptionService.addRestEndpointServiceDescription(CLIENT_ID_SS1, "https://testurl.com", "testcode");
        Boolean sslAuthentication = client.getServiceDescriptions().stream()
                .flatMap(sd -> sd.getServices().stream())
                .filter(s -> s.getServiceCode().equals("testcode"))
                .findFirst()
                .get().getSslAuthentication();
        assertNotEquals(Boolean.FALSE, sslAuthentication);
    }

    @Test
    @WithMockUser(authorities = "ADD_OPENAPI3")
    public void addRestEndpoinServiceDescriptionWithDuplicateServiceCodes() throws Exception {
        ClientEntity client = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        assertTrue(client.getServiceDescriptions().stream()
                .flatMap(sd -> sd.getServices().stream())
                .anyMatch(service -> service.getServiceCode().equals("getRandom")
                        && service.getServiceVersion().equals("v1")));

        // Test adding service with duplicate service code
        try {
            serviceDescriptionService.addRestEndpointServiceDescription(CLIENT_ID_SS1,
                    "http://testurl.com", "getRandom");
            throw new Exception("Should have thrown ServiceCodeAlreadyExistsException");
        } catch (ServiceDescriptionService.ServiceCodeAlreadyExistsException ignored) {
        }

        // Test adding service with duplicate full service code
        try {
            serviceDescriptionService.addRestEndpointServiceDescription(CLIENT_ID_SS1,
                    "http:://testurl.com", "getRandom.v1");
            throw new Exception("Should have thrown ServiceCodeAlreadyExistsException");
        } catch (ServiceDescriptionService.ServiceCodeAlreadyExistsException ignored) {
        }

    }

    @Test
    @WithMockUser(authorities = "ADD_OPENAPI3")
    public void addOpenApi3ServiceDescriptionSuccess() throws Exception {
        ClientEntity client = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        assertEquals(SS1_ENDPOINTS, client.getEndpoints().size());
        URL url = getClass().getResource("/openapiparser/valid.yaml");
        String urlString = url.toString();
        serviceDescriptionService.addOpenApi3ServiceDescription(CLIENT_ID_SS1, urlString, "testcode", false);

        client = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        assertEquals(SS1_ENDPOINTS + 3, client.getEndpoints().size());
        assertEquals(3, client.getEndpoints().stream()
                .map(EndpointEntity::getServiceCode)
                .filter("testcode"::equals)
                .count());

        OpenApiParser.Result parsedOasResult = openApiParser.parse(urlString);
        assertEquals(OAS3_SERVICE_URL, parsedOasResult.getBaseUrl());
    }

    @Test
    @WithMockUser(authorities = "ADD_OPENAPI3")
    public void addOpenApi3ServiceDescriptionWithWarnings() throws Exception {
        ClientEntity client = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        assertEquals(SS1_ENDPOINTS, client.getEndpoints().size());
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

        client = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        assertEquals(SS1_ENDPOINTS + 3, client.getEndpoints().size());
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

        ClientEntity client = clientService.getLocalClientEntity(CLIENT_ID_SS1);
        ServiceDescriptionEntity serviceDescriptionEntity = serviceDescriptionService.getServiceDescriptionEntity(5L);

        assertEquals(3, getEndpointCountByServiceCode(client, serviceCode));
        assertTrue(serviceDescriptionContainsServiceWithServiceCode(serviceDescriptionEntity, serviceCode));

        serviceDescriptionService.updateRestServiceDescription(5L, "https://restservice.com/api/v1/nosuchservice",
                serviceCode, newServiceCode);

        assertEquals(3, getEndpointCountByServiceCode(client, newServiceCode));
        assertTrue(serviceDescriptionContainsServiceWithServiceCode(serviceDescriptionEntity, newServiceCode));

        assertEquals(0, getEndpointCountByServiceCode(client, serviceCode));
        assertFalse(serviceDescriptionContainsServiceWithServiceCode(serviceDescriptionEntity, serviceCode));
    }

    private boolean serviceDescriptionContainsServiceWithServiceCode(ServiceDescriptionEntity serviceDescription,
                                                                     String serviceCode) {
        return serviceDescription.getServices().stream()
                .map(org.niis.xroad.serverconf.entity.ServiceEntity::getServiceCode)
                .toList()
                .contains(serviceCode);
    }

    private int getEndpointCountByServiceCode(ClientEntity client, String serviceCode) {
        return client.getEndpoints().stream()
                .map(EndpointEntity::getServiceCode)
                .filter(serviceCode::equals)
                .toList()
                .size();
    }

    @Test
    @WithMockUser(authorities = "EDIT_OPENAPI3")
    public void updateOpenApi3ServiceDescriptionSuccess() throws Exception {
        URL url = getClass().getResource("/openapiparser/valid.yaml");

        ClientEntity client = clientService.getLocalClientEntity(CLIENT_ID_SS6);
        assertEquals(5, getEndpointCountByServiceCode(client, "openapi3-test"));
        assertEquals(4, client.getAccessRights().size());
        assertEquals(1, client.getEndpoints().stream().filter(ep -> ep.getMethod().equals("POST")).count());

        serviceDescriptionService.updateOpenApi3ServiceDescription(6L, url.toString(), "openapi3-test",
                "openapi3-test", false);

        List<EndpointEntity> endpoints = client.getEndpoints();
        assertEquals(5, getEndpointCountByServiceCode(client, "openapi3-test"));
        assertEquals(4, client.getAccessRights().size());

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
    @WithMockUser(authorities = "EDIT_OPENAPI3")
    public void updateOpenApi3ServiceDescriptionWithWarnings() throws Exception {
        URL url = getClass().getResource("/openapiparser/valid_modified.yaml");

        ClientEntity client = clientService.getLocalClientEntity(CLIENT_ID_SS6);
        assertEquals(5, getEndpointCountByServiceCode(client, "openapi3-test"));
        assertEquals(4, client.getAccessRights().size());
        assertEquals(1, client.getEndpoints().stream().filter(ep -> ep.getMethod().equals("POST")).count());

        boolean foundWarnings = false;
        try {
            serviceDescriptionService.updateOpenApi3ServiceDescription(6L, url.toString(), "openapi3-test",
                    "openapi3-test", false);
        } catch (UnhandledWarningsException e) {
            foundWarnings = true;
        }
        assertTrue(foundWarnings);

        try {
            serviceDescriptionService.updateOpenApi3ServiceDescription(6L, url.toString(), "openapi3-test",
                    "openapi3-test", true);
        } catch (UnhandledWarningsException e) {
            fail("Shouldn't throw warnings exception when ignorewarning is true");
        }


        List<EndpointEntity> endpoints = client.getEndpoints();
        assertEquals(5, getEndpointCountByServiceCode(client, "openapi3-test"));
        assertEquals(3, client.getAccessRights().size());
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
        ClientEntity testClient = new ClientEntity();
        ServiceDescriptionEntity sd1 = new ServiceDescriptionEntity();
        testClient.getServiceDescriptions().add(sd1);

        // backend returns the title of the first matching service (which can be null or empty),
        // or null if no matches
        assertNull(serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getServices().add(createServiceType("bar-title", "bar", "v2"));
        assertNull(serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getServices().clear();
        sd1.getServices().add(createServiceType(null, "foo", "v1"));
        assertNull(serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getServices().clear();
        sd1.getServices().add(createServiceType("", "foo", "v2"));
        assertEquals("", serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getServices().clear();
        sd1.getServices().add(createServiceType(null, "foo", "v1"));
        sd1.getServices().add(createServiceType("v3-title", "foo", "v3"));
        assertEquals("v3-title", serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getServices().clear();
        sd1.getServices().add(createServiceType("", "foo", "v2"));
        sd1.getServices().add(createServiceType("v3-title", "foo", "v3"));
        assertEquals("v3-title", serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getServices().clear();
        sd1.getServices().add(createServiceType("v4-title", "foo", "v4"));
        sd1.getServices().add(createServiceType("v3-title", "foo", "v3"));
        sd1.getServices().add(createServiceType("v5-title", "foo", "v5"));

        assertEquals("v5-title", serviceDescriptionService.getServiceTitle(testClient, "foo"));

        sd1.getServices().clear();
        sd1.getServices().add(createServiceType("AAA-title", "foo", "AAA"));
        sd1.getServices().add(createServiceType("XYZ-title", "foo", "XYZ"));
        sd1.getServices().add(createServiceType("v1-title", "foo", "v1"));
        sd1.getServices().add(createServiceType("V1`-title", "foo", "V1`"));

        assertEquals("XYZ-title", serviceDescriptionService.getServiceTitle(testClient, "foo"));
    }

    private ServiceEntity createServiceType(String title, String serviceCode, String serviceVersion) {
        ServiceEntity serviceFooNullTitle = new ServiceEntity();
        serviceFooNullTitle.setTitle(title);
        serviceFooNullTitle.setServiceCode(serviceCode);
        serviceFooNullTitle.setServiceVersion(serviceVersion);
        return serviceFooNullTitle;
    }

}
