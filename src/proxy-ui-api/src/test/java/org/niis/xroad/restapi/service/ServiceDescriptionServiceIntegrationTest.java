/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.exceptions.DeviationAware;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.Warning;
import org.niis.xroad.restapi.util.TestUtils;
import org.niis.xroad.restapi.wsdl.WsdlValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

/**
 * test ServiceDescription service.
 * Use SpyBean to override parseWsdl, so that we can use WSDL urls that
 * are independent of the files we actually read.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Slf4j
@Transactional
public class ServiceDescriptionServiceIntegrationTest {

    public static final String BIG_ATTACHMENT_V1_SERVICECODE = "xroadBigAttachment.v1";
    public static final String SMALL_ATTACHMENT_V1_SERVICECODE = "xroadSmallAttachment.v1";
    public static final String GET_RANDOM_V1_SERVICECODE = "xroadGetRandom.v1";
    public static final String BIG_ATTACHMENT_SERVICECODE = "xroadBigAttachment";
    public static final String SMALL_ATTACHMENT_SERVICECODE = "xroadSmallAttachment";
    public static final String GET_RANDOM_SERVICECODE = "xroadGetRandom";
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final ClientId CLIENT_ID_SS1 = ClientId.create(
            "FI", "GOV", "M1", "SS1");

    @Autowired
    private ServiceDescriptionService serviceDescriptionService;

    @Autowired
    private ClientService clientService;

    @MockBean
    private WsdlValidator wsdlValidator;

    @Test
    @WithMockUser(authorities = { "ADD_WSDL", "REFRESH_WSDL",
            "VIEW_CLIENT_SERVICES", "VIEW_CLIENT_DETAILS" })
    public void refreshServiceDetectsAddedService() throws Exception {
        File testServiceWsdl = tempFolder.newFile("test.wsdl");
        File getRandomWsdl = getTestResouceFile("valid-getrandom.wsdl");
        File threeServicesWsdl = getTestResouceFile("valid.wsdl");
        FileUtils.copyFile(getRandomWsdl, testServiceWsdl);
        String url = testServiceWsdl.toURI().toURL().toString();
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1,
                url,
                false);

        // update wsdl to one with 3 services
        FileUtils.copyFile(threeServicesWsdl, testServiceWsdl);
        ClientType clientType = clientService.getClient(CLIENT_ID_SS1);
        ServiceDescriptionType serviceDescriptionType = getServiceDescription(url, clientType);

        try {
            serviceDescriptionService.refreshServiceDescription(serviceDescriptionType.getId(),
                    false);
            fail("should throw exception warning about service addition");
        } catch (DeviationAwareRuntimeException expected) {
            assertErrorWithoutMetadata(ServiceDescriptionService.ERROR_WARNINGS_DETECTED, expected);
            assertEquals(1, expected.getWarnings().size());
            assertWarning(ServiceDescriptionService.WARNING_ADDING_SERVICES, expected,
                    BIG_ATTACHMENT_V1_SERVICECODE, SMALL_ATTACHMENT_V1_SERVICECODE);
        }

        // with ignorewarnings, should succeed
        serviceDescriptionService.refreshServiceDescription(serviceDescriptionType.getId(),
                true);
        serviceDescriptionType = getServiceDescription(url, clientType);
        assertServiceCodes(serviceDescriptionType,
                BIG_ATTACHMENT_SERVICECODE, SMALL_ATTACHMENT_SERVICECODE, GET_RANDOM_SERVICECODE);
    }

    @Test
    @WithMockUser(authorities = { "ADD_WSDL", "REFRESH_WSDL",
            "VIEW_CLIENT_SERVICES", "VIEW_CLIENT_DETAILS" })
    public void refreshServiceDetectsRemovedService() throws Exception {
        File testServiceWsdl = tempFolder.newFile("test.wsdl");
        File getRandomWsdl = getTestResouceFile("valid-getrandom.wsdl");
        File threeServicesWsdl = getTestResouceFile("valid.wsdl");
        FileUtils.copyFile(threeServicesWsdl, testServiceWsdl);
        String url = testServiceWsdl.toURI().toURL().toString();
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1,
                url,
                false);

        // update wsdl to one with just one service
        FileUtils.copyFile(getRandomWsdl, testServiceWsdl);
        ClientType clientType = clientService.getClient(CLIENT_ID_SS1);
        ServiceDescriptionType serviceDescriptionType = getServiceDescription(url, clientType);

        try {
            serviceDescriptionService.refreshServiceDescription(serviceDescriptionType.getId(),
                    false);
            fail("should throw exception warning about service addition");
        } catch (DeviationAwareRuntimeException expected) {
            assertErrorWithoutMetadata(ServiceDescriptionService.ERROR_WARNINGS_DETECTED, expected);
            assertEquals(1, expected.getWarnings().size());
            assertWarning(ServiceDescriptionService.WARNING_DELETING_SERVICES, expected,
                    BIG_ATTACHMENT_V1_SERVICECODE, SMALL_ATTACHMENT_V1_SERVICECODE);
        }

        // with ignorewarnings, should succeed
        serviceDescriptionService.refreshServiceDescription(serviceDescriptionType.getId(),
                true);
        serviceDescriptionType = getServiceDescription(url, clientType);
        assertServiceCodes(serviceDescriptionType,
                GET_RANDOM_SERVICECODE);
    }

    @Test
    @WithMockUser(authorities = { "ADD_WSDL", "REFRESH_WSDL",
            "VIEW_CLIENT_SERVICES", "VIEW_CLIENT_DETAILS" })
    public void refreshServiceDetectsAllWarnings() throws Exception {
        // show warnings about
        // - add service
        // - remove service
        // - validation warnings

        // start with wsdl containing getrandom
        // then switch to one with smallattachment
        // and mock some warnings
        File testServiceWsdl = tempFolder.newFile("test.wsdl");
        File getRandomWsdl = getTestResouceFile("valid-getrandom.wsdl");
        File smallWsdl = getTestResouceFile("valid-smallattachment.wsdl");
        FileUtils.copyFile(getRandomWsdl, testServiceWsdl);
        String url = testServiceWsdl.toURI().toURL().toString();
        serviceDescriptionService.addWsdlServiceDescription(CLIENT_ID_SS1,
                    url, false);
        ClientType clientType = clientService.getClient(CLIENT_ID_SS1);
        ServiceDescriptionType serviceDescriptionType = getServiceDescription(url, clientType);

        // start mocking validation failures, when ignoreFailures = false
        List<String> mockValidationFailures = Arrays.asList("mock warning", "mock warning 2");
        doReturn(mockValidationFailures)
                .when(wsdlValidator).executeValidator(anyString());

        FileUtils.copyFile(smallWsdl, testServiceWsdl);

        try {
            serviceDescriptionService.refreshServiceDescription(serviceDescriptionType.getId(),
                    false);
            fail("should get warnings");
        } catch (DeviationAwareRuntimeException expected) {
            // we should get 3 warnings
            assertErrorWithoutMetadata(ServiceDescriptionService.ERROR_WARNINGS_DETECTED, expected);
            assertEquals(3, expected.getWarnings().size());
            assertWarning(ServiceDescriptionService.WARNING_ADDING_SERVICES, expected,
                    SMALL_ATTACHMENT_V1_SERVICECODE);
            assertWarning(ServiceDescriptionService.WARNING_DELETING_SERVICES, expected,
                    GET_RANDOM_V1_SERVICECODE);
            assertWarning(ServiceDescriptionService.WARNING_WSDL_VALIDATION_WARNINGS, expected,
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
    public void addWsdlServiceDescription() {
    }

    @Test
    public void updateWsdlUrl() {
    }

    @Test
    public void refreshServiceDescription() {
    }

    /**
     * Assert servicedescription contains the given codes. Checks codes only, no versions
     * @param serviceDescriptionType
     */
    private void assertServiceCodes(ServiceDescriptionType serviceDescriptionType,
                                    String...expectedCodes) {
        List<String> serviceCodes = serviceDescriptionType.getService()
                .stream()
                .map(service -> service.getServiceCode())
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(expectedCodes), serviceCodes);
    }

    /**
     * assert that DeviationAware contains correct warning, with given metadata
     * @param warningCode
     * @param deviationAware
     * @param metadata
     */
    private void assertWarning(String warningCode,
                               DeviationAware deviationAware,
                               String...metadata) {
        List<String> metadataList = Arrays.asList(metadata);
        Warning warning = TestUtils.findWarning(warningCode, deviationAware);
        assertNotNull(warning);
        // should contain the added service codes
        assertEquals(metadataList.size(), warning.getMetadata().size());
        for (String m: metadataList) {
            assertTrue(warning.getMetadata().contains(m));
        }
    }

    private ServiceDescriptionType getServiceDescription(String url, ClientType clientType) {
        return clientType.getServiceDescription()
                .stream()
                .filter(sd -> sd.getUrl().equals(url))
                .findFirst().get();
    }

    private File getTestResouceFile(String fileName) {
        return new File(this.getClass().getClassLoader().getResource(fileName)
                .getFile());
    }

    private void assertErrorWithoutMetadata(String errorCode, DeviationAware deviationAware) {
        assertEquals(errorCode, deviationAware.getError().getCode());
        assertNull(deviationAware.getError().getMetadata());
    }



}
