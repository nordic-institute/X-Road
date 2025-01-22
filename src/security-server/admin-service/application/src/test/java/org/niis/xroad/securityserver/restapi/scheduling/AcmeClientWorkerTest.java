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
import org.mockito.Mock;
import org.niis.xroad.common.acme.AcmeServiceException;
import org.niis.xroad.common.managementrequest.ManagementRequestSender;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.client.SignerProxy;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.scheduling.support.NoOpTaskScheduler;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.ORDER_CREATION_FAILURE;
import static org.niis.xroad.securityserver.restapi.util.CertificateTestUtils.getMockSignCsrBytes;

public class AcmeClientWorkerTest extends AbstractFacadeMockingTestContext {

    private static final String DNS = "ss9";
    @SpyBean
    private AcmeClientWorker acmeClientWorker;
    @Mock
    ManagementRequestSender managementRequestSenderMock;

    private final KeyPair keyPair = getKeyPairGenerator().generateKeyPair();
    private final TestCertUtil.PKCS12 ca = getCa();

    @Before
    public void setUp() throws Exception {
        when(globalConfProvider.isValid()).thenReturn(true);
        when(globalConfProvider.getApprovedCA(any(), any()))
                .thenReturn(new ApprovedCAInfo("testca", false, "ee.test.Profile", "http://test-ca/acme", "123.4.5.6", "5", "6"));

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

        when(signerProxyFacade.getTokens()).thenReturn(new ArrayList<>(List.of(tokenInfo)));
        when(signerProxyFacade.getTokenAndKeyIdForCertHash(any())).thenReturn(new TokenInfoAndKeyId(tokenInfo, authKey.getId()));
        when(signerProxyFacade.getCertForHash(calculateCertHexHash(authCertInfo.getCertificateBytes()))).thenReturn(authCertInfo);
        when(signerProxyFacade.getCertForHash(calculateCertHexHash(signCertInfo.getCertificateBytes()))).thenReturn(signCertInfo);

        KeyInfo newKey = new TokenTestUtils.KeyInfoBuilder()
                .id("new_key_id")
                .build();

        when(signerProxyFacade.generateKey(any(), any(), any())).thenReturn(newKey);
        when(signerProxyFacade.generateCertRequest(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SignerProxy.GeneratedCertRequestInfo(null, getMockSignCsrBytes(), null, null, null));

        when(acmeService.hasRenewalInfo(any(), any(), any())).thenReturn(true);
        when(acmeService.isRenewalRequired(any(), any(), any(), any())).thenReturn(true);

        CertificateInfo newSignCertInfo = createCertificateInfo("new_sign_cert_id", "M1", new KeyUsage(KeyUsage.nonRepudiation),
                Date.from(TimeUtils.now()), Date.from(TimeUtils.now().plus(365, ChronoUnit.DAYS)), null);

        CertificateInfo newAuthCertInfo = createCertificateInfo("new_auth_cert_id", DNS, new KeyUsage(KeyUsage.digitalSignature),
                Date.from(TimeUtils.now()), Date.from(TimeUtils.now().plus(365, ChronoUnit.DAYS)), null);

        when(signerProxyFacade.getCertForHash(calculateCertHexHash(newSignCertInfo.getCertificateBytes()))).thenReturn(newSignCertInfo);
        when(signerProxyFacade.getCertForHash(calculateCertHexHash(newAuthCertInfo.getCertificateBytes()))).thenReturn(newAuthCertInfo);

        when(acmeService.renew(any(),
                any(),
                any(),
                eq(KeyUsageInfo.SIGNING),
                any(),
                any())).thenReturn(List.of(readCertificate(newSignCertInfo.getCertificateBytes())));

        when(acmeService.renew(any(),
                any(),
                any(),
                eq(KeyUsageInfo.AUTHENTICATION),
                any(),
                any())).thenReturn(List.of(readCertificate(newAuthCertInfo.getCertificateBytes())));

        doReturn(managementRequestSenderMock).when(acmeClientWorker).createManagementRequestSender();
    }

    private CertificateInfo createCertificateInfo(String certId, String commonName, KeyUsage keyUsage, Date notBefore,
                                                  Date notAfter, String renewedCertHash)
            throws OperatorCreationException, IOException, CertificateException {
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(ca.key);
        var issuer = ca.certChain[0].getSubjectX500Principal();
        var subject = new X500Principal("CN=" + commonName);
        var subjectAltName = new GeneralName[1];
        subjectAltName[0] = new GeneralName(GeneralName.dNSName, DNS);
        X509CertificateHolder certificateHolder = new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.ONE,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic())
                .addExtension(Extension.create(
                        Extension.keyUsage,
                        true,
                        keyUsage))
                .addExtension(Extension.create(Extension.subjectAlternativeName, false, new GeneralNames(subjectAltName)))
                .build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(certificateHolder);
        CertificateTestUtils.CertificateInfoBuilder certificateInfoBuilder = new CertificateTestUtils.CertificateInfoBuilder()
                .id(certId)
                .certificate(certificate);
        if (renewedCertHash != null) {
            certificateInfoBuilder.renewedCertHash(renewedCertHash);
        }
        return certificateInfoBuilder.build();
    }

    @Test
    public void successfulAuthAndSignCertRenewals() throws Exception {
        CertificateRenewalScheduler scheduler = new CertificateRenewalScheduler(acmeClientWorker, new NoOpTaskScheduler());
        acmeClientWorker.execute(scheduler);
        verify(signerProxyFacade, times(2)).importCert(any(), any(), any(), anyBoolean());
        verify(managementRequestSenderMock, times(1)).sendAuthCertRegRequest(any(), any(), any());
        verify(signerProxyFacade, times(2)).setRenewedCertHash(any(), any());
        verify(signerProxyFacade, times(2)).setNextPlannedRenewal(any(), any());
    }

    @Test
    public void failureAuthAndSignCertRollback() throws Exception {
        when(acmeService.renew(any(), any(), any(), any(), any(), any())).thenThrow(new AcmeServiceException(ORDER_CREATION_FAILURE));

        CertificateRenewalScheduler scheduler = new CertificateRenewalScheduler(acmeClientWorker, new NoOpTaskScheduler());
        acmeClientWorker.execute(scheduler);

        verify(signerProxyFacade, never()).importCert(any(), any(), any(), anyBoolean());
        verify(signerProxyFacade, times(4)).deleteKey(any(), anyBoolean());
        verify(signerProxyFacade, times(2)).setRenewalError(any(), any());
    }

}
