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
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_RPC_CA_NOT_AVAILABLE;

public class SystemApiControllerInternalRpcCaTest extends AbstractApiControllerTestContext {

    @Autowired
    SystemApiController systemApiController;

    @Test
    @WithMockUser(authorities = {"VIEW_INTERNAL_RPC_CA"})
    public void getInternalRpcCaReturnsSubjectIssuerFingerprintAndValidity() {
        X509Certificate ca = CertificateTestUtils.getMockTopCaCertificate();
        when(internalRpcCaService.getInternalRpcCa()).thenReturn(ca);

        var response = systemApiController.getInternalRpcCa();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var details = response.getBody();
        assertEquals(ca.getSubjectX500Principal().toString(), details.getSubjectDistinguishedName());
        assertEquals(ca.getIssuerX500Principal().toString(), details.getIssuerDistinguishedName());
        assertEquals(64, details.getHash().length());
        assertEquals(ca.getNotBefore().toInstant(), details.getNotBefore().toInstant());
        assertEquals(ca.getNotAfter().toInstant(), details.getNotAfter().toInstant());
    }

    @Test
    @WithMockUser(authorities = {"DOWNLOAD_INTERNAL_RPC_CA"})
    public void downloadInternalRpcCaReturnsThePemFileAsAnAttachmentWithPemContentType() throws Exception {
        byte[] pem = "-----BEGIN CERTIFICATE-----\nAA==\n-----END CERTIFICATE-----\n".getBytes(StandardCharsets.UTF_8);
        when(internalRpcCaService.downloadInternalRpcCa()).thenReturn(pem);

        var response = systemApiController.downloadInternalRpcCa();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("application/x-pem-file", response.getHeaders().getContentType().toString());
        assertEquals("internal-rpc-ca.pem", response.getHeaders().getContentDisposition().getFilename());
        assertArrayEquals(pem, response.getBody().getContentAsByteArray());
    }

    @Test
    @WithMockUser(authorities = {"DOWNLOAD_INTERNAL_RPC_CA"})
    public void getInternalRpcCaRequiresViewPermission() {
        assertThrows(AccessDeniedException.class, () -> systemApiController.getInternalRpcCa());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_INTERNAL_RPC_CA"})
    public void downloadInternalRpcCaRequiresDownloadPermission() {
        assertThrows(AccessDeniedException.class, () -> systemApiController.downloadInternalRpcCa());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_INTERNAL_RPC_CA"})
    public void getInternalRpcCaAnswersNotFoundWhenTheCaIsNotAvailable() {
        when(internalRpcCaService.getInternalRpcCa()).thenThrow(new NotFoundException(INTERNAL_RPC_CA_NOT_AVAILABLE.build()));

        assertThrows(NotFoundException.class, () -> systemApiController.getInternalRpcCa());
    }

    @Test
    @WithMockUser(authorities = {"DOWNLOAD_INTERNAL_RPC_CA"})
    public void downloadInternalRpcCaAnswersNotFoundWhenTheCaIsNotAvailable() {
        when(internalRpcCaService.downloadInternalRpcCa()).thenThrow(new NotFoundException(INTERNAL_RPC_CA_NOT_AVAILABLE.build()));

        assertThrows(NotFoundException.class, () -> systemApiController.downloadInternalRpcCa());
    }
}
