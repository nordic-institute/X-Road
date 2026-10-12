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
package org.niis.xroad.securityserver.restapi.openapi;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SystemApiControllerOpMonitorCaTest extends AbstractApiControllerTestContext {

    @Autowired
    SystemApiController systemApiController;

    @Test
    @WithMockUser(authorities = {"VIEW_OP_MONITOR_CA"})
    public void getOpMonitorCaReturnsTheDetailsOfTheStoredCa() {
        X509Certificate ca = CertificateTestUtils.getMockTopCaCertificate();
        when(opMonitorCaService.getOpMonitorCa()).thenReturn(Optional.of(ca));

        var response = systemApiController.getOpMonitorCa();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var details = response.getBody();
        assertEquals(ca.getSubjectX500Principal().toString(), details.getSubjectDistinguishedName());
        assertEquals(ca.getIssuerX500Principal().toString(), details.getIssuerDistinguishedName());
        assertEquals(64, details.getHash().length());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_OP_MONITOR_CA"})
    public void getOpMonitorCaReturnsNoContentWhenNoneStored() {
        when(opMonitorCaService.getOpMonitorCa()).thenReturn(Optional.empty());

        var response = systemApiController.getOpMonitorCa();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @WithMockUser(authorities = {"UPLOAD_OP_MONITOR_CA"})
    public void uploadOpMonitorCaPassesTheFileToTheServiceAndReturnsTheDetails() {
        X509Certificate ca = CertificateTestUtils.getMockTopCaCertificate();
        byte[] content = "file content".getBytes(StandardCharsets.UTF_8);
        when(opMonitorCaService.uploadOpMonitorCa(content)).thenReturn(ca);

        var response = systemApiController.uploadOpMonitorCa(new MockMultipartFile("certificate", content));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ca.getSubjectX500Principal().toString(), response.getBody().getSubjectDistinguishedName());
    }

    @Test
    @WithMockUser(authorities = {"UPLOAD_OP_MONITOR_CA"})
    public void getOpMonitorCaRequiresViewPermission() {
        assertThrows(AccessDeniedException.class, () -> systemApiController.getOpMonitorCa());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_OP_MONITOR_CA"})
    public void uploadOpMonitorCaRequiresUploadPermission() {
        var file = new MockMultipartFile("certificate", "content".getBytes(StandardCharsets.UTF_8));

        assertThrows(AccessDeniedException.class, () -> systemApiController.uploadOpMonitorCa(file));
        verify(opMonitorCaService, never()).uploadOpMonitorCa(any());
    }
}
