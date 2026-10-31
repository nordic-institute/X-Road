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

package org.niis.xroad.cs.admin.rest.api.openapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.SystemDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.MemberDidRegistrationService;
import org.niis.xroad.cs.admin.api.service.SystemDidRegistrationService;
import org.niis.xroad.cs.admin.rest.api.converter.db.ManagementRequestDtoConverter;
import org.niis.xroad.cs.openapi.model.MemberDidRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.SystemDidRegistrationRequestDto;
import org.niis.xroad.restapi.exceptions.ExceptionTranslator;
import org.niis.xroad.restapi.exceptions.ValidationErrorHelper;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_FINAL_CONFLICT;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_FIXABLE_CONFLICT;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_NOT_RESOLVED;

@ExtendWith(MockitoExtension.class)
class ManagementRequestsApiControllerTest {

    @Mock
    private ManagementRequestService service;
    @Mock
    private SystemDidRegistrationService systemDidRegistrationService;
    @Mock
    private MemberDidRegistrationService memberDidRegistrationService;
    @Mock
    private ManagementRequestDtoConverter converter;

    @InjectMocks
    private ManagementRequestsApiController controller;

    @Test
    void addSystemDidRegistrationRequestGoesThroughThePreCheckServiceAndAnswersAccepted() {
        var dto = new SystemDidRegistrationRequestDto();
        var request = mock(SystemDidRegistrationRequest.class);
        var added = mock(SystemDidRegistrationRequest.class);
        var responseDto = new SystemDidRegistrationRequestDto();
        when(converter.fromDto(dto)).thenReturn(request);
        when(systemDidRegistrationService.add(request)).thenReturn(added);
        when(converter.toDto(added)).thenReturn(responseDto);

        var response = controller.addManagementRequest(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isSameAs(responseDto);
        verify(systemDidRegistrationService).add(request);
        verifyNoInteractions(service);
    }

    @Test
    void addMemberDidRegistrationRequestGoesThroughTheBatchServiceAndAnswersAccepted() {
        var dto = new MemberDidRegistrationRequestDto();
        var request = mock(MemberDidRegistrationRequest.class);
        var added = mock(MemberDidRegistrationRequest.class);
        var responseDto = new MemberDidRegistrationRequestDto();
        when(converter.fromDto(dto)).thenReturn(request);
        when(memberDidRegistrationService.add(request)).thenReturn(added);
        when(converter.toDto(added)).thenReturn(responseDto);

        var response = controller.addManagementRequest(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isSameAs(responseDto);
        verify(memberDidRegistrationService).add(request);
        verifyNoInteractions(service, systemDidRegistrationService);
    }

    @Test
    void finalConflictIsAnsweredAs409WithTheReasonInTheMetadata() {
        var response = answerOf(new ConflictException(MR_DID_FINAL_CONFLICT.build("WRONG_SCHEME")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getError().getCode()).isEqualTo("management_request_did_final_conflict");
        assertThat(response.getBody().getError().getMetadata()).containsExactly("WRONG_SCHEME");
    }

    @Test
    void fixableConflictIsAnsweredAs409WithTheReasonInTheMetadata() {
        var response = answerOf(new ConflictException(MR_DID_FIXABLE_CONFLICT.build("HOST_DIFFERS")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getError().getCode()).isEqualTo("management_request_did_fixable_conflict");
        assertThat(response.getBody().getError().getMetadata()).containsExactly("HOST_DIFFERS");
    }

    @Test
    void notResolvedIsAnsweredAs400WithTheReasonInTheMetadata() {
        var response = answerOf(new BadRequestException(MR_DID_NOT_RESOLVED.build("NO_DOCUMENT")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError().getCode()).isEqualTo("management_request_did_not_resolved");
        assertThat(response.getBody().getError().getMetadata()).containsExactly("NO_DOCUMENT");
    }

    private ResponseEntity<ErrorInfo> answerOf(RuntimeException failure) {
        var dto = new SystemDidRegistrationRequestDto();
        var request = mock(SystemDidRegistrationRequest.class);
        when(converter.fromDto(dto)).thenReturn(request);
        when(systemDidRegistrationService.add(request)).thenThrow(failure);

        var thrown = catchThrowableOfType(RuntimeException.class, () -> controller.addManagementRequest(dto));

        assertThat(thrown).isSameAs(failure);
        return new ExceptionTranslator(new ValidationErrorHelper()).toResponseEntity(thrown, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
