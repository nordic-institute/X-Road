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
package org.niis.xroad.opmonitor.test;

import io.quarkus.vault.VaultKVSecretEngine;
import io.quarkus.vault.VaultPKISecretEngineFactory;
import io.quarkus.vault.client.http.jdk.JDKVaultHttpClient;
import io.quarkus.vault.runtime.VaultConfigHolder;
import io.quarkus.vault.runtime.VaultKvManager;
import io.quarkus.vault.runtime.VaultPKIManagerFactory;
import io.quarkus.vault.runtime.config.VaultRuntimeConfig;
import io.restassured.path.json.JsonPath;
import lombok.SneakyThrows;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.vault.VaultCaClient;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.VaultKeyClient;
import org.niis.xroad.common.vault.config.CertificateProvisioningConfig;
import org.niis.xroad.common.vault.quarkus.QuarkusVaultCaClient;
import org.niis.xroad.common.vault.quarkus.QuarkusVaultClient;
import org.niis.xroad.common.vault.quarkus.QuarkusVaultKeyClient;
import org.niis.xroad.opmonitor.test.container.OpMonitorContainerSetup;
import org.niis.xroad.test.apitest.core.junit.ApiStackExtension;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * 0200 - Op-monitor CA and external RPC CA through the Quarkus vault clients: the real
 * {@link QuarkusVaultClient}, {@link QuarkusVaultKeyClient} and {@link QuarkusVaultCaClient} run against the
 * OpenBao of the stack, started from the {@code openbao-dev} image with the shipped init and policy. The raw record
 * is seeded and checked with the {@code bao} CLI inside the container. Each case starts and ends without an
 * op-monitor CA record.
 */
@ExtendWith(ApiStackExtension.class)
@SuppressWarnings("checkstyle:magicnumber")
class OpMonitorCaOpenBaoIntTest {

    private static final String KV_MOUNT = "xrd-secret";
    private static final String PKI_MOUNT = "xrd-pki";
    private static final String RAW_RECORD_PATH = KV_MOUNT + "/tls/opmonitor-ca";
    private static final String ISSUING_CA_PATH = PKI_MOUNT + "/cert/ca";
    private static final Pattern PEM_CERTIFICATE_BLOCK = Pattern.compile(
            "\\A-----BEGIN CERTIFICATE-----\\R([A-Za-z0-9+/=]+\\R)+-----END CERTIFICATE-----\\R?\\z");

    private OpMonitorContainerSetup stack;
    private VaultClient vaultClient;
    private VaultKeyClient vaultKeyClient;
    private VaultCaClient vaultCaClient;

    @BeforeEach
    @SneakyThrows
    void vaultClientsInitialized(OpMonitorContainerSetup setup) {
        stack = setup;
        deleteRawRecord();

        var vaultApi = io.quarkus.vault.client.VaultClient.builder()
                .baseUrl(setup.openBaoUrl())
                .executor(new JDKVaultHttpClient(HttpClient.newHttpClient()))
                .clientToken(setup.openBaoClientToken())
                .build();
        var provisioning = new CertificateProvisioningConfig(
                "xrd-internal", "localhost", List.of(), List.of(), Duration.ofDays(1), PKI_MOUNT);

        vaultClient = new QuarkusVaultClient(new VaultKVSecretEngine(new VaultKvManager(vaultApi, quarkusConfigHolder())));
        vaultKeyClient = new QuarkusVaultKeyClient(pkiEngineFactory(vaultApi), provisioning);
        vaultCaClient = new QuarkusVaultCaClient(pkiEngineFactory(vaultApi), provisioning);
    }

    @AfterEach
    void rawRecordRemoved() {
        deleteRawRecord();
    }

    @Test
    @DisplayName("A certificate seeded with bao is read through the Quarkus vault client")
    void seededCertificateIsReadThroughQuarkus() {
        var ca = newCa("CN=Op-monitor CA " + System.nanoTime());

        given("the op-monitor CA record is seeded with bao", () -> seedRawRecord(pem(ca)));

        var read = when("the Quarkus vault client reads the op-monitor CA", () -> vaultClient.getOpMonitorCa());
        then("it returns the seeded certificate", () -> assertThat(read).contains(ca));
    }

    @Test
    @DisplayName("A missing record and a record with a blank certificate are both read as absent")
    void missingAndBlankRecordsAreReadAsAbsent() {
        then("the Quarkus vault client reads no op-monitor CA when no record exists",
                () -> assertThat(vaultClient.getOpMonitorCa()).isEmpty());

        given("the op-monitor CA record is seeded with a blank certificate", () -> seedRawRecord(""));
        then("the Quarkus vault client reads no op-monitor CA",
                () -> assertThat(vaultClient.getOpMonitorCa()).isEmpty());
    }

