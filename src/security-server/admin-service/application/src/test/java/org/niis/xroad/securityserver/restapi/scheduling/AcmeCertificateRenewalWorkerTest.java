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
package org.niis.xroad.securityserver.restapi.scheduling;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.TimeUtils;

import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.acme.AcmeKeyPurpose;
import org.niis.xroad.common.acme.AcmeServiceException;
import org.niis.xroad.common.acme.config.AcmeConfig;
import org.niis.xroad.common.acme.spring.scheduling.CertificateRenewalScheduler;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.managementrequest.ManagementRequestSender;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.MailNotificationHelper;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.serverconf.impl.ownserver.OwnAddress;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.NoOpTaskScheduler;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.security.auth.x500.X500Principal;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static ee.ria.xroad.common.TestCertUtil.getCa;
import static ee.ria.xroad.common.TestCertUtil.getKeyPairGenerator;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.ORDER_CREATION_FAILURE;
import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_GLOBALCONF;
import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_SERVERCONF;
import static org.niis.xroad.securityserver.restapi.util.CertificateTestUtils.getMockSignCsrBytes;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.approvedCaWithAcme;

@WithAnonymousUser
public class AcmeCertificateRenewalWorkerTest extends AbstractFacadeMockingTestContext {

    private static final String DNS = "ss9";
    private static final ClientId.Conf DEFAULT_MEMBER_ID = ClientId.Conf.create("a", "b", "c");
    @MockitoSpyBean
    private AcmeCertificateRenewalWorker acmeCertificateRenewalWorker;
    @MockitoBean
    ManagementRequestSender managementRequestSenderMock;
    @MockitoSpyBean
    MailNotificationHelper mailNotificationHelper;
    @MockitoSpyBean
    AcmeConfig acmeConfig;
    @MockitoSpyBean
    OwnSecurityServerResolver ownSecurityServerResolver;
    @Autowired
    ServerConfRepository serverConfRepository;

    private final KeyPair keyPair = getKeyPairGenerator().generateKeyPair();
    private final TestCertUtil.PKCS12 ca = getCa();

