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
import org.niis.xroad.common.vault.AcmeAccountKey;
import org.niis.xroad.common.vault.VaultClient;

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
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link AcmeClient} end to end (order placement, EAB account creation, ARI renewal decisions, account
 * key pair generation/rotation) against a WireMock-stubbed ACME server and a fake {@link VaultClient} — purely
 * through {@link AcmeAccountContext}, the same shape both {@link AcmeService} (member auth/sign) and DS TLS's own
 * service build before calling in.
 */
class AcmeClientTest {

    private static final String CA_NAME = "testca";
    private static final String MEMBER_ID = "MEMBER1";
    private static final String DS_TLS_ALIAS = "dataspace-tls";
    private static final String CHALLENGE_TOKEN = "test-challenge-token";

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @TempDir
    Path tempDir;

    private AcmeConfig acmeConfig;
    private AcmeProperties acmeProperties;
    private VaultClient vaultClient;
    private Map<String, AcmeAccountKey> vaultStore;
    private AcmeClient acmeClient;
    private String directoryUrl;
    private X509Certificate certificate;

    @BeforeEach
    void setUp() throws Exception {
        acmeConfig = mock(AcmeConfig.class);
        when(acmeConfig.getAcmeChallengePath()).thenReturn(tempDir.toString());
        when(acmeConfig.getAcmeKeyLength()).thenReturn(2048);
        when(acmeConfig.getAcmeCertificateAccountKeyPairExpiration()).thenReturn(30);
        when(acmeConfig.getAcmeKeypairRenewalTimeBeforeExpirationDate()).thenReturn(7);
        when(acmeConfig.getAcmeAuthorizationWaitAttempts()).thenReturn(3);
        when(acmeConfig.getAcmeAuthorizationWaitInterval()).thenReturn(1);
        when(acmeConfig.getAcmeCertificateWaitAttempts()).thenReturn(3);
        when(acmeConfig.getAcmeCertificateWaitInterval()).thenReturn(1);

        acmeProperties = eabConfiguredProperties();

        vaultStore = new HashMap<>();
        vaultClient = mock(VaultClient.class);
        lenient().when(vaultClient.getAcmeAccountKey(any())).thenAnswer(invocation ->
                Optional.ofNullable(vaultStore.get(invocation.getArgument(0, String.class))));
        lenient().doAnswer(invocation -> {
            vaultStore.put(invocation.getArgument(0, String.class), invocation.getArgument(1, AcmeAccountKey.class));
            return null;
        }).when(vaultClient).createAcmeAccountKey(any(), any());

        acmeClient = new AcmeClient(acmeProperties, acmeConfig, vaultClient);

        directoryUrl = wm.getRuntimeInfo().getHttpBaseUrl() + "/directory";

        certificate = selfSignedCertificateWithAuthorityKeyIdentifier(generateKeyPair());

        wm.stubFor(head(urlEqualTo("/new-nonce"))
                .willReturn(aResponse().withStatus(200).withHeader("Replay-Nonce", "boot-nonce")));
    }

    private AcmeAccountContext accountContext(String alias, AcmeKeyPurpose keyUsage, String certificateProfileId,
                                              List<String> contacts) {
        return new AcmeAccountContext(alias, CA_NAME, directoryUrl, certificateProfileId, keyUsage, contacts);
    }

    @Test
    void firstUseGeneratesAndStoresAccountKeyPairForAlias() {
        stubDirectory(true);
        stubNewAccount();

        acmeClient.hasRenewalInfo(accountContext(MEMBER_ID, AcmeKeyPurpose.SIGNING, null, List.of()));

        verify(vaultClient).createAcmeAccountKey(eq(MEMBER_ID), any(AcmeAccountKey.class));
        AcmeAccountKey stored = vaultStore.get(MEMBER_ID);
        assertThat(stored).isNotNull();
        assertThat(stored.privateKey()).isNotNull();
        assertThat(stored.publicKey()).isNotNull();
        assertThat(stored.expiresAt()).isAfter(Instant.now().plus(29, ChronoUnit.DAYS));
        assertThat(stored.expiresAt()).isBefore(Instant.now().plus(31, ChronoUnit.DAYS));
    }

