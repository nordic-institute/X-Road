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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceReadinessPredicatesTest {

    private static final String INSTANCE = "DEV";

    @Mock
    private GlobalConfProvider globalConfProvider;
    @Mock
    private ServerConfRepository serverConfRepository;
    @Mock
    private SignerRpcClient signerRpcClient;

    private DataspaceReadinessPredicates predicates;

    @BeforeEach
    void setUp() throws Exception {
        predicates = new DataspaceReadinessPredicates(globalConfProvider, serverConfRepository, signerRpcClient);
        var whileEveryMockCertIsValid = CertificateTestUtils.getMockCertificate().getNotAfter().toInstant()
                .minus(Duration.ofDays(1));
        predicates.setClock(Clock.fixed(whileEveryMockCertIsValid, ZoneOffset.UTC));
    }

    @Test
    void isDataspaceReadyWhenOwnInstanceListsACredentialIssuer() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE)).thenReturn(List.of("did:web:issuer.example"));

        assertThat(predicates.isDataspaceReady()).isTrue();
    }

    @Test
    void isDataspaceNotReadyWhenOwnInstanceListsNoCredentialIssuer() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE)).thenReturn(List.of());

        assertThat(predicates.isDataspaceReady()).isFalse();
    }

    @Test
    void isDataspaceNotReadyWhenInstanceIdentifierIsUnavailable() {
        when(globalConfProvider.getInstanceIdentifier()).thenThrow(new IllegalStateException("no anchor"));

        assertThat(predicates.isDataspaceReady()).isFalse();
    }

    @Test
    void isDataspaceNotReadyWhenSharedParametersAreUnavailable() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR).build());

        assertThat(predicates.isDataspaceReady()).isFalse();
    }

    @Test
    void isDataspaceReadyFollowsGlobalConfWithoutRestart() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE))
                .thenReturn(List.of("did:web:issuer.example"))
                .thenReturn(List.of())
                .thenReturn(List.of("did:web:issuer.example"));

        assertThat(predicates.isDataspaceReady()).isTrue();
        assertThat(predicates.isDataspaceReady()).isFalse();
        assertThat(predicates.isDataspaceReady()).isTrue();
    }

    @Test
    void registeredAuthCertBytesReturnsTheBytesOfTheRegisteredAuthCert() throws Exception {
        var cert = new CertificateTestUtils.CertificateInfoBuilder().build();
        when(signerRpcClient.getTokens()).thenReturn(List.of(tokenWith(KeyUsageInfo.AUTHENTICATION, cert)));

        assertThat(predicates.registeredAuthCertBytes()).contains(CertificateTestUtils.getMockCertificate().getEncoded());
        assertThat(predicates.hasRegisteredAuthCert()).isTrue();
    }

    @Test
    void registeredAuthCertBytesReturnsTheCertThatExpiresLastRegardlessOfEnumerationOrder() throws Exception {
        var older = CertificateTestUtils.getMockCertificate();
        var newer = CertificateTestUtils.getMockAuthCertificate();
        assertThat(newer.getNotAfter()).isAfter(older.getNotAfter());
        when(signerRpcClient.getTokens()).thenReturn(List.of(
                tokenWith(KeyUsageInfo.AUTHENTICATION, new CertificateTestUtils.CertificateInfoBuilder().certificate(older).build()),
                tokenWith(KeyUsageInfo.AUTHENTICATION, new CertificateTestUtils.CertificateInfoBuilder().certificate(newer).build())));

        assertThat(predicates.registeredAuthCertBytes()).contains(newer.getEncoded());
    }

    @Test
    void anUnparseableRegisteredAuthCertIsNeverPickedButStillCountsAsRegistered() throws Exception {
        var unparseable = CertificateTestUtils.createCertificateInfo(ClientId.Conf.create("a", "b", "c"), true, true,
                CertificateInfo.STATUS_REGISTERED, "2", new byte[] {1, 2, 3}, null, null);
        var valid = new CertificateTestUtils.CertificateInfoBuilder().build();
        when(signerRpcClient.getTokens()).thenReturn(List.of(
                tokenWith(KeyUsageInfo.AUTHENTICATION, valid), tokenWith(KeyUsageInfo.AUTHENTICATION, unparseable)));

        assertThat(predicates.registeredAuthCertBytes()).contains(CertificateTestUtils.getMockCertificate().getEncoded());

        when(signerRpcClient.getTokens()).thenReturn(List.of(tokenWith(KeyUsageInfo.AUTHENTICATION, unparseable)));

        assertThat(predicates.registeredAuthCertBytes()).isEmpty();
        assertThat(predicates.hasRegisteredAuthCert()).isTrue();
    }

    @Test
    void anOlderUsableCertBeatsTheLatestExpiringOneWhoseKeyIsUnavailable() throws Exception {
        var older = CertificateTestUtils.getMockCertificate();
        var newer = CertificateTestUtils.getMockAuthCertificate();
        when(signerRpcClient.getTokens()).thenReturn(List.of(
                tokenWith(KeyUsageInfo.AUTHENTICATION, true, certInfo(older)),
                tokenWith(KeyUsageInfo.AUTHENTICATION, false, certInfo(newer))));

        assertThat(predicates.registeredAuthCertBytes()).contains(older.getEncoded());
        assertThat(predicates.hasRegisteredAuthCert()).isTrue();
    }

    @Test
    void anOlderUsableCertBeatsTheLatestExpiringOneThatIsNotValidYet() throws Exception {
        var older = CertificateTestUtils.getMockCertificate();
        var newer = CertificateTestUtils.getMockAuthCertificate();
        var between = newer.getNotBefore().toInstant().compareTo(older.getNotBefore().toInstant()) > 0
                ? newer.getNotBefore().toInstant().minusSeconds(1) : older.getNotBefore().toInstant().plusSeconds(1);
        assertThat(older.getNotBefore().toInstant()).isBeforeOrEqualTo(between);
        assertThat(newer.getNotBefore().toInstant()).isAfter(between);
        predicates.setClock(Clock.fixed(between, ZoneOffset.UTC));
        when(signerRpcClient.getTokens()).thenReturn(List.of(
                tokenWith(KeyUsageInfo.AUTHENTICATION, true, certInfo(older)),
                tokenWith(KeyUsageInfo.AUTHENTICATION, true, certInfo(newer))));

        assertThat(predicates.registeredAuthCertBytes()).contains(older.getEncoded());
    }

    @Test
    void anExpiredOlderCertIsSkippedAndNoCertIsUsableOnceAllHaveExpired() throws Exception {
        var older = CertificateTestUtils.getMockCertificate();
        var newer = CertificateTestUtils.getMockAuthCertificate();
        var validForNewerOnly = older.getNotAfter().toInstant().plusSeconds(1);
        assertThat(newer.getNotAfter().toInstant()).isAfter(validForNewerOnly);
        assertThat(newer.getNotBefore().toInstant()).isBefore(validForNewerOnly);
        predicates.setClock(Clock.fixed(validForNewerOnly, ZoneOffset.UTC));
        when(signerRpcClient.getTokens()).thenReturn(List.of(
                tokenWith(KeyUsageInfo.AUTHENTICATION, true, certInfo(older)),
                tokenWith(KeyUsageInfo.AUTHENTICATION, true, certInfo(newer))));

        assertThat(predicates.registeredAuthCertBytes()).contains(newer.getEncoded());

        predicates.setClock(Clock.fixed(newer.getNotAfter().toInstant().plusSeconds(1), ZoneOffset.UTC));

        assertThat(predicates.registeredAuthCertBytes()).isEmpty();
        assertThat(predicates.hasRegisteredAuthCert()).isTrue();
    }

    @Test
    void registeredAuthCertBytesIsEmptyWhenTheOnlyKeyIsUnavailable() {
        when(signerRpcClient.getTokens()).thenReturn(List.of(
                tokenWith(KeyUsageInfo.AUTHENTICATION, false, certInfo(CertificateTestUtils.getMockCertificate()))));

        assertThat(predicates.registeredAuthCertBytes()).isEmpty();
        assertThat(predicates.hasRegisteredAuthCert()).isTrue();
    }

    @Test
    void registeredAuthCertBytesIgnoresACertThatIsNotRegistered() {
        var cert = new CertificateTestUtils.CertificateInfoBuilder()
                .certificateStatus(CertificateInfo.STATUS_SAVED).build();
        when(signerRpcClient.getTokens()).thenReturn(List.of(tokenWith(KeyUsageInfo.AUTHENTICATION, cert)));

        assertThat(predicates.registeredAuthCertBytes()).isEmpty();
        assertThat(predicates.hasRegisteredAuthCert()).isFalse();
    }

    @Test
    void registeredAuthCertBytesIgnoresASigningKeyCert() {
        var cert = new CertificateTestUtils.CertificateInfoBuilder().build();
        when(signerRpcClient.getTokens()).thenReturn(List.of(tokenWith(KeyUsageInfo.SIGNING, cert)));

        assertThat(predicates.registeredAuthCertBytes()).isEmpty();
    }

    @Test
    void registeredAuthCertBytesIsEmptyWhenThereAreNoTokens() {
        when(signerRpcClient.getTokens()).thenReturn(List.of());

        assertThat(predicates.registeredAuthCertBytes()).isEmpty();
    }

    @Test
    void registeredAuthCertBytesIsEmptyWhenTheSignerIsUnavailable() {
        when(signerRpcClient.getTokens()).thenThrow(new IllegalStateException("signer down"));

        assertThat(predicates.registeredAuthCertBytes()).isEmpty();
        assertThat(predicates.hasRegisteredAuthCert()).isFalse();
    }

    private static TokenInfo tokenWith(KeyUsageInfo usage, CertificateInfo cert) {
        return tokenWith(usage, true, cert);
    }

    private static TokenInfo tokenWith(KeyUsageInfo usage, boolean keyAvailable, CertificateInfo cert) {
        var key = new TokenTestUtils.KeyInfoBuilder().keyUsageInfo(usage).available(keyAvailable).cert(cert).build();
        return new TokenTestUtils.TokenInfoBuilder().key(key).build();
    }

    private static CertificateInfo certInfo(X509Certificate certificate) {
        return new CertificateTestUtils.CertificateInfoBuilder().certificate(certificate).build();
    }
}