    @Before
    public void setUp() throws Exception {
        when(globalConfProvider.isValid()).thenReturn(true);
        when(globalConfProvider.getApprovedCA(any(), any()))
                .thenReturn(approvedCaWithAcme("testca", false, "ee.test.Profile"));

        CertificateInfo signCertInfo = createCertificateInfo("sign_cert_id", "M1", new KeyUsage(KeyUsage.nonRepudiation),
                Date.from(TimeUtils.now().minus(360, ChronoUnit.DAYS)), Date.from(TimeUtils.now().plus(5, ChronoUnit.DAYS)), null);
        KeyInfo signKey = new TokenTestUtils.KeyInfoBuilder()
                .id("sign_key_id")
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .cert(signCertInfo)
                .build();

        CertificateInfo authCertInfo = createCertificateInfo("auth_cert_id", DNS, new KeyUsage(KeyUsage.digitalSignature),
                Date.from(TimeUtils.now().minus(360, ChronoUnit.DAYS)), Date.from(TimeUtils.now().plus(5, ChronoUnit.DAYS)), null);
        KeyInfo authKey = new TokenTestUtils.KeyInfoBuilder()
                .id("auth_key_id")
                .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                .cert(authCertInfo)
                .build();

        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .friendlyName("test-token")
                .key(signKey)
                .key(authKey)
                .build();

        when(signerRpcClient.getTokens()).thenReturn(new ArrayList<>(List.of(tokenInfo)));
        when(signerRpcClient.getTokenAndKeyIdForCertHash(any())).thenReturn(new TokenInfoAndKeyId(tokenInfo, authKey.getId()));
        when(signerRpcClient.getCertForHash(calculateCertHexHash(authCertInfo.getCertificateBytes()))).thenReturn(authCertInfo);
        when(signerRpcClient.getCertForHash(calculateCertHexHash(signCertInfo.getCertificateBytes()))).thenReturn(signCertInfo);

        KeyInfo newKey = new TokenTestUtils.KeyInfoBuilder()
                .id("new_key_id")
                .build();

        when(signerRpcClient.generateKey(any(), any(), any())).thenReturn(newKey);
        when(signerRpcClient.generateCertRequest(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SignerRpcClient.GeneratedCertRequestInfo(null, getMockSignCsrBytes(), null, null, null));

        when(acmeService.hasRenewalInfo(any(), any(), any(), any())).thenReturn(true);
        when(acmeService.isRenewalRequired(any(), any(), any(), any(), any())).thenReturn(true);

        CertificateInfo newSignCertInfo = createCertificateInfo("new_sign_cert_id", "M1", new KeyUsage(KeyUsage.nonRepudiation),
                Date.from(TimeUtils.now()), Date.from(TimeUtils.now().plus(365, ChronoUnit.DAYS)), null);

        CertificateInfo newAuthCertInfo = createCertificateInfo("new_auth_cert_id", DNS, new KeyUsage(KeyUsage.digitalSignature),
                Date.from(TimeUtils.now()), Date.from(TimeUtils.now().plus(365, ChronoUnit.DAYS)), null);

        when(signerRpcClient.getCertForHash(calculateCertHexHash(newSignCertInfo.getCertificateBytes()))).thenReturn(newSignCertInfo);
        when(signerRpcClient.getCertForHash(calculateCertHexHash(newAuthCertInfo.getCertificateBytes()))).thenReturn(newAuthCertInfo);

        when(acmeService.renew(any(),
                any(),
                any(),
                eq(AcmeKeyPurpose.SIGNING),
                any(),
                any(),
                any())).thenReturn(List.of(readCertificate(newSignCertInfo.getCertificateBytes())));

        when(acmeService.renew(any(),
                any(),
                any(),
                eq(AcmeKeyPurpose.AUTHENTICATION),
                any(),
                any(),
                any())).thenReturn(List.of(readCertificate(newAuthCertInfo.getCertificateBytes())));

        doReturn(managementRequestSenderMock).when(acmeCertificateRenewalWorker).createManagementRequestSender(any());
    }

    private CertificateInfo createCertificateInfo(String certId, String commonName, KeyUsage keyUsage, Date notBefore,
                                                  Date notAfter, String renewedCertHash)
            throws OperatorCreationException, IOException, CertificateException {
        return createCertificateInfo(certId, commonName, keyUsage, notBefore, notAfter, renewedCertHash, true, DEFAULT_MEMBER_ID);
    }

    private CertificateInfo createCertificateInfo(String certId, String commonName, KeyUsage keyUsage, Date notBefore,
                                                  Date notAfter, String renewedCertHash, boolean withSubjectAltName,
                                                  ClientId.Conf memberId)
            throws OperatorCreationException, IOException, CertificateException {
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(ca.key);
        var issuer = ca.certChain[0].getSubjectX500Principal();
        var subject = new X500Principal("CN=" + commonName);
        var subjectAltName = new GeneralName[1];
        subjectAltName[0] = new GeneralName(GeneralName.dNSName, DNS);
        var certificateBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.ONE,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic())
                .addExtension(Extension.create(
                        Extension.keyUsage,
                        true,
                        keyUsage));
        if (withSubjectAltName) {
            certificateBuilder.addExtension(Extension.create(Extension.subjectAlternativeName, false, new GeneralNames(subjectAltName)));
        }
        X509CertificateHolder certificateHolder = certificateBuilder.build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(certificateHolder);
        CertificateTestUtils.CertificateInfoBuilder certificateInfoBuilder = new CertificateTestUtils.CertificateInfoBuilder()
                .id(certId)
                .clientId(memberId)
                .certificate(certificate);
        if (renewedCertHash != null) {
            certificateInfoBuilder.renewedCertHash(renewedCertHash);
        }
        return certificateInfoBuilder.build();
    }

    @Test
    public void successfulAuthAndSignCertRenewals() throws Exception {
        CertificateRenewalScheduler scheduler =
                new CertificateRenewalScheduler(acmeCertificateRenewalWorker, acmeConfig, new NoOpTaskScheduler());
        acmeCertificateRenewalWorker.execute(scheduler);
        verify(signerRpcClient, times(2)).importCert(any(), any(), any(), anyBoolean());
        verify(managementRequestSenderMock, times(1)).sendAuthCertRegRequest(any(), any(), any(), anyBoolean());
        verify(signerRpcClient, times(2)).setRenewedCertHash(any(), any());
        verify(signerRpcClient, times(2)).setNextPlannedRenewal(any(), any());
    }

    @Test
    public void successfulAuthAndSignCertRenewalsAutoActivateCert() {
        when(acmeConfig.isAutomaticActivateAcmeSignCertificate()).thenReturn(true);

        CertificateRenewalScheduler scheduler =
                new CertificateRenewalScheduler(acmeCertificateRenewalWorker, acmeConfig, new NoOpTaskScheduler());
        acmeCertificateRenewalWorker.execute(scheduler);

        verify(signerRpcClient).importCert(any(), any(), any(), eq(false));
        verify(signerRpcClient).importCert(any(), any(), any(), eq(true));
        verify(mailNotificationHelper).sendCertActivatedNotification(any(), any(), any(), any());
    }

