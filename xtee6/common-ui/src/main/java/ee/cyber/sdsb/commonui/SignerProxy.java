package ee.cyber.sdsb.commonui;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.util.PasswordStore;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.*;

/**
 * Responsible for managing cryptographic tokens (smartcards, HSMs,
 * etc.) through the signer.
 */
public final class SignerProxy {

    private SignerProxy() {
    }

    private static final Logger LOG =
            LoggerFactory.getLogger(SignerProxy.class);

    public static final String SSL_TOKEN_ID = "0";

    public static void initSoftwareToken(char[] password) throws Exception {
        LOG.trace("Initializing software token");
        execute(new InitSoftwareToken(password));
    }

    public static List<TokenInfo> getTokens() throws Exception {
        return execute(new ListTokens());
    }

    public static TokenInfo getToken(String tokenId) throws Exception {
        return execute(new GetTokenInfo(tokenId));
    }

    public static void activateToken(String tokenId, char[] password)
            throws Exception {
        PasswordStore.storePassword(tokenId, password);

        LOG.trace("Activating token '{}'", tokenId);
        execute(new ActivateToken(tokenId, true));
    }

    public static void deactivateToken(String tokenId) throws Exception {
        PasswordStore.storePassword(tokenId, null);

        LOG.trace("Deactivating token '{}''", tokenId);
        execute(new ActivateToken(tokenId, false));
    }

    public static void setTokenFriendlyName(String tokenId,
            String friendlyName) throws Exception {
        LOG.trace("Setting friendly name '{}' for token '{}'", friendlyName,
                tokenId);
        execute(new SetTokenFriendlyName(tokenId, friendlyName));
    }

    public static void setKeyFriendlyName(String keyId, String friendlyName)
            throws Exception {
        LOG.trace("Setting friendly name '{}' for key '{}'", friendlyName,
                keyId);
        execute(new SetKeyFriendlyName(keyId, friendlyName));
    }

    public static KeyInfo generateKey(String tokenId) throws Exception {
        LOG.trace("Generating key for token '{}'", tokenId);
        KeyInfo keyInfo = execute(new GenerateKey(tokenId));

        LOG.trace("Received response with keyId '{}' and public key '{}'",
                keyInfo.getId(), keyInfo.getPublicKey());

        return keyInfo;
    }

    public static byte[] generateSelfSignedCert(String keyId, ClientId memberId,
            KeyUsageInfo keyUsage, String commonName, Date notBefore,
            Date notAfter) throws Exception {
        LOG.trace("Generate self-signed cert for key '{}'", keyId);
        GenerateSelfSignedCertResponse response =
                execute(new GenerateSelfSignedCert(keyId, commonName,
                        notBefore, notAfter, keyUsage, memberId));

        byte[] certificateBytes = response.getCertificateBytes();

        LOG.trace("Certificate with length of {} bytes generated",
                certificateBytes.length);

        return certificateBytes;
    }

    public static String importCert(byte[] certBytes, String initialStatus)
            throws Exception {
        return importCert(certBytes, initialStatus, null);
    }

    public static String importCert(byte[] certBytes, String initialStatus,
            ClientId clientId) throws Exception {
        LOG.trace("Importing cert from file with length of '{}' bytes",
                certBytes.length);

        ImportCertResponse response =
                execute(new ImportCert(certBytes, initialStatus, clientId));

        LOG.trace("Cert imported successfully, keyId received: {}",
                response.getKeyId());

        return response.getKeyId();
    }

    public static void activateCert(String certId) throws Exception {
        LOG.trace("Activating cert '{}'", certId);

        execute(new ActivateCert(certId, true));
    }

    public static void deactivateCert(String certId) throws Exception {
        LOG.trace("Deactivating cert '{}'", certId);

        execute(new ActivateCert(certId, false));
    }

    public static byte[] generateCertRequest(String keyId, ClientId memberId,
            KeyUsageInfo keyUsage, String subjectName) throws Exception {
        GenerateCertRequestResponse response =
                execute(new GenerateCertRequest(keyId, memberId, keyUsage,
                        subjectName));

        byte[] certRequestBytes = response.getCertRequest();

        LOG.trace("Cert request with length of {} bytes generated",
                certRequestBytes.length);

        return certRequestBytes;
    }

    public static void deleteCertRequest(String certRequestId) throws Exception {
        LOG.trace("Deleting cert request '{}'", certRequestId);
        execute(new DeleteCertRequest(certRequestId));
    }

    public static void deleteCert(String certId) throws Exception {
        LOG.trace("Deleting cert '{}'", certId);
        execute(new DeleteCert(certId));
    }

    public static void deleteKey(String keyId, boolean deleteFromToken) throws Exception {
        LOG.trace("Deleting key '{}', from token = ", keyId, deleteFromToken);
        execute(new DeleteKey(keyId, deleteFromToken));
    }

    public static void setCertStatus(String certId, String status)
            throws Exception {
        LOG.trace("Setting cert ('{}') status to '{}'", certId, status);
        execute(new SetCertStatus(certId, status));
    }

    private static <T> T execute(Object message) throws Exception {
        return SignerClient.execute(message);
    }

}
