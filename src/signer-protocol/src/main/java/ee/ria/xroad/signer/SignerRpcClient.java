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
package ee.ria.xroad.signer;

import ee.ria.xroad.common.CertificationServiceDiagnostics;
import ee.ria.xroad.common.CertificationServiceStatus;
import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.OcspResponderStatus;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.signer.exception.SignerException;
import ee.ria.xroad.signer.protocol.Utils;
import ee.ria.xroad.signer.protocol.dto.AuthKeyCertInfo;
import ee.ria.xroad.signer.protocol.dto.AuthKeyInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoAndKeyId;

import com.google.protobuf.ByteString;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.common.rpc.mapper.SecurityServerIdMapper;
import org.niis.xroad.rpc.common.Empty;
import org.niis.xroad.signer.proto.ActivateCertReq;
import org.niis.xroad.signer.proto.ActivateTokenReq;
import org.niis.xroad.signer.proto.AdminServiceGrpc;
import org.niis.xroad.signer.proto.Algorithm;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.proto.CertificateServiceGrpc;
import org.niis.xroad.signer.proto.CertificationServiceDiagnosticsResp;
import org.niis.xroad.signer.proto.DeleteCertReq;
import org.niis.xroad.signer.proto.DeleteCertRequestReq;
import org.niis.xroad.signer.proto.DeleteKeyReq;
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

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.util.CertUtils.isAuthCert;
import static ee.ria.xroad.common.util.CryptoUtils.loadPkcs12KeyStore;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static java.time.Instant.ofEpochMilli;
import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static org.niis.xroad.restapi.util.FormatUtils.fromInstantToOffsetDateTime;

/**
 * Responsible for managing cryptographic tokens (smartcards, HSMs, etc.) through the signer.
 */
@Slf4j
@RequiredArgsConstructor
public final class SignerRpcClient extends AbstractRpcClient {
    public static final String SSL_TOKEN_ID = "0";

    private final RpcChannelFactory proxyRpcChannelFactory;
    private final SignerRpcChannelProperties rpcChannelProperties;

    private TokenServiceGrpc.TokenServiceBlockingStub blockingTokenService;
    private CertificateServiceGrpc.CertificateServiceBlockingStub blockingCertificateService;
    private KeyServiceGrpc.KeyServiceBlockingStub blockingKeyService;

    private OcspServiceGrpc.OcspServiceBlockingStub blockingOcspService;
    private AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub;

    @PostConstruct
    public void afterPropertiesSet() throws Exception {
        log.info("Initializing {} rpc client to {}:{}", getClass().getSimpleName(), rpcChannelProperties.host(),
                rpcChannelProperties.port());
        var channel = proxyRpcChannelFactory.createChannel(rpcChannelProperties);

        blockingTokenService = TokenServiceGrpc.newBlockingStub(channel).withWaitForReady();
        blockingCertificateService = CertificateServiceGrpc.newBlockingStub(channel).withWaitForReady();
        blockingKeyService = KeyServiceGrpc.newBlockingStub(channel).withWaitForReady();
        blockingOcspService = OcspServiceGrpc.newBlockingStub(channel).withWaitForReady();
        adminServiceBlockingStub = AdminServiceGrpc.newBlockingStub(channel).withWaitForReady();
    }

    private static void tryToRun(Action action) throws SignerException {
        try {
            action.run();
        } catch (SignerException e) {
            throw e;
        } catch (CodedException e) {
            throw new SignerException(e);
        } catch (Exception e) {
            throw new SignerException(ErrorCodes.X_INTERNAL_ERROR, e);
        }
    }

    private static <R, T> T tryToRun(ActionWithResult<R> action, Function<R, T> mapper) throws SignerException {
        return tryToRun(() -> mapper.apply(action.run()));
    }

    private static <T> T tryToRun(ActionWithResult<T> action) throws SignerException {
        try {
            return action.run();
        } catch (SignerException e) {
            throw e;
        } catch (CodedException e) {
            throw new SignerException(e);
        } catch (Exception e) {
            throw new SignerException(ErrorCodes.X_INTERNAL_ERROR, e);
        }
    }