    @Test
    public void successfulAuthAndSignCertRenewalsManualActivateCert() {
        CertificateRenewalScheduler scheduler =
                new CertificateRenewalScheduler(acmeCertificateRenewalWorker, acmeConfig, new NoOpTaskScheduler());
        acmeCertificateRenewalWorker.execute(scheduler);

        verify(signerRpcClient, times(2)).importCert(any(), any(), any(), eq(false));
        verify(signerRpcClient, times(0)).importCert(any(), any(), any(), eq(true));
        verify(mailNotificationHelper, times(0)).sendCertActivatedNotification(any(), any(), any(), any());
    }

    @Test
    public void failureAuthAndSignCertRollback() {
        when(acmeService.renew(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new AcmeServiceException(ORDER_CREATION_FAILURE.build()));

        CertificateRenewalScheduler scheduler =
                new CertificateRenewalScheduler(acmeCertificateRenewalWorker, acmeConfig, new NoOpTaskScheduler());
        acmeCertificateRenewalWorker.execute(scheduler);

        verify(signerRpcClient, never()).importCert(any(), any(), any(), anyBoolean());
        verify(signerRpcClient, times(4)).deleteKey(any(), anyBoolean());
        verify(signerRpcClient, times(2)).setRenewalError(any(), any());
    }

    @Test
    public void signCertWithoutSubjectAltNameUsesRegisteredAddress() throws Exception {
        useTokens(signKeyWithoutSubjectAltName(DEFAULT_MEMBER_ID));
        doReturn(new OwnAddress.Registered(securityServerId(), "ss.example.org")).when(ownSecurityServerResolver).address();

        runRenewal();

        verify(acmeService).renew(any(), eq("ss.example.org"), any(), eq(AcmeKeyPurpose.SIGNING), any(), any(), any());
        verify(signerRpcClient).generateCertRequest(any(), any(), any(), any(), eq("ss.example.org"), any(), any());
        verify(signerRpcClient).importCert(any(), any(), any(), anyBoolean());
    }

    @Test
    public void signCertWithoutSubjectAltNameSkippedBeforeKeyGenerationWhenNotRegistered() throws Exception {
        useTokens(signKeyWithoutSubjectAltName(DEFAULT_MEMBER_ID));
        doReturn(new OwnAddress.NotRegistered(securityServerId())).when(ownSecurityServerResolver).address();

        runRenewal();

        verify(signerRpcClient, never()).generateKey(any(), any(), any());
        verify(signerRpcClient, never()).deleteKey(any(), anyBoolean());
        verify(signerRpcClient).setRenewalError(eq("sign_cert_id"), contains("not registered in GlobalConf"));
        verify(mailNotificationHelper).sendFailureNotification(any(), any(), any(), contains("not registered in GlobalConf"));
        verify(acmeService, never()).renew(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void signCertWithoutSubjectAltNameAbortsBeforeKeyGenerationWhenGlobalConfUnavailable() throws Exception {
        useTokens(signKeyWithoutSubjectAltName(DEFAULT_MEMBER_ID));
        var cause = XrdRuntimeException.systemException(MALFORMED_GLOBALCONF, "global conf is not readable");
        doReturn(new OwnAddress.GlobalConfUnavailable(securityServerId(), cause)).when(ownSecurityServerResolver).address();

        runRenewal();

        verify(signerRpcClient, never()).generateKey(any(), any(), any());
        verify(signerRpcClient).setRenewalError(eq("sign_cert_id"), contains(MALFORMED_GLOBALCONF.code()));
        verify(acmeService, never()).renew(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void signCertWithoutSubjectAltNameReportsUninitialisedOwnerWithoutNullPointerException() throws Exception {
        useTokens(signKeyWithoutSubjectAltName(DEFAULT_MEMBER_ID));
        doReturn(new OwnAddress.OwnerNotInitialised()).when(ownSecurityServerResolver).address();

        runRenewal();

        verify(signerRpcClient, never()).generateKey(any(), any(), any());
        var errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(signerRpcClient).setRenewalError(eq("sign_cert_id"), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).contains(MALFORMED_SERVERCONF.code()).doesNotContain("NullPointerException");
    }

    @Test
    public void ownerNotSetStoresErrorsWithoutNotificationAndProcessesRemainingCertificates() throws Exception {
        useTokens(signKeyWithoutSubjectAltName(DEFAULT_MEMBER_ID), authKey());
        clearOwner();
        CertificateRenewalScheduler scheduler = mock(CertificateRenewalScheduler.class);

        acmeCertificateRenewalWorker.execute(scheduler);

        verify(signerRpcClient, never()).generateKey(any(), any(), any());
        var signErrorCaptor = ArgumentCaptor.forClass(String.class);
        verify(signerRpcClient).setRenewalError(eq("sign_cert_id"), signErrorCaptor.capture());
        assertThat(signErrorCaptor.getValue()).contains(MALFORMED_SERVERCONF.code()).doesNotContain("NullPointerException");
        verify(signerRpcClient).setRenewalError(eq("auth_cert_id"), contains(MALFORMED_SERVERCONF.code()));
        verify(mailNotificationHelper, never()).sendFailureNotification(any(), any(), any(), any());
        verify(scheduler).failure();
        verify(scheduler, never()).success();
    }

    @Test
    public void ownerNotSetWithoutCertificateMemberIdStoresErrorWithoutNotification() throws Exception {
        useTokens(signKeyWithoutSubjectAltName(null));
        when(globalConfProvider.getServerIdOrThrow(any()))
                .thenThrow(XrdRuntimeException.systemException(MALFORMED_GLOBALCONF, "server id is not available"));
        clearOwner();

        runRenewal();

        verify(signerRpcClient).setRenewalError(eq("sign_cert_id"), contains(MALFORMED_GLOBALCONF.code()));
        verify(mailNotificationHelper, never()).sendFailureNotification(any(), any(), any(), any());
        verify(signerRpcClient, never()).generateKey(any(), any(), any());
    }

    @Test
    public void unexpectedExceptionFromOneCertificateDoesNotStopTheCycle() throws Exception {
        doThrow(new IllegalStateException("unexpected"))
                .when(acmeService).checkAccountKeyPairAndRenewIfNecessary(any(), any(), eq(AcmeKeyPurpose.SIGNING), any());
        CertificateRenewalScheduler scheduler = mock(CertificateRenewalScheduler.class);

        acmeCertificateRenewalWorker.execute(scheduler);

        verify(acmeService, times(2)).checkAccountKeyPairAndRenewIfNecessary(any(), any(), any(), any());
        verify(signerRpcClient, times(1)).importCert(any(), eq(CertificateInfo.STATUS_SAVED), any(), eq(false));
        verify(managementRequestSenderMock, times(1)).sendAuthCertRegRequest(any(), any(), any(), anyBoolean());
        verify(scheduler).failure();
        verify(scheduler, never()).success();
    }

    private void runRenewal() {
        CertificateRenewalScheduler scheduler =
                new CertificateRenewalScheduler(acmeCertificateRenewalWorker, acmeConfig, new NoOpTaskScheduler());
        acmeCertificateRenewalWorker.execute(scheduler);
    }

    private void clearOwner() {
        serverConfRepository.getServerConf().setOwner(null);
    }

    private SecurityServerId.Conf securityServerId() {
        return SecurityServerId.Conf.create("DEV", "COM", "222", "SS1");
    }

    private KeyInfo signKeyWithoutSubjectAltName(ClientId.Conf memberId) throws Exception {
        CertificateInfo signCertInfo = createCertificateInfo("sign_cert_id", "M1", new KeyUsage(KeyUsage.nonRepudiation),
                Date.from(TimeUtils.now().minus(360, ChronoUnit.DAYS)), Date.from(TimeUtils.now().plus(5, ChronoUnit.DAYS)), null,
                false, memberId);
        return new TokenTestUtils.KeyInfoBuilder()
                .id("sign_key_id")
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .cert(signCertInfo)
                .build();
    }

    private KeyInfo authKey() throws Exception {
        CertificateInfo authCertInfo = createCertificateInfo("auth_cert_id", DNS, new KeyUsage(KeyUsage.digitalSignature),
                Date.from(TimeUtils.now().minus(360, ChronoUnit.DAYS)), Date.from(TimeUtils.now().plus(5, ChronoUnit.DAYS)), null);
        return new TokenTestUtils.KeyInfoBuilder()
                .id("auth_key_id")
                .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                .cert(authCertInfo)
                .build();
    }

    private void useTokens(KeyInfo... keys) throws Exception {
        TokenTestUtils.TokenInfoBuilder tokenInfoBuilder = new TokenTestUtils.TokenInfoBuilder().friendlyName("test-token");
        for (KeyInfo key : keys) {
            tokenInfoBuilder.key(key);
        }
        TokenInfo tokenInfo = tokenInfoBuilder.build();
        when(signerRpcClient.getTokens()).thenReturn(new ArrayList<>(List.of(tokenInfo)));
        when(signerRpcClient.getTokenAndKeyIdForCertHash(any())).thenReturn(new TokenInfoAndKeyId(tokenInfo, keys[0].getId()));
    }
}
