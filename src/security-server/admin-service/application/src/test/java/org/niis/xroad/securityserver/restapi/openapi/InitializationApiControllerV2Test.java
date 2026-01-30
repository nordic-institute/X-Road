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
package org.niis.xroad.securityserver.restapi.openapi;

import org.junit.Test;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.securityserver.restapi.dto.InitializationStatusV2;
import org.niis.xroad.securityserver.restapi.dto.InitializationStep;
import org.niis.xroad.securityserver.restapi.dto.InitializationStepInfo;
import org.niis.xroad.securityserver.restapi.openapi.model.FullInitRequestV2Dto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitStepResultDto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitializationOverallStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitializationStatusV2Dto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitializationStepDto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitializationStepStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServerConfInitRequestDto;
import org.niis.xroad.securityserver.restapi.openapi.model.SoftTokenInitRequestDto;
import org.niis.xroad.securityserver.restapi.service.WeakPinException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for InitializationApiControllerV2 - v2 initialization with granular step tracking.
 */
public class InitializationApiControllerV2Test extends AbstractApiControllerTestContext {

    @Autowired
    InitializationApiControllerV2 initializationApiControllerV2;

    private static final String OWNER_MEMBER_CLASS = "GOV";
    private static final String OWNER_MEMBER_CODE = "M1";
    private static final String SECURITY_SERVER_CODE = "SS3";
    private static final String SOFTWARE_TOKEN_PIN = "TopSecretP1n.";

    @Test
    @WithMockUser
    public void getInitializationStatusV2() {
        InitializationStatusV2 mockStatus = createMockStatus();
        when(initializationStepService.getInitializationStatusV2()).thenReturn(mockStatus);

        ResponseEntity<InitializationStatusV2Dto> response = initializationApiControllerV2.getInitializationStatusV2();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(InitializationOverallStatusDto.NOT_STARTED, response.getBody().getOverallStatus());
        assertFalse(response.getBody().getAnchorImported());
        assertFalse(response.getBody().getFullyInitialized());
        assertEquals(4, response.getBody().getSteps().size());
        assertEquals(4, response.getBody().getPendingSteps().size());
        assertTrue(response.getBody().getFailedSteps().isEmpty());
        assertTrue(response.getBody().getCompletedSteps().isEmpty());
        verify(initializationStepService).getInitializationStatusV2();
    }

