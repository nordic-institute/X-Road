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
package org.niis.xroad.signer.client;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.PasswordStore;

import com.google.protobuf.ByteString;
import io.grpc.Channel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.client.RpcClient;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.common.rpc.mapper.SecurityServerIdMapper;
import org.niis.xroad.signer.api.dto.AuthKeyInfo;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.proto.ActivateCertReq;
import org.niis.xroad.signer.proto.ActivateTokenReq;
import org.niis.xroad.signer.proto.Algorithm;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.proto.CertificateServiceGrpc;
import org.niis.xroad.signer.proto.DeleteCertReq;
import org.niis.xroad.signer.proto.DeleteCertRequestReq;
import org.niis.xroad.signer.proto.DeleteKeyReq;
import org.niis.xroad.signer.proto.DeleteTokenReq;
import org.niis.xroad.signer.proto.GenerateCertRequestReq;
import org.niis.xroad.signer.proto.GenerateKeyReq;
import org.niis.xroad.signer.proto.GenerateSelfSignedCertReq;
import org.niis.xroad.signer.proto.GetAuthKeyReq;
import org.niis.xroad.signer.proto.GetCertificateInfoForHashReq;
import org.niis.xroad.signer.proto.GetKeyIdForCertHashReq;
import org.niis.xroad.signer.proto.GetMemberCertsReq;
import org.niis.xroad.signer.proto.GetMemberSigningInfoReq;
import org.niis.xroad.signer.proto.GetOcspResponsesReq;
import org.niis.xroad.signer.proto.GetSignMechanismReq;
import org.niis.xroad.signer.proto.GetTokenBatchSigningEnabledReq;
import org.niis.xroad.signer.proto.GetTokenByCertHashReq;
import org.niis.xroad.signer.proto.GetTokenByCertRequestIdReq;
import org.niis.xroad.signer.proto.GetTokenByIdReq;
import org.niis.xroad.signer.proto.GetTokenByKeyIdReq;
import org.niis.xroad.signer.proto.ImportCertReq;
import org.niis.xroad.signer.proto.InitSoftwareTokenReq;
import org.niis.xroad.signer.proto.KeyServiceGrpc;
import org.niis.xroad.signer.proto.OcspServiceGrpc;
import org.niis.xroad.signer.proto.RegenerateCertRequestReq;
import org.niis.xroad.signer.proto.SetCertStatusReq;
import org.niis.xroad.signer.proto.SetKeyFriendlyNameReq;
import org.niis.xroad.signer.proto.SetNextPlannedRenewalReq;
import org.niis.xroad.signer.proto.SetOcspResponsesReq;
import org.niis.xroad.signer.proto.SetRenewalErrorReq;
import org.niis.xroad.signer.proto.SetRenewedCertHashReq;
import org.niis.xroad.signer.proto.SetTokenFriendlyNameReq;
import org.niis.xroad.signer.proto.SignCertificateReq;
import org.niis.xroad.signer.proto.SignReq;
import org.niis.xroad.signer.proto.TokenServiceGrpc;
import org.niis.xroad.signer.proto.UpdateSoftwareTokenPinReq;
import org.niis.xroad.signer.protocol.dto.Empty;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.SystemProperties.getGrpcInternalHost;
import static ee.ria.xroad.common.SystemProperties.getGrpcSignerPort;
import static ee.ria.xroad.common.SystemProperties.getSignerClientTimeout;
import static ee.ria.xroad.common.util.CertUtils.isAuthCert;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;

/**
 * Responsible for managing cryptographic tokens (smartcards, HSMs, etc.) through the signer.
 */
@Slf4j
public final class SignerRpcClient {
    public static final String SSL_TOKEN_ID = "0";

    private RpcClient<SignerRpcExecutionContext> client;

