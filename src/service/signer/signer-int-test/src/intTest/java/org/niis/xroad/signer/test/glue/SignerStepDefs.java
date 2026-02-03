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

package org.niis.xroad.signer.test.glue;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Step;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.ThrowableAssert;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.encoders.Base64;
import org.junit.jupiter.api.Assertions;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;
import org.niis.xroad.signer.test.SignerIntTestContainerSetup;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA256;
import static ee.ria.xroad.common.crypto.identifier.SignAlgorithm.SHA256_WITH_ECDSA;
import static ee.ria.xroad.common.crypto.identifier.SignAlgorithm.SHA256_WITH_RSA;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.niis.xroad.common.properties.NodeProperties.NodeType.PRIMARY;
import static org.niis.xroad.common.properties.NodeProperties.NodeType.SECONDARY;


@Slf4j
@SuppressWarnings("checkstyle:MagicNumber")
public class SignerStepDefs extends BaseSignerStepDefs {
    private String scenarioKeyId;
    private String scenarioCsrId;
    private String certHash;
    private CertificateInfo certInfo;
    private byte[] scenarioCert;

    private final Map<String, String> tokenLabelToIdMapping = new HashMap<>();

    @Step("tokens are listed")
    public void listTokens() {
        var tokens = clientHolder.get().getTokens();
        testReportService.attachJson("Tokens", tokens.toArray());

        tokenLabelToIdMapping.clear();
        getTokenFriendlyNameToIdMapping().clear();

        tokens.forEach(token -> {
            if (StringUtils.isNotBlank(token.getLabel())) {
                tokenLabelToIdMapping.put(token.getLabel(), token.getId());
            }
            if (StringUtils.isNotBlank(token.getFriendlyName())) {
                getTokenFriendlyNameToIdMapping().put(token.getFriendlyName(), token.getId());
            }
        });
    }

    @Step("tokens are listed on {nodeType} node")
    public void listTokens(NodeProperties.NodeType nodeType) {
        var tokens = clientHolder.get(nodeType).getTokens();
        testReportService.attachJson("Tokens[%s]".formatted(nodeType), tokens.toArray());
    }

    @Step("signer is initialized with pin {string}")
    public void signerIsInitializedWithPin(String pin) {
        clientHolder.get().initSoftwareToken(pin.toCharArray());
    }

    @Step("signer service is restarted")
    public void signerIsRestarted() {
        signerIntTestSetup.restartContainer(SignerIntTestContainerSetup.SIGNER);
    }

    @Step("token {string} is not active")
    public void tokenIsNotActive(String friendlyName) {
        final TokenInfo tokenInfo = getTokenInfoByFriendlyName(friendlyName);

        Assertions.assertFalse(tokenInfo.isActive());
    }

    @Step("token {string} status is {string}")
    public void assertTokenStatus(String friendlyName, String status) {
        final TokenInfo token = getTokenInfoByFriendlyName(friendlyName);
        assertThat(token.getStatus()).isEqualTo(TokenStatusInfo.valueOf(status));
    }

    @Step("tokens list contains token {string}")
    public void tokensListContainsToken(String friendlyName) {
        var tokens = clientHolder.get().getTokens();

        final TokenInfo tokenInfo = tokens.stream()
                .filter(token -> token.getFriendlyName().equals(friendlyName))
                .findFirst()
                .orElseThrow();
        assertThat(tokenInfo).isNotNull();
    }

    @Step("tokens list contains token with label {string}")
    public void tokensListContainsTokenLabel(String label) {
        var tokens = clientHolder.get().getTokens();
        testReportService.attachJson("Tokens", tokens);
        final TokenInfo tokenInfo = tokens.stream()
                .filter(token -> token.getLabel().equals(label))
                .findFirst()
                .orElseThrow();
        assertThat(tokenInfo).isNotNull();
    }


    @Step("token {string} is logged in with pin {string}")
    public void tokenIsActivatedWithPin(String friendlyName, String pin) {
        tokenIsActivatedWithPin(friendlyName, pin, PRIMARY);
    }

    @Step("token {string} is logged in with pin {string} on {nodeType} node")
    public void tokenIsActivatedWithPin(String friendlyName, String pin, NodeProperties.NodeType nodeType) {
        var tokenId = getTokenFriendlyNameToIdMapping().get(friendlyName);
        clientHolder.get(nodeType).activateToken(tokenId, pin.toCharArray());
    }

