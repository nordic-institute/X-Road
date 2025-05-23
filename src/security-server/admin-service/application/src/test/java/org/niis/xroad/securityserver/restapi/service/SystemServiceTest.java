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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.globalconf.model.ConfigurationAnchor;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.service.ConfigurationVerifier;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.cache.SecurityServerAddressChangeStatus;
import org.niis.xroad.securityserver.restapi.dto.AnchorFile;
import org.niis.xroad.securityserver.restapi.repository.AnchorRepository;
import org.niis.xroad.securityserver.restapi.util.DeviationTestUtils;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.serverconf.impl.entity.TimestampingServiceEntity;
import org.niis.xroad.serverconf.impl.mapper.TimestampingServiceMapper;
import org.niis.xroad.serverconf.model.TimestampingService;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.MISSING_PRIVATE_PARAMS;

@RunWith(MockitoJUnitRunner.class)
public class SystemServiceTest {
    private static final String TSA_1_URL = "https://tsa.com";
    private static final String TSA_1_NAME = "TSA 1";
    private static final String TSA_2_URL = "https://example.com";
    private static final String TSA_2_NAME = "TSA 2";
    private static final String SERVER_ADDRESS = "new.address";

    @Mock
    private ServerConfService serverConfService;
    @Mock
    private GlobalConfService globalConfService;
    @Mock
    private AnchorRepository anchorRepository;
    @Mock
    private ConfigurationVerifier configurationVerifier;
    @Mock
    private CurrentSecurityServerId currentSecurityServerId;
    @Mock
    private ManagementRequestSenderService managementRequestSenderService;
    @Mock
    private AuditDataHelper auditDataHelper;
    private final SecurityServerAddressChangeStatus addressChangeStatus = new SecurityServerAddressChangeStatus();
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private SystemService systemService;

    @Before
    public void setup() throws Exception {
        TimestampingServiceEntity tsa1 = TestUtils.createTspTypeEntity(TSA_1_URL, TSA_1_NAME);
        TimestampingServiceEntity tsa2 = TestUtils.createTspTypeEntity(TSA_2_URL, TSA_2_NAME);

        when(globalConfService.getApprovedTspsForThisInstance()).thenReturn(TimestampingServiceMapper.get().toTargets(List.of(tsa1, tsa2)));
        ClientId.Conf ownerId = ClientId.Conf.create("CS", "GOV", "1111");
        when(serverConfService.getConfiguredTimestampingServiceEntities()).thenReturn(new ArrayList<>(Arrays.asList(tsa1)));
        SecurityServerId.Conf ownerSsId = SecurityServerId.Conf.create(ownerId, "TEST-INMEM-SS");
        when(currentSecurityServerId.getServerId()).thenReturn(ownerSsId);

        systemService = new SystemService(globalConfService, serverConfService, anchorRepository,
                configurationVerifier, currentSecurityServerId, managementRequestSenderService, auditDataHelper,
                addressChangeStatus);
        systemService.setInternalKeyPath("src/test/resources/internal.key");
        systemService.setTempFilesPath(tempFolder.newFolder().getAbsolutePath());
    }

    @Test
    public void generateInternalCsr() throws Exception {
        byte[] csrBytes = systemService.generateInternalCsr("C=FI, serialNumber=123");
        assertTrue(csrBytes.length > 0);
    }

    @Test(expected = InvalidDistinguishedNameException.class)
    public void generateInternalCsrFail() throws Exception {
        systemService.generateInternalCsr("this is wrong");
    }

    @Test
    public void addConfiguredTimestampingService()
            throws SystemService.DuplicateConfiguredTimestampingServiceException, TimestampingServiceNotFoundException {
        TimestampingService timestampingService = TestUtils.createTspType(TSA_2_URL, TSA_2_NAME);

        assertEquals(1, serverConfService.getConfiguredTimestampingServiceEntities().size());

        systemService.addConfiguredTimestampingService(timestampingService);

        assertEquals(2, serverConfService.getConfiguredTimestampingServiceEntities().size());
        assertEquals(TSA_2_NAME, serverConfService.getConfiguredTimestampingServiceEntities().get(1).getName());
        assertEquals(TSA_2_URL, serverConfService.getConfiguredTimestampingServiceEntities().get(1).getUrl());
    }

    @Test
    public void addConfiguredTimestampingServiceNonApproved() throws
                                                              SystemService.DuplicateConfiguredTimestampingServiceException {
        TimestampingService timestampingService = TestUtils.createTspType("http://test.com", "TSA 3");

        assertThrows(TimestampingServiceNotFoundException.class, () -> systemService.addConfiguredTimestampingService(timestampingService));
    }

    @Test
    public void addConfiguredTimestampingServiceDuplicate() throws TimestampingServiceNotFoundException {
        TimestampingService timestampingService = TestUtils.createTspType(TSA_1_URL, TSA_1_NAME);

        assertThrows(SystemService.DuplicateConfiguredTimestampingServiceException.class,
                () -> systemService.addConfiguredTimestampingService(timestampingService));
    }

    @Test
    public void deleteConfiguredTimestampingService() throws TimestampingServiceNotFoundException {
        TimestampingService timestampingService = TestUtils.createTspType(TSA_1_URL, TSA_1_NAME);

        assertEquals(1, serverConfService.getConfiguredTimestampingServiceEntities().size());

        systemService.deleteConfiguredTimestampingService(timestampingService);

        assertEquals(0, serverConfService.getConfiguredTimestampingServiceEntities().size());
    }

