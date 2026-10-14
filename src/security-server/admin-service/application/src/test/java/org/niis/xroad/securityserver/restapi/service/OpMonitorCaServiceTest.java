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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpMonitorCaServiceTest {

    private static KeyPair rootKeyPair;
    private static X509Certificate rootCa;
    private static X509Certificate otherCa;
    private static X509Certificate leaf;
    private static X509Certificate expiredCa;
    private static X509Certificate notYetValidCa;

    @Mock
    private VaultClient vaultClient;
    @Mock
    private AuditDataHelper auditDataHelper;

    private final AtomicReference<X509Certificate> stored = new AtomicReference<>();
    private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private Level rootLevel;

    private OpMonitorCaService service;

    @BeforeAll
    static void createCertificates() throws Exception {
        rootKeyPair = newKeyPair();
        rootCa = issue("CN=Root CA", rootKeyPair, new BasicConstraints(true), -1, 365);
        otherCa = issue("CN=Other CA", newKeyPair(), new BasicConstraints(true), -1, 365);
        leaf = issue("CN=Op-monitor", newKeyPair(), new BasicConstraints(false), -1, 365);
        expiredCa = issue("CN=Old CA", newKeyPair(), new BasicConstraints(true), -30, -1);
        notYetValidCa = issue("CN=Future CA", newKeyPair(), new BasicConstraints(true), 1, 30);
    }

    @BeforeEach
    void setUp() {
        service = new OpMonitorCaService(vaultClient, auditDataHelper);
        rootLevel = rootLogger.getLevel();
        rootLogger.setLevel(Level.ALL);
        logEvents.start();
        rootLogger.addAppender(logEvents);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(logEvents);
        rootLogger.setLevel(rootLevel);
    }

    @Test
    void shouldStoreAValidPemCaAndReturnIt() throws Exception {
        keepVaultState();

        var result = service.uploadOpMonitorCa(pem(rootCa));

        assertThat(result).isEqualTo(rootCa);
        assertThat(stored.get()).isEqualTo(rootCa);
        verify(vaultClient, times(1)).createOpMonitorCa(rootCa);
    }

    @Test
    void shouldStoreAValidDerCa() throws Exception {
        keepVaultState();

        var result = service.uploadOpMonitorCa(rootCa.getEncoded());

        assertThat(result).isEqualTo(rootCa);
        assertThat(stored.get()).isEqualTo(rootCa);
    }

    @Test
    void shouldKeepTheStoredCaWhenTheSameCertificateIsUploadedAgain() throws Exception {
        keepVaultState();

        service.uploadOpMonitorCa(pem(rootCa));
        var second = service.uploadOpMonitorCa(pem(rootCa));
        var third = service.uploadOpMonitorCa(rootCa.getEncoded());

        assertThat(second).isEqualTo(rootCa);
        assertThat(third).isEqualTo(rootCa);
        assertThat(stored.get()).isEqualTo(rootCa);
    }

    @Test
    void shouldReplaceTheStoredCaWhenADifferentCertificateIsUploaded() throws Exception {
        keepVaultState();

        service.uploadOpMonitorCa(pem(rootCa));
        service.uploadOpMonitorCa(pem(otherCa));

        assertThat(stored.get()).isEqualTo(otherCa);
        verify(vaultClient, times(2)).createOpMonitorCa(any());
    }

    @Test
    void shouldAuditTheFingerprintOfTheUploadedCertificate() throws Exception {
        keepVaultState();

        service.uploadOpMonitorCa(pem(rootCa));

        verify(auditDataHelper).putCertificateHash(rootCa);
    }

    @Test
    void shouldRejectEveryInvalidInputWithItsOwnErrorCodeAndLeaveTheStoredCaUnchanged() throws Exception {
        stored.set(otherCa);
        keepVaultState();
        byte[] key = pemBlock(rootKeyPair.getPrivate());
        byte[] random = new byte[64];
        random[0] = 1;

        assertRejected(new byte[0], ErrorCode.CA_CERT_EMPTY);
        assertRejected(random, ErrorCode.CA_CERT_NOT_A_CERTIFICATE);
        assertRejected(concat(pem(rootCa), pem(otherCa)), ErrorCode.CA_CERT_MULTIPLE_CERTIFICATES);
        assertRejected(concat(pem(rootCa), key), ErrorCode.CA_CERT_KEY_MATERIAL);
        assertRejected(key, ErrorCode.CA_CERT_KEY_MATERIAL);
        assertRejected(pem(leaf), ErrorCode.CA_CERT_NOT_A_CA);
        assertRejected(pem(expiredCa), ErrorCode.CA_CERT_EXPIRED);
        assertRejected(pem(notYetValidCa), ErrorCode.CA_CERT_NOT_YET_VALID);

        verify(vaultClient, never()).createOpMonitorCa(any());
        assertThat(stored.get()).isEqualTo(otherCa);
    }

    @Test
    void shouldPassTheRejectionMessageToTheBadRequest() throws Exception {
        assertThatThrownBy(() -> service.uploadOpMonitorCa(concat(pem(rootCa), pem(otherCa))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The file contains more than one certificate or PEM block.");
    }

    @Test
    void shouldNeverLogOrAuditAnyPartOfACertificatePlusKeyUpload() throws Exception {
        byte[] upload = concat(pem(rootCa), pemBlock(rootKeyPair.getPrivate()));
        String keyBody = pemBody(pemBlock(rootKeyPair.getPrivate()));

        BadRequestException rejection = null;
        try {
            service.uploadOpMonitorCa(upload);
        } catch (BadRequestException e) {
            rejection = e;
        }

        assertThat(rejection).isNotNull();
        assertThat(rejection.getMessage()).doesNotContain(keyBody).doesNotContain("MII");
        assertThat(logEvents.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(keyBody).doesNotContain("PRIVATE KEY").doesNotContain("MII");
            if (event.getThrowableProxy() != null) {
                assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy())).doesNotContain(keyBody).doesNotContain("MII");
            }
        });
        verify(auditDataHelper, never()).putCertificateHash(any(X509Certificate.class));
        verify(auditDataHelper, never()).put(any(), any());
    }

    @Test
    void shouldReturnTheStoredCaAndNoneWhenNothingIsStored() {
        keepVaultState();
        assertThat(service.getOpMonitorCa()).isEmpty();

        stored.set(rootCa);

        assertThat(service.getOpMonitorCa()).contains(rootCa);
    }

    @Test
    void shouldAnswerInternalErrorWhenTheSecretStoreFailsOnWrite() throws Exception {
        doThrow(new IllegalStateException("vault sealed")).when(vaultClient).createOpMonitorCa(any());

        assertThatThrownBy(() -> service.uploadOpMonitorCa(pem(rootCa)))
                .isInstanceOfSatisfying(InternalServerErrorException.class,
                        e -> assertThat(e.getErrorDeviation().code()).isEqualTo(ErrorCode.INTERNAL_ERROR.code()));
    }

    @Test
    void shouldKeepTheErrorCodeOfAnXrdRuntimeExceptionFromTheSecretStore() throws Exception {
        doThrow(XrdRuntimeException.systemException(ErrorCode.EXTERNAL_RPC_CA_NOT_AVAILABLE).build())
                .when(vaultClient).createOpMonitorCa(any());
        assertThatThrownBy(() -> service.uploadOpMonitorCa(pem(rootCa)))
                .isInstanceOfSatisfying(InternalServerErrorException.class,
                        e -> assertThat(e.getErrorDeviation().code()).isEqualTo(ErrorCode.EXTERNAL_RPC_CA_NOT_AVAILABLE.code()));
    }

    @Test
    void shouldAnswerInternalErrorWhenTheSecretStoreFailsOnRead() {
        when(vaultClient.getOpMonitorCa()).thenThrow(new IllegalStateException("vault sealed"));

        assertThatThrownBy(() -> service.getOpMonitorCa()).isInstanceOf(InternalServerErrorException.class);
    }

    @Test
    void shouldClearTheStoredCaAndAuditItsFingerprintOnDelete() {
        stored.set(rootCa);
        keepVaultState();
        service.deleteOpMonitorCa();
        verify(vaultClient).clearOpMonitorCa();
        verify(auditDataHelper).putCertificateHash(rootCa);
        assertThat(service.getOpMonitorCa()).isEmpty();
    }

    @Test
    void shouldSucceedOnDeleteWhenNothingIsStoredAndRepeatedDeletesToo() {
        keepVaultState();
        service.deleteOpMonitorCa();
        service.deleteOpMonitorCa();
        assertThat(service.getOpMonitorCa()).isEmpty();
        verify(auditDataHelper, never()).putCertificateHash(any(X509Certificate.class));
    }

    @Test
    void shouldAllowAnUploadAfterADelete() throws Exception {
        keepVaultState();
        service.uploadOpMonitorCa(pem(rootCa));
        service.deleteOpMonitorCa();
        service.uploadOpMonitorCa(pem(otherCa));
        assertThat(stored.get()).isEqualTo(otherCa);
    }

    @Test
    void shouldKeepNothingStoredWhenAnUploadIsRejectedAfterADelete() throws Exception {
        stored.set(rootCa);
        keepVaultState();
        service.deleteOpMonitorCa();
        assertRejected(pem(leaf), ErrorCode.CA_CERT_NOT_A_CA);
        assertThat(service.getOpMonitorCa()).isEmpty();
    }

    @Test
    void shouldLeaveNothingStoredWhenTwoCasAreUploadedAndThenDeleted() throws Exception {
        keepVaultState();
        service.uploadOpMonitorCa(pem(rootCa));
        service.uploadOpMonitorCa(pem(otherCa));
        service.deleteOpMonitorCa();
        assertThat(service.getOpMonitorCa()).isEmpty();
    }

    @Test
    void shouldClearTheCaWithoutAuditingItsFingerprintWhenTheStoredCaCannotBeParsed() {
        when(vaultClient.getOpMonitorCa()).thenThrow(XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR).build());
        service.deleteOpMonitorCa();
        verify(vaultClient, times(1)).clearOpMonitorCa();
        verify(auditDataHelper, never()).putCertificateHash(any(X509Certificate.class));
    }

    @Test
    void shouldAnswerInternalErrorWhenTheSecretStoreFailsOnDelete() {
        doThrow(new IllegalStateException("vault sealed")).when(vaultClient).clearOpMonitorCa();
        assertThatThrownBy(() -> service.deleteOpMonitorCa())
                .isInstanceOfSatisfying(InternalServerErrorException.class,
                        e -> assertThat(e.getErrorDeviation().code()).isEqualTo(ErrorCode.INTERNAL_ERROR.code()));
    }

    @Test
    void shouldAnswerInternalErrorWhenTheVaultClientCannotClearTheCa() {
        doThrow(new UnsupportedOperationException("Not supported")).when(vaultClient).clearOpMonitorCa();
        assertThatThrownBy(() -> service.deleteOpMonitorCa()).isInstanceOf(InternalServerErrorException.class);
    }

    @Test
    void shouldKeepTheErrorCodeOfAnXrdRuntimeExceptionFromTheSecretStoreOnDelete() {
        doThrow(XrdRuntimeException.systemException(ErrorCode.EXTERNAL_RPC_CA_NOT_AVAILABLE).build())
                .when(vaultClient).clearOpMonitorCa();
        assertThatThrownBy(() -> service.deleteOpMonitorCa())
                .isInstanceOfSatisfying(InternalServerErrorException.class,
                        e -> assertThat(e.getErrorDeviation().code()).isEqualTo(ErrorCode.EXTERNAL_RPC_CA_NOT_AVAILABLE.code()));
    }

    private void assertRejected(byte[] upload, ErrorCode expected) {
        assertThatThrownBy(() -> service.uploadOpMonitorCa(upload))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorDeviation().code()).isEqualTo(expected.code()));
    }

    private void keepVaultState() {
        lenient().when(vaultClient.getOpMonitorCa()).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        lenient().doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(vaultClient).createOpMonitorCa(any());
        lenient().doAnswer(invocation -> {
            stored.set(null);
            return null;
        }).when(vaultClient).clearOpMonitorCa();
    }

    private static KeyPair newKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate issue(String subjectName, KeyPair keyPair, BasicConstraints basicConstraints,
                                         int notBeforeDaysFromNow, int notAfterDaysFromNow) throws Exception {
        var subject = new X500Name(subjectName);
        var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.now().plus(notBeforeDaysFromNow, ChronoUnit.DAYS)),
                Date.from(Instant.now().plus(notAfterDaysFromNow, ChronoUnit.DAYS)),
                subject, keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, basicConstraints);
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static byte[] pem(X509Certificate certificate) throws IOException {
        return pemBlock(certificate);
    }

    private static byte[] pemBlock(Object object) throws IOException {
        var writer = new StringWriter();
        try (var pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(object);
        }
        return writer.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String pemBody(byte[] pemBlock) {
        var lines = new String(pemBlock, StandardCharsets.US_ASCII).lines().filter(line -> !line.startsWith("-----")).toList();
        return lines.get(1);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
