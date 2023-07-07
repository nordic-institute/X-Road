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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.util.CryptoUtils;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.InternalServerErrorException;
import org.niis.xroad.securityserver.restapi.dto.AnchorFile;
import org.niis.xroad.securityserver.restapi.dto.VersionInfoDto;
import org.niis.xroad.securityserver.restapi.openapi.model.Anchor;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.securityserver.restapi.openapi.model.DistinguishedName;
import org.niis.xroad.securityserver.restapi.openapi.model.NodeType;
import org.niis.xroad.securityserver.restapi.openapi.model.NodeTypeResponse;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingService;
import org.niis.xroad.securityserver.restapi.openapi.model.VersionInfo;
import org.niis.xroad.securityserver.restapi.service.AnchorNotFoundException;
import org.niis.xroad.securityserver.restapi.service.InvalidDistinguishedNameException;
import org.niis.xroad.securityserver.restapi.service.SystemService;
import org.niis.xroad.securityserver.restapi.service.TimestampingServiceNotFoundException;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.ANCHOR_FILE;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.INTERNAL_CERT_CN;

/**
 * test system api
 */
public class SystemApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    SystemApiController systemApiController;

    private static final String TSA_1_URL = "https://tsa.com";

    private static final String TSA_1_NAME = "TSA 1";

    private static final String TSA_2_URL = "https://example.com";

    private static final String TSA_2_NAME = "TSA 2";

    private static final String ANCHOR_HASH =
            "CE2CA4FBBB67260F6CE97F9BCB73501F40432A1A2C4E5DA6F9F50DD1";

    private static final String ANCHOR_CREATED_AT = "2019-04-28T09:03:31.841Z";

    private static final Long ANCHOR_CREATED_AT_MILLIS = 1556442211841L;

    @Before
    public void setup() throws Exception {
        when(globalConfFacade.getInstanceIdentifier()).thenReturn("TEST");
        AnchorFile anchorFile = new AnchorFile(ANCHOR_HASH);
        anchorFile.setCreatedAt(new Date(ANCHOR_CREATED_AT_MILLIS).toInstant().atOffset(ZoneOffset.UTC));
        when(systemService.getAnchorFileFromBytes(any(), anyBoolean())).thenReturn(anchorFile);
        when(systemService.getServerNodeType()).thenReturn(SystemProperties.NodeType.STANDALONE);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = { "VIEW_PROXY_INTERNAL_CERT" })
    public void getSystemCertificateWrongPermission() {
        systemApiController.getSystemCertificate();
    }

    @Test
    @WithMockUser(authorities = { "GENERATE_INTERNAL_TLS_CSR" })
    public void generateSystemCertificateRequestCorrectPermission() throws InvalidDistinguishedNameException {
        when(systemService.generateInternalCsr(any())).thenReturn("foo".getBytes());
        ResponseEntity<Resource> result = systemApiController.generateSystemCertificateRequest(
                new DistinguishedName().name("foobar"));
        assertNotNull(result);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = { "GENERATE_INTERNAL_CERT_REQ" })
    public void generateSystemCertificateRequestWrongPermission() {
        systemApiController.generateSystemCertificateRequest(new DistinguishedName().name("foobar"));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_INTERNAL_TLS_CERT" })
    public void getSystemCertificateWithViewInternalSslCertPermission() throws Exception {
        getSystemCertificate();
    }

    @Test
    @WithMockUser(authorities = { "VIEW_VERSION" })
    public void getVersionInfo()  {
        VersionInfoDto mockVersionInfo = new VersionInfoDto();
        mockVersionInfo.setJavaVersion(33);

        given(versionService.getVersionInfo()).willReturn(mockVersionInfo);
        ResponseEntity<VersionInfo> response = systemApiController.systemVersion();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(33, (long) response.getBody().getJavaVersion());
    }

    private void getSystemCertificate() throws IOException {
        X509Certificate x509Certificate = null;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("internal.crt")) {
            x509Certificate = CryptoUtils.readCertificate(stream);
        }
        given(mockRepository.getInternalTlsCertificate()).willReturn(x509Certificate);

        CertificateDetails certificate =
                systemApiController.getSystemCertificate().getBody();
        assertEquals(INTERNAL_CERT_CN, certificate.getIssuerCommonName());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_TSPS" })
    public void getConfiguredTimestampingServices() {
        when(systemService.getConfiguredTimestampingServices()).thenReturn(new ArrayList<>(
                Arrays.asList(TestUtils.createTspType(TSA_1_URL, TSA_1_NAME),
                        TestUtils.createTspType(TSA_2_URL, TSA_2_NAME))));

        ResponseEntity<Set<TimestampingService>> response =
                systemApiController.getConfiguredTimestampingServices();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Set<TimestampingService> timestampingServices = response.getBody();

        assertEquals(2, timestampingServices.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_TSPS" })
    public void getConfiguredTimestampingServicesEmptyList() {
        when(systemService.getConfiguredTimestampingServices()).thenReturn(new ArrayList<TspType>());

        ResponseEntity<Set<TimestampingService>> response =
                systemApiController.getConfiguredTimestampingServices();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Set<TimestampingService> timestampingServices = response.getBody();

        assertEquals(0, timestampingServices.size());
    }

    @Test
    @WithMockUser(authorities = { "ADD_TSP" })
    public void addConfiguredTimestampingService() {
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_2_URL, TSA_2_NAME);

        ResponseEntity<TimestampingService> response = systemApiController
                .addConfiguredTimestampingService(timestampingService);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(TSA_2_NAME, response.getBody().getName());
        assertEquals(TSA_2_URL, response.getBody().getUrl());
    }

    @Test
    @WithMockUser(authorities = { "ADD_TSP" })
    public void addDuplicateConfiguredTimestampingService() throws
            SystemService.DuplicateConfiguredTimestampingServiceException, TimestampingServiceNotFoundException {
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME);

        Mockito.doThrow(new SystemService.DuplicateConfiguredTimestampingServiceException("")).when(systemService)
                .addConfiguredTimestampingService(any());

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
    public void addNonExistingConfiguredTimestampingService() throws
            SystemService.DuplicateConfiguredTimestampingServiceException,
            TimestampingServiceNotFoundException {
        TimestampingService timestampingService = TestUtils
                .createTimestampingService("http://dummy.com", "Dummy");

        Mockito.doThrow(new TimestampingServiceNotFoundException("")).when(systemService)
                .addConfiguredTimestampingService(any());

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
        ResponseEntity<Void> response = systemApiController
                .deleteConfiguredTimestampingService(TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME));
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = { "DELETE_TSP" })
    public void deleteNonExistingConfiguredTimestampingService() throws TimestampingServiceNotFoundException {
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME);

        Mockito.doThrow(new TimestampingServiceNotFoundException("")).when(systemService)
                .deleteConfiguredTimestampingService(any());

        try {
            ResponseEntity<Void> response = systemApiController
                    .deleteConfiguredTimestampingService(timestampingService);
            fail("should throw ResourceNotFoundException");
        } catch (BadRequestException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_ANCHOR" })
    public void getAnchor() throws AnchorNotFoundException {
        AnchorFile anchorFile = new AnchorFile(ANCHOR_HASH);
        anchorFile.setCreatedAt(new Date(ANCHOR_CREATED_AT_MILLIS).toInstant().atOffset(ZoneOffset.UTC));
        when(systemService.getAnchorFile()).thenReturn(anchorFile);

        ResponseEntity<Anchor> response = systemApiController.getAnchor();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Anchor anchor = response.getBody();
        assertEquals(ANCHOR_HASH, anchor.getHash());
        assertEquals(ANCHOR_CREATED_AT, anchor.getCreatedAt().toString());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_ANCHOR" })
    public void getAnchorNotFound() throws AnchorNotFoundException {
        Mockito.doThrow(new AnchorNotFoundException("")).when(systemService).getAnchorFile();

        try {
            ResponseEntity<Anchor> response = systemApiController.getAnchor();
            fail("should throw InternalServerErrorException");
        } catch (InternalServerErrorException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "DOWNLOAD_ANCHOR" })
    public void downloadAnchor() throws AnchorNotFoundException, IOException {
        byte[] bytes = "teststring".getBytes(StandardCharsets.UTF_8);
        when(systemService.readAnchorFile()).thenReturn(bytes);
        when(systemService.getAnchorFilenameForDownload())
                .thenReturn("configuration_anchor_UTC_2019-04-28_09_03_31.xml");

        ResponseEntity<Resource> response = systemApiController.downloadAnchor();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(bytes.length, response.getBody().contentLength());
    }

    @Test
    @WithMockUser(authorities = { "DOWNLOAD_ANCHOR" })
    public void downloadAnchorNotFound() throws AnchorNotFoundException {
        Mockito.doThrow(new AnchorNotFoundException("")).when(systemService).readAnchorFile();

        try {
            ResponseEntity<Resource> response = systemApiController.downloadAnchor();
            fail("should throw InternalServerErrorException");
        } catch (InternalServerErrorException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "UPLOAD_ANCHOR" })
    public void replaceAnchor() throws IOException {
        Resource anchorResource = new ByteArrayResource(FileUtils.readFileToByteArray(ANCHOR_FILE));
        ResponseEntity<Void> response = systemApiController.replaceAnchor(anchorResource);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/system/anchor", response.getHeaders().getLocation().getPath());
    }

    @Test
    @WithMockUser(authorities = { "UPLOAD_ANCHOR" })
    public void previewAnchor() throws IOException {
        Resource anchorResource = new ByteArrayResource(FileUtils.readFileToByteArray(ANCHOR_FILE));
        ResponseEntity<Anchor> response = systemApiController.previewAnchor(true, anchorResource);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Anchor anchor = response.getBody();
        assertEquals(ANCHOR_HASH, anchor.getHash());
        assertEquals(ANCHOR_CREATED_AT, anchor.getCreatedAt().toString());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_NODE_TYPE" })
    public void getNodeTypeStandalone() {
        ResponseEntity<NodeTypeResponse> response = systemApiController.getNodeType();
        assertEquals(response.getBody().getNodeType(), NodeType.STANDALONE); // default value is STANDALONE
    }

    @Test
    @WithMockUser(authorities = { "VIEW_NODE_TYPE" })
    public void getNodeTypePrimary() {
        when(systemService.getServerNodeType()).thenReturn(SystemProperties.NodeType.MASTER);
        ResponseEntity<NodeTypeResponse> response = systemApiController.getNodeType();
        assertEquals(response.getBody().getNodeType(), NodeType.PRIMARY);
    }

    @Test
    @WithMockUser(authorities = { "VIEW_NODE_TYPE" })
    public void getNodeTypeSecondary() {
        when(systemService.getServerNodeType()).thenReturn(SystemProperties.NodeType.SLAVE);
        ResponseEntity<NodeTypeResponse> response = systemApiController.getNodeType();
        assertEquals(response.getBody().getNodeType(), NodeType.SECONDARY);
    }
}