    /**
     * Initialize the software token with the given password.
     *
     * @param password software token password
     * @throws SignerException if any errors occur
     */
    public void initSoftwareToken(char[] password) throws SignerException {
        log.trace("Initializing software token");
        tryToRun(
                () -> blockingTokenService.initSoftwareToken(InitSoftwareTokenReq.newBuilder()
                        .setPin(ByteString.copyFrom(Utils.charToByte(password)))
                        .build())
        );
    }

    /**
     * Gets information about all configured tokens.
     *
     * @return a List of TokenInfo objects
     * @throws SignerException if any errors occur
     */
    public List<TokenInfo> getTokens() throws SignerException {
        return tryToRun(
                () -> blockingTokenService.listTokens(Empty.newBuilder().build())
                        .getTokensList().stream()
                        .map(TokenInfo::new)
                        .collect(Collectors.toList())
        );
    }

    /**
     * Gets information about the token with the specified token ID.
     *
     * @param tokenId ID of the token
     * @return TokenInfo
     * @throws SignerException if any errors occur
     */
    public TokenInfo getToken(String tokenId) throws SignerException {
        return tryToRun(
                () -> blockingTokenService.getTokenById(GetTokenByIdReq.newBuilder()
                        .setTokenId(tokenId).build()),
                TokenInfo::new
        );
    }

    /**
     * Activates the token with the given ID using the provided password.
     *
     * @param tokenId  ID of the token
     * @param password token password
     * @throws SignerException if any errors occur
     */
    public void activateToken(String tokenId, char[] password) throws SignerException {
        tryToRun(() -> internalActivateToken(tokenId, password));
    }

    private void internalActivateToken(String tokenId, char[] password) throws Exception {
        log.trace("Activating token '{}'", tokenId);

        var activateTokenReq = ActivateTokenReq.newBuilder()
                .setTokenId(tokenId)
                .setActivate(true);
        ofNullable(password).ifPresent(p -> activateTokenReq.setPin(ByteString.copyFrom(Utils.charToByte(p))));

        blockingTokenService.activateToken(activateTokenReq.build());
    }

    /**
     * Updates the token pin with the provided new one
     *
     * @param tokenId ID of the token
     * @param oldPin  the old (current) pin of the token
     * @param newPin  the new pin
     * @throws SignerException if any errors occur
     */
    public void updateTokenPin(String tokenId, char[] oldPin, char[] newPin) throws SignerException {
        log.trace("Updating token pin '{}'", tokenId);

        tryToRun(
                () -> blockingTokenService.updateSoftwareTokenPin(UpdateSoftwareTokenPinReq.newBuilder()
                        .setTokenId(tokenId)
                        .setOldPin(ByteString.copyFrom(Utils.charToByte(oldPin)))
                        .setNewPin(ByteString.copyFrom(Utils.charToByte(newPin)))
                        .build())
        );
    }

    /**
     * Deactivates the token with the given ID.
     *
     * @param tokenId ID of the token
     * @throws SignerException if any errors occur
     */
    public void deactivateToken(String tokenId) throws SignerException {
        log.trace("Deactivating token '{}'", tokenId);

        tryToRun(
                () -> blockingTokenService.activateToken(ActivateTokenReq.newBuilder()
                        .setTokenId(tokenId)
                        .setActivate(false)
                        .build())
        );
    }

    /**
     * Sets the friendly name of the token with the given ID.
     *
     * @param tokenId      ID of the token
     * @param friendlyName new friendly name of the token
     * @throws SignerException if any errors occur
     */
    public void setTokenFriendlyName(String tokenId, String friendlyName) throws SignerException {
        log.trace("Setting friendly name '{}' for token '{}'", friendlyName, tokenId);

        tryToRun(
                () -> blockingTokenService.setTokenFriendlyName(SetTokenFriendlyNameReq.newBuilder()
                        .setTokenId(tokenId)
                        .setFriendlyName(friendlyName)
                        .build())
        );
    }

    /**
     * Sets the friendly name of the key with the given ID.
     *
     * @param keyId        ID of the key
     * @param friendlyName new friendly name of the key
     * @throws SignerException if any errors occur
     */
    public void setKeyFriendlyName(String keyId, String friendlyName) throws SignerException {
        log.trace("Setting friendly name '{}' for key '{}'", friendlyName, keyId);

        tryToRun(
                () -> blockingKeyService.setKeyFriendlyName(SetKeyFriendlyNameReq.newBuilder()
                        .setKeyId(keyId)
                        .setFriendlyName(friendlyName)
                        .build())
        );
    }

