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

package ee.ria.xroad.signer.glue;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.signer.SignerProxy;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoAndKeyId;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;
import ee.ria.xroad.signer.protocol.message.CertificateRequestFormat;
import ee.ria.xroad.signer.protocol.message.GetMemberCertsResponse;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.encoders.Base64;
import org.junit.jupiter.api.Assertions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.SystemProperties.SIGNER_PORT;
import static ee.ria.xroad.common.util.CryptoUtils.SHA256WITHRSA_ID;
import static ee.ria.xroad.common.util.CryptoUtils.SHA256_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class SignerStepDefs {

    private static Process signerProcess;

    private String keyId;
    private String csrId;
    private String certHash;
    private CertificateInfo certInfo;

    @BeforeAll
    public static void setup() throws Exception {
        int port = findAvailablePort();

        System.setProperty(SIGNER_PORT, String.valueOf(port));

        startSigner(port);

        ActorSystem actorSystem = ActorSystem.create("SignerProxyIntTest", getConf());
        SignerClient.init(actorSystem);
    }

    @AfterAll
    public static void tearDown() {
        signerProcess.destroy();
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find available port", e);
        }
    }

    @When("signer is initialized with pin {string}")
    public void signerIsInitializedWithPin(String pin) throws Exception {
        SignerProxy.initSoftwareToken(pin.toCharArray());
    }

    @Then("token {string} is not active")
    public void tokenIsNotActive(String tokenId) throws Exception {
        final TokenInfo tokenInfo = SignerProxy.getToken(tokenId);

        Assertions.assertFalse(tokenInfo.isActive());
    }

    @Given("token {string} status is {string}")
    public void assertTokenStatus(String tokenId, String status) throws Exception {
        final TokenInfo token = SignerProxy.getToken(tokenId);
        assertThat(token.getStatus()).isEqualTo(TokenStatusInfo.valueOf(status));
    }

    @Given("tokens list contains token {string}")
    public void tokensListContainsToken(String tokenId) throws Exception {
        final TokenInfo tokenInfo = SignerProxy.getTokens().stream()
                .filter(token -> token.getId().equals(tokenId))
                .findFirst()
                .orElseThrow();
        assertThat(tokenInfo).isNotNull();
    }

    @When("token {string} is logged in with pin {string}")
    public void tokenIsActivatedWithPin(String tokenId, String pin) throws Exception {
        SignerProxy.activateToken(tokenId, pin.toCharArray());
    }

    @When("token {string} is logged out")
    public void tokenIsLoggedOut(String tokenId) throws Exception {
        SignerProxy.deactivateToken(tokenId);
    }

    @Then("token {string} is active")
    public void tokenIsActive(String tokenId) throws Exception {
        assertThat(SignerProxy.getToken(tokenId).isActive()).isTrue();
    }

    @When("token {string} pin is updated from {string} to {string}")
    public void tokenPinIsUpdatedFromTo(String tokenId, String oldPin, String newPin) throws Exception {
        SignerProxy.updateTokenPin(tokenId, oldPin.toCharArray(), newPin.toCharArray());
    }

    @When("name {string} is set for token {string}")
    public void nameIsSetForToken(String name, String tokenId) throws Exception {
        SignerProxy.setTokenFriendlyName(tokenId, name);
    }

    @Then("token {string} name is {string}")
    public void tokenNameIs(String tokenId, String name) throws Exception {
        assertThat(SignerProxy.getToken(tokenId).getFriendlyName()).isEqualTo(name);
    }

    @When("new key {string} generated for token {string}")
    public void newKeyGeneratedForToken(String keyLabel, String tokenId) throws Exception {
        final KeyInfo keyInfo = SignerProxy.generateKey(tokenId, keyLabel);
        this.keyId = keyInfo.getId();
    }

    @And("name {string} is set for generated key")
    public void nameIsSetForGeneratedKey(String keyFriendlyName) throws Exception {
        SignerProxy.setKeyFriendlyName(this.keyId, keyFriendlyName);
    }

    @Then("token {string} has exact keys {string}")
    public void tokenHasKeys(String tokenId, String keyNames) throws Exception {
        final List<String> keys = Arrays.asList(keyNames.split(","));
        final TokenInfo token = SignerProxy.getToken(tokenId);

        assertThat(token.getKeyInfo().size()).isEqualTo(keys.size());

        final List<String> tokenKeyNames = token.getKeyInfo().stream()
                .map(KeyInfo::getFriendlyName)
                .collect(Collectors.toList());

        assertThat(tokenKeyNames).containsExactlyInAnyOrderElementsOf(keys);
    }

    @When("key {string} is deleted from token {string}")
    public void keyIsDeletedFromToken(String keyName, String tokenId) throws Exception {
        final KeyInfo key = findKeyInToken(tokenId, keyName);
        SignerProxy.deleteKey(key.getId(), true);
    }

    private KeyInfo findKeyInToken(String tokenId, String keyName) throws Exception {
        return SignerProxy.getToken(tokenId).getKeyInfo().stream()
                .filter(keyInfo -> keyInfo.getFriendlyName().equals(keyName))
                .findFirst()
                .orElseThrow();
    }

    @Given("self signed cert generated for token {string} key {string}, client {string}")
    public void selfSignedCertGeneratedForTokenKeyForClient(String tokenId, String keyName, String client) throws Exception {
        final KeyInfo keyInToken = findKeyInToken(tokenId, keyName);

        final byte[] certBytes = SignerProxy.generateSelfSignedCert(keyInToken.getId(), getClientId(client), KeyUsageInfo.SIGNING,
                "CN=" + client, Date.from(TimeUtils.now().minus(5, DAYS)), Date.from(TimeUtils.now().plus(5, DAYS)));
        this.certHash = CryptoUtils.calculateCertHexHash(certBytes);
    }

    private ClientId.Conf getClientId(String client) {
        final String[] parts = client.split(":");
        return ClientId.Conf.create(parts[0], parts[1], parts[2]);
    }

    @When("cert request is generated for token {string} key {string} for client {string}")
    public void certRequestIsGeneratedForTokenKey(String tokenId, String keyName, String client) throws Exception {
        final KeyInfo key = findKeyInToken(tokenId, keyName);
        final ClientId.Conf clientId = getClientId(client);
        final SignerProxy.GeneratedCertRequestInfo csrInfo =
                SignerProxy.generateCertRequest(key.getId(), clientId, KeyUsageInfo.SIGNING,
                        "CN=key-" + keyName, CertificateRequestFormat.DER);

        this.csrId = csrInfo.getCertReqId();
    }

    @And("cert request is regenerated")
    public void certRequestIsRegenerated() throws Exception {
        SignerProxy.regenerateCertRequest(this.csrId, CertificateRequestFormat.DER);
    }

    @Given("token {string} key {string} has {int} certificates")
    public void tokenKeyHasCertificates(String tokenId, String keyName, int certCount) throws Exception {
        final KeyInfo key = findKeyInToken(tokenId, keyName);

        assertThat(key.getCerts()).hasSize(certCount);
    }

    @And("sign mechanism for token {string} key {string} is not null")
    public void signMechanismForTokenKeyIsNotNull(String tokenId, String keyName) throws Exception {
        final KeyInfo keyInToken = findKeyInToken(tokenId, keyName);
        final String signMechanism = SignerProxy.getSignMechanism(keyInToken.getId());

        assertThat(signMechanism).isNotBlank();
    }

    @Then("member {string} has {int} certificate")
    public void memberHasCertificate(String memberId, int certCount) throws Exception {
        final GetMemberCertsResponse memberCerts = SignerProxy.getMemberCerts(getClientId(memberId));
        assertThat(memberCerts.getCerts()).hasSize(certCount);
    }

    @When("check token {string} key {string} batch signing enabled")
    public void checkTokenBatchSigningEnabled(String tokenId, String keyname) throws Exception {
        final KeyInfo key = findKeyInToken(tokenId, keyname);

        assertThat(SignerProxy.isTokenBatchSigningEnabled(key.getId())).isNotNull();
    }

    @Then("cert request can be deleted")
    public void certRequestCanBeDeleted() throws Exception {
        SignerProxy.deleteCertRequest(this.csrId);
    }

    @And("certificate info can be retrieved by cert hash")
    public void certificateInfoCanBeRetrievedByHash() throws Exception {
        final CertificateInfo certInfoResponse = SignerProxy.getCertForHash(this.certHash);
        assertThat(certInfoResponse).isNotNull();
        this.certInfo = certInfoResponse;
    }

    @And("keyId can be retrieved by cert hash")
    public void keyidCanBeRetrievedByCertHash() throws Exception {
        final SignerProxy.KeyIdInfo keyIdForCertHash = SignerProxy.getKeyIdForCertHash(this.certHash);
        assertThat(keyIdForCertHash).isNotNull();
    }

    @And("token and keyId can be retrieved by cert hash")
    public void tokenAndKeyIdCanBeRetrievedByCertHash() throws Exception {
        final TokenInfoAndKeyId tokenAndKeyIdForCertHash = SignerProxy.getTokenAndKeyIdForCertHash(this.certHash);
        assertThat(tokenAndKeyIdForCertHash).isNotNull();
    }

    @And("token and key can be retrieved by cert request")
    public void tokenAndKeyCanBeRetrievedByCertRequest() throws Exception {
        final TokenInfoAndKeyId tokenAndKeyIdForCertRequestId = SignerProxy.getTokenAndKeyIdForCertRequestId(this.csrId);
        assertThat(tokenAndKeyIdForCertRequestId).isNotNull();
    }

    @Then("token info can be retrieved by key id")
    public void tokenInfoCanBeRetrievedByKeyId() throws Exception {
        final TokenInfo tokenForKeyId = SignerProxy.getTokenForKeyId(this.keyId);
        assertThat(tokenForKeyId).isNotNull();
    }

    @Given("digest can be signed using key {string} from token {string}")
    public void digestCanBeSignedUsingKeyFromToken(String keyName, String tokenId) throws Exception {
        final KeyInfo key = findKeyInToken(tokenId, keyName);

        SignerProxy.sign(key.getId(), SHA256WITHRSA_ID, calculateDigest(SHA256_ID, "digest".getBytes(UTF_8)));
    }

    @Then("certificate can be deactivated")
    public void certificateCanBeDeactivated() throws Exception {
        SignerProxy.deactivateCert(this.certInfo.getId());
    }

    @And("certificate can be activated")
    public void certificateCanBeActivated() throws Exception {
        SignerProxy.activateCert(this.certInfo.getId());
    }

    @And("certificate can be deleted")
    public void certificateCanBeDeleted() throws Exception {
        SignerProxy.deleteCert(this.certInfo.getId());
    }

    @And("certificate status can be changed to {string}")
    public void certificateStatusCanBeChangedTo(String status) throws Exception {
        SignerProxy.setCertStatus(this.certInfo.getId(), status);
    }

    @And("certificate can be signed using key {string} from token {string}")
    public void certificateCanBeSignedUsingKeyFromToken(String keyName, String tokenId) throws Exception {
        final KeyInfo key = findKeyInToken(tokenId, keyName);
        byte[] keyBytes = Base64.decode(key.getPublicKey().getBytes());
        X509EncodedKeySpec x509publicKey = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey publicKey = kf.generatePublic(x509publicKey);

        final byte[] bytes = SignerProxy.signCertificate(key.getId(), SHA256WITHRSA_ID, "CN=cs", publicKey);
        assertThat(bytes).isNotEmpty();
    }

    private static Config getConf() {
        return ConfigFactory.load().getConfig("signer-integration-test")
                .withFallback(ConfigFactory.load());
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void startSigner(int port) throws InterruptedException {
        String signerPath = "../signer/build/libs/signer-1.0.jar";

        Thread t = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("java",
                        // --add-opens required for akka to work with java 17.
                        // should be removed with akka.
                        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                        "--add-opens=java.base/java.nio=ALL-UNNAMED",
                        "-Dxroad.signer.port=" + port,
                        "-Dxroad.signer.key-configuration-file="
                                + "build/resources/intTest/keyconf.xml",
                        "-Dxroad.signer.device-configuration-file="
                                + "build/resources/intTest/devices.ini",
                        "-Djava.library.path=../passwordstore/",
                        "-jar", signerPath);

                signerProcess = pb.start();

                new StreamGobbler(signerProcess.getErrorStream()).start();
                new StreamGobbler(signerProcess.getInputStream()).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t.start();
        MILLISECONDS.sleep(3000);
    }

    @RequiredArgsConstructor
    static class StreamGobbler extends Thread {
        private final InputStream is;

        public void run() {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
}
