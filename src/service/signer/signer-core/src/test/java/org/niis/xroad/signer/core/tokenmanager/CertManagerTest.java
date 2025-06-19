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

package org.niis.xroad.signer.core.tokenmanager;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.core.model.CertRequestData;
import org.niis.xroad.signer.core.model.RuntimeCertImpl;
import org.niis.xroad.signer.core.model.RuntimeKeyImpl;
import org.niis.xroad.signer.core.model.RuntimeTokenImpl;
import org.niis.xroad.signer.core.service.TokenKeyCertRequestService;
import org.niis.xroad.signer.core.service.TokenKeyCertService;
import org.niis.xroad.signer.core.service.TokenKeyService;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Slf4j
class CertManagerTest {

    private final TokenKeyCertService tokenKeyCertService = mock(TokenKeyCertService.class);
    private final TokenKeyService tokenKeyService = mock(TokenKeyService.class);
    private final TokenKeyCertRequestService tokenKeyCertRequestService = mock(TokenKeyCertRequestService.class);
    private final TokenRegistryLoader tokenRegistryLoader = mock(TokenRegistryLoader.class);
    private final TokenRegistry tokenRegistry = new TokenRegistry(tokenRegistryLoader);
    private final CertManager certManager = new CertManager(tokenRegistry, tokenKeyCertService, tokenKeyService,
            tokenKeyCertRequestService);

    private static final long KEY_ID = 3L;
    private static final String KEY_EXTERNAL_ID = "key-external-id";
    private static final String CERT_EXTERNAL_ID = "cert-external-id";
    private static final String CERT_REQ_EXTERNAL_ID = "cert-request-external-id";

    @Test
    void testAddCert() throws Exception {
        initRegistry(mock(RuntimeKeyImpl.class));

        ClientId.Conf clientId = ClientId.Conf.create("a", "b", "c");

        certManager.addCert(KEY_EXTERNAL_ID, clientId, "status", "id", new byte[]{'c', 'e', 'r', 't'});

        verify(tokenKeyCertService).save(3L, "id", clientId, "status", new byte[]{'c', 'e', 'r', 't'});

        verify(tokenRegistryLoader).refreshTokens(any());
    }

    @Test
    void testAddTransientCert() {
        var keyMock = mock(RuntimeKeyImpl.class);
        initRegistry(keyMock);

        certManager.addTransientCert(KEY_EXTERNAL_ID, new byte[]{'c', 'e', 'r', 't'});

        verify(keyMock).addTransientCert(anyString(), eq(new byte[]{'c', 'e', 'r', 't'}));
    }

    @Test
    void testSetCertActiveFailsForTransientCert() {
        var keyMock = mock(RuntimeKeyImpl.class);
        var certMock = mock(RuntimeCertImpl.class);
        when(certMock.externalId()).thenReturn(CERT_EXTERNAL_ID);
        when(certMock.isTransientCert()).thenReturn(true);
        when(keyMock.certs()).thenReturn(Set.of(certMock));
        initRegistry(keyMock);

        var exception = assertThrows(SignerException.class, () -> certManager.setCertActive(CERT_EXTERNAL_ID, true));

        assertEquals("Signer.InternalError", exception.getFaultCode());
        assertEquals("Operation not allowed for transient cert " + CERT_EXTERNAL_ID, exception.getFaultString());

        verify(tokenRegistryLoader).loadTokens();
        verifyNoMoreInteractions(tokenRegistryLoader);
    }

    @Test
    void testSetCertActive() throws Exception {
        var keyMock = mock(RuntimeKeyImpl.class);
        var certMock = mock(RuntimeCertImpl.class);
        when(certMock.id()).thenReturn(1L);
        when(certMock.externalId()).thenReturn(CERT_EXTERNAL_ID);
        when(keyMock.certs()).thenReturn(Set.of(certMock));
        initRegistry(keyMock);

        certManager.setCertActive(CERT_EXTERNAL_ID, true);
        verify(tokenKeyCertService).setActive(1L, true);

        verify(tokenRegistryLoader).refreshTokens(any());
    }