    @Step("token {string} is logged out")
    public void tokenIsLoggedOut(String friendlyName) {
        var tokenId = getTokenFriendlyNameToIdMapping().get(friendlyName);
        clientHolder.get().deactivateToken(tokenId);
    }

    @Step("token {string} is active")
    public void tokenIsActive(String friendlyName) {
        tokenIsActive(friendlyName, PRIMARY);
    }

    @Step("token {string} is active on {nodeType} node")
    public void tokenIsActive(String friendlyName, NodeProperties.NodeType nodeType) {
        var tokenInfo = getTokenInfoByFriendlyName(friendlyName, nodeType);
        assertThat(tokenInfo.isActive()).isTrue();
    }


    @Step("token {string} pin is updated from {string} to {string}")
    public void tokenPinIsUpdatedFromTo(String friendlyName, String oldPin, String newPin) {
        var tokenId = getTokenFriendlyNameToIdMapping().get(friendlyName);
        clientHolder.get().updateTokenPin(tokenId, oldPin.toCharArray(), newPin.toCharArray());
    }

    @Step("token {string} pin is update from {string} to {string} fails with an error")
    public void tokenPinIsUpdatedFromToError(String friendlyName, String oldPin, String newPin) {
        var tokenId = getTokenFriendlyNameToIdMapping().get(friendlyName);
        try {
            clientHolder.get().updateTokenPin(tokenId, oldPin.toCharArray(), newPin.toCharArray());
        } catch (XrdRuntimeException e) {
            assertException("signer.internal_error",
                    "\\[.*?\\] signer\\.internal_error: Software token not found", e);
        }
    }

    @Step("name {string} is set for token with id {string}")
    public void nameIsSetForToken(String name, String tokenId) {
        clientHolder.get().setTokenFriendlyName(tokenId, name);
    }

    @Step("friendly name {string} is set for token with label {string}")
    public void nameIsSetForTokenLabel(String name, String label) {
        var tokenId = tokenLabelToIdMapping.get(label);
        clientHolder.get().setTokenFriendlyName(tokenId, name);
    }

    @Step("token with id {string} name is {string} on {nodeType} node")
    public void tokenNameIs(String tokenId, String name, NodeProperties.NodeType nodeType) {
        assertThat(clientHolder.get(nodeType).getToken(tokenId).getFriendlyName()).isEqualTo(name);
    }

    @Step("token with label {string} name is {string}")
    public void tokenNameByLabelIs(String label, String name) {
        tokenNameByLabelIs(label, name, NodeProperties.NodeType.PRIMARY);
    }

    @Step("token with label {string} name is {string} on {nodeType} node")
    public void tokenNameByLabelIs(String label, String name, NodeProperties.NodeType nodeType) {
        var tokenId = tokenLabelToIdMapping.get(label);
        assertThat(clientHolder.get(nodeType).getToken(tokenId).getFriendlyName()).isEqualTo(name);
    }

    @Step("new {algorithm} key {string} generated for token {string}")
    public void newKeyGeneratedForToken(KeyAlgorithm algorithm, String keyLabel, String friendlyName) {
        var tokenId = getTokenFriendlyNameToIdMapping().get(friendlyName);
        final KeyInfo keyInfo = clientHolder.get().generateKey(tokenId, keyLabel, algorithm);
        testReportService.attachJson("keyInfo", keyInfo);
        this.scenarioKeyId = keyInfo.getId();
    }

    @Step("name {string} is set for generated key")
    public void nameIsSetForGeneratedKey(String keyFriendlyName) {
        clientHolder.get().setKeyFriendlyName(this.scenarioKeyId, keyFriendlyName);
    }

    @Step("set name for key not allowed on secondary node")
    public void nameIsSetForGeneratedKey() {
        assertAccessDenied(() -> clientHolder.get(SECONDARY).setKeyFriendlyName(this.scenarioKeyId, "keyFriendlyName"));
    }

    @Step("token {string} has exact keys {string}")
    public void tokenHasKeys(String friendlyName, String keyNames) {
        tokenHasKeys(friendlyName, keyNames, NodeProperties.NodeType.PRIMARY);
    }

    @Step("token {string} has exact keys {string} on {nodeType} node")
    public void tokenHasKeys(String friendlyName, String keyNames, NodeProperties.NodeType nodeType) {
        final List<String> keys = Arrays.asList(keyNames.split(","));
        final TokenInfo token = getTokenInfoByFriendlyName(friendlyName, nodeType);

        assertThat(token.getKeyInfo().size()).isEqualTo(keys.size());

        final List<String> tokenKeyNames = token.getKeyInfo().stream()
                .map(KeyInfo::getFriendlyName)
                .collect(Collectors.toList());

        assertThat(tokenKeyNames).containsExactlyInAnyOrderElementsOf(keys);
    }