    @Test
    void rotatesAccountKeyPairAndCallsChangeKeyWhenStoredExpiryIsWithinRenewalWindow() throws Exception {
        KeyPair oldKeyPair = generateKeyPair();
        Instant dueExpiry = Instant.now().plus(3, ChronoUnit.DAYS);
        vaultStore.put(MEMBER_ID, new AcmeAccountKey(oldKeyPair.getPrivate(), oldKeyPair.getPublic(), dueExpiry));

        stubDirectory(true);
        stubNewAccount();
        stubKeyChange();

        acmeClient.checkAccountKeyPairAndRenewIfNecessary(accountContext(MEMBER_ID, AcmeKeyPurpose.SIGNING, null, List.of()));

        wm.verify(postRequestedFor(urlEqualTo("/key-change")));
        verify(vaultClient).createAcmeAccountKey(eq(MEMBER_ID), any(AcmeAccountKey.class));
        AcmeAccountKey renewed = vaultStore.get(MEMBER_ID);
        assertThat(renewed.privateKey()).isNotEqualTo(oldKeyPair.getPrivate());
        assertThat(renewed.expiresAt()).isAfter(dueExpiry);
    }

    @Test
    void doesNotRotateAccountKeyPairWhenStoredExpiryIsNotYetDue() {
        KeyPair existingKeyPair = generateKeyPair();
        Instant farExpiry = Instant.now().plus(60, ChronoUnit.DAYS);
        vaultStore.put(MEMBER_ID, new AcmeAccountKey(existingKeyPair.getPrivate(), existingKeyPair.getPublic(), farExpiry));

        acmeClient.checkAccountKeyPairAndRenewIfNecessary(accountContext(MEMBER_ID, AcmeKeyPurpose.SIGNING, null, List.of()));

        verify(vaultClient, never()).createAcmeAccountKey(any(), any());
        wm.verify(0, getRequestedFor(urlEqualTo("/directory")));
        wm.verify(0, postRequestedFor(urlEqualTo("/new-account")));
        AcmeAccountKey unchanged = vaultStore.get(MEMBER_ID);
        assertThat(unchanged.privateKey()).isEqualTo(existingKeyPair.getPrivate());
        assertThat(unchanged.expiresAt()).isEqualTo(farExpiry);
    }

    @Test
    void splitAuthAndSignAliasesForTheSameMemberHaveIndependentAccountKeyPairs() {
        acmeProperties = eabConfiguredPropertiesWithSplitCredentials();
        acmeClient = new AcmeClient(acmeProperties, acmeConfig, vaultClient);
        stubDirectory(true);
        stubNewAccount();

        acmeClient.hasRenewalInfo(accountContext(MEMBER_ID, AcmeKeyPurpose.AUTHENTICATION, null, List.of()));
        acmeClient.hasRenewalInfo(accountContext(MEMBER_ID, AcmeKeyPurpose.SIGNING, null, List.of()));

        assertThat(vaultStore).containsKeys("auth_" + MEMBER_ID, "sign_" + MEMBER_ID);
        AcmeAccountKey authKey = vaultStore.get("auth_" + MEMBER_ID);
        AcmeAccountKey signKey = vaultStore.get("sign_" + MEMBER_ID);
        assertThat(authKey.privateKey()).isNotEqualTo(signKey.privateKey());
    }

    @Test
    void ordersCertificateThroughFullAcmeFlowUsingEabAccount() throws Exception {
        stubDirectory(true);
        stubNewAccount();
        stubOrderLifecycle();

        List<X509Certificate> chain = acmeClient.renew(accountContext(MEMBER_ID, AcmeKeyPurpose.SIGNING, null,
                        List.of("member@example.org")), "ss1.example.org",
                certificate, "csr-bytes".getBytes(StandardCharsets.UTF_8));

        assertThat(chain).hasSize(1);
        assertThat(chain.getFirst().getEncoded()).isEqualTo(certificate.getEncoded());
        assertThat(vaultStore).containsKey(MEMBER_ID);
    }