    @Test
    void testCertActiveCacheRefreshOnException() throws Exception {
        var keyMock = mock(RuntimeKeyImpl.class);
        var certMock = mock(RuntimeCertImpl.class);
        when(certMock.id()).thenReturn(1L);
        when(certMock.externalId()).thenReturn(CERT_EXTERNAL_ID);
        when(keyMock.certs()).thenReturn(Set.of(certMock));
        initRegistry(keyMock);

        // Simulate an exception during setActive
        doThrow(new SignerException("Test exception")).when(tokenKeyCertService).setActive(1L, true);

        var exception = assertThrows(SignerException.class, () -> certManager.setCertActive(CERT_EXTERNAL_ID, true));

        assertEquals("Signer.Test exception", exception.getFaultCode());

        verify(tokenRegistryLoader).refreshTokens(any());
    }

    @Test
    void testSetCertStatus() throws Exception {
        var keyMock = mock(RuntimeKeyImpl.class);
        var certMock = mock(RuntimeCertImpl.class);
        when(certMock.id()).thenReturn(1L);
        when(certMock.externalId()).thenReturn(CERT_EXTERNAL_ID);
        when(keyMock.certs()).thenReturn(Set.of(certMock));
        initRegistry(keyMock);

        certManager.setCertStatus(CERT_EXTERNAL_ID, "status");

        verify(tokenKeyCertService).updateStatus(1L, "status");
        verify(tokenRegistryLoader).refreshTokens(any());
    }

    @Test
    void testSetCertStatusFailsForTransient() {
        var keyMock = mock(RuntimeKeyImpl.class);
        var certMock = mock(RuntimeCertImpl.class);
        when(certMock.externalId()).thenReturn(CERT_EXTERNAL_ID);
        when(certMock.isTransientCert()).thenReturn(true);
        when(keyMock.certs()).thenReturn(Set.of(certMock));
        initRegistry(keyMock);

        var exception = assertThrows(SignerException.class, () -> certManager.setCertStatus(CERT_EXTERNAL_ID, "status"));

        assertEquals("Signer.InternalError", exception.getFaultCode());
        assertEquals("Operation not allowed for transient cert " + CERT_EXTERNAL_ID, exception.getFaultString());

        verifyNoInteractions(tokenKeyCertService);
        verify(tokenRegistryLoader).loadTokens();
        verifyNoMoreInteractions(tokenRegistryLoader);
    }

    @Test
    void testSetRenewedCertHash() throws Exception {
        var keyMock = mock(RuntimeKeyImpl.class);
        var certMock = mock(RuntimeCertImpl.class);
        when(certMock.id()).thenReturn(1L);
        when(certMock.externalId()).thenReturn(CERT_EXTERNAL_ID);
        when(keyMock.certs()).thenReturn(Set.of(certMock));
        initRegistry(keyMock);

        certManager.setRenewedCertHash(CERT_EXTERNAL_ID, "newHash");

        verify(tokenKeyCertService).updateRenewedCertHash(1L, "newHash");
        verify(tokenRegistryLoader).refreshTokens(any());
    }

    @Test
    void testSetRenewedCertHashFailsForTransient() {
        var keyMock = mock(RuntimeKeyImpl.class);
        var certMock = mock(RuntimeCertImpl.class);
        when(certMock.id()).thenReturn(1L);
        when(certMock.isTransientCert()).thenReturn(true);
        when(certMock.externalId()).thenReturn(CERT_EXTERNAL_ID);
        when(keyMock.certs()).thenReturn(Set.of(certMock));
        initRegistry(keyMock);

        var exception = assertThrows(SignerException.class, () -> certManager.setRenewedCertHash(CERT_EXTERNAL_ID, "status"));

        assertEquals("Signer.InternalError", exception.getFaultCode());
        assertEquals("Operation not allowed for transient cert " + CERT_EXTERNAL_ID, exception.getFaultString());

        verifyNoInteractions(tokenKeyCertService);
        verify(tokenRegistryLoader).loadTokens();
        verifyNoMoreInteractions(tokenRegistryLoader);
    }

    @Test
    void testSetRenewalError() throws Exception {
        var keyMock = mock(RuntimeKeyImpl.class);
        var certMock = mock(RuntimeCertImpl.class);
        when(certMock.id()).thenReturn(1L);
        when(certMock.externalId()).thenReturn(CERT_EXTERNAL_ID);
        when(keyMock.certs()).thenReturn(Set.of(certMock));
        initRegistry(keyMock);

        certManager.setRenewalError(CERT_EXTERNAL_ID, "renewal error");

        verify(tokenKeyCertService).updateRenewalError(1L, "renewal error");
        verify(tokenRegistryLoader).refreshTokens(any());
    }

