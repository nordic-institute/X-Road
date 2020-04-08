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

import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.dto.AnchorFile;
import org.niis.xroad.restapi.repository.AnchorRepository;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.service.ConfigurationVerifier.MISSING_PRIVATE_PARAMS;
import static org.niis.xroad.restapi.util.DeviationTestUtils.assertErrorWithoutMetadata;
import static org.niis.xroad.restapi.util.TestUtils.ANCHOR_FILE;
import static org.niis.xroad.restapi.util.TestUtils.ANCHOR_HASH;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class SystemServiceTest {
    private static final String TSA_1_URL = "https://tsa.com";
    private static final String TSA_1_NAME = "TSA 1";
    private static final String TSA_2_URL = "https://example.com";
    private static final String TSA_2_NAME = "TSA 2";

    @Autowired
    private SystemService systemService;
    @MockBean
    private ServerConfService serverConfService;
    @MockBean
    private GlobalConfService globalConfService;
    @MockBean
    private AnchorRepository anchorRepository;
    @MockBean
    private ConfigurationVerifier configurationVerifier;
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setup() throws Exception {
        systemService.setInternalKeyPath("src/test/resources/internal.key");
        systemService.setTempFilesPath(tempFolder.newFolder().getAbsolutePath());

        TspType tsa1 = TestUtils.createTspType(TSA_1_URL, TSA_1_NAME);
        TspType tsa2 = TestUtils.createTspType(TSA_2_URL, TSA_2_NAME);

        when(globalConfService.getApprovedTspsForThisInstance()).thenReturn(
                Arrays.asList(tsa1, tsa2));
        when(globalConfService.getApprovedTspName(TSA_1_URL))
                .thenReturn(tsa1.getName());
        when(globalConfService.getApprovedTspName(TSA_2_URL))
                .thenReturn(tsa2.getName());
        when(systemService.getConfiguredTimestampingServices()).thenReturn(new ArrayList<>(Arrays.asList(tsa1)));
        when(serverConfService.getSecurityServerOwnerId()).thenReturn(ClientId.create("CS", "GOV", "1111"));
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
    public void addConfiguredTimestampingService() throws
            SystemService.DuplicateConfiguredTimestampingServiceException, TimestampingServiceNotFoundException {
        TspType tspType = TestUtils.createTspType(TSA_2_URL, TSA_2_NAME);

        assertEquals(1, serverConfService.getConfiguredTimestampingServices().size());

        systemService.addConfiguredTimestampingService(tspType);

        assertEquals(2, serverConfService.getConfiguredTimestampingServices().size());
        assertEquals(TSA_2_NAME, serverConfService.getConfiguredTimestampingServices().get(1).getName());
        assertEquals(TSA_2_URL, serverConfService.getConfiguredTimestampingServices().get(1).getUrl());
    }

    @Test
    public void addConfiguredTimestampingServiceNonApproved() throws
            SystemService.DuplicateConfiguredTimestampingServiceException {
        TspType tspType = TestUtils.createTspType("http://test.com", "TSA 3");

        try {
            systemService.addConfiguredTimestampingService(tspType);
            fail("should throw TimestampingServiceNotFoundException");
        } catch (TimestampingServiceNotFoundException expected) {
            // success
        }
    }

    @Test
    public void addConfiguredTimestampingServiceDuplicate() throws TimestampingServiceNotFoundException {
        TspType tspType = TestUtils.createTspType(TSA_1_URL, TSA_1_NAME);

        try {
            systemService.addConfiguredTimestampingService(tspType);
            fail("should throw DuplicateConfiguredTimestampingServiceException");
        } catch (SystemService.DuplicateConfiguredTimestampingServiceException expected) {
            // success
        }
    }

    @Test
    public void deleteConfiguredTimestampingService() throws TimestampingServiceNotFoundException {
        TspType tspType = TestUtils.createTspType(TSA_1_URL, TSA_1_NAME);

        assertEquals(1, serverConfService.getConfiguredTimestampingServices().size());

        systemService.deleteConfiguredTimestampingService(tspType);

        assertEquals(0, serverConfService.getConfiguredTimestampingServices().size());
    }

    @Test
    public void deleteConfiguredTimestampingServiceNonExisting() {
        TspType tspType = TestUtils.createTspType(TSA_2_URL, TSA_2_NAME);

        try {
            systemService.deleteConfiguredTimestampingService(tspType);
            fail("should throw TimestampingServiceNotFoundException");
        } catch (TimestampingServiceNotFoundException expected) {
            // success
        }
    }

    @Test
    public void getAnchorFileFromBytes() throws Exception {
        byte[] anchorBytes = FileUtils.readFileToByteArray(ANCHOR_FILE);
        AnchorFile anchorFile = systemService.getAnchorFileFromBytes(anchorBytes);
        assertEquals(ANCHOR_HASH, anchorFile.getHash());
    }

    @Test(expected = SystemService.InvalidAnchorInstanceException.class)
    public void getAnchorFileFromBytesWrongInstance() throws Exception {
        when(serverConfService.getSecurityServerOwnerId()).thenReturn(ClientId.create("INVALID", "GOV", "1111"));
        byte[] anchorBytes = FileUtils.readFileToByteArray(ANCHOR_FILE);
        systemService.getAnchorFileFromBytes(anchorBytes);
    }

    @Test
    public void uploadAnchor() throws Exception {
        byte[] anchorBytes = FileUtils.readFileToByteArray(ANCHOR_FILE);
        try {
            systemService.uploadAnchor(anchorBytes);
        } catch (Exception e) {
            fail("Should not fail");
        }
    }

    @Test
    public void uploadAnchorFailVerification() throws Exception {
        doThrow(new ConfigurationVerifier.ConfigurationVerificationException(MISSING_PRIVATE_PARAMS))
                .when(configurationVerifier).verifyInternalConfiguration(any());
        byte[] anchorBytes = FileUtils.readFileToByteArray(ANCHOR_FILE);
        try {
            systemService.uploadAnchor(anchorBytes);
            fail("Should have failed");
        } catch (Exception e) {
            assertErrorWithoutMetadata(MISSING_PRIVATE_PARAMS,
                    (ConfigurationVerifier.ConfigurationVerificationException) e);
        }
    }

    @Test(expected = SystemService.MalformedAnchorException.class)
    public void uploadAnchorWithBadData() throws Exception {
        byte[] anchorBytes = new byte[8];
        systemService.uploadAnchor(anchorBytes);
    }
}