    @Test
    void getLoginFailsFastWhenNoEabCredentialsAreConfiguredForMember() {
        acmeProperties.setEabCredentials(new AcmeProperties.EabCredentials());

        assertThatThrownBy(() -> acmeClient.hasRenewalInfo(accountContext(MEMBER_ID, AcmeKeyPurpose.SIGNING, null, List.of())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void hasRenewalInfoReturnsTrueWhenServerAdvertisesTheRenewalInfoResource() {
        stubDirectory(true);
        stubNewAccount();

        boolean result = acmeClient.hasRenewalInfo(accountContext(MEMBER_ID, AcmeKeyPurpose.SIGNING, null, List.of()));

        assertThat(result).isTrue();
    }

    @Test
    void hasRenewalInfoReturnsFalseWhenServerDoesNotAdvertiseTheRenewalInfoResource() {
        stubDirectory(false);
        stubNewAccount();

        boolean result = acmeClient.hasRenewalInfo(accountContext(MEMBER_ID, AcmeKeyPurpose.SIGNING, null, List.of()));

        assertThat(result).isFalse();
    }

    @Test
    void isRenewalRequiredReturnsFalseWhenSuggestedWindowIsInTheFuture() {
        stubDirectory(true);
        stubNewAccount();
        stubRenewalInfo(Instant.now().plus(10, ChronoUnit.DAYS), Instant.now().plus(20, ChronoUnit.DAYS));

        boolean result = acmeClient.isRenewalRequired(accountContext(MEMBER_ID, AcmeKeyPurpose.SIGNING, null, List.of()), certificate);

        assertThat(result).isFalse();
    }

    @Test
    void isRenewalRequiredReturnsTrueWhenSuggestedWindowIsInThePast() {
        stubDirectory(true);
        stubNewAccount();
        stubRenewalInfo(Instant.now().minus(20, ChronoUnit.DAYS), Instant.now().minus(10, ChronoUnit.DAYS));

        boolean result = acmeClient.isRenewalRequired(accountContext(MEMBER_ID, AcmeKeyPurpose.SIGNING, null, List.of()), certificate);

        assertThat(result).isTrue();
    }

    @Test
    void isExternalAccountBindingRequiredReflectsServerMetadata() {
        stubDirectory(true);

        boolean result = acmeClient.isExternalAccountBindingRequired(directoryUrl);

        assertThat(result).isTrue();
    }

    @Test
    void ordersCertificateForAnAccountContextThroughFullAcmeFlowWithNoProfileIdConfigured() throws Exception {
        // No profile id configured here so this exercises the plain xrd-acme scheme, whose provider is registered
        // in this module. An account context with a profile id configured switches to the xrd-acme-profile-id
        // scheme, whose provider (AcmeProfileIdConnection) lives in lib/acme-spring — that header-content
        // behavior is covered by AcmeProfileIdConnectionTest there, not here.
        stubDirectory(true);
        stubNewAccount();
        stubOrderLifecycle();
        AcmeAccountContext account = accountContext(DS_TLS_ALIAS, AcmeKeyPurpose.AUTHENTICATION, null, List.of());

        List<X509Certificate> chain = acmeClient.orderCertificate("ss1.example.org", "ss1.example.org", account,
                "csr-bytes".getBytes(StandardCharsets.UTF_8));

        assertThat(chain).hasSize(1);
        assertThat(chain.getFirst().getEncoded()).isEqualTo(certificate.getEncoded());
        assertThat(vaultStore).containsKey(DS_TLS_ALIAS);
    }

    @Test
    void getNextRenewalTimeForAnAccountContextFallsBackToFixedWindowWhenNoRenewalInfoAdvertised() {
        when(acmeConfig.getAcmeRenewalTimeBeforeExpirationDate()).thenReturn(14);
        stubDirectory(false);
        stubNewAccount();
        AcmeAccountContext account = accountContext(DS_TLS_ALIAS, AcmeKeyPurpose.AUTHENTICATION, null, List.of());

        Instant nextRenewalTime = acmeClient.getNextRenewalTime(account, certificate);

        assertThat(nextRenewalTime).isEqualTo(certificate.getNotAfter().toInstant().minus(14, ChronoUnit.DAYS));
    }

    @Test
    void getNextRenewalTimeFallsBackToFixedWindowWhenNoEabCredentialsAreConfiguredForMember() {
        acmeProperties.setEabCredentials(new AcmeProperties.EabCredentials());
        when(acmeConfig.getAcmeRenewalTimeBeforeExpirationDate()).thenReturn(14);
        AcmeAccountContext account = accountContext(MEMBER_ID, AcmeKeyPurpose.SIGNING, null, List.of());

        Instant nextRenewalTime = acmeClient.getNextRenewalTime(account, certificate);

        assertThat(nextRenewalTime).isEqualTo(certificate.getNotAfter().toInstant().minus(14, ChronoUnit.DAYS));
    }

    @Test
    void renewsCertificateForAnAccountContextWithNoProfileIdConfigured() throws Exception {
        stubDirectory(true);
        stubNewAccount();
        stubOrderLifecycle();
        AcmeAccountContext account = accountContext(DS_TLS_ALIAS, AcmeKeyPurpose.AUTHENTICATION, null, List.of());

        List<X509Certificate> chain = acmeClient.renew(account, "ss1.example.org",
                certificate, "csr-bytes".getBytes(StandardCharsets.UTF_8));

        assertThat(chain).hasSize(1);
        assertThat(chain.getFirst().getEncoded()).isEqualTo(certificate.getEncoded());
    }

    private void stubDirectory(boolean advertiseRenewalInfo) {
        String base = wm.getRuntimeInfo().getHttpBaseUrl();
        String renewalInfo = advertiseRenewalInfo ? ("\"renewalInfo\":\"" + base + "/renewal-info/\",") : "";
        String directory = "{"
                + "\"newNonce\":\"" + base + "/new-nonce\","
                + "\"newAccount\":\"" + base + "/new-account\","
                + "\"newOrder\":\"" + base + "/new-order\","
                + "\"keyChange\":\"" + base + "/key-change\","
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

    private void stubKeyChange() {
        wm.stubFor(post(urlEqualTo("/key-change")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Replay-Nonce", "key-change-nonce")));
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

        return propertiesWithCredentials(credentials);
    }

    private static AcmeProperties eabConfiguredPropertiesWithSplitCredentials() {
        AcmeProperties.Credentials credentials = new AcmeProperties.Credentials();
        credentials.setAuthKid("auth-kid");
        credentials.setAuthMacKey("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE");
        credentials.setSignKid("sign-kid");
        credentials.setSignMacKey("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE");

        return propertiesWithCredentials(credentials);
    }

    private static AcmeProperties propertiesWithCredentials(AcmeProperties.Credentials credentials) {
        AcmeProperties.CA ca = new AcmeProperties.CA();
        ca.setMacKeyBase64Encoded(true);
        Map<String, AcmeProperties.Credentials> members = new HashMap<>();
        members.put(MEMBER_ID, credentials);
        members.put(DS_TLS_ALIAS, credentials);
        ca.setMembers(members);

        AcmeProperties.EabCredentials eabCredentials = new AcmeProperties.EabCredentials();
        Map<String, AcmeProperties.CA> certificateAuthorities = new HashMap<>();
        certificateAuthorities.put(CA_NAME, ca);
        eabCredentials.setCertificateAuthorities(certificateAuthorities);

        AcmeProperties properties = new AcmeProperties();
        properties.setEabCredentials(eabCredentials);
        return properties;
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static X509Certificate selfSignedCertificateWithAuthorityKeyIdentifier(KeyPair keyPair) throws Exception {
        Instant now = Instant.now();
        X500Name subject = new X500Name("CN=acme-client-test");
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
