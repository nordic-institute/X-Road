/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.service.managementrequest;

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.api.domain.Request;
import org.niis.xroad.cs.admin.api.domain.RequestWithProcessing;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagementRequestServiceImplTest {
    private final AuthenticationCertificateRegistrationRequestHandler certificateRegistrationRequestHandler =
            mock(AuthenticationCertificateRegistrationRequestHandler.class);
    private final ClientRegistrationRequestHandler clientRegistrationRequestHandler =
            mock(ClientRegistrationRequestHandler.class);
    private final ClientDeletionRequestHandler clientDeletionRequestHandler =
            mock(ClientDeletionRequestHandler.class);
    private final OwnerChangeRequestHandler ownerChangeRequestHandler =
            mock(OwnerChangeRequestHandler.class);
    @Spy
    private List<RequestHandler<? extends Request>> handlers = Arrays.asList(
            certificateRegistrationRequestHandler,
            clientRegistrationRequestHandler,
            clientDeletionRequestHandler,
            ownerChangeRequestHandler);

    @InjectMocks
    private ManagementRequestServiceImpl service;

    @Test
    void shouldSelectCorrectHandler() {
        AuthenticationCertificateRegistrationRequest request = new AuthenticationCertificateRegistrationRequest(
                Origin.SECURITY_SERVER, SecurityServerId.create("Instance",
                "memberClass",
                "memberCode",
                "ServerCode"));
        when(certificateRegistrationRequestHandler.narrow(
                any(AuthenticationCertificateRegistrationRequest.class)))
                .thenReturn(Option.of(request));

        service.add(request);

        verify(certificateRegistrationRequestHandler, times(1))
                .add(any(AuthenticationCertificateRegistrationRequest.class));
        verify(certificateRegistrationRequestHandler, never())
                .approve(any(AuthenticationCertificateRegistrationRequest.class));
    }

    @Test
    void shouldThrowExceptionIfNoCorrectHandler() {
        when(certificateRegistrationRequestHandler.narrow(any())).thenReturn(Option.none());
        when(clientRegistrationRequestHandler.narrow(any())).thenReturn(Option.none());
        when(clientDeletionRequestHandler.narrow(any())).thenReturn(Option.none());
        when(ownerChangeRequestHandler.narrow(any())).thenReturn(Option.none());

        assertThrows(ServiceException.class, () -> service.add(new IncorrectRequest()));

        verify(certificateRegistrationRequestHandler, never()).add(any());
        verify(clientRegistrationRequestHandler, never()).add(any());
        verify(clientDeletionRequestHandler, never()).add(any());
        verify(ownerChangeRequestHandler, never()).add(any());
    }

    private static class IncorrectRequest extends RequestWithProcessing {
        @Override
        public ManagementRequestType getManagementRequestType() {
            return null;
        }
    }
}
