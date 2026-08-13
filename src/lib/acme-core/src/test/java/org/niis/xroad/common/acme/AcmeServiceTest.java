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
package org.niis.xroad.common.acme;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.acme.config.AcmeConfig;
import org.niis.xroad.common.acme.config.AcmeProperties;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link AcmeService} end to end (order placement, EAB account creation, ARI renewal decisions) against
 * a WireMock-stubbed ACME server, and the account-keystore-password provider seam.
 */
class AcmeServiceTest {

    private static final String CA_NAME = "testca";
    private static final String MEMBER_ID = "MEMBER1";
    private static final String CHALLENGE_TOKEN = "test-challenge-token";
    private static final String KEYSTORE_PASSWORD = "test-keystore-password";

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @TempDir
    Path tempDir;

    private AcmeConfig acmeConfig;
    private AcmeProperties acmeProperties;
    private AtomicInteger passwordProviderCalls;
    private AcmeService acmeService;
    private ApprovedCAInfo caInfo;
    private X509Certificate certificate;

    @BeforeEach
    void setUp() throws Exception {
        acmeConfig = mock(AcmeConfig.class);
        when(acmeConfig.getAcmeAccountKeystorePath()).thenReturn(tempDir.resolve("acme-accounts.p12").toString());
        when(acmeConfig.getAcmeChallengePath()).thenReturn(tempDir.toString());
        when(acmeConfig.getAcmeKeyLength()).thenReturn(2048);
        when(acmeConfig.getAcmeCertificateAccountKeyPairExpiration()).thenReturn(30);
        when(acmeConfig.getAcmeAuthorizationWaitAttempts()).thenReturn(3);
        when(acmeConfig.getAcmeAuthorizationWaitInterval()).thenReturn(1);
        when(acmeConfig.getAcmeCertificateWaitAttempts()).thenReturn(3);
        when(acmeConfig.getAcmeCertificateWaitInterval()).thenReturn(1);

        acmeProperties = eabConfiguredProperties();
        acmeProperties.setAccountKeystorePassword(KEYSTORE_PASSWORD);

        passwordProviderCalls = new AtomicInteger();
        acmeService = new AcmeService(acmeProperties, acmeConfig, () -> {
            passwordProviderCalls.incrementAndGet();
            return KEYSTORE_PASSWORD.toCharArray();
        });

        caInfo = new ApprovedCAInfo(CA_NAME, false, null, null,
                wm.getRuntimeInfo().getHttpBaseUrl() + "/directory", null, null, null);

        certificate = selfSignedCertificateWithAuthorityKeyIdentifier(generateKeyPair());

        wm.stubFor(head(urlEqualTo("/new-nonce"))
                .willReturn(aResponse().withStatus(200).withHeader("Replay-Nonce", "boot-nonce")));
    }

    @Test
    void ordersCertificateThroughFullAcmeFlowUsingEabAccountAndProvisionedPassword() throws Exception {
        acmeProperties.setAccountKeystorePassword(null);

        stubDirectory(true);
        stubNewAccount();
        stubOrderLifecycle();

        List<X509Certificate> chain = acmeService.renew(MEMBER_ID, "ss1.example.org", caInfo, AcmeKeyPurpose.SIGNING,
                certificate, "csr-bytes".getBytes(StandardCharsets.UTF_8), List.of("member@example.org"));

        assertThat(chain).hasSize(1);
        assertThat(chain.getFirst().getEncoded()).isEqualTo(certificate.getEncoded());
        assertThat(passwordProviderCalls.get()).isEqualTo(1);
        assertThat(tempDir.resolve("acme-accounts.p12")).exists();
    }

