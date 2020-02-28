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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.TimestampingService;
import org.niis.xroad.restapi.openapi.model.Version;
import org.niis.xroad.restapi.repository.InternalTlsCertificateRepository;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.niis.xroad.restapi.service.ServerConfService;
import org.niis.xroad.restapi.service.VersionService;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

/**
 * test system api
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class SystemApiControllerTest {

    @MockBean
    private InternalTlsCertificateRepository mockRepository;

    @MockBean
    private VersionService versionService;

    @Autowired
    private SystemApiController systemApiController;

    @MockBean
    GlobalConfService globalConfService;

    @MockBean
    ServerConfService serverConfService;

    @MockBean
    GlobalConfFacade globalConfFacade;

    private static final Map<String, TspType> APPROVED_TIMESTAMPING_SERVICES = new HashMap<>();

    private static final List<TspType> CONFIGURED_TIMESTAMPING_SERVICES = new ArrayList<>();

    private static final String TSA_1_URL = "https://tsa.com";

    private static final String TSA_1_NAME = "TSA 1";

    private static final String TSA_2_URL = "https://example.com";

    private static final String TSA_2_NAME = "TSA 2";

    @Before
    public void setup() {

        TspType tsa1 = TestUtils.createTspType(TSA_1_URL, TSA_1_NAME);
        TspType tsa2 = TestUtils.createTspType(TSA_2_URL, TSA_2_NAME);
        APPROVED_TIMESTAMPING_SERVICES.put(tsa1.getName(), tsa1);
        APPROVED_TIMESTAMPING_SERVICES.put(tsa2.getName(), tsa2);

        CONFIGURED_TIMESTAMPING_SERVICES.addAll(Arrays.asList(tsa1));

        when(globalConfFacade.getInstanceIdentifier()).thenReturn("TEST");
        when(globalConfService.getApprovedTspsForThisInstance()).thenReturn(
                Arrays.asList(tsa1, tsa2));
        when(globalConfService.getApprovedTspName(TSA_1_URL))
                .thenReturn(tsa1.getName());
        when(globalConfService.getApprovedTspName(TSA_2_URL))
                .thenReturn(tsa2.getName());
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(CONFIGURED_TIMESTAMPING_SERVICES);
    }

    @Test
    @WithMockUser(authorities = { "VIEW_PROXY_INTERNAL_CERT" })
    public void getSystemCertificateWithViewProxyInternalCertPermission() throws Exception {
        getSystemCertificate();
    }

    @Test
    @WithMockUser(authorities = { "VIEW_INTERNAL_SSL_CERT" })
    public void getSystemCertificateWithViewInternalSslCertPermission() throws Exception {
        getSystemCertificate();
    }

    @Test
    @WithMockUser(authorities = { "VIEW_VERSION" })
    public void getVersion() throws Exception {
        String versionNumber = "6.24.0";
        given(versionService.getVersion()).willReturn(versionNumber);
        ResponseEntity<Version> response = systemApiController.systemVersion();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(versionNumber, response.getBody().getInfo());
    }

    private void getSystemCertificate() throws IOException {
        X509Certificate x509Certificate = null;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("internal.crt")) {
            x509Certificate = CryptoUtils.readCertificate(stream);
        }
        given(mockRepository.getInternalTlsCertificate()).willReturn(x509Certificate);

        CertificateDetails certificate =
                systemApiController.getSystemCertificate().getBody();
        assertEquals("xroad2-lxd-ss1", certificate.getIssuerCommonName());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_TSPS" })
    public void getConfiguredTimestampingServices() {
        ResponseEntity<List<TimestampingService>> response =
                systemApiController.getConfiguredTimestampingServices();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<TimestampingService> timestampingServices = response.getBody();

        assertEquals(CONFIGURED_TIMESTAMPING_SERVICES.size(), timestampingServices.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_TSPS" })
    public void getConfiguredTimestampingServicesEmptyList() {
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(new ArrayList<TspType>());

        ResponseEntity<List<TimestampingService>> response =
                systemApiController.getConfiguredTimestampingServices();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<TimestampingService> timestampingServices = response.getBody();

        assertEquals(0, timestampingServices.size());
    }

    @Test
    @WithMockUser(authorities = { "ADD_TSP" })
    public void addConfiguredTimestampingService() {
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(
                new ArrayList<TspType>(Arrays.asList(TestUtils.createTspType(TSA_1_URL, TSA_1_NAME))));
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_2_URL, TSA_2_NAME);

        ResponseEntity<TimestampingService> response = systemApiController
                .addConfiguredTimestampingService(timestampingService);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(2, serverConfService.getConfiguredTimestampingServices().size());
        assertEquals(serverConfService.getConfiguredTimestampingServices().get(1).getName(),
                timestampingService.getName());
        assertEquals(serverConfService.getConfiguredTimestampingServices().get(1).getUrl(),
                timestampingService.getUrl());
    }

    @Test
    @WithMockUser(authorities = { "ADD_TSP" })
    public void addDuplicateConfiguredTimestampingService() {
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(
                new ArrayList<TspType>(Arrays.asList(TestUtils.createTspType(TSA_1_URL, TSA_1_NAME))));
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME);

        try {
            ResponseEntity<TimestampingService> response = systemApiController
                    .addConfiguredTimestampingService(timestampingService);
            fail("should throw ConflictException");
        } catch (ConflictException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "ADD_TSP" })
    public void addNonExistingConfiguredTimestampingService() {
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(
                new ArrayList<TspType>(Arrays.asList(TestUtils.createTspType(TSA_1_URL, TSA_1_NAME))));
        TimestampingService timestampingService = TestUtils
                .createTimestampingService("http://dummy.com", "Dummy");

        try {
            ResponseEntity<TimestampingService> response = systemApiController
                    .addConfiguredTimestampingService(timestampingService);
            fail("should throw ResourceNotFoundException");
        } catch (BadRequestException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "DELETE_TSP" })
    public void deleteConfiguredTimestampingService() {
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(
                new ArrayList<TspType>(Arrays.asList(TestUtils.createTspType(TSA_1_URL, TSA_1_NAME))));

        ResponseEntity<Void> response = systemApiController
                .deleteConfiguredTimestampingService(TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME));
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(0, serverConfService.getConfiguredTimestampingServices().size());
    }

    @Test
    @WithMockUser(authorities = { "DELETE_TSP" })
    public void deleteNonExistingConfiguredTimestampingService() {
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(new ArrayList<TspType>());

        try {
            ResponseEntity<Void> response = systemApiController
                    .deleteConfiguredTimestampingService(TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME));
            fail("should throw ResourceNotFoundException");
        } catch (BadRequestException expected) {
            // success
        }
    }
}