    @Test
    public void deleteConfiguredTimestampingServiceNonExisting() {
        TimestampingService timestampingService = TestUtils.createTspType(TSA_2_URL, TSA_2_NAME);

        assertThrows(TimestampingServiceNotFoundException.class,
                () -> systemService.deleteConfiguredTimestampingService(timestampingService));
    }

    @Test
    public void getAnchorFileFromBytes() throws Exception {
        byte[] anchorBytes = FileUtils.readFileToByteArray(TestUtils.ANCHOR_FILE);
        AnchorFile anchorFile = systemService.getAnchorFileFromBytes(anchorBytes, true);
        Assert.assertEquals(TestUtils.ANCHOR_HASH, anchorFile.getHash());
    }

    @Test(expected = SystemService.InvalidAnchorInstanceException.class)
    public void getAnchorFileFromBytesWrongInstance() throws Exception {
        ClientId.Conf ownerId = ClientId.Conf.create("INVALID", "GOV", "1111");
        SecurityServerId.Conf ownerSsId = SecurityServerId.Conf.create(ownerId, "TEST-INMEM-SS");
        when(currentSecurityServerId.getServerId()).thenReturn(ownerSsId);
        byte[] anchorBytes = FileUtils.readFileToByteArray(TestUtils.ANCHOR_FILE);
        systemService.getAnchorFileFromBytes(anchorBytes, true);
    }

    @Test
    public void getAnchorFileFromBytesSkipVerify() throws Exception {
        byte[] anchorBytes = FileUtils.readFileToByteArray(TestUtils.ANCHOR_FILE);
        AnchorFile anchorFile = systemService.getAnchorFileFromBytes(anchorBytes, false);
        Assert.assertEquals(TestUtils.ANCHOR_HASH, anchorFile.getHash());
    }

    @Test
    @SuppressWarnings("java:S2699") // Add at least one assertion to this test case
    public void replaceAnchor() throws Exception {
        byte[] anchorBytes = FileUtils.readFileToByteArray(TestUtils.ANCHOR_FILE);
        systemService.replaceAnchor(anchorBytes);
    }

    @Test
    public void replaceAnchorFailVerification() throws Exception {
        Mockito.doThrow(new InternalServerErrorException(MISSING_PRIVATE_PARAMS.build()))
                .when(configurationVerifier).verifyConfiguration(any(), any());
        byte[] anchorBytes = FileUtils.readFileToByteArray(TestUtils.ANCHOR_FILE);
        try {
            systemService.replaceAnchor(anchorBytes);
            fail("Should have failed");
        } catch (InternalServerErrorException e) {
            DeviationTestUtils.assertErrorWithoutMetadata(MISSING_PRIVATE_PARAMS.code(), e);
        }
    }

    @Test(expected = SystemService.MalformedAnchorException.class)
    public void replaceAnchorWithBadData() throws Exception {
        byte[] anchorBytes = new byte[8];
        systemService.replaceAnchor(anchorBytes);
    }

    @Test
    @SuppressWarnings("java:S2699") // Add at least one assertion to this test case
    public void uploadInitialAnchor() throws Exception {
        byte[] anchorBytes = FileUtils.readFileToByteArray(TestUtils.ANCHOR_FILE);
        when(anchorRepository.readAnchorFile()).thenThrow(new NoSuchFileException(""));
        systemService.uploadInitialAnchor(anchorBytes);
    }

    @Test(expected = SystemService.AnchorAlreadyExistsException.class)
    public void uploadInitialAnchorAgain() throws Exception {
        byte[] anchorBytes = FileUtils.readFileToByteArray(TestUtils.ANCHOR_FILE);
        when(anchorRepository.readAnchorFile()).thenReturn(anchorBytes);
        when(anchorRepository.loadAnchorFromFile())
                .thenReturn(new ConfigurationAnchor("src/test/resources/internal-configuration-anchor.xml"));
        systemService.uploadInitialAnchor(anchorBytes);
    }

    @Test
    public void changeSecurityServerAddress() throws Exception {
        when(globalConfService.getSecurityServerAddress(any())).thenReturn("ss.address");

        systemService.changeSecurityServerAddress(SERVER_ADDRESS);

        verify(auditDataHelper).put(RestApiAuditProperty.ADDRESS, SERVER_ADDRESS);
        verify(managementRequestSenderService).sendAddressChangeRequest(SERVER_ADDRESS);
    }

    @Test
    public void changeSecurityServerAddressAlreadySubmitted() throws Exception {
        addressChangeStatus.setAddress(SERVER_ADDRESS);

        try {
            systemService.changeSecurityServerAddress("another address");
            fail();
        } catch (ConflictException e) {
            assertEquals("Error[code=address_change_request_already_submitted]", e.getMessage());
            // ok
        }
    }

    @Test
    public void changeSecurityServerAddressSameAddress() throws Exception {
        when(globalConfService.getSecurityServerAddress(any())).thenReturn(SERVER_ADDRESS);

        try {
            systemService.changeSecurityServerAddress(SERVER_ADDRESS);
            fail();
        } catch (ConflictException e) {
            assertEquals("Error[code=same_address_change_request]", e.getMessage());
            // ok
        }
    }
}