    @Step("key {string} is deleted from token {string}")
    public void keyIsDeletedFromToken(String keyName, String friendlyName) {
        final KeyInfo key = findKeyInToken(friendlyName, keyName);
        clientHolder.get().deleteKey(key.getId(), true);
    }


    @Step("Certificate is imported for client {string}")
    public void certificateIsImported(String client) {
        scenarioKeyId = clientHolder.get().importCert(scenarioCert, CertificateInfo.STATUS_REGISTERED, getClientId(client));
    }

    @Step("Wrong Certificate is not imported for client {string}")
    public void certImportFails(String client) {
        byte[] certBytes = classpathFileResolver.getFileAsBytes("cert-01.pem");
        try {
            clientHolder.get().importCert(certBytes, CertificateInfo.STATUS_REGISTERED, getClientId(client));
        } catch (XrdRuntimeException e) {
            assertException("signer.key_not_found",
                    "\\[.*?\\] signer\\.key_not_found: Could not find key that has public key that matches "
                            + "the public key of certificate", e);
        }
    }

    @Step("self signed cert generated for token {string} key {string}, client {string}")
    public void selfSignedCertGeneratedForTokenKeyForClient(String friendlyName, String keyName, String client) throws IOException {
        final KeyInfo keyInToken = findKeyInToken(friendlyName, keyName);

        scenarioCert = clientHolder.get().generateSelfSignedCert(keyInToken.getId(), getClientId(client), KeyUsageInfo.SIGNING,
                client, Date.from(now().minus(5, DAYS)), Date.from(now().plus(5, DAYS)));
        this.certHash = calculateCertHexHash(scenarioCert);
    }

    private ClientId.Conf getClientId(String client) {
        final String[] parts = client.split(":");
        return ClientId.Conf.create(parts[0], parts[1], parts[2]);
    }

    private SecurityServerId.Conf getSecurityServerId(String securityServerId) {
        final String[] parts = securityServerId.split(":");
        return SecurityServerId.Conf.create(parts[0], parts[1], parts[2], parts[3]);
    }

    @Step("the {} cert request is generated for token {string} key {string} for client {string} throws exception")
    public void certRequestIsGeneratedForTokenKeyException(String keyUsage, String friendlyName, String keyName, String client)
            throws Exception {
        try {
            certRequestIsGeneratedForTokenKey(keyUsage, friendlyName, keyName, client);
        } catch (XrdRuntimeException e) {
            assertException("signer.wrong_cert_usage",
                    "\\[.*?\\] signer\\.wrong_cert_usage: Authentication certificate requests can only be created under "
                            + "software tokens", e);
        }
    }

