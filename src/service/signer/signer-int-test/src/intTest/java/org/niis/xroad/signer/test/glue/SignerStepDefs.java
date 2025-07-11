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

import ee.ria.xroad.common.CodedException;
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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.encoders.Base64;
import org.junit.jupiter.api.Assertions;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;
import org.niis.xroad.signer.test.SignerClientHolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.nio.file.Path;
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
import java.util.stream.Collectors;

import static ee.ria.xroad.common.SystemProperties.getGrpcInternalHost;
import static ee.ria.xroad.common.SystemProperties.getGrpcSignerPort;
import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA256;
import static ee.ria.xroad.common.crypto.identifier.SignAlgorithm.SHA256_WITH_ECDSA;
import static ee.ria.xroad.common.crypto.identifier.SignAlgorithm.SHA256_WITH_RSA;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertSha1HexHash;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;


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
        var tokens = signerRpcClient.getTokens();
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

    @Step("signer is initialized with pin {string}")
    public void signerIsInitializedWithPin(String pin) {
        signerRpcClient.initSoftwareToken(pin.toCharArray());
    }

    @Step("token {string} is not active")
    public void tokenIsNotActive(String friendlyName) throws Exception {
        final TokenInfo tokenInfo = getTokenInfoByFriendlyName(friendlyName);

        Assertions.assertFalse(tokenInfo.isActive());
    }

    @Step("token {string} status is {string}")
    public void assertTokenStatus(String friendlyName, String status) throws Exception {
        final TokenInfo token = getTokenInfoByFriendlyName(friendlyName);
        assertThat(token.getStatus()).isEqualTo(TokenStatusInfo.valueOf(status));
    }

    @Step("tokens list contains token {string}")
    public void tokensListContainsToken(String friendlyName) {
        var tokens = signerRpcClient.getTokens();

        final TokenInfo tokenInfo = tokens.stream()
                .filter(token -> token.getFriendlyName().equals(friendlyName))
                .findFirst()
                .orElseThrow();
        assertThat(tokenInfo).isNotNull();
    }

    @Step("tokens list contains token with label {string}")
    public void tokensListContainsTokenLabel(String label) {
        var tokens = signerRpcClient.getTokens();
        testReportService.attachJson("Tokens", tokens);
        final TokenInfo tokenInfo = tokens.stream()
                .filter(token -> token.getLabel().equals(label))
                .findFirst()
                .orElseThrow();
        assertThat(tokenInfo).isNotNull();
    }


    @Step("token {string} is logged in with pin {string}")
    public void tokenIsActivatedWithPin(String friendlyName, String pin) {
        var tokenId = getTokenFriendlyNameToIdMapping().get(friendlyName);
        signerRpcClient.activateToken(tokenId, pin.toCharArray());
    }

    @Step("token {string} is logged out")
    public void tokenIsLoggedOut(String friendlyName) {
        var tokenId = getTokenFriendlyNameToIdMapping().get(friendlyName);
        signerRpcClient.deactivateToken(tokenId);
    }

    @Step("token {string} is active")
    public void tokenIsActive(String friendlyName) throws Exception {
        var tokenInfo = getTokenInfoByFriendlyName(friendlyName);
        assertThat(tokenInfo.isActive()).isTrue();
    }

    @Step("token {string} pin is updated from {string} to {string}")
    public void tokenPinIsUpdatedFromTo(String friendlyName, String oldPin, String newPin) {
        var tokenId = getTokenFriendlyNameToIdMapping().get(friendlyName);
        signerRpcClient.updateTokenPin(tokenId, oldPin.toCharArray(), newPin.toCharArray());
    }

    @Step("token {string} pin is update from {string} to {string} fails with an error")
    public void tokenPinIsUpdatedFromToError(String friendlyName, String oldPin, String newPin) {
        var tokenId = getTokenFriendlyNameToIdMapping().get(friendlyName);
        try {
            signerRpcClient.updateTokenPin(tokenId, oldPin.toCharArray(), newPin.toCharArray());
        } catch (CodedException codedException) {
            assertException("Signer.InternalError", "",
                    "Signer.InternalError: Software token not found", codedException);
        }
    }

    @Step("name {string} is set for token with id {string}")
    public void nameIsSetForToken(String name, String tokenId) {
        signerRpcClient.setTokenFriendlyName(tokenId, name);
    }

    @Step("friendly name {string} is set for token with label {string}")
    public void nameIsSetForTokenLabel(String name, String label) {
        var tokenId = tokenLabelToIdMapping.get(label);
        signerRpcClient.setTokenFriendlyName(tokenId, name);
    }

    @Step("token with id {string} name is {string}")
    public void tokenNameIs(String tokenId, String name) {
        assertThat(signerRpcClient.getToken(tokenId).getFriendlyName()).isEqualTo(name);
    }

    @Step("token with label {string} name is {string}")
    public void tokenNameByLabelIs(String label, String name) {
        var tokenId = tokenLabelToIdMapping.get(label);
        assertThat(signerRpcClient.getToken(tokenId).getFriendlyName()).isEqualTo(name);
    }

    @Step("new {algorithm} key {string} generated for token {string}")
    public void newKeyGeneratedForToken(KeyAlgorithm algorithm, String keyLabel, String friendlyName) {
        var tokenId = getTokenFriendlyNameToIdMapping().get(friendlyName);
        final KeyInfo keyInfo = signerRpcClient.generateKey(tokenId, keyLabel, algorithm);
        testReportService.attachJson("keyInfo", keyInfo);
        this.scenarioKeyId = keyInfo.getId();
    }

    @Step("name {string} is set for generated key")
    public void nameIsSetForGeneratedKey(String keyFriendlyName) {
        signerRpcClient.setKeyFriendlyName(this.scenarioKeyId, keyFriendlyName);
    }

    @Step("token {string} has exact keys {string}")
    public void tokenHasKeys(String friendlyName, String keyNames) throws Exception {
        final List<String> keys = Arrays.asList(keyNames.split(","));
        final TokenInfo token = getTokenInfoByFriendlyName(friendlyName);

        assertThat(token.getKeyInfo().size()).isEqualTo(keys.size());

        final List<String> tokenKeyNames = token.getKeyInfo().stream()
                .map(KeyInfo::getFriendlyName)
                .collect(Collectors.toList());

        assertThat(tokenKeyNames).containsExactlyInAnyOrderElementsOf(keys);
    }

    @Step("key {string} is deleted from token {string}")
    public void keyIsDeletedFromToken(String keyName, String friendlyName) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyName);
        signerRpcClient.deleteKey(key.getId(), true);
    }


    @Step("Certificate is imported for client {string}")
    public void certificateIsImported(String client) {
        scenarioKeyId = signerRpcClient.importCert(scenarioCert, CertificateInfo.STATUS_REGISTERED, getClientId(client));
    }

    @Step("Wrong Certificate is not imported for client {string}")
    public void certImportFails(String client) throws Exception {
        byte[] certBytes = fileToBytes("src/intTest/resources/cert-01.pem");
        try {
            signerRpcClient.importCert(certBytes, CertificateInfo.STATUS_REGISTERED, getClientId(client));
        } catch (CodedException codedException) {
            assertException("Signer.KeyNotFound", "key_not_found_for_certificate",
                    "Signer.KeyNotFound: Could not find key that has public key that matches the public key of certificate",
                    codedException);
        }
    }

    private byte[] fileToBytes(String fileName) throws Exception {
        try (FileInputStream in = new FileInputStream(fileName)) {
            return IOUtils.toByteArray(in);
        }
    }

    @Step("self signed cert generated for token {string} key {string}, client {string}")
    public void selfSignedCertGeneratedForTokenKeyForClient(String friendlyName, String keyName, String client) throws Exception {
        final KeyInfo keyInToken = findKeyInToken(friendlyName, keyName);

        scenarioCert = signerRpcClient.generateSelfSignedCert(keyInToken.getId(), getClientId(client), KeyUsageInfo.SIGNING,
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
        } catch (CodedException codedException) {
            assertException("Signer.WrongCertUsage", "auth_cert_under_softtoken",
                    "Signer.WrongCertUsage: Authentication certificate requests can only be created under software tokens", codedException);
        }
    }

    @Step("the {} cert request is generated for token {string} key {string} for client {string}")
    public void certRequestIsGeneratedForTokenKey(String keyUsage, String friendlyName, String keyName, String client) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyName);
        final ClientId.Conf clientId = getClientId(client);
        SignerRpcClient.GeneratedCertRequestInfo csrInfo = signerRpcClient.generateCertRequest(key.getId(), clientId,
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
        scenarioKeyId = signerRpcClient.importCert(certFileBytes, initialStatus, clientId);
        X509Certificate x509Certificate = readCertificate(certFileBytes);
        scenarioCert = x509Certificate.getEncoded();
        certInfo = signerRpcClient.getCertForHash(calculateCertHexHash(scenarioCert));
    }

    @Step("cert request is regenerated")
    public void certRequestIsRegenerated() {
        signerRpcClient.regenerateCertRequest(this.scenarioCsrId, CertificateRequestFormat.DER);
    }

    @Step("token {string} key {string} has {int} certificates")
    public void tokenKeyHasCertificates(String friendlyName, String keyName, int certCount) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyName);

        assertThat(key.getCerts()).hasSize(certCount);
    }

    @Step("sign mechanism for token {string} key {string} is not null")
    public void signMechanismForTokenKeyIsNotNull(String friendlyName, String keyName) throws Exception {
        final KeyInfo keyInToken = findKeyInToken(friendlyName, keyName);
        final var signMechanism = signerRpcClient.getSignMechanism(keyInToken.getId());

        assertThat(signMechanism.name()).isNotBlank();
    }

    @Step("member {string} has {int} certificate")
    public void memberHasCertificate(String memberId, int certCount) {
        final List<CertificateInfo> memberCerts = signerRpcClient.getMemberCerts(getClientId(memberId));
        assertThat(memberCerts).hasSize(certCount);
    }

    @Step("check token {string} key {string} batch signing enabled")
    public void checkTokenBatchSigningEnabled(String friendlyName, String keyname) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyname);

        assertThat(signerRpcClient.isTokenBatchSigningEnabled(key.getId())).isNotNull();
    }

    @Step("cert request can be deleted")
    public void certRequestCanBeDeleted() {
        signerRpcClient.deleteCertRequest(this.scenarioCsrId);
    }

    @Step("certificate info can be retrieved by cert hash")
    public void certificateInfoCanBeRetrievedByHash() {
        final CertificateInfo certInfoResponse = signerRpcClient.getCertForHash(this.certHash);
        assertThat(certInfoResponse).isNotNull();
        this.certInfo = certInfoResponse;
    }

    @Step("keyId can be retrieved by cert hash")
    public void keyidCanBeRetrievedByCertHash() {
        final SignerRpcClient.KeyIdInfo keyIdForCertHash = signerRpcClient.getKeyIdForCertHash(this.certHash);
        assertThat(keyIdForCertHash).isNotNull();
    }

    @Step("token and keyId can be retrieved by cert hash")
    public void tokenAndKeyIdCanBeRetrievedByCertHash() {
        final TokenInfoAndKeyId tokenAndKeyIdForCertHash = signerRpcClient.getTokenAndKeyIdForCertHash(this.certHash);
        assertThat(tokenAndKeyIdForCertHash).isNotNull();
    }

    @Step("token and key can be retrieved by cert request")
    public void tokenAndKeyCanBeRetrievedByCertRequest() {
        final TokenInfoAndKeyId tokenAndKeyIdForCertRequestId = signerRpcClient.getTokenAndKeyIdForCertRequestId(this.scenarioCsrId);
        assertThat(tokenAndKeyIdForCertRequestId).isNotNull();
    }

    @Step("token info can be retrieved by key id")
    public void tokenInfoCanBeRetrievedByKeyId() {
        final TokenInfo tokenForKeyId = signerRpcClient.getTokenForKeyId(this.scenarioKeyId);
        testReportService.attachJson("tokenInfo", tokenForKeyId);
        assertThat(tokenForKeyId).isNotNull();
    }

    @Step("digest can be signed using key {string} from token {string}")
    public void digestCanBeSignedUsingKeyFromToken(String keyName, String friendlyName) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyName);

        var digest = format("%s-%d", randomUUID(), System.currentTimeMillis());

        var signAlgorithm = switch (SignMechanism.valueOf(key.getSignMechanismName()).keyAlgorithm()) {
            case RSA -> SHA256_WITH_RSA;
            case EC -> SHA256_WITH_ECDSA;
        };

        signerRpcClient.sign(key.getId(), signAlgorithm, calculateDigest(SHA256, digest.getBytes(UTF_8)));
    }

    @Step("certificate can be deactivated")
    public void certificateCanBeDeactivated() {
        signerRpcClient.deactivateCert(this.certInfo.getId());
    }

    @Step("certificate can be activated")
    public void certificateCanBeActivated() {
        signerRpcClient.activateCert(this.certInfo.getId());
    }

    @Step("certificate can be deleted")
    public void certificateCanBeDeleted() {
        signerRpcClient.deleteCert(this.certInfo.getId());
    }

    @Step("certificate status can be changed to {string}")
    public void certificateStatusCanBeChangedTo(String status) {
        signerRpcClient.setCertStatus(this.certInfo.getId(), status);
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

        final byte[] bytes = signerRpcClient.signCertificate(key.getId(), signAlgorithm, "CN=CS", publicKey);
        assertThat(bytes).isNotEmpty();
    }


    @Step("Digest is signed using key {string} from token {string}")
    public void sign(String keyName, String friendlyName) throws Exception {

        final KeyInfo key = findKeyInToken(friendlyName, keyName);

        var digest = format("%s-%d", randomUUID(), System.currentTimeMillis());

        var signAlgorithm = switch (SignMechanism.valueOf(key.getSignMechanismName()).keyAlgorithm()) {
            case RSA -> SHA256_WITH_RSA;
            case EC -> SHA256_WITH_ECDSA;
        };

        byte[] bytes = signerRpcClient.sign(key.getId(), signAlgorithm, calculateDigest(SHA256, digest.getBytes(UTF_8)));
        assertThat(bytes).isNotEmpty();
    }

    @Step("auth key for Security Server {string} is retrieved")
    public void getAuthKey(String securityServerId) {
        var authKeyInfo = signerRpcClient.getAuthKey(getSecurityServerId(securityServerId));
        testReportService.attachJson("authKeyInfo", authKeyInfo);
        assertThat(authKeyInfo).isNotNull();
    }

    @Step("auth key retrieval for Security Server {string} fails when no active token is found")
    public void getAuthKeyFail(String securityServerId) {
        try {
            signerRpcClient.getAuthKey(getSecurityServerId(securityServerId));
            Assertions.fail("Exception expected");
        } catch (CodedException codedException) {
            var errorServerId = securityServerId.replace(":", "/");
            assertException("Signer.KeyNotFound", "auth_key_not_found_for_server",
                    format("Signer.KeyNotFound: Could not find active authentication key for security server 'SERVER:%s'", errorServerId),
                    codedException);
        }
    }

    @Step("Set token name fails with TokenNotFound exception when token does not exist")
    public void setTokenNameFail() {
        String tokenId = randomUUID().toString();
        try {
            signerRpcClient.setTokenFriendlyName(tokenId, randomUUID().toString());
            Assertions.fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.TokenNotFound", "token_not_found",
                    "Signer.TokenNotFound: Token '" + tokenId + "' not found", codedException);
        }
    }

    @Step("Deleting not existing certificate from token fails")
    public void failOnDeleteCert() {
        String cerId = randomUUID().toString();
        try {
            signerRpcClient.deleteCert(cerId);
            Assertions.fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.CertNotFound", "cert_with_id_not_found",
                    "Signer.CertNotFound: Certificate with id '" + cerId + "' not found", codedException);
        }
    }

    @Step("Retrieving token info by not existing key fails")
    public void retrievingTokenInfoCanByNotExistingKeyFails() {
        String keyId = randomUUID().toString();
        try {
            signerRpcClient.getTokenForKeyId(keyId);
            Assertions.fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.KeyNotFound", "key_not_found",
                    "Signer.KeyNotFound: Key '" + keyId + "' not found", codedException);
        }
    }

    @Step("Deleting not existing certRequest fails")
    public void deletingCertRequestFails() {
        String csrId = randomUUID().toString();
        try {
            signerRpcClient.deleteCertRequest(csrId);
            Assertions.fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.CsrNotFound", "csr_not_found",
                    "Signer.CsrNotFound: Certificate request '" + csrId + "' not found", codedException);
        }
    }

    @Step("Signing with unknown key fails")
    public void signKeyFail() {
        String keyId = randomUUID().toString();
        try {
            signerRpcClient.sign(keyId, SignAlgorithm.ofName(randomUUID().toString()), new byte[0]);
            Assertions.fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.KeyNotFound", "key_not_found",
                    "Signer.KeyNotFound: Key '" + keyId + "' not found", codedException);
        }
    }

    @Step("Signing with unknown algorithm fails using key {string} from token {string}")
    public void signAlgorithmFail(String keyName, String friendlyName) throws Exception {
        try {
            final KeyInfo key = findKeyInToken(friendlyName, keyName);
            signerRpcClient.sign(key.getId(),
                    SignAlgorithm.ofName("NOT-ALGORITHM-ID"),
                    calculateDigest(SHA256, "digest".getBytes(UTF_8)));

            Assertions.fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.CannotSign.InternalError",
                    "",
                    "Signer.CannotSign.InternalError: Unknown sign mechanism of signature algorithm: uSA[name=NOT-ALGORITHM-ID, uri=null]",
                    codedException);
        }
    }

    @Step("Getting key by not existing cert hash fails")
    public void getKeyIdByHashFail() {
        String hash = randomUUID().toString();
        try {
            signerRpcClient.getKeyIdForCertHash(hash);
            Assertions.fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.CertNotFound", "certificate_with_hash_not_found",
                    "Signer.CertNotFound: Certificate with hash '" + hash + "' not found", codedException);
        }
    }

    @Step("Not existing certificate can not be activated")
    public void notExistingCertActivateFail() {
        String certId = randomUUID().toString();
        try {
            signerRpcClient.activateCert(certId);
            Assertions.fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.CertNotFound", "cert_with_id_not_found",
                    "Signer.CertNotFound: Certificate with id '" + certId + "' not found", codedException);
        }
    }

    @Step("Member signing info for client {string} fails if not suitable certificates are found")
    public void getMemberSigningInfoFail(String client) {
        try {
            signerRpcClient.getMemberSigningInfo(getClientId(client));
            Assertions.fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.InternalError", "member_has_no_suitable_certs",
                    "Signer.InternalError: Member 'MEMBER:DEV/test/member-1' has no suitable certificates", codedException);
        }
    }

    @Step("Member signing info for client {string} is retrieved")
    public void getMemberSigningInfo(String client) {
        var memberInfo = signerRpcClient.getMemberSigningInfo(getClientId(client));
        testReportService.attachJson("MemberSigningInfo", memberInfo);
    }

    @Step("HSM is operational")
    public void hsmIsNotOperational() {
        Assertions.assertTrue(signerRpcClient.isHSMOperational());
    }

    private void assertException(String faultCode, String translationCode, String message, CodedException codedException) {
        Assertions.assertEquals(faultCode, codedException.getFaultCode());
        Assertions.assertEquals(translationCode, codedException.getTranslationCode());
        Assertions.assertEquals(message, codedException.getMessage());
    }

    @Step("ocsp responses are set to REVOKED")
    public void ocspResponsesAreSetUnknown() throws Exception {
        CertificateStatus certificateStatus = new RevokedStatus(Date.from(Instant.parse("2022-01-01T00:00:00Z")));
        X509Certificate subject = readCertificate(certInfo.getCertificateBytes());
        String caHomePath = "./build/resources/intTest/META-INF/ca-container/files/home/ca/CA";
        X509Certificate caCert =
                readCertificate(readAllBytes(Path.of(caHomePath + "/certs/ca.cert.pem")));
        X509Certificate ocspCert =
                readCertificate(readAllBytes(Path.of(caHomePath + "/certs/ocsp.cert.pem")));
        try (FileReader keyReader = new FileReader(caHomePath + "/private/ocsp.key.pem")) {

            PEMParser pemParser = new PEMParser(keyReader);
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(pemParser.readObject());

            PrivateKey ocspPrivateKey = converter.getPrivateKey(privateKeyInfo);
            final OCSPResp ocspResponse = OcspTestUtils.createOCSPResponse(subject, caCert,
                    ocspCert, ocspPrivateKey, certificateStatus);

            signerRpcClient.setOcspResponses(new String[]{calculateCertSha1HexHash(subject)},
                    new String[]{Base64.toBase64String(ocspResponse.getEncoded())});
        }
    }

    @Step("certificate activation fails with ocsp verification")
    public void certificateActivationFailsWithOcspVerification() {
        try {
            signerRpcClient.activateCert(this.certInfo.getId());
            Assertions.fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.InternalError",
                    "",
                    "Signer.InternalError: Failed to verify OCSP responses for certificate. Error: InvalidCertPath.CertValidation: OCSP "
                          + "response indicates certificate status is REVOKED (date: 2022-01-01 00:00:00)",
                    codedException);
        }
    }


    @Step("ocsp responses are set")
    public void ocspResponsesAreSet() throws Exception {
        X509Certificate subject = TestCertUtil.getConsumer().certChain[0];
        final OCSPResp ocspResponse = OcspTestUtils.createOCSPResponse(subject, TestCertUtil.getCaCert(),
                TestCertUtil.getOcspSigner().certChain[0],
                TestCertUtil.getOcspSigner().key, CertificateStatus.GOOD);

        signerRpcClient.setOcspResponses(new String[]{calculateCertSha1HexHash(subject)},
                new String[]{Base64.toBase64String(ocspResponse.getEncoded())});
    }

    @Step("ocsp responses can be retrieved")
    public void ocspResponsesCanBeRetrieved() throws Exception {
        X509Certificate subject = TestCertUtil.getConsumer().certChain[0];
        final String hash = calculateCertSha1HexHash(subject);

        final String[] ocspResponses = signerRpcClient.getOcspResponses(new String[]{hash});
        assertThat(ocspResponses[0]).isNotNull();
    }

    @Step("null ocsp response is returned for unknown certificate")
    public void emptyOcspResponseIsReturnedForUnknownCertificate() throws Exception {
        final String[] ocspResponses = signerRpcClient
                .getOcspResponses(new String[]{calculateCertSha1HexHash("not a cert".getBytes())});
        assertThat(ocspResponses).hasSize(1);
        assertThat(ocspResponses[0]).isNull();
    }

    @Step("signer client initialized with default settings")
    public void signerClientInitializedWithDefaultSettings() throws Exception {
        signerClientReinitializedWithTimeoutMilliseconds(60000);
    }

    @Step("signer client initialized with timeout {int} milliseconds")
    public void signerClientReinitializedWithTimeoutMilliseconds(int timeoutMillis) throws Exception {
        if (signerRpcClient != null) {
            signerRpcClient.destroy();
        }
        signerRpcClient = new SignerRpcClient();
        signerRpcClient.init(getGrpcInternalHost(), getGrpcSignerPort(), timeoutMillis);
        SignerClientHolder.set(signerRpcClient);
    }

    @Step("getTokens fails with timeout exception")
    public void signerGetTokensFailsWithTimeoutException() {
        assertThatThrownBy(signerRpcClient::getTokens)
                .isInstanceOf(SignerException.class)
                .hasMessageContaining("Signer.NetworkError: gRPC client timed out.");
    }

    @ParameterType("RSA|EC")
    public KeyAlgorithm algorithm(String value) {
        return KeyAlgorithm.valueOf(value);
    }
}