    @Test
    void getLoginFailsFastWhenNoEabCredentialsAreConfiguredForMember() {
        acmeProperties.setEabCredentials(new AcmeProperties.EabCredentials());

        assertThatThrownBy(() -> acmeService.hasRenewalInfo(MEMBER_ID, caInfo, AcmeKeyPurpose.SIGNING, List.of()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void hasRenewalInfoReturnsTrueWhenServerAdvertisesTheRenewalInfoResource() {
        stubDirectory(true);
        stubNewAccount();

        boolean result = acmeService.hasRenewalInfo(MEMBER_ID, caInfo, AcmeKeyPurpose.SIGNING, List.of());

        assertThat(result).isTrue();
    }

    @Test
    void hasRenewalInfoReturnsFalseWhenServerDoesNotAdvertiseTheRenewalInfoResource() {
        stubDirectory(false);
        stubNewAccount();

        boolean result = acmeService.hasRenewalInfo(MEMBER_ID, caInfo, AcmeKeyPurpose.SIGNING, List.of());

        assertThat(result).isFalse();
    }

    @Test
    void isRenewalRequiredReturnsFalseWhenSuggestedWindowIsInTheFuture() {
        stubDirectory(true);
        stubNewAccount();
        stubRenewalInfo(Instant.now().plus(10, ChronoUnit.DAYS), Instant.now().plus(20, ChronoUnit.DAYS));

        boolean result = acmeService.isRenewalRequired(MEMBER_ID, caInfo, certificate, AcmeKeyPurpose.SIGNING, List.of());

        assertThat(result).isFalse();
    }

    @Test
    void isRenewalRequiredReturnsTrueWhenSuggestedWindowIsInThePast() {
        stubDirectory(true);
        stubNewAccount();
        stubRenewalInfo(Instant.now().minus(20, ChronoUnit.DAYS), Instant.now().minus(10, ChronoUnit.DAYS));

        boolean result = acmeService.isRenewalRequired(MEMBER_ID, caInfo, certificate, AcmeKeyPurpose.SIGNING, List.of());

        assertThat(result).isTrue();
    }

    @Test
    void isExternalAccountBindingRequiredReflectsServerMetadata() {
        stubDirectory(true);

        boolean result = acmeService.isExternalAccountBindingRequired(caInfo.getAcmeServerDirectoryUrl());

        assertThat(result).isTrue();
    }

    private void stubDirectory(boolean advertiseRenewalInfo) {
        String base = wm.getRuntimeInfo().getHttpBaseUrl();
        String renewalInfo = advertiseRenewalInfo ? ("\"renewalInfo\":\"" + base + "/renewal-info/\",") : "";
        String directory = "{"
                + "\"newNonce\":\"" + base + "/new-nonce\","
                + "\"newAccount\":\"" + base + "/new-account\","
                + "\"newOrder\":\"" + base + "/new-order\","
                + renewalInfo
                + "\"meta\":{\"externalAccountRequired\":true}"
                + "}";
        wm.stubFor(get(urlEqualTo("/directory"))
                .willReturn(okJson(directory).withHeader("Replay-Nonce", "dir-nonce")));
    }

    private void stubNewAccount() {
        String base = wm.getRuntimeInfo().getHttpBaseUrl();
        wm.stubFor(post(urlEqualTo("/new-account")).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withHeader("Location", base + "/account/1")
                .withHeader("Replay-Nonce", "acct-nonce")
                .withBody("{\"status\":\"valid\"}")));
    }

    private void stubOrderLifecycle() throws Exception {
        String base = wm.getRuntimeInfo().getHttpBaseUrl();

        wm.stubFor(post(urlEqualTo("/new-order")).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withHeader("Location", base + "/order/1")
                .withHeader("Replay-Nonce", "order-nonce")
                .withBody("{\"status\":\"pending\",\"identifiers\":[{\"type\":\"dns\",\"value\":\"ss1.example.org\"}],"
                        + "\"authorizations\":[\"" + base + "/authz/1\"],\"finalize\":\"" + base + "/order/1/finalize\"}")));

        wm.stubFor(post(urlEqualTo("/authz/1")).willReturn(okJson(
                "{\"status\":\"pending\",\"identifier\":{\"type\":\"dns\",\"value\":\"ss1.example.org\"},"
                        + "\"challenges\":[{\"type\":\"http-01\",\"url\":\"" + base + "/chall/1\",\"token\":\""
                        + CHALLENGE_TOKEN + "\",\"status\":\"pending\"}]}"
        ).withHeader("Replay-Nonce", "authz-nonce")));

        wm.stubFor(post(urlEqualTo("/chall/1")).willReturn(okJson(
                "{\"type\":\"http-01\",\"url\":\"" + base + "/chall/1\",\"token\":\"" + CHALLENGE_TOKEN + "\",\"status\":\"valid\"}"
        ).withHeader("Replay-Nonce", "chall-nonce")));

        wm.stubFor(post(urlEqualTo("/order/1/finalize")).willReturn(okJson("{}")
                .withHeader("Replay-Nonce", "finalize-nonce")));

        wm.stubFor(post(urlEqualTo("/order/1")).willReturn(okJson(
                "{\"status\":\"valid\",\"certificate\":\"" + base + "/cert/1\"}"
        ).withHeader("Replay-Nonce", "order-fetch-nonce")));

        wm.stubFor(post(urlEqualTo("/cert/1")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/pem-certificate-chain")
                .withHeader("Replay-Nonce", "cert-nonce")
                .withBody(toPem(certificate))));
    }

    private void stubRenewalInfo(Instant windowStart, Instant windowEnd) {
        wm.stubFor(get(urlPathMatching("/renewal-info/.*")).willReturn(okJson(
                "{\"suggestedWindow\":{\"start\":\"" + windowStart + "\",\"end\":\"" + windowEnd + "\"}}"
        ).withHeader("Replay-Nonce", "renewal-info-nonce")));
    }

    private static AcmeProperties eabConfiguredProperties() {
        AcmeProperties.Credentials credentials = new AcmeProperties.Credentials();
        credentials.setKid("test-kid");
        // 32 raw bytes, base64url-encoded without padding, to also exercise base64 padding
        credentials.setMacKey("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE");

        AcmeProperties.CA ca = new AcmeProperties.CA();
        ca.setMacKeyBase64Encoded(true);
        Map<String, AcmeProperties.Credentials> members = new HashMap<>();
        members.put(MEMBER_ID, credentials);
        ca.setMembers(members);

        AcmeProperties.EabCredentials eabCredentials = new AcmeProperties.EabCredentials();
        Map<String, AcmeProperties.CA> certificateAuthorities = new HashMap<>();
        certificateAuthorities.put(CA_NAME, ca);
        eabCredentials.setCertificateAuthorities(certificateAuthorities);

        AcmeProperties properties = new AcmeProperties();
        properties.setEabCredentials(eabCredentials);
        return properties;
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSignedCertificateWithAuthorityKeyIdentifier(KeyPair keyPair) throws Exception {
        Instant now = Instant.now();
        X500Name subject = new X500Name("CN=acme-service-test");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minus(1, ChronoUnit.DAYS)),
                Date.from(now.plus(365, ChronoUnit.DAYS)),
                subject,
                keyPair.getPublic());
        JcaX509ExtensionUtils extensionUtils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensionUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false, extensionUtils.createAuthorityKeyIdentifier(keyPair.getPublic()));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static String toPem(X509Certificate certificate) throws Exception {
        return "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(certificate.getEncoded())
                + "\n-----END CERTIFICATE-----\n";
    }
}