    @Step("the {} cert request is generated for token {string} key {string} for client {string}")
    public void certRequestIsGeneratedForTokenKey(String keyUsage, String friendlyName, String keyName, String client) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyName);
        final ClientId.Conf clientId = getClientId(client);
        SignerRpcClient.GeneratedCertRequestInfo csrInfo = clientHolder.get().generateCertRequest(key.getId(), clientId,
                KeyUsageInfo.valueOf(keyUsage),
                "CN=key-" + keyName, CertificateRequestFormat.DER);

        this.scenarioCsrId = csrInfo.certReqId();

        File csrFile = File.createTempFile("tmp", keyUsage.toLowerCase() + "_csr" + System.currentTimeMillis());
        FileUtils.writeByteArrayToFile(csrFile, csrInfo.certRequest());
        putStepData(StepDataKey.DOWNLOADED_FILE, csrFile);
    }

    @Step("Generated certificate with initial status {string} is imported for client {string}")
    public void importCertFromFile(String initialStatus, String client) throws Exception {
        final Optional<File> cert = getStepData(StepDataKey.CERT_FILE);
        final ClientId.Conf clientId = getClientId(client);
        byte[] certFileBytes = FileUtils.readFileToByteArray(cert.orElseThrow());
        scenarioKeyId = clientHolder.get().importCert(certFileBytes, initialStatus, clientId);
        X509Certificate x509Certificate = readCertificate(certFileBytes);
        scenarioCert = x509Certificate.getEncoded();
        certInfo = clientHolder.get().getCertForHash(calculateCertHexHash(scenarioCert));
    }

    @Step("cert request is regenerated")
    public void certRequestIsRegenerated() {
        clientHolder.get().regenerateCertRequest(this.scenarioCsrId, CertificateRequestFormat.DER);
    }

    @Step("token {string} key {string} has {int} certificates")
    public void tokenKeyHasCertificates(String friendlyName, String keyName, int certCount) {
        tokenKeyHasCertificates(friendlyName, keyName, certCount, NodeProperties.NodeType.PRIMARY);
    }

    @Step("token {string} key {string} has {int} certificates on {nodeType} node")
    public void tokenKeyHasCertificates(String friendlyName, String keyName, int certCount, NodeProperties.NodeType nodeType) {
        final KeyInfo key = findKeyInToken(friendlyName, keyName, nodeType);

        assertThat(key.getCerts()).hasSize(certCount);
    }

    @Step("sign mechanism for token {string} key {string} is not null")
    public void signMechanismForTokenKeyIsNotNull(String friendlyName, String keyName) {
        signMechanismForTokenKeyIsNotNull(friendlyName, keyName, NodeProperties.NodeType.PRIMARY);
    }

    @Step("sign mechanism for token {string} key {string} is not null on {nodeType} node")
    public void signMechanismForTokenKeyIsNotNull(String friendlyName, String keyName, NodeProperties.NodeType nodeType) {
        final KeyInfo keyInToken = findKeyInToken(friendlyName, keyName);
        final var signMechanism = clientHolder.get(nodeType).getSignMechanism(keyInToken.getId());

        assertThat(signMechanism.name()).isNotBlank();
    }

    @Step("member {string} has {int} certificate")
    public void memberHasCertificate(String memberId, int certCount) {
        memberHasCertificate(memberId, certCount, NodeProperties.NodeType.PRIMARY);
    }

    @Step("member {string} has {int} certificate on {nodeType} node")
    public void memberHasCertificate(String memberId, int certCount, NodeProperties.NodeType nodeType) {
        final List<CertificateInfo> memberCerts = clientHolder.get(nodeType).getMemberCerts(getClientId(memberId));
        assertThat(memberCerts).hasSize(certCount);
    }

    @Step("check token {string} key {string} batch signing enabled")
    public void checkTokenBatchSigningEnabled(String friendlyName, String keyname) {
        final KeyInfo key = findKeyInToken(friendlyName, keyname);

        assertThat(clientHolder.get().isTokenBatchSigningEnabled(key.getId())).isNotNull();
    }

    @Step("cert request can be deleted")
    public void certRequestCanBeDeleted() {
        clientHolder.get().deleteCertRequest(this.scenarioCsrId);
    }

    @Step("certificate info can be retrieved by cert hash")
    public void certificateInfoCanBeRetrievedByHash() {
        certificateInfoCanBeRetrievedByHash(NodeProperties.NodeType.PRIMARY);
    }

    @Step("certificate info can be retrieved by cert hash on {nodeType} node")
    public void certificateInfoCanBeRetrievedByHash(NodeProperties.NodeType nodeType) {
        final CertificateInfo certInfoResponse = clientHolder.get(nodeType).getCertForHash(this.certHash);
        assertThat(certInfoResponse).isNotNull();
        this.certInfo = certInfoResponse;
    }

    @Step("keyId can be retrieved by cert hash")
    public void keyidCanBeRetrievedByCertHash() {
        keyidCanBeRetrievedByCertHash(NodeProperties.NodeType.PRIMARY);
    }

    @Step("keyId can be retrieved by cert hash on {nodeType} node")
    public void keyidCanBeRetrievedByCertHash(NodeProperties.NodeType nodeType) {
        final SignerRpcClient.KeyIdInfo keyIdForCertHash = clientHolder.get(nodeType).getKeyIdForCertHash(this.certHash);
        assertThat(keyIdForCertHash).isNotNull();
    }

    @Step("token and keyId can be retrieved by cert hash")
    public void tokenAndKeyIdCanBeRetrievedByCertHash() {
        tokenAndKeyIdCanBeRetrievedByCertHash(NodeProperties.NodeType.PRIMARY);
    }

    @Step("token and keyId can be retrieved by cert hash on {nodeType} node")
    public void tokenAndKeyIdCanBeRetrievedByCertHash(NodeProperties.NodeType nodeType) {
        final TokenInfoAndKeyId tokenAndKeyIdForCertHash = clientHolder.get(nodeType).getTokenAndKeyIdForCertHash(this.certHash);
        assertThat(tokenAndKeyIdForCertHash).isNotNull();
    }

    @Step("token and key can be retrieved by cert request")
    public void tokenAndKeyCanBeRetrievedByCertRequest() {
        tokenAndKeyCanBeRetrievedByCertRequest(NodeProperties.NodeType.PRIMARY);
    }

    @Step("token and key can be retrieved by cert request on {nodeType} node")
    public void tokenAndKeyCanBeRetrievedByCertRequest(NodeProperties.NodeType nodeType) {
        final TokenInfoAndKeyId tokenAndKeyIdForCertRequestId = clientHolder.get(nodeType)
                .getTokenAndKeyIdForCertRequestId(this.scenarioCsrId);
        assertThat(tokenAndKeyIdForCertRequestId).isNotNull();
    }

    @Step("token info can be retrieved by key id")
    public void tokenInfoCanBeRetrievedByKeyId() {
        tokenInfoCanBeRetrievedByKeyId(NodeProperties.NodeType.PRIMARY);
    }

    @Step("token info can be retrieved by key id on {nodeType} node")
    public void tokenInfoCanBeRetrievedByKeyId(NodeProperties.NodeType nodeType) {
        final TokenInfo tokenForKeyId = clientHolder.get(nodeType).getTokenForKeyId(this.scenarioKeyId);
        testReportService.attachJson("tokenInfo", tokenForKeyId);
        assertThat(tokenForKeyId).isNotNull();
    }

    @Step("digest can be signed using key {string} from token {string}")
    public void digestCanBeSignedUsingKeyFromToken(String keyName, String friendlyName) throws Exception {
        digestCanBeSignedUsingKeyFromToken(keyName, friendlyName, PRIMARY);
    }

    @Step("digest can be signed using key {string} from token {string} on {nodeType} node")
    public void digestCanBeSignedUsingKeyFromToken(String keyName, String friendlyName, NodeProperties.NodeType nodeType) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyName, nodeType);

        var digest = format("%s-%d", randomUUID(), System.currentTimeMillis());

        var signAlgorithm = switch (SignMechanism.valueOf(key.getSignMechanismName()).keyAlgorithm()) {
            case RSA -> SHA256_WITH_RSA;
            case EC -> SHA256_WITH_ECDSA;
        };

        byte[] bytes = clientHolder.getSignClient(nodeType).sign(key.getId(), signAlgorithm,
                calculateDigest(SHA256, digest.getBytes(UTF_8)));
        assertThat(bytes).isNotEmpty();
    }

    @Step("certificate can be deactivated")
    public void certificateCanBeDeactivated() {
        clientHolder.get().deactivateCert(this.certInfo.getId());
    }

    @Step("certificate can be activated")
    public void certificateCanBeActivated() {
        clientHolder.get().activateCert(this.certInfo.getId());
    }

    @Step("certificate can be deleted")
    public void certificateCanBeDeleted() {
        clientHolder.get().deleteCert(this.certInfo.getId());
    }

    @Step("certificate status can be changed to {string}")
    public void certificateStatusCanBeChangedTo(String status) {
        clientHolder.get().setCertStatus(this.certInfo.getId(), status);
    }

    @Step("certificate can be signed using key {string} from token {string}")
    public void certificateCanBeSignedUsingKeyFromToken(String keyName, String friendlyName) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyName);
        byte[] keyBytes = Base64.decode(key.getPublicKey().getBytes());
        X509EncodedKeySpec x509publicKey = new X509EncodedKeySpec(keyBytes);
        var algorithm = SignMechanism.valueOf(key.getSignMechanismName()).keyAlgorithm();
        KeyFactory kf = KeyFactory.getInstance(algorithm.name());
        PublicKey publicKey = kf.generatePublic(x509publicKey);

        var signAlgorithm = switch (algorithm) {
            case RSA -> SHA256_WITH_RSA;
            case EC -> SHA256_WITH_ECDSA;
        };

        final byte[] bytes = clientHolder.getSignClient(PRIMARY).signCertificate(key.getId(), signAlgorithm, "CN=CS", publicKey);
        assertThat(bytes).isNotEmpty();
    }

    @Step("auth key for Security Server {string} is retrieved")
    public void getAuthKey(String securityServerId) {
        var authKeyInfo = clientHolder.get().getAuthKey(getSecurityServerId(securityServerId));
        testReportService.attachJson("authKeyInfo", authKeyInfo);
        assertThat(authKeyInfo).isNotNull();
    }

    @Step("auth key retrieval for Security Server {string} fails when no active token is found")
    public void getAuthKeyFail(String securityServerId) {
        try {
            clientHolder.get().getAuthKey(getSecurityServerId(securityServerId));
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            var errorServerId = securityServerId.replace(":", "/");
            assertException("signer.key_not_found",
                    format("\\[.*?\\] signer\\.key_not_found: Could not find active authentication key for "
                            + "security server 'SERVER:%s'", errorServerId), e);
        }
    }

    @Step("Set token name fails with TokenNotFound exception when token does not exist")
    public void setTokenNameFail() {
        String tokenId = randomUUID().toString();
        try {
            clientHolder.get().setTokenFriendlyName(tokenId, randomUUID().toString());
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertException("signer.token_not_found",
                    "\\[.*?\\] signer\\.token_not_found: Token '" + tokenId + "' not found", e);
        }
    }

    @Step("Deleting not existing certificate from token fails")
    public void failOnDeleteCert() {
        String cerId = randomUUID().toString();
        try {
            clientHolder.get().deleteCert(cerId);
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertException("signer.cert_not_found",
                    "\\[.*?\\] signer\\.cert_not_found: Certificate with id '" + cerId + "' not found", e);
        }
    }

    @Step("Retrieving token info by not existing key fails")
    public void retrievingTokenInfoCanByNotExistingKeyFails() {
        String keyId = randomUUID().toString();
        try {
            clientHolder.get().getTokenForKeyId(keyId);
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertException("signer.key_not_found",
                    "\\[.*?\\] signer\\.key_not_found: Key '" + keyId + "' not found", e);
        }
    }

    @Step("Deleting not existing certRequest fails")
    public void deletingCertRequestFails() {
        String csrId = randomUUID().toString();
        try {
            clientHolder.get().deleteCertRequest(csrId);
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertException("signer.csr_not_found",
                    "\\[.*?\\] signer\\.csr_not_found: Certificate request '" + csrId + "' not found", e);
        }
    }

    @Step("Signing with unknown key fails")
    public void signKeyFail() {
        String keyId = randomUUID().toString();
        try {
            clientHolder.getSignClient(PRIMARY).sign(keyId, SignAlgorithm.ofName(randomUUID().toString()), new byte[0]);
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertException("signer.key_not_found",
                    "\\[.*?\\] signer\\.key_not_found: Key '" + keyId + "' not found", e);
        }
    }

    @Step("Signing with unknown algorithm fails using key {string} from token {string}")
    public void signAlgorithmFail(String keyName, String friendlyName) throws Exception {
        try {
            final KeyInfo key = findKeyInToken(friendlyName, keyName);
            clientHolder.getSignClient(PRIMARY).sign(key.getId(),
                    SignAlgorithm.ofName("NOT-ALGORITHM-ID"),
                    calculateDigest(SHA256, "digest".getBytes(UTF_8)));

            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertException("signer.cannot_sign.internal_error",
                    "\\[.*?\\] signer\\.cannot_sign\\.internal_error: Unknown sign mechanism of signature algorithm:"
                            + " uSA\\[name=NOT-ALGORITHM-ID, uri=null\\]", e);
        }
    }

    @Step("Getting key by not existing cert hash fails")
    public void getKeyIdByHashFail() {
        String hash = randomUUID().toString();
        try {
            clientHolder.get().getKeyIdForCertHash(hash);
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertException("signer.cert_not_found",
                    "\\[.*?\\] signer\\.cert_not_found: Certificate with hash '" + hash + "' not found", e);
        }
    }

    @Step("Not existing certificate can not be activated")
    public void notExistingCertActivateFail() {
        String certId = randomUUID().toString();
        try {
            clientHolder.get().activateCert(certId);
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertException("signer.cert_not_found",
                    "\\[.*?\\] signer\\.cert_not_found: Certificate with id '" + certId + "' not found", e);
        }
    }

    @Step("Member signing info for client {string} fails if not suitable certificates are found")
    public void getMemberSigningInfoFail(String client) {
        try {
            clientHolder.get().getMemberSigningInfo(getClientId(client));
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertException("signer.internal_error",
                    "\\[.*?\\] signer\\.internal_error: Member 'MEMBER:DEV/test/member-1' has no suitable certificates",
                    e);
        }
    }

    @Step("Member signing info for client {string} is retrieved")
    public void getMemberSigningInfo(String client) {
        var memberInfo = clientHolder.get().getMemberSigningInfo(getClientId(client));
        testReportService.attachJson("MemberSigningInfo", memberInfo);
    }

    @Step("HSM is operational")
    public void hsmIsNotOperational() {
        Assertions.assertTrue(clientHolder.get().isHSMOperational());
    }

    private void assertException(String faultCode, String messagePattern, XrdRuntimeException e) {
        Assertions.assertEquals(faultCode, e.getErrorCode());

        // Use the provided regex pattern directly for message validation
        Assertions.assertTrue(e.getMessage().matches(messagePattern),
                "Expected message to match pattern: " + messagePattern + ", but got: " + e.getMessage());
    }

    @Step("ocsp responses are set to REVOKED")
    public void ocspResponsesAreSetUnknown() throws Exception {
        CertificateStatus certificateStatus = new RevokedStatus(Date.from(Instant.parse("2022-01-01T00:00:00Z")));
        X509Certificate subject = readCertificate(certInfo.getCertificateBytes());
        String caHomePath = "/files/home/ca/CA";
        X509Certificate caCert =
                readCertificate(classpathFileResolver.getFileAsBytes(caHomePath + "/certs/ca.cert.pem"));
        X509Certificate ocspCert =
                readCertificate(classpathFileResolver.getFileAsBytes(caHomePath + "/certs/ocsp.cert.pem"));
        try (var reader =
                     new InputStreamReader(classpathFileResolver.getFileAsStream(caHomePath + "/private/ocsp.key.pem"))) {

            PEMParser pemParser = new PEMParser(reader);
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(pemParser.readObject());

            PrivateKey ocspPrivateKey = converter.getPrivateKey(privateKeyInfo);
            final OCSPResp ocspResponse = OcspTestUtils.createOCSPResponse(subject, caCert,
                    ocspCert, ocspPrivateKey, certificateStatus);

            clientHolder.get().setOcspResponses(new String[]{calculateCertHexHash(subject)},
                    new String[]{Base64.toBase64String(ocspResponse.getEncoded())});
        }
    }

    @Step("certificate activation fails with ocsp verification")
    public void certificateActivationFailsWithOcspVerification() {
        try {
            clientHolder.get().activateCert(this.certInfo.getId());
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertException("signer.internal_error",
                    "\\[.*?\\] signer\\.internal_error: Failed to verify OCSP responses for certificate\\. "
                            + "Error: \\[.*?\\] invalid_cert_path\\.cert_validation: OCSP "
                            + "response indicates certificate status is REVOKED \\(date: 2022-01-01 00:00:00\\)", e);
        }
    }

    @Step("ocsp responses are set")
    public void ocspResponsesAreSet() throws Exception {
        X509Certificate subject = TestCertUtil.getConsumer().certChain[0];
        final OCSPResp ocspResponse = OcspTestUtils.createOCSPResponse(subject, TestCertUtil.getCaCert(),
                TestCertUtil.getOcspSigner().certChain[0],
                TestCertUtil.getOcspSigner().key, CertificateStatus.GOOD);

        clientHolder.get().setOcspResponses(new String[]{calculateCertHexHash(subject)},
                new String[]{Base64.toBase64String(ocspResponse.getEncoded())});
    }

    @Step("ocsp responses can be retrieved")
    public void ocspResponsesCanBeRetrieved() throws Exception {
        X509Certificate subject = TestCertUtil.getConsumer().certChain[0];
        final String hash = calculateCertHexHash(subject);

        final String[] ocspResponses = clientHolder.get().getOcspResponses(new String[]{hash});
        assertThat(ocspResponses[0]).isNotNull();
    }

    @Step("null ocsp response is returned for unknown certificate on {nodeType} node")
    public void emptyOcspResponseIsReturnedForUnknownCertificate(NodeProperties.NodeType nodeType) throws Exception {
        final String[] ocspResponses = clientHolder.get(nodeType)
                .getOcspResponses(new String[]{calculateCertHexHash("not a cert".getBytes())});
        assertThat(ocspResponses).hasSize(1);
        assertThat(ocspResponses[0]).isNull();
    }

    @Step("signer client initialized with default settings")
    public void signerClientInitializedWithDefaultSettings() {
        signerClientReinitializedWithTimeoutMilliseconds(60000);
    }

    @Step("signer client initialized with timeout {int} milliseconds")
    public void signerClientReinitializedWithTimeoutMilliseconds(int timeoutMillis) {
        clientHolder.initWithTimeout(timeoutMillis);
    }

    @Step("getTokens fails with timeout exception")
    public void signerGetTokensFailsWithTimeoutException() {
        assertThatThrownBy(() -> clientHolder.get().getTokens())
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageMatching("\\[.*?] signer\\.network_error: gRPC client timed out\\..*");
    }

    @Step("Policy enforcement status endpoint returns false")
    public void checkPolicyEnforcement() {
        assertThat(clientHolder.get().isEnforcedTokenPinPolicy())
                .isFalse();
    }

    @Step("secondary node sync is forced")
    public void secondaryNodeSyncIsForced() {
        clientHolder.get(SECONDARY).refreshModules();
    }

    @Step("primary node is refreshed")
    public void primaryNodeIsRefreshed() {
        clientHolder.get(PRIMARY).refreshModules();
    }

    @Step("Init software token on secondary node not allowed")
    public void initSoftwareTokenFailsOnSecondaryNode() {
        var secondaryClient = clientHolder.get(SECONDARY);
        assertAccessDenied(() -> secondaryClient.initSoftwareToken("1234".toCharArray()));
    }

    @Step("Set token friendly name on secondary node not allowed")
    public void setTokenFriendlyNameFailsOnSecondaryNode() {
        var secondaryClient = clientHolder.get(SECONDARY);
        assertAccessDenied(() -> secondaryClient.setTokenFriendlyName("0", "name"));
    }

    @Step("Update token pin on secondary node not allowed")
    public void updateTokenPinFailsOnSecondaryNode() {
        var secondaryClient = clientHolder.get(SECONDARY);
        assertAccessDenied(() -> secondaryClient.updateTokenPin("0", "4321".toCharArray(), "pin".toCharArray()));
    }

    @Step("Delete token on secondary node not allowed")
    public void deleteTokenFailsOnSecondaryNode() {
        var secondaryClient = clientHolder.get(SECONDARY);
        assertAccessDenied(() -> secondaryClient.deleteToken("0"));
    }

    @Step("Generate new key on secondary node not allowed")
    public void generateNewKeyFailsOnSecondaryNode() {
        assertAccessDenied(() -> clientHolder.get(SECONDARY).generateKey("0", "key-label", KeyAlgorithm.RSA));
    }

    void assertAccessDenied(ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isExactlyInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("signer.access_denied: Write operations are not allowed on secondary node");
    }

    @ParameterType("RSA|EC")
    public KeyAlgorithm algorithm(String value) {
        return KeyAlgorithm.valueOf(value);
    }

    @ParameterType("primary|secondary")
    public NodeProperties.NodeType nodeType(String value) {
        return NodeProperties.NodeType.fromStringIgnoreCaseOrReturnDefault(value);
    }

    @Step("new key with id {string} and certificate magically appears on HSM")
    public void newKeyMagicallyAppearsOnHsm(String keyId) {
        var result = signerIntTestSetup.execInContainer(SignerIntTestContainerSetup.SIGNER, "/add-key-into-hsm.sh", keyId);
        testReportService.attachText("result", result.toString());
        assertThat(result.getExitCode()).isEqualTo(0);
    }

    @Step("token {string} has {int} key on {nodeType} node")
    public void tokenHasKeyOnNode(String tokenFriendlyName, int keyCount, NodeProperties.NodeType nodeType) {
        TokenInfo tokenInfo = clientHolder.get(nodeType).getTokens().stream()
                .filter(token -> tokenFriendlyName.equals(token.getFriendlyName()))
                .findFirst().orElseThrow();

        assertThat(tokenInfo.getKeyInfo()).hasSize(keyCount);
    }

    @Step("all keys are deleted from token {string}")
    public void allKeysAreDeletedFromToken(String friendlyName) {
        TokenInfo tokenInfo = clientHolder.get(PRIMARY).getTokens().stream()
                .filter(t -> friendlyName.equals(t.getFriendlyName()))
                .findFirst().orElseThrow();

        tokenInfo.getKeyInfo()
                .forEach(keyInfo -> clientHolder.get(PRIMARY).deleteKey(keyInfo.getId(), true));
    }

    @Step("token {string} token is not saved to configuration on {nodeType} node")
    public void tokenTokenIsNotSavedToConfiguration(String tokenFriendlyName, NodeProperties.NodeType nodeType) {
        TokenInfo tokenInfo = getTokenInfoByFriendlyName(tokenFriendlyName, nodeType);
        assertThat(tokenInfo.isSavedToConfiguration()).isFalse();
    }

    @Step("Waiting {int} seconds for auto-login to take effect")
    public void waitForAutologinToTakeEffect(int seconds) throws InterruptedException {
        TimeUnit.SECONDS.sleep(seconds);
    }
}