    /**
     * Generate a new key for the token with the given ID.
     *
     * @param tokenId   ID of the token
     * @param keyLabel  label of the key
     * @param algorithm algorithm to use, RSA or EC
     * @return generated key KeyInfo object
     * @throws SignerException if any errors occur
     */
    public KeyInfo generateKey(String tokenId, String keyLabel, KeyAlgorithm algorithm) throws SignerException {
        return tryToRun(() -> internalGenerateKey(tokenId, keyLabel, algorithm));
    }

    private KeyInfo internalGenerateKey(String tokenId, String keyLabel, KeyAlgorithm algorithm) throws Exception {
        log.trace("Generating key for token '{}'", tokenId);

        var builder = GenerateKeyReq.newBuilder()
                .setTokenId(tokenId)
                .setKeyLabel(keyLabel);
        if (algorithm != null) {
            builder.setAlgorithm(Algorithm.valueOf(algorithm.name()));
        }

        var response = blockingKeyService.generateKey(builder.build());

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
     * @throws SignerException if any errors occur
     */
    public byte[] generateSelfSignedCert(String keyId, ClientId.Conf memberId, KeyUsageInfo keyUsage,
                                         String commonName, Date notBefore, Date notAfter) throws SignerException {
        return tryToRun(() -> internalGenerateSelfSignedCert(keyId, memberId, keyUsage, commonName, notBefore, notAfter));
    }

    private byte[] internalGenerateSelfSignedCert(String keyId, ClientId.Conf memberId, KeyUsageInfo keyUsage,
                                                  String commonName, Date notBefore, Date notAfter) throws Exception {
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

        var response = blockingCertificateService.generateSelfSignedCert(builder.build());

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
     * @throws SignerException if any errors occur
     */
    public String importCert(byte[] certBytes, String initialStatus, ClientId.Conf clientId, boolean activate) throws SignerException {
        return tryToRun(() -> internalImportCert(certBytes, initialStatus, clientId, activate));
    }

    private String internalImportCert(byte[] certBytes, String initialStatus, ClientId.Conf clientId, boolean activate)
            throws Exception {
        log.trace("Importing cert from file with length of '{}' bytes", certBytes.length);

        final ImportCertReq.Builder builder = ImportCertReq.newBuilder()
                .setCertData(ByteString.copyFrom(certBytes))
                .setInitialStatus(initialStatus)
                .setActivate(activate);
        ofNullable(clientId).map(ClientIdMapper::toDto).ifPresent(builder::setMemberId);

        var response = blockingCertificateService.importCert(builder.build());

        log.trace("Cert imported successfully, keyId received: {}", response.getKeyId());

        return response.getKeyId();
    }

    public String importCert(byte[] certBytes, String initialStatus, ClientId.Conf clientId) throws SignerException {
        return tryToRun(() -> {
            X509Certificate x509Certificate = readCertificate(certBytes);
            return importCert(certBytes, initialStatus, clientId, !isAuthCert(x509Certificate));
        });
    }

    /**
     * Activates the certificate with the given ID.
     *
     * @param certId ID of the certificate
     * @throws SignerException if any errors occur
     */
    public void activateCert(String certId) throws SignerException {
        log.trace("Activating cert '{}'", certId);

        tryToRun(
                () -> blockingCertificateService.activateCert(ActivateCertReq.newBuilder()
                        .setCertIdOrHash(certId)
                        .setActive(true)
                        .build())
        );
    }

    /**
     * Deactivates the certificate with the given ID.
     *
     * @param certId ID of the certificate
     * @throws SignerException if any errors occur
     */
    public void deactivateCert(String certId) throws SignerException {
        log.trace("Deactivating cert '{}'", certId);

        tryToRun(
                () -> blockingCertificateService.activateCert(ActivateCertReq.newBuilder()
                        .setCertIdOrHash(certId)
                        .setActive(false)
                        .build())
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
     * @throws SignerException if any errors occur
     */
    public GeneratedCertRequestInfo generateCertRequest(String keyId, ClientId.Conf memberId,
                                                        KeyUsageInfo keyUsage, String subjectName,
                                                        CertificateRequestFormat format) throws SignerException {
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
     * @throws SignerException if any errors occur
     */
    public GeneratedCertRequestInfo generateCertRequest(String keyId, ClientId.Conf memberId,
                                                        KeyUsageInfo keyUsage, String subjectName, String subjectAltName,
                                                        CertificateRequestFormat format, String certificateProfile) throws SignerException {
        return tryToRun(
                () -> internalGenerateCertRequest(keyId, memberId, keyUsage, subjectName, subjectAltName, format, certificateProfile)
        );
    }

    private GeneratedCertRequestInfo internalGenerateCertRequest(String keyId, ClientId.Conf memberId,
                                                                 KeyUsageInfo keyUsage, String subjectName, String subjectAltName,
                                                                 CertificateRequestFormat format, String certificateProfile)
            throws Exception {
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
        var response = blockingCertificateService.generateCertRequest(reqBuilder.build());
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
     * @throws SignerException if any errors occur
     */
    public GeneratedCertRequestInfo regenerateCertRequest(String certRequestId,
                                                          CertificateRequestFormat format) throws SignerException {
        return tryToRun(() -> internalRegenerateCertRequest(certRequestId, format));
    }

    private GeneratedCertRequestInfo internalRegenerateCertRequest(String certRequestId,
                                                                   CertificateRequestFormat format) throws Exception {
        var response = exec(() -> blockingCertificateService
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
     * @throws SignerException if any errors occur
     */
    public void deleteCertRequest(String certRequestId) throws SignerException {
        log.trace("Deleting cert request '{}'", certRequestId);
        tryToRun(
                () -> blockingCertificateService.deleteCertRequest(DeleteCertRequestReq.newBuilder()
                        .setCertRequestId(certRequestId)
                        .build())
        );
    }

    /**
     * Delete the certificate with the given ID.
     *
     * @param certId ID of the certificate
     * @throws SignerException if any errors occur
     */
    public void deleteCert(String certId) throws SignerException {
        log.trace("Deleting cert '{}'", certId);
        tryToRun(
                () -> blockingCertificateService.deleteCert(DeleteCertReq.newBuilder()
                        .setCertId(certId)
                        .build())
        );
    }

    /**
     * Delete the key with the given ID from the signer database. Optionally,
     * deletes it from the token as well.
     *
     * @param keyId           ID of the certificate request
     * @param deleteFromToken whether the key should be deleted from the token
     * @throws SignerException if any errors occur
     */
    public void deleteKey(String keyId, boolean deleteFromToken) throws SignerException {
        log.trace("Deleting key '{}', from token = {}", keyId, deleteFromToken);
        tryToRun(
                () -> blockingKeyService.deleteKey(DeleteKeyReq.newBuilder()
                        .setKeyId(keyId)
                        .setDeleteFromDevice(deleteFromToken)
                        .build())
        );
    }

    /**
     * Sets the status of the certificate with the given ID.
     *
     * @param certId ID of the certificate
     * @param status new status of the certificate
     * @throws SignerException if any errors occur
     */
    public void setCertStatus(String certId, String status) throws SignerException {
        log.trace("Setting cert ('{}') status to '{}'", certId, status);
        tryToRun(
                () -> blockingCertificateService.setCertStatus(SetCertStatusReq.newBuilder()
                        .setCertId(certId)
                        .setStatus(status)
                        .build())
        );
    }

    /**
     * Sets the hash of the renewed certificate with the given old cert ID.
     *
     * @param certId ID of the old certificate
     * @param hash   new hash of the renewed certificate
     * @throws SignerException if any errors occur
     */
    public void setRenewedCertHash(String certId, String hash) throws SignerException {
        log.trace("Setting cert ('{}') renewed cert hash to '{}'", certId, hash);
        tryToRun(
                () -> blockingCertificateService.setRenewedCertHash(SetRenewedCertHashReq.newBuilder()
                        .setCertId(certId)
                        .setHash(hash)
                        .build())
        );
    }

    /**
     * Sets the error of the certificate renewal process.
     *
     * @param certId       ID of the certificate to be renewed
     * @param errorMessage message of the error that was thrown when trying to renew the given certificate
     * @throws SignerException if any errors occur
     */
    public void setRenewalError(String certId, String errorMessage) throws SignerException {
        log.trace("Setting cert ('{}') renewal error to '{}'", certId, errorMessage);
        tryToRun(
                () -> blockingCertificateService.setRenewalError(SetRenewalErrorReq.newBuilder()
                        .setCertId(certId)
                        .setErrorMessage(errorMessage)
                        .build())
        );
    }

    /**
     * Sets the error of the certificate renewal process.
     *
     * @param certId          ID of the certificate to be renewed
     * @param nextRenewalTime message of the error that was thrown when trying to renew the given certificate
     * @throws SignerException if any errors occur
     */
    public void setNextPlannedRenewal(String certId, Instant nextRenewalTime) throws SignerException {
        log.trace("Setting cert ('{}') next planned renewal time to '{}'", certId, nextRenewalTime);
        tryToRun(() -> {
            com.google.protobuf.Timestamp nextRenewalTimestamp = com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(nextRenewalTime.getEpochSecond())
                    .setNanos(nextRenewalTime.getNano())
                    .build();
            blockingCertificateService.setNextPlannedRenewal(SetNextPlannedRenewalReq.newBuilder()
                    .setCertId(certId)
                    .setNextRenewalTime(nextRenewalTimestamp)
                    .build());
        });
    }

    /**
     * Get a cert by it's hash
     *
     * @param hash cert hash. Will be converted to lowercase, which is what signer uses internally
     * @return CertificateInfo
     * @throws SignerException if any error occur
     */
    public CertificateInfo getCertForHash(String hash) throws SignerException {
        return tryToRun(() -> internalGetCertForHash(hash));
    }

    private CertificateInfo internalGetCertForHash(String hash) throws Exception {
        final String finalHash = hash.toLowerCase();
        log.trace("Getting cert by hash '{}'", hash);

        var response = blockingCertificateService.getCertificateInfoForHash(GetCertificateInfoForHashReq.newBuilder()
                .setCertHash(finalHash)
                .build());

        log.trace("Cert with hash '{}' found", finalHash);

        return new CertificateInfo(response.getCertificateInfo());
    }

    /**
     * Get key for a given cert hash
     *
     * @param hash cert hash. Will be converted to lowercase, which is what signer uses internally
     * @return Key id and sign mechanism
     * @throws SignerException
     */
    public KeyIdInfo getKeyIdForCertHash(String hash) throws SignerException {
        return tryToRun(() -> internalGetKeyIdForCertHash(hash));
    }

    private KeyIdInfo internalGetKeyIdForCertHash(String hash) throws Exception {
        final String finalHash = hash.toLowerCase();
        log.trace("Getting cert by hash '{}'", finalHash);

        var response = blockingKeyService.getKeyIdForCertHash(GetKeyIdForCertHashReq.newBuilder()
                .setCertHash(finalHash)
                .build());
        log.trace("Cert with hash '{}' found", finalHash);

        return new KeyIdInfo(response.getKeyId(), SignMechanism.valueOf(response.getSignMechanismName()));
    }

    /**
     * Get TokenInfoAndKeyId for a given cert hash
     *
     * @param hash cert hash. Will be converted to lowercase, which is what signer uses internally
     * @return TokenInfoAndKeyId
     * @throws SignerException
     */
    public TokenInfoAndKeyId getTokenAndKeyIdForCertHash(String hash) throws SignerException {
        return tryToRun(() -> internalGetTokenAndKeyIdForCertHash(hash));
    }

    private TokenInfoAndKeyId internalGetTokenAndKeyIdForCertHash(String hash) throws Exception {
        String hashLowercase = hash.toLowerCase();
        log.trace("Getting token and key id by cert hash '{}'", hashLowercase);

        var response = blockingTokenService.getTokenAndKeyIdByCertHash(GetTokenByCertHashReq.newBuilder()
                .setCertHash(hashLowercase)
                .build());
        log.trace("Token and key id with hash '{}' found", hashLowercase);

        return new TokenInfoAndKeyId(new TokenInfo(response.getTokenInfo()), response.getKeyId());
    }

    /**
     * Get OCSP responses for certs with given hashes. Hashes are converted to lowercase
     *
     * @param certHashes cert hashes to find OCSP responses for
     * @return base64 encoded OCSP responses. Each array item is OCSP response for
     * corresponding cert in {@code certHashes}
     * @throws SignerException if something failed
     */
    @WithSpan("SignerProxy#getOcspResponses")
    public String[] getOcspResponses(String[] certHashes) throws SignerException {
        return tryToRun(() -> internalGetOcspResponses(certHashes));
    }

    private String[] internalGetOcspResponses(String[] certHashes) throws Exception {
        var response = blockingOcspService.getOcspResponses(GetOcspResponsesReq.newBuilder()
                .addAllCertHash(toLowerCase(certHashes))
                .build());

        final Map<String, String> responsesMap = response.getBase64EncodedResponsesMap();
        String[] result = new String[certHashes.length];
        for (int i = 0; i < certHashes.length; i++) {
            if (responsesMap.containsKey(certHashes[i])) {
                result[i] = responsesMap.get(certHashes[i]);
            }
        }
        return result;
    }

    public void setOcspResponses(String[] certHashes, String[] base64EncodedResponses) throws Exception {
        tryToRun(
                () -> blockingOcspService.setOcspResponses(SetOcspResponsesReq.newBuilder()
                        .addAllCertHashes(asList(certHashes))
                        .addAllBase64EncodedResponses(asList(base64EncodedResponses))
                        .build())
        );
    }

    private List<String> toLowerCase(String[] certHashes) {
        return Arrays.stream(certHashes)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    /**
     * Get Security Server auth key certificate.
     *
     * @param serverId securityServerId
     * @return authKeyInfo
     * @throws Exception
     */
    public AuthKeyCertInfo getAuthKeyCert(SecurityServerId serverId) throws SignerException {
        return tryToRun(
                () -> blockingKeyService.getAuthKeyCert(GetAuthKeyReq.newBuilder()
                        .setSecurityServer(SecurityServerIdMapper.toDto(serverId))
                        .build()),
                response -> {
                    return new AuthKeyCertInfo(response.getAlias(),
                            new CertificateInfo(response.getCert()));
                }
        );
    }


    /**
     * Get Security Server auth key
     *
     * @param serverId securityServerId
     * @return authKeyInfo
     * @throws SignerException
     */
    public AuthKeyInfo getAuthKey(SecurityServerId serverId) throws SignerException {
        return tryToRun(
                () -> internalGetAuthKey(serverId)
        );
    }

    public AuthKeyInfo internalGetAuthKey(SecurityServerId serverId) throws Exception {
        var response = blockingKeyService.getAuthKey(GetAuthKeyReq.newBuilder()
                .setSecurityServer(SecurityServerIdMapper.toDto(serverId))
                .build());

        try (var keystoreStream = response.getKeyStore().newInput()) {
            var password = response.getPassword().toCharArray();
            var ks = loadPkcs12KeyStore(keystoreStream, password);
            var privateKey = (PrivateKey) ks.getKey(response.getAlias(),
                    password);
            return new AuthKeyInfo(response.getAlias(),
                    privateKey,
                    new CertificateInfo(response.getCert()));
        }
    }

    /**
     * Get TokenInfoAndKeyId for a given cert hash
     *
     * @param certRequestId
     * @return TokenInfoAndKeyId
     * @throws SignerException
     */
    public TokenInfoAndKeyId getTokenAndKeyIdForCertRequestId(String certRequestId) throws SignerException {
        log.trace("Getting token and key id by cert request id '{}'", certRequestId);

        return tryToRun(
                () -> blockingTokenService.getTokenAndKeyIdByCertRequestId(GetTokenByCertRequestIdReq.newBuilder()
                        .setCertRequestId(certRequestId)
                        .build()),
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
     * @throws SignerException if any errors occur
     */
    public TokenInfo getTokenForKeyId(String keyId) throws SignerException {
        return tryToRun(
                () -> blockingTokenService.getTokenByKey(GetTokenByKeyIdReq.newBuilder().setKeyId(keyId).build()),
                TokenInfo::new
        );
    }

    public SignMechanism getSignMechanism(String keyId) throws SignerException {
        return tryToRun(
                () -> blockingKeyService.getSignMechanism(GetSignMechanismReq.newBuilder()
                        .setKeyId(keyId)
                        .build()),
                response -> SignMechanism.valueOf(response.getSignMechanismName())
        );
    }

    @WithSpan("SignerProxy#sign")
    public byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] digest) throws SignerException {
        return tryToRun(
                () -> blockingKeyService.sign(SignReq.newBuilder()
                                .setKeyId(keyId)
                                .setSignatureAlgorithmId(signatureAlgorithmId.name())
                                .setDigest(ByteString.copyFrom(digest))
                                .build())
                        .getSignature().toByteArray()
        );
    }

    public Boolean isTokenBatchSigningEnabled(String keyId) throws SignerException {
        return tryToRun(
                () -> blockingTokenService.getTokenBatchSigningEnabled(GetTokenBatchSigningEnabledReq.newBuilder()
                        .setKeyId(keyId)
                        .build()).getBatchingSigningEnabled()
        );
    }

    public MemberSigningInfoDto getMemberSigningInfo(ClientId clientId) throws SignerException {
        return tryToRun(
                () -> blockingTokenService.getMemberSigningInfo(GetMemberSigningInfoReq.newBuilder()
                        .setMemberId(ClientIdMapper.toDto(clientId))
                        .build()),
                response -> new MemberSigningInfoDto(response.getKeyId(),
                        new CertificateInfo(response.getCert()),
                        SignMechanism.valueOf(response.getSignMechanismName()))
        );
    }

    public List<CertificateInfo> getMemberCerts(ClientId memberId) throws SignerException {
        return tryToRun(
                () -> blockingCertificateService.getMemberCerts(GetMemberCertsReq.newBuilder()
                                .setMemberId(ClientIdMapper.toDto(memberId))
                                .build())
                        .getCertsList().stream()
                        .map(CertificateInfo::new)
                        .collect(Collectors.toList())
        );
    }

    public boolean isHSMOperational() throws SignerException {
        return tryToRun(
                () -> blockingTokenService.getHSMOperationalInfo(Empty.getDefaultInstance())
                        .getOperational()
        );
    }

    public byte[] signCertificate(String keyId, SignAlgorithm signatureAlgorithmId, String subjectName, PublicKey publicKey)
            throws SignerException {
        return tryToRun(
                () -> blockingKeyService.signCertificate(SignCertificateReq.newBuilder()
                                .setKeyId(keyId)
                                .setSignatureAlgorithmId(signatureAlgorithmId.name())
                                .setSubjectName(subjectName)
                                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                                .build())
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

    public CertificationServiceDiagnostics getCertificationServiceDiagnostics() throws SignerException {
        return tryToRun(
                () -> adminServiceBlockingStub.getCertificationServiceDiagnostics(Empty.newBuilder().build()),
                response -> CertificationServiceDiagnosticsMapper.fromDto(response)
        );
    }

    public String getKeyConfChecksum() throws SignerException {
        return tryToRun(
                () -> adminServiceBlockingStub.getKeyConfChecksum(Empty.getDefaultInstance()),
                response -> {
                    return response.hasChecksum() ? response.getChecksum() : null;
                }
        );
    }

    private static final class CertificationServiceDiagnosticsMapper {

        public static CertificationServiceDiagnostics fromDto(CertificationServiceDiagnosticsResp dto) {
            Map<String, CertificationServiceStatus> statusMap = dto.getCertificationServiceStatusMapMap()
                    .entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            entry -> toDto(entry.getValue())));

            CertificationServiceDiagnostics response = new CertificationServiceDiagnostics();
            response.update(statusMap);
            return response;
        }

        private static CertificationServiceStatus toDto(org.niis.xroad.signer.proto.CertificationServiceStatus status) {
            var response = new CertificationServiceStatus(status.getName());
            status.getOcspResponderStatusMapMap()
                    .forEach((key, value) -> response.getOcspResponderStatusMap().put(key,
                            new OcspResponderStatus(value.getStatus(),
                                    value.getUrl(),
                                    value.hasPrevUpdate() ? fromInstantToOffsetDateTime(ofEpochMilli(value.getPrevUpdate())) : null,
                                    fromInstantToOffsetDateTime(ofEpochMilli(value.getNextUpdate())))
                    ));
            return response;
        }

    }

    public record MemberSigningInfoDto(String keyId, CertificateInfo cert, SignMechanism signMechanismName) {
    }

    public record KeyIdInfo(String keyId, SignMechanism signMechanismName) {
    }

    private interface ActionWithResult<T> {
        T run() throws Exception;
    }

    private interface Action {
        void run() throws Exception;
    }
}