    @Test
    void testSetRenewalErrorFailsForTransient() {
        var keyMock = mock(RuntimeKeyImpl.class);
        var certMock = mock(RuntimeCertImpl.class);
        when(certMock.id()).thenReturn(1L);
        when(certMock.isTransientCert()).thenReturn(true);
        when(certMock.externalId()).thenReturn(CERT_EXTERNAL_ID);
        when(keyMock.certs()).thenReturn(Set.of(certMock));
        initRegistry(keyMock);

        var exception = assertThrows(SignerException.class, () -> certManager.setRenewalError(CERT_EXTERNAL_ID, "renewal error"));

        assertEquals("Signer.InternalError", exception.getFaultCode());
        assertEquals("Operation not allowed for transient cert " + CERT_EXTERNAL_ID, exception.getFaultString());

        verifyNoInteractions(tokenKeyCertService);
        verify(tokenRegistryLoader).loadTokens();
        verifyNoMoreInteractions(tokenRegistryLoader);
    }

    @Test
    void testSetNextPlannedRenewal() throws Exception {
        var keyMock = mock(RuntimeKeyImpl.class);
        var certMock = mock(RuntimeCertImpl.class);
        when(certMock.id()).thenReturn(1L);
        when(certMock.externalId()).thenReturn(CERT_EXTERNAL_ID);
        when(keyMock.certs()).thenReturn(Set.of(certMock));
        initRegistry(keyMock);

        Instant nextRenewalTime = Instant.now();

        certManager.setNextPlannedRenewal(CERT_EXTERNAL_ID, nextRenewalTime);

        verify(tokenKeyCertService).updateNextAutomaticRenewalTime(1L, nextRenewalTime);
        verify(tokenRegistryLoader).refreshTokens(any());
    }

    @Test
    void testSetNextPlannedRenewalFailsForTransient() {
        var keyMock = mock(RuntimeKeyImpl.class);
        var certMock = mock(RuntimeCertImpl.class);
        when(certMock.id()).thenReturn(1L);
        when(certMock.isTransientCert()).thenReturn(true);
        when(certMock.externalId()).thenReturn(CERT_EXTERNAL_ID);
        when(keyMock.certs()).thenReturn(Set.of(certMock));
        initRegistry(keyMock);

        var exception = assertThrows(SignerException.class, () -> certManager.setNextPlannedRenewal(CERT_EXTERNAL_ID, Instant.now()));

        assertEquals("Signer.InternalError", exception.getFaultCode());
        assertEquals("Operation not allowed for transient cert " + CERT_EXTERNAL_ID, exception.getFaultString());

        verifyNoInteractions(tokenKeyCertService);
        verify(tokenRegistryLoader).loadTokens();
        verifyNoMoreInteractions(tokenRegistryLoader);
    }

    @Test
    void testRemoveCert() throws Exception {
        var keyMock = mock(RuntimeKeyImpl.class);
        var certMock = mock(RuntimeCertImpl.class);
        when(certMock.id()).thenReturn(1L);
        when(certMock.externalId()).thenReturn(CERT_EXTERNAL_ID);
        when(keyMock.certs()).thenReturn(Set.of(certMock));
        initRegistry(keyMock);
        when(tokenKeyCertService.delete(1L)).thenReturn(true);

        assertTrue(certManager.removeCert(CERT_EXTERNAL_ID));

        verify(tokenKeyCertService).delete(1L);
        verify(tokenRegistryLoader).refreshTokens(any());
    }

    @Test
    void testRemoveCertNoFailWhenNotFound() {
        initRegistry(mock(RuntimeKeyImpl.class));

        assertFalse(certManager.removeCert(CERT_EXTERNAL_ID));

        verifyNoInteractions(tokenKeyCertService);
        verify(tokenRegistryLoader).loadTokens();
        verifyNoMoreInteractions(tokenRegistryLoader);
    }