    @PostConstruct
    public void init() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        init(getGrpcInternalHost(), getGrpcSignerPort(), getSignerClientTimeout());
    }

    public void init(String host, int port, int clientTimeoutMillis)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        client = RpcClient.newClient(host, port, clientTimeoutMillis, SignerRpcExecutionContext::new);
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.shutdown();
        }
    }

    private void tryToRun(Action action) {
        try {
            action.run();
        } catch (XrdRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    private <R, T> T tryToRun(ActionWithResult<R> action, Function<R, T> mapper) {
        return tryToRun(() -> mapper.apply(action.run()));
    }

    private <T> T tryToRun(ActionWithResult<T> action) {
        try {
            return action.run();
        } catch (XrdRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    /**
     * Initialize the software token with the given password.
     *
     * @param password software token password
     */
    public void initSoftwareToken(char[] password) {
        log.trace("Initializing software token");
        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingTokenService()
                        .initSoftwareToken(InitSoftwareTokenReq.newBuilder()
                                .setPin(new String(password))
                                .build()))
        );
    }

    /**
     * Gets information about all configured tokens.
     *
     * @return a List of TokenInfo objects
     */
    public List<TokenInfo> getTokens() {
        return tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingTokenService().listTokens(Empty.newBuilder().build()))
                        .getTokensList().stream()
                        .map(TokenInfo::new)
                        .toList()
        );
    }

    /**
     * Gets information about the token with the specified token ID.
     *
     * @param tokenId ID of the token
     * @return TokenInfo
     */
    public TokenInfo getToken(String tokenId) {

        return tryToRun(
                () -> client.execute(ctx -> new TokenInfo(ctx.getBlockingTokenService()
                        .getTokenById(GetTokenByIdReq.newBuilder()
                                .setTokenId(tokenId)
                                .build())))
        );
    }

    /**
     * Activates the token with the given ID using the provided password.
     *
     * @param tokenId  ID of the token
     * @param password token password
     */
    public void activateToken(String tokenId, char[] password) {
        tryToRun(() -> internalActivateToken(tokenId, password));
    }

    @ArchUnitSuppressed("NoVanillaExceptions")
    private void internalActivateToken(String tokenId, char[] password) throws IOException {
        log.trace("Activating token '{}'", tokenId);

        PasswordStore.storePassword(tokenId, password);

        client.execute(ctx -> ctx.getBlockingTokenService()
                .activateToken(ActivateTokenReq.newBuilder()
                        .setTokenId(tokenId)
                        .setActivate(true)
                        .build()));
    }

    /**
     * Updates the token pin with the provided new one
     *
     * @param tokenId ID of the token
     * @param oldPin  the old (current) pin of the token
     * @param newPin  the new pin
     * @throws XrdRuntimeException if any errors occur
     */
    public void updateTokenPin(String tokenId, char[] oldPin, char[] newPin) {
        log.trace("Updating token pin '{}'", tokenId);

        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingTokenService()
                        .updateSoftwareTokenPin(UpdateSoftwareTokenPinReq.newBuilder()
                                .setTokenId(tokenId)
                                .setOldPin(new String(oldPin))
                                .setNewPin(new String(newPin))
                                .build()))
        );
    }

    /**
     * Deactivates the token with the given ID.
     *
     * @param tokenId ID of the token
     */
    public void deactivateToken(String tokenId) {
        tryToRun(() -> internalDeactivateToken(tokenId));
    }

    @ArchUnitSuppressed("NoVanillaExceptions")
    private void internalDeactivateToken(String tokenId) throws IOException {
        log.trace("Deactivating token '{}'", tokenId);

        PasswordStore.storePassword(tokenId, null);

        client.execute(ctx -> ctx.getBlockingTokenService()
                .activateToken(ActivateTokenReq.newBuilder()
                        .setTokenId(tokenId)
                        .setActivate(false)
                        .build()));
    }

    /**
     * Delete the token with the given ID.
     *
     * @param tokenId ID of the token
     */
    public void deleteToken(String tokenId) {
        log.trace("Delete token '{}'", tokenId);

        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingTokenService()
                        .deleteToken(DeleteTokenReq.newBuilder()
                                .setTokenId(tokenId)
                                .build()))
        );
    }

    /**
     * Sets the friendly name of the token with the given ID.
     *
     * @param tokenId      ID of the token
     * @param friendlyName new friendly name of the token
     */
    public void setTokenFriendlyName(String tokenId, String friendlyName) {
        log.trace("Setting friendly name '{}' for token '{}'", friendlyName, tokenId);

        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingTokenService()
                        .setTokenFriendlyName(SetTokenFriendlyNameReq.newBuilder()
                                .setTokenId(tokenId)
                                .setFriendlyName(friendlyName)
                                .build()))
        );
    }

    /**
     * Sets the friendly name of the key with the given ID.
     *
     * @param keyId        ID of the key
     * @param friendlyName new friendly name of the key
     */
    public void setKeyFriendlyName(String keyId, String friendlyName) {
        log.trace("Setting friendly name '{}' for key '{}'", friendlyName, keyId);

        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingKeyService()
                        .setKeyFriendlyName(SetKeyFriendlyNameReq.newBuilder()
                                .setKeyId(keyId)
                                .setFriendlyName(friendlyName)
                                .build()))
        );
    }

    /**
     * Generate a new key for the token with the given ID.
     *
     * @param tokenId   ID of the token
     * @param keyLabel  label of the key
     * @param algorithm algorithm to use, RSA or EC
     * @return generated key KeyInfo object
     */
    public KeyInfo generateKey(String tokenId, String keyLabel, KeyAlgorithm algorithm) {
        return tryToRun(() -> internalGenerateKey(tokenId, keyLabel, algorithm));
    }

    private KeyInfo internalGenerateKey(String tokenId, String keyLabel, KeyAlgorithm algorithm) {
        log.trace("Generating key for token '{}'", tokenId);

        var builder = GenerateKeyReq.newBuilder()
                .setTokenId(tokenId)
                .setKeyLabel(keyLabel);
        if (algorithm != null) {
            builder.setAlgorithm(Algorithm.valueOf(algorithm.name()));
        }

        var response = client.execute(ctx -> ctx.getBlockingKeyService().generateKey(builder.build()));

        KeyInfo keyInfo = new KeyInfo(response);

        log.trace("Received key with keyId '{}' and public key '{}'", keyInfo.getId(), keyInfo.getPublicKey());

        return keyInfo;
    }

    /**
     * Generate a self-signed certificate for the key with the given ID.
     *
     * @param keyId      ID of the key
     * @param memberId   client ID of the certificate owner
     * @param keyUsage   specifies whether the certificate is for signing or authentication
     * @param commonName common name of the certificate
     * @param notBefore  date the certificate becomes valid
     * @param notAfter   date the certificate becomes invalid
     * @return byte content of the generated certificate
     */
    public byte[] generateSelfSignedCert(String keyId, ClientId.Conf memberId, KeyUsageInfo keyUsage,
                                         String commonName, Date notBefore, Date notAfter) {
        return tryToRun(() -> internalGenerateSelfSignedCert(keyId, memberId, keyUsage, commonName, notBefore, notAfter));
    }

    private byte[] internalGenerateSelfSignedCert(String keyId, ClientId.Conf memberId, KeyUsageInfo keyUsage,
                                                  String commonName, Date notBefore, Date notAfter) {
        log.trace("Generate self-signed cert for key '{}'", keyId);

        var builder = GenerateSelfSignedCertReq.newBuilder()
                .setKeyId(keyId)
                .setCommonName(commonName)
                .setDateNotBefore(notBefore.getTime())
                .setDateNotAfter(notAfter.getTime())
                .setKeyUsage(keyUsage);

        if (memberId != null) {
            builder.setMemberId(ClientIdMapper.toDto(memberId));
        }

        var response = client.execute(ctx -> ctx.getBlockingCertificateService()
                .generateSelfSignedCert(builder.build()));

        byte[] certificateBytes = response.getCertificateBytes().toByteArray();

        log.trace("Certificate with length of {} bytes generated", certificateBytes.length);

        return certificateBytes;
    }

    /**
     * Imports the given byte array as a new certificate with the provided initial status and owner client ID.
     *
     * @param certBytes     byte content of the new certificate
     * @param initialStatus initial status of the certificate
     * @param clientId      client ID of the certificate owner
     * @return key ID of the new certificate as a String
     */
    public String importCert(byte[] certBytes, String initialStatus, ClientId.Conf clientId, boolean activate) {
        return tryToRun(() -> internalImportCert(certBytes, initialStatus, clientId, activate));
    }

    private String internalImportCert(byte[] certBytes, String initialStatus, ClientId.Conf clientId, boolean activate) {
        log.trace("Importing cert from file with length of '{}' bytes", certBytes.length);

        final ImportCertReq.Builder builder = ImportCertReq.newBuilder()
                .setCertData(ByteString.copyFrom(certBytes))
                .setInitialStatus(initialStatus)
                .setActivate(activate);
        ofNullable(clientId).map(ClientIdMapper::toDto).ifPresent(builder::setMemberId);

        var response = client.execute(ctx -> ctx.getBlockingCertificateService()
                .importCert(builder.build()));

        log.trace("Cert imported successfully, keyId received: {}", response.getKeyId());

        return response.getKeyId();
    }

    public String importCert(byte[] certBytes, String initialStatus, ClientId.Conf clientId) {
        return tryToRun(() -> {
            X509Certificate x509Certificate = readCertificate(certBytes);
            return importCert(certBytes, initialStatus, clientId, !isAuthCert(x509Certificate));
        });
    }

    /**
     * Activates the certificate with the given ID.
     *
     * @param certId ID of the certificate
     */
    public void activateCert(String certId) {
        log.trace("Activating cert '{}'", certId);

        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingCertificateService()
                        .activateCert(ActivateCertReq.newBuilder()
                                .setCertIdOrHash(certId)
                                .setActive(true)
                                .build()))
        );
    }

    /**
     * Deactivates the certificate with the given ID.
     *
     * @param certId ID of the certificate
     */
    public void deactivateCert(String certId) {
        log.trace("Deactivating cert '{}'", certId);

        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingCertificateService()
                        .activateCert(ActivateCertReq.newBuilder()
                                .setCertIdOrHash(certId)
                                .setActive(false)
                                .build()))
        );
    }

    /**
     * Generates a certificate request for the given key and with provided parameters.
     *
     * @param keyId       ID of the key
     * @param memberId    client ID of the certificate owner
     * @param keyUsage    specifies whether the certificate is for signing or authentication
     * @param subjectName subject name of the certificate
     * @param format      the format of the request
     * @return GeneratedCertRequestInfo containing details and content of the certificate request
     */
    public GeneratedCertRequestInfo generateCertRequest(String keyId,
                                                        ClientId.Conf memberId,
                                                        KeyUsageInfo keyUsage,
                                                        String subjectName,
                                                        CertificateRequestFormat format) {
        return tryToRun(
                () -> generateCertRequest(keyId, memberId, keyUsage, subjectName, null, format, null)
        );
    }

    /**
     * Generates a certificate request for the given key and with provided parameters.
     *
     * @param keyId          ID of the key
     * @param memberId       client ID of the certificate owner
     * @param keyUsage       specifies whether the certificate is for signing or authentication
     * @param subjectName    subject name of the certificate
     * @param subjectAltName subject alternative name of the certificate
     * @param format         the format of the request
     * @return GeneratedCertRequestInfo containing details and content of the certificate request
     */
    public GeneratedCertRequestInfo generateCertRequest(String keyId, ClientId.Conf memberId,
                                                        KeyUsageInfo keyUsage, String subjectName, String subjectAltName,
                                                        CertificateRequestFormat format, String certificateProfile) {
        return tryToRun(
                () -> internalGenerateCertRequest(keyId, memberId, keyUsage, subjectName, subjectAltName, format, certificateProfile)
        );
    }

    private GeneratedCertRequestInfo internalGenerateCertRequest(String keyId, ClientId.Conf memberId,
                                                                 KeyUsageInfo keyUsage, String subjectName, String subjectAltName,
                                                                 CertificateRequestFormat format, String certificateProfile) {

        var reqBuilder = GenerateCertRequestReq.newBuilder()
                .setKeyId(keyId)
                .setKeyUsage(keyUsage)
                .setSubjectName(subjectName)
                .setFormat(format);

        if (subjectAltName != null) {
            reqBuilder.setSubjectAltName(subjectAltName);
        }

        if (certificateProfile != null) {
            reqBuilder.setCertificateProfile(certificateProfile);
        }

        ofNullable(memberId)
                .map(ClientIdMapper::toDto)
                .ifPresent(reqBuilder::setMemberId);

        var response = client.execute(ctx -> ctx.getBlockingCertificateService()
                .generateCertRequest(reqBuilder.build()));

        byte[] certRequestBytes = response.getCertRequest().toByteArray();

        log.trace("Cert request with length of {} bytes generated", certRequestBytes.length);

        return new GeneratedCertRequestInfo(
                response.getCertReqId(),
                certRequestBytes,
                response.getFormat(),
                memberId,
                keyUsage);
    }

    /**
     * Regenerates a certificate request for the given csr id
     *
     * @param certRequestId csr ID
     * @param format        the format of the request
     * @return GeneratedCertRequestInfo containing details and content of the certificate request
     */
    public GeneratedCertRequestInfo regenerateCertRequest(String certRequestId,
                                                          CertificateRequestFormat format) {
        return tryToRun(() -> internalRegenerateCertRequest(certRequestId, format));
    }

    private GeneratedCertRequestInfo internalRegenerateCertRequest(String certRequestId,
                                                                   CertificateRequestFormat format) {

        var response = client.execute(ctx -> ctx.getBlockingCertificateService()
                .regenerateCertRequest(RegenerateCertRequestReq.newBuilder()
                        .setCertRequestId(certRequestId)
                        .setFormat(format)
                        .build()));

        log.trace("Cert request with length of {} bytes generated", response.getCertRequest().size());

        return new GeneratedCertRequestInfo(
                response.getCertReqId(),
                response.getCertRequest().toByteArray(),
                response.getFormat(),
                response.hasMemberId() ? ClientIdMapper.fromDto(response.getMemberId()) : null,
                response.getKeyUsage());
    }


    /**
     * Delete the certificate request with the given ID.
     *
     * @param certRequestId ID of the certificate request
     */
    public void deleteCertRequest(String certRequestId) {
        log.trace("Deleting cert request '{}'", certRequestId);

        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingCertificateService()
                        .deleteCertRequest(DeleteCertRequestReq.newBuilder()
                                .setCertRequestId(certRequestId)
                                .build()))
        );
    }

    /**
     * Delete the certificate with the given ID.
     *
     * @param certId ID of the certificate
     */
    public void deleteCert(String certId) {
        log.trace("Deleting cert '{}'", certId);

        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingCertificateService()
                        .deleteCert(DeleteCertReq.newBuilder()
                                .setCertId(certId)
                                .build()))
        );
    }

    /**
     * Delete the key with the given ID from the signer database. Optionally,
     * deletes it from the token as well.
     *
     * @param keyId           ID of the certificate request
     * @param deleteFromToken whether the key should be deleted from the token
     */
    public void deleteKey(String keyId, boolean deleteFromToken) {
        log.trace("Deleting key '{}', from token = {}", keyId, deleteFromToken);

        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingKeyService()
                        .deleteKey(DeleteKeyReq.newBuilder()
                                .setKeyId(keyId)
                                .setDeleteFromDevice(deleteFromToken)
                                .build()))
        );
    }

    /**
     * Sets the status of the certificate with the given ID.
     *
     * @param certId ID of the certificate
     * @param status new status of the certificate
     */
    public void setCertStatus(String certId, String status) {
        log.trace("Setting cert ('{}') status to '{}'", certId, status);

        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingCertificateService()
                        .setCertStatus(SetCertStatusReq.newBuilder()
                                .setCertId(certId)
                                .setStatus(status)
                                .build()))
        );
    }

    /**
     * Sets the hash of the renewed certificate with the given old cert ID.
     *
     * @param certId ID of the old certificate
     * @param hash   new hash of the renewed certificate
     */
    public void setRenewedCertHash(String certId, String hash) {
        log.trace("Setting cert ('{}') renewed cert hash to '{}'", certId, hash);

        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingCertificateService()
                        .setRenewedCertHash(SetRenewedCertHashReq.newBuilder()
                                .setCertId(certId)
                                .setHash(hash)
                                .build()))
        );
    }

    /**
     * Sets the error of the certificate renewal process.
     *
     * @param certId       ID of the certificate to be renewed
     * @param errorMessage message of the error that was thrown when trying to renew the given certificate
     */
    public void setRenewalError(String certId, String errorMessage) {
        log.trace("Setting cert ('{}') renewal error to '{}'", certId, errorMessage);

        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingCertificateService()
                        .setRenewalError(SetRenewalErrorReq.newBuilder()
                                .setCertId(certId)
                                .setErrorMessage(errorMessage)
                                .build()))
        );
    }

    /**
     * Sets the error of the certificate renewal process.
     *
     * @param certId          ID of the certificate to be renewed
     * @param nextRenewalTime message of the error that was thrown when trying to renew the given certificate
     */
    public void setNextPlannedRenewal(String certId, Instant nextRenewalTime) {
        log.trace("Setting cert ('{}') next planned renewal time to '{}'", certId, nextRenewalTime);
        tryToRun(() -> {
            com.google.protobuf.Timestamp nextRenewalTimestamp = com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(nextRenewalTime.getEpochSecond())
                    .setNanos(nextRenewalTime.getNano())
                    .build();
            client.execute(ctx -> ctx.getBlockingCertificateService()
                    .setNextPlannedRenewal(SetNextPlannedRenewalReq.newBuilder()
                            .setCertId(certId)
                            .setNextRenewalTime(nextRenewalTimestamp)
                            .build()));
        });
    }

    /**
     * Get a cert by it's hash
     *
     * @param hash cert hash. Will be converted to lowercase, which is what signer uses internally
     * @return CertificateInfo
     */
    public CertificateInfo getCertForHash(String hash) {
        return tryToRun(() -> internalGetCertForHash(hash));
    }

    private CertificateInfo internalGetCertForHash(String hash) {
        final String finalHash = hash.toLowerCase();
        log.trace("Getting cert by hash '{}'", hash);

        var response = client.execute(ctx -> ctx.getBlockingCertificateService()
                .getCertificateInfoForHash(GetCertificateInfoForHashReq.newBuilder()
                        .setCertHash(finalHash)
                        .build()));

        log.trace("Cert with hash '{}' found", finalHash);

        return new CertificateInfo(response.getCertificateInfo());
    }

    /**
     * Get key for a given cert hash
     *
     * @param hash cert hash. Will be converted to lowercase, which is what signer uses internally
     * @return Key id and sign mechanism
     */
    public KeyIdInfo getKeyIdForCertHash(String hash) {
        return tryToRun(() -> internalGetKeyIdForCertHash(hash));
    }

    private KeyIdInfo internalGetKeyIdForCertHash(String hash) {
        final String finalHash = hash.toLowerCase();
        log.trace("Getting cert by hash '{}'", finalHash);

        var response = client.execute(ctx -> ctx.getBlockingKeyService()
                .getKeyIdForCertHash(GetKeyIdForCertHashReq.newBuilder()
                        .setCertHash(finalHash)
                        .build()));

        log.trace("Cert with hash '{}' found", finalHash);

        return new KeyIdInfo(response.getKeyId(), SignMechanism.valueOf(response.getSignMechanismName()));
    }

    /**
     * Get TokenInfoAndKeyId for a given cert hash
     *
     * @param hash cert hash. Will be converted to lowercase, which is what signer uses internally
     * @return TokenInfoAndKeyId
     */
    public TokenInfoAndKeyId getTokenAndKeyIdForCertHash(String hash) {
        return tryToRun(() -> internalGetTokenAndKeyIdForCertHash(hash));
    }

    private TokenInfoAndKeyId internalGetTokenAndKeyIdForCertHash(String hash) {
        String hashLowercase = hash.toLowerCase();
        log.trace("Getting token and key id by cert hash '{}'", hashLowercase);

        var response = client.execute(ctx -> ctx.getBlockingTokenService()
                .getTokenAndKeyIdByCertHash(GetTokenByCertHashReq.newBuilder()
                        .setCertHash(hashLowercase)
                        .build()));
        log.trace("Token and key id with hash '{}' found", hashLowercase);

        return new TokenInfoAndKeyId(new TokenInfo(response.getTokenInfo()), response.getKeyId());
    }

    /**
     * Get OCSP responses for certs with given hashes. Hashes are converted to lowercase
     *
     * @param certHashes cert hashes to find OCSP responses for
     * @return base64 encoded OCSP responses. Each array item is OCSP response for
     * corresponding cert in {@code certHashes}
     */
    public String[] getOcspResponses(String[] certHashes) {
        return tryToRun(() -> internalGetOcspResponses(certHashes));
    }

    private String[] internalGetOcspResponses(String[] certHashes) {

        var response = client.execute(ctx -> ctx.getBlockingOcspService()
                .getOcspResponses(GetOcspResponsesReq.newBuilder()
                        .addAllCertHash(toLowerCase(certHashes))
                        .build()));

        final Map<String, String> responsesMap = response.getBase64EncodedResponsesMap();
        String[] result = new String[certHashes.length];
        for (int i = 0; i < certHashes.length; i++) {
            if (responsesMap.containsKey(certHashes[i])) {
                result[i] = responsesMap.get(certHashes[i]);
            }
        }

        return result;
    }

    public void setOcspResponses(String[] certHashes, String[] base64EncodedResponses) {
        tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingOcspService()
                        .setOcspResponses(SetOcspResponsesReq.newBuilder()
                                .addAllCertHashes(asList(certHashes))
                                .addAllBase64EncodedResponses(asList(base64EncodedResponses))
                                .build()))
        );
    }

    private List<String> toLowerCase(String[] certHashes) {
        return Arrays.stream(certHashes)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    /**
     * Get Security Server auth key
     *
     * @param serverId securityServerId
     * @return authKeyInfo
     */
    public AuthKeyInfo getAuthKey(SecurityServerId serverId) {
        return tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingKeyService()
                        .getAuthKey(GetAuthKeyReq.newBuilder()
                                .setSecurityServer(SecurityServerIdMapper.toDto(serverId))
                                .build())),
                response -> new AuthKeyInfo(response.getAlias(),
                        response.getKeyStoreFileName(),
                        response.getPassword().toCharArray(),
                        new CertificateInfo(response.getCert()))
        );
    }

    public TokenInfoAndKeyId getTokenAndKeyIdForCertRequestId(String certRequestId) {
        log.trace("Getting token and key id by cert request id '{}'", certRequestId);
        return tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingTokenService()
                        .getTokenAndKeyIdByCertRequestId(GetTokenByCertRequestIdReq.newBuilder()
                                .setCertRequestId(certRequestId)
                                .build())),
                response -> {
                    log.trace("Token and key id with cert request id '{}' found", certRequestId);
                    return new TokenInfoAndKeyId(new TokenInfo(response.getTokenInfo()), response.getKeyId());
                }
        );
    }

    /**
     * Gets information about the token which has the specified key.
     *
     * @param keyId id of the key
     * @return TokenInfo
     */
    public TokenInfo getTokenForKeyId(String keyId) {
        return tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingTokenService()
                        .getTokenByKey(GetTokenByKeyIdReq.newBuilder().setKeyId(keyId).build())),
                TokenInfo::new
        );
    }

    public SignMechanism getSignMechanism(String keyId) {
        return tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingKeyService()
                        .getSignMechanism(GetSignMechanismReq.newBuilder()
                                .setKeyId(keyId)
                                .build())),
                response -> SignMechanism.valueOf(response.getSignMechanismName())
        );
    }

    public byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] digest) {
        return tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingKeyService()
                                .sign(SignReq.newBuilder()
                                        .setKeyId(keyId)
                                        .setSignatureAlgorithmId(signatureAlgorithmId.name())
                                        .setDigest(ByteString.copyFrom(digest))
                                        .build()))
                        .getSignature().toByteArray()
        );
    }

    public Boolean isTokenBatchSigningEnabled(String keyId) {
        return tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingTokenService()
                                .getTokenBatchSigningEnabled(GetTokenBatchSigningEnabledReq.newBuilder()
                                        .setKeyId(keyId)
                                        .build()))
                        .getBatchingSigningEnabled()
        );
    }

    public MemberSigningInfoDto getMemberSigningInfo(ClientId clientId) {
        return tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingTokenService()
                        .getMemberSigningInfo(GetMemberSigningInfoReq.newBuilder()
                                .setMemberId(ClientIdMapper.toDto(clientId))
                                .build())),
                response -> new MemberSigningInfoDto(response.getKeyId(),
                        new CertificateInfo(response.getCert()),
                        SignMechanism.valueOf(response.getSignMechanismName()))
        );
    }

    public List<CertificateInfo> getMemberCerts(ClientId memberId) {
        return tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingCertificateService()
                                .getMemberCerts(GetMemberCertsReq.newBuilder()
                                        .setMemberId(ClientIdMapper.toDto(memberId))
                                        .build()))
                        .getCertsList().stream()
                        .map(CertificateInfo::new)
                        .toList()
        );
    }

    public boolean isHSMOperational() {
        return tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingTokenService()
                                .getHSMOperationalInfo(Empty.getDefaultInstance()))
                        .getOperational()
        );
    }

    public byte[] signCertificate(String keyId, SignAlgorithm signatureAlgorithmId, String subjectName, PublicKey publicKey) {
        return tryToRun(
                () -> client.execute(ctx -> ctx.getBlockingKeyService()
                                .signCertificate(SignCertificateReq.newBuilder()
                                        .setKeyId(keyId)
                                        .setSignatureAlgorithmId(signatureAlgorithmId.name())
                                        .setSubjectName(subjectName)
                                        .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                                        .build()))
                        .getCertificateChain().toByteArray()
        );
    }

    /**
     * DTO since we don't want to leak signer message objects out
     *
     * @param certReqId
     * @param certRequest
     * @param format
     * @param memberId
     * @param keyUsage
     */
    public record GeneratedCertRequestInfo(String certReqId, byte[] certRequest, CertificateRequestFormat format, ClientId memberId,
                                           KeyUsageInfo keyUsage) {
    }

    public record MemberSigningInfoDto(String keyId, CertificateInfo cert, SignMechanism signMechanismName) {
    }

    public record KeyIdInfo(String keyId, SignMechanism signMechanismName) {
    }

    @ArchUnitSuppressed("NoVanillaExceptions")
    private interface ActionWithResult<T> {
        T run() throws Exception;
    }

    @ArchUnitSuppressed("NoVanillaExceptions")
    private interface Action {
        void run() throws Exception;
    }

    @Getter
    static class SignerRpcExecutionContext implements RpcClient.ExecutionContext {
        private final TokenServiceGrpc.TokenServiceBlockingStub blockingTokenService;
        private final CertificateServiceGrpc.CertificateServiceBlockingStub blockingCertificateService;
        private final KeyServiceGrpc.KeyServiceBlockingStub blockingKeyService;
        private final OcspServiceGrpc.OcspServiceBlockingStub blockingOcspService;

        SignerRpcExecutionContext(Channel channel) {
            blockingTokenService = TokenServiceGrpc.newBlockingStub(channel).withWaitForReady();
            blockingCertificateService = CertificateServiceGrpc.newBlockingStub(channel).withWaitForReady();
            blockingKeyService = KeyServiceGrpc.newBlockingStub(channel).withWaitForReady();
            blockingOcspService = OcspServiceGrpc.newBlockingStub(channel).withWaitForReady();
        }
    }
}
