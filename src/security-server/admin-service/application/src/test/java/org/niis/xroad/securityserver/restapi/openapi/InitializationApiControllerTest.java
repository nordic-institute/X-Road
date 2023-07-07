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

import org.junit.Test;
import org.mockito.Mockito;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.InternalServerErrorException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.dto.InitializationStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitialServerConf;
import org.niis.xroad.securityserver.restapi.service.AnchorNotFoundException;
import org.niis.xroad.securityserver.restapi.service.InitializationService;
import org.niis.xroad.securityserver.restapi.service.InvalidCharactersException;
import org.niis.xroad.securityserver.restapi.service.WeakPinException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * test init
 */
public class InitializationApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    InitializationApiController initializationApiController;

    private static final String OWNER_MEMBER_CLASS = "GOV";
    private static final String OWNER_MEMBER_CODE = "M1";
    private static final String SECURITY_SERVER_CODE = "SS3";
    private static final String SOFTWARE_TOKEN_PIN = "1234";

    @Test
    @WithMockUser
    public void initStatus() {
        when(initializationService.getSecurityServerInitializationStatus()).thenReturn(new InitializationStatusDto());
        initializationApiController.getInitializationStatus();
        verify(initializationService).getSecurityServerInitializationStatus();
    }

    @Test
    @WithMockUser(authorities = { "INIT_CONFIG" })
    public void initSecurityServerSuccess() {
        InitialServerConf initialServerConf = createInitConfWithPin(SOFTWARE_TOKEN_PIN);
        ResponseEntity<Void> response = initializationApiController.initSecurityServer(initialServerConf);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = { "INIT_CONFIG" })
    public void initSecurityServerFail() throws Exception {
        InitialServerConf initialServerConf = createInitConfWithPin(SOFTWARE_TOKEN_PIN);
        Mockito.doThrow(new AnchorNotFoundException(""))
                .when(initializationService).initialize(any(), any(), any(), any(), anyBoolean());
        try {
            initializationApiController.initSecurityServer(initialServerConf);
            fail("should have thrown");
        } catch (ConflictException expected) {
            // expected
        }

        Mockito.doThrow(new InitializationService.ServerAlreadyFullyInitializedException(""))
                .when(initializationService).initialize(any(), any(), any(), any(), anyBoolean());
        try {
            initializationApiController.initSecurityServer(initialServerConf);
            fail("should have thrown");
        } catch (ConflictException expected) {
            // expected
        }

        Mockito.doThrow(new InitializationService.InvalidInitParamsException("", Collections.emptyList()))
                .when(initializationService).initialize(any(), any(), any(), any(), anyBoolean());
        try {
            initializationApiController.initSecurityServer(initialServerConf);
            fail("should have thrown");
        } catch (BadRequestException expected) {
            // expected
        }

        Mockito.doThrow(new UnhandledWarningsException(Collections.emptyList()))
                .when(initializationService).initialize(any(), any(), any(), any(), anyBoolean());
        try {
            initializationApiController.initSecurityServer(initialServerConf);
            fail("should have thrown");
        } catch (BadRequestException expected) {
            // expected
        }

        Mockito.doThrow(new InvalidCharactersException(""))
                .when(initializationService).initialize(any(), any(), any(), any(), anyBoolean());
        try {
            initializationApiController.initSecurityServer(initialServerConf);
            fail("should have thrown");
        } catch (BadRequestException expected) {
            // expected
        }

        Mockito.doThrow(new WeakPinException("", Collections.emptyList()))
                .when(initializationService).initialize(any(), any(), any(), any(), anyBoolean());
        try {
            initializationApiController.initSecurityServer(initialServerConf);
            fail("should have thrown");
        } catch (BadRequestException expected) {
            // expected
        }

        Mockito.doThrow(new InitializationService.SoftwareTokenInitException("", new Exception()))
                .when(initializationService).initialize(any(), any(), any(), any(), anyBoolean());
        try {
            initializationApiController.initSecurityServer(initialServerConf);
            fail("should have thrown");
        } catch (InternalServerErrorException expected) {
            // expected
        }
    }

    private InitialServerConf createInitConfWithPin(String pin) {
        return new InitialServerConf().ownerMemberClass(OWNER_MEMBER_CLASS)
                .ownerMemberCode(OWNER_MEMBER_CODE)
                .securityServerCode(SECURITY_SERVER_CODE)
                .softwareTokenPin(pin)
                .ignoreWarnings(true);
    }
}