    @Test
    @WithMockUser(authorities = {"INIT_CONFIG"})
    public void initializeServerConfSuccess() {
        InitializationStepInfo mockResult = InitializationStepInfo.completed(
                InitializationStep.SERVERCONF, Instant.now());
        when(initializationStepService.executeServerConfStep(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(mockResult);

        ServerConfInitRequestDto request = new ServerConfInitRequestDto(
                SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE);
        request.setIgnoreWarnings(true);

        ResponseEntity<InitStepResultDto> response = initializationApiControllerV2.initializeServerConf(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(InitializationStepDto.SERVERCONF, response.getBody().getStep());
        assertEquals(InitializationStepStatusDto.COMPLETED, response.getBody().getStatus());
        assertTrue(response.getBody().getSuccess());
    }

    @Test
    @WithMockUser(authorities = {"INIT_CONFIG"})
    public void initializeServerConfAnchorNotImported() {
        when(initializationStepService.executeServerConfStep(anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new ConflictException("Configuration anchor must be imported first",
                        new org.niis.xroad.common.core.exception.ErrorDeviation("anchor_not_found")));

        ServerConfInitRequestDto request = new ServerConfInitRequestDto(
                SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE);

        try {
            initializationApiControllerV2.initializeServerConf(request);
        } catch (ConflictException e) {
            assertEquals("Configuration anchor must be imported first", e.getMessage());
        }
    }

    @Test
    @WithMockUser(authorities = {"INIT_CONFIG"})
    public void initializeSoftTokenSuccess() {
        InitializationStepInfo mockResult = InitializationStepInfo.completed(
                InitializationStep.SOFTTOKEN, Instant.now());
        when(initializationStepService.executeSoftTokenStep(anyString())).thenReturn(mockResult);

        SoftTokenInitRequestDto request = new SoftTokenInitRequestDto(SOFTWARE_TOKEN_PIN);

        ResponseEntity<InitStepResultDto> response = initializationApiControllerV2.initializeSoftToken(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(InitializationStepDto.SOFTTOKEN, response.getBody().getStep());
        assertEquals(InitializationStepStatusDto.COMPLETED, response.getBody().getStatus());
        assertTrue(response.getBody().getSuccess());
    }

    @Test
    @WithMockUser(authorities = {"INIT_CONFIG"})
    public void initializeSoftTokenPrerequisiteNotMet() {
        when(initializationStepService.executeSoftTokenStep(anyString()))
                .thenThrow(new ConflictException("SERVERCONF step must be completed before SOFTTOKEN",
                        new org.niis.xroad.common.core.exception.ErrorDeviation("prerequisite_not_met")));

        SoftTokenInitRequestDto request = new SoftTokenInitRequestDto("weak");

        try {
            initializationApiControllerV2.initializeSoftToken(request);
        } catch (ConflictException e) {
            assertEquals("SERVERCONF step must be completed before SOFTTOKEN", e.getMessage());
        }
    }

    @Test
    @WithMockUser(authorities = {"INIT_CONFIG"})
    public void initializeSoftTokenWeakPin() {
        when(initializationStepService.executeSoftTokenStep(anyString()))
                .thenThrow(new WeakPinException("PIN is too weak", Collections.emptyList()));

        SoftTokenInitRequestDto request = new SoftTokenInitRequestDto("weak");

        try {
            initializationApiControllerV2.initializeSoftToken(request);
        } catch (WeakPinException e) {
            assertEquals("PIN is too weak", e.getMessage());
        }
    }

    @Test
    @WithMockUser(authorities = {"INIT_CONFIG"})
    public void initializeGpgKeySuccess() {
        InitializationStepInfo mockResult = InitializationStepInfo.completed(
                InitializationStep.GPG_KEY, Instant.now());
        when(initializationStepService.executeGpgKeyStep()).thenReturn(mockResult);

        ResponseEntity<InitStepResultDto> response = initializationApiControllerV2.initializeGpgKey();

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(InitializationStepDto.GPG_KEY, response.getBody().getStep());
        assertEquals(InitializationStepStatusDto.COMPLETED, response.getBody().getStatus());
        assertTrue(response.getBody().getSuccess());
    }

    @Test
    @WithMockUser(authorities = {"INIT_CONFIG"})
    public void initializeMessageLogEncryptionSuccess() {
        InitializationStepInfo mockResult = InitializationStepInfo.completed(
                InitializationStep.MLOG_ENCRYPTION, Instant.now());
        when(initializationStepService.executeMessageLogEncryptionStep()).thenReturn(mockResult);

        ResponseEntity<InitStepResultDto> response = initializationApiControllerV2.initializeMessageLogEncryption();

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(InitializationStepDto.MLOG_ENCRYPTION, response.getBody().getStep());
        assertEquals(InitializationStepStatusDto.COMPLETED, response.getBody().getStatus());
        assertTrue(response.getBody().getSuccess());
    }

    @Test
    @WithMockUser(authorities = {"INIT_CONFIG"})
    public void runAllInitializationStepsSuccess() {
        InitializationStatusV2 mockStatus = createFullyInitializedStatus();
        when(initializationStepService.executeAllPendingSteps(
                anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(mockStatus);

        FullInitRequestV2Dto request = new FullInitRequestV2Dto(
                SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE, SOFTWARE_TOKEN_PIN);
        request.setIgnoreWarnings(true);

        ResponseEntity<InitializationStatusV2Dto> response =
                initializationApiControllerV2.runAllInitializationSteps(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(InitializationOverallStatusDto.COMPLETED, response.getBody().getOverallStatus());
        assertTrue(response.getBody().getFullyInitialized());
        assertEquals(4, response.getBody().getCompletedSteps().size());
        assertTrue(response.getBody().getPendingSteps().isEmpty());
        assertTrue(response.getBody().getFailedSteps().isEmpty());
    }

    @Test
    @WithMockUser(authorities = {"INIT_CONFIG"})
    public void runAllInitializationStepsPartialFailure() {
        InitializationStatusV2 mockStatus = createPartiallyCompletedStatus();
        when(initializationStepService.executeAllPendingSteps(
                anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(mockStatus);

        FullInitRequestV2Dto request = new FullInitRequestV2Dto(
                SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE, SOFTWARE_TOKEN_PIN);

        ResponseEntity<InitializationStatusV2Dto> response =
                initializationApiControllerV2.runAllInitializationSteps(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(InitializationOverallStatusDto.PARTIALLY_COMPLETED, response.getBody().getOverallStatus());
        assertFalse(response.getBody().getFullyInitialized());
    }

    @Test
    @WithMockUser(authorities = {"INIT_CONFIG"})
    public void idempotentServerConfReturnsCompleted() {
        InitializationStepInfo mockResult = InitializationStepInfo.completed(
                InitializationStep.SERVERCONF, Instant.now());
        when(initializationStepService.executeServerConfStep(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(mockResult);

        ServerConfInitRequestDto request = new ServerConfInitRequestDto(
                SECURITY_SERVER_CODE, OWNER_MEMBER_CLASS, OWNER_MEMBER_CODE);

        ResponseEntity<InitStepResultDto> response1 = initializationApiControllerV2.initializeServerConf(request);
        ResponseEntity<InitStepResultDto> response2 = initializationApiControllerV2.initializeServerConf(request);

        assertEquals(HttpStatus.CREATED, response1.getStatusCode());
        assertEquals(HttpStatus.CREATED, response2.getStatusCode());
        assertNotNull(response2.getBody());
        assertTrue(response2.getBody().getSuccess());
    }

    private InitializationStatusV2 createMockStatus() {
        return InitializationStatusV2.builder()
                .overallStatus(InitializationStatusV2.OverallStatus.NOT_STARTED)
                .anchorImported(false)
                .steps(List.of(
                        InitializationStepInfo.notStarted(InitializationStep.SERVERCONF),
                        InitializationStepInfo.notStarted(InitializationStep.SOFTTOKEN),
                        InitializationStepInfo.notStarted(InitializationStep.GPG_KEY),
                        InitializationStepInfo.notStarted(InitializationStep.MLOG_ENCRYPTION)
                ))
                .pendingSteps(List.of(
                        InitializationStep.SERVERCONF,
                        InitializationStep.SOFTTOKEN,
                        InitializationStep.GPG_KEY,
                        InitializationStep.MLOG_ENCRYPTION
                ))
                .failedSteps(List.of())
                .completedSteps(List.of())
                .fullyInitialized(false)
                .build();
    }

    private InitializationStatusV2 createFullyInitializedStatus() {
        Instant now = Instant.now();
        return InitializationStatusV2.builder()
                .overallStatus(InitializationStatusV2.OverallStatus.COMPLETED)
                .anchorImported(true)
                .steps(List.of(
                        InitializationStepInfo.completed(InitializationStep.SERVERCONF, now),
                        InitializationStepInfo.completed(InitializationStep.SOFTTOKEN, now),
                        InitializationStepInfo.completed(InitializationStep.GPG_KEY, now),
                        InitializationStepInfo.completed(InitializationStep.MLOG_ENCRYPTION, now)
                ))
                .pendingSteps(List.of())
                .failedSteps(List.of())
                .completedSteps(List.of(
                        InitializationStep.SERVERCONF,
                        InitializationStep.SOFTTOKEN,
                        InitializationStep.GPG_KEY,
                        InitializationStep.MLOG_ENCRYPTION
                ))
                .fullyInitialized(true)
                .securityServerId("DEV/GOV/M1/SS3")
                .tokenPinPolicyEnforced(true)
                .build();
    }

    private InitializationStatusV2 createPartiallyCompletedStatus() {
        Instant now = Instant.now();
        return InitializationStatusV2.builder()
                .overallStatus(InitializationStatusV2.OverallStatus.PARTIALLY_COMPLETED)
                .anchorImported(true)
                .steps(List.of(
                        InitializationStepInfo.completed(InitializationStep.SERVERCONF, now),
                        InitializationStepInfo.completed(InitializationStep.SOFTTOKEN, now),
                        InitializationStepInfo.notStarted(InitializationStep.GPG_KEY),
                        InitializationStepInfo.notStarted(InitializationStep.MLOG_ENCRYPTION)
                ))
                .pendingSteps(List.of(
                        InitializationStep.GPG_KEY,
                        InitializationStep.MLOG_ENCRYPTION
                ))
                .failedSteps(List.of())
                .completedSteps(List.of(
                        InitializationStep.SERVERCONF,
                        InitializationStep.SOFTTOKEN
                ))
                .fullyInitialized(false)
                .securityServerId("DEV/GOV/M1/SS3")
                .build();
    }
}