    @Test
    @DisplayName("Create and clear through the Quarkus vault client write the raw record format")
    void createAndClearWriteTheRawRecordFormat() {
        var ca = newCa("CN=Op-monitor CA " + System.nanoTime());

        given("the Quarkus vault client creates the op-monitor CA", () -> vaultClient.createOpMonitorCa(ca));
        then("the raw record has a certificate field only, holding the PEM of that CA", () -> {
            var record = rawRecord();
            assertThat(record).containsOnlyKeys("certificate");
            var certificate = (String) record.get("certificate");
            assertThat(certificate).matches(PEM_CERTIFICATE_BLOCK);
            assertThat(parseCertificate(certificate)).isEqualTo(ca);
        });
        and("the Quarkus vault client reads that CA", () -> assertThat(vaultClient.getOpMonitorCa()).contains(ca));

        and("the Quarkus vault client clears the op-monitor CA", () -> vaultClient.clearOpMonitorCa());
        then("the raw record still exists with a blank certificate",
                () -> assertThat(rawRecord()).containsExactly(entry("certificate", "")));
        and("the Quarkus vault client reads no op-monitor CA", () -> assertThat(vaultClient.getOpMonitorCa()).isEmpty());
    }

    @Test
    @DisplayName("The external RPC CA through the Quarkus CA client is the xrd-pki issuing CA, without key material")
    void externalRpcCaIsTheIssuingCaWithoutKeyMaterial() {
        var rawCa = given("the issuing CA is read raw with bao", () -> bao("read", "-format=json", ISSUING_CA_PATH));
        var expected = parseCertificate(JsonPath.from(rawCa).getString("data.certificate"));

        var externalRpcCa = when("the Quarkus CA client reads the external RPC CA", () -> vaultCaClient.readCaCertificate());

        then("it equals the raw issuing CA", () -> assertThat(externalRpcCa).contains(expected));
        and("the raw record has no private key", () -> {
            assertThat(JsonPath.from(rawCa).getMap("data")).doesNotContainKey("private_key");
            assertThat(rawCa).doesNotContain("PRIVATE KEY");
        });
        and("a leaf provisioned through the Quarkus key client verifies against it",
                () -> assertProvisionedLeafVerifiesAgainst(externalRpcCa.orElseThrow()));
    }

    @SneakyThrows
    private void assertProvisionedLeafVerifiesAgainst(X509Certificate ca) {
        var leaf = vaultKeyClient.provisionNewCerts().identityCertChain()[0];
        leaf.verify(ca.getPublicKey());
    }

    private void seedRawRecord(String certificate) {
        exec("sh", "-c", "bao write " + RAW_RECORD_PATH + " certificate='" + certificate + "'");
    }

    private Map<String, Object> rawRecord() {
        return JsonPath.from(bao("read", "-format=json", RAW_RECORD_PATH)).getMap("data");
    }

    private void deleteRawRecord() {
        bao("delete", RAW_RECORD_PATH);
    }

    private String bao(String... arguments) {
        var command = new String[arguments.length + 1];
        command[0] = "bao";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        return exec(command);
    }

    private String exec(String... command) {
        var result = stack.execInContainer(OpMonitorContainerSetup.OPENBAO, command);
        assertThat(result.getExitCode()).as(String.join(" ", command)).isZero();
        return result.getStdout();
    }

    private static VaultConfigHolder quarkusConfigHolder() {
        var config = mock(VaultRuntimeConfig.class);
        when(config.kvSecretEngineVersion()).thenReturn(1);
        when(config.kvSecretEngineMountPath()).thenReturn(KV_MOUNT);
        when(config.kvSecretEngineAlias()).thenReturn(Map.of());
        return new VaultConfigHolder().setVaultRuntimeConfig(config);
    }

    @SneakyThrows
    private static VaultPKISecretEngineFactory pkiEngineFactory(io.quarkus.vault.client.VaultClient vaultApi) {
        var factory = new VaultPKIManagerFactory();
        var field = VaultPKIManagerFactory.class.getDeclaredField("vaultClient");
        field.setAccessible(true);
        field.set(factory, vaultApi);
        return factory;
    }

    @SneakyThrows
    private static X509Certificate newCa(String subjectName) {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        var subject = new X500Name(subjectName);
        var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE, Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)), subject, keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    @SneakyThrows
    private static String pem(X509Certificate certificate) {
        var writer = new StringWriter();
        try (var pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(certificate);
        }
        return writer.toString();
    }

    @SneakyThrows
    private static X509Certificate parseCertificate(String pem) {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
    }
}