    @Test
    void testAddCertRequest() throws Exception {
        var keyMock = mock(RuntimeKeyImpl.class);

        initRegistry(keyMock);

        ClientId.Conf memberID = ClientId.Conf.create("a", "b", "c");

        certManager.addCertRequest(KEY_EXTERNAL_ID, memberID, "subjectName", "subjectAltName",
                KeyUsageInfo.AUTHENTICATION, "certProfile");

        verify(tokenKeyService).updateKeyUsage(KEY_ID, KeyUsageInfo.AUTHENTICATION);
        verify(tokenKeyCertRequestService).save(
                eq(KEY_ID), anyString(), eq(memberID), eq("subjectName"), eq("subjectAltName"), eq("certProfile"));
        verify(tokenRegistryLoader).refreshTokens(any());
    }

    @Test
    void testAddCertRequestKeyUsageDiffers() {
        var keyMock = mock(RuntimeKeyImpl.class);
        when(keyMock.usage()).thenReturn(KeyUsageInfo.SIGNING);

        initRegistry(keyMock);

        ClientId.Conf memberID = ClientId.Conf.create("a", "b", "c");

        var exception = assertThrows(CodedException.class, () ->
                certManager.addCertRequest(KEY_EXTERNAL_ID, memberID, "subjectName", "subjectAltName",
                        KeyUsageInfo.AUTHENTICATION, "certProfile"));

        assertEquals("WrongCertUsage", exception.getFaultCode());
        assertEquals("cert_request_wrong_usage", exception.getTranslationCode());
        assertEquals("Cannot add AUTHENTICATION certificate request to SIGNING key", exception.getFaultString());

        verifyNoInteractions(tokenKeyService);
        verifyNoInteractions(tokenKeyCertRequestService);
        verify(tokenRegistryLoader).loadTokens();
        verifyNoMoreInteractions(tokenRegistryLoader);
    }

    @Test
    void testAddCertRequestCertReqAlreadyExists() throws Exception {
        ClientId.Conf memberID = ClientId.Conf.create("a", "b", "c");

        var keyMock = mock(RuntimeKeyImpl.class);
        var certReqMock = mock(CertRequestData.class);
        when(certReqMock.externalId()).thenReturn(CERT_REQ_EXTERNAL_ID);
        when(certReqMock.subjectName()).thenReturn("subjectName");
        when(certReqMock.memberId()).thenReturn(memberID);
        when(keyMock.certRequests()).thenReturn(Set.of(certReqMock));
        initRegistry(keyMock);

        var crId = certManager.addCertRequest(KEY_EXTERNAL_ID, memberID, "subjectName", "subjectAltName",
                KeyUsageInfo.AUTHENTICATION, "certProfile");

        assertEquals(CERT_REQ_EXTERNAL_ID, crId);

        verify(tokenKeyService).updateKeyUsage(KEY_ID, KeyUsageInfo.AUTHENTICATION);
        verifyNoInteractions(tokenKeyCertRequestService);
        verify(tokenRegistryLoader).refreshTokens(any());
    }

    @Test
    void testRemoveCertRequest() throws Exception {
        var keyMock = mock(RuntimeKeyImpl.class);
        var certReqMock = mock(CertRequestData.class);
        when(certReqMock.externalId()).thenReturn(CERT_REQ_EXTERNAL_ID);
        when(certReqMock.id()).thenReturn(1L);
        when(keyMock.certRequests()).thenReturn(Set.of(certReqMock));
        initRegistry(keyMock);
        when(tokenKeyCertRequestService.delete(1L)).thenReturn(true);

        assertTrue(certManager.removeCertRequest(CERT_REQ_EXTERNAL_ID));

        verify(tokenKeyCertRequestService).delete(1L);
        verify(tokenRegistryLoader).refreshTokens(any());
    }

    @Test
    void testRemoveCertRequestNoFailWhenNotFound() {
        initRegistry(mock(RuntimeKeyImpl.class));

        assertFalse(certManager.removeCertRequest("no-such-cert-request"));

        verifyNoInteractions(tokenKeyCertRequestService);
        verify(tokenRegistryLoader).loadTokens();
        verifyNoMoreInteractions(tokenRegistryLoader);
    }

    private void initRegistry(RuntimeKeyImpl keyMock) {
        RuntimeTokenImpl tokenMock = mock(RuntimeTokenImpl.class);
        when(keyMock.externalId()).thenReturn(KEY_EXTERNAL_ID);
        when(keyMock.id()).thenReturn(KEY_ID);
        when(tokenMock.keys()).thenReturn(Set.of(keyMock));
        when(tokenRegistryLoader.loadTokens()).thenReturn(Set.of(tokenMock));

        tokenRegistry.init();
    }

}
