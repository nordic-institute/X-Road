package ee.cyber.sdsb.proxyui;

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
public class SignerProxy {

    private static final Logger LOG = LoggerFactory.getLogger(SignerProxy.class);

    public static void initSoftwareToken(char[] password) throws Exception {
        LOG.info("Initializing software token");
        execute(new InitSoftwareToken(password));
    }

    public static List<TokenInfo> getTokens() throws Exception {
        List<TokenInfo> tokens = execute(new ListTokens());

        LOG.debug("Received tokens: {}", tokens.toString());
        return tokens;
    }

    public static void activateToken(String tokenId, char[] password)
            throws Exception {
        PasswordStore.storePassword(tokenId, password);

        LOG.info("Activating token '{}'", tokenId);
        execute(new ActivateToken(tokenId, true));
    }

    public static void deactivateToken(String tokenId) throws Exception {
        PasswordStore.storePassword(tokenId, null);

        LOG.info("Deactivating token '{}''", tokenId);
        execute(new ActivateToken(tokenId, false));
    }

    public static void setTokenFriendlyName(String tokenId,
            String friendlyName) throws Exception {
        LOG.info("Setting friendly name '{}' for token '{}'", friendlyName,
                tokenId);
        execute(new SetTokenFriendlyName(tokenId, friendlyName));
    }

    public static void setKeyFriendlyName(String keyId, String friendlyName)
            throws Exception {
        LOG.info("Setting friendly name '{}' for key '{}'", friendlyName,
                keyId);
        execute(new SetKeyFriendlyName(keyId, friendlyName));
    }

    public static KeyInfo generateKey(String tokenId) throws Exception {
        LOG.info("Generating key for token '{}'", tokenId);
        KeyInfo keyInfo = execute(new GenerateKey(tokenId));

        LOG.debug("Received response with keyId '{}' and public key '{}'",
                keyInfo.getId(), keyInfo.getPublicKey());

        return keyInfo;
    }

    public static String importCert(byte[] certBytes, String initialStatus)
            throws Exception {
        LOG.info("Importing cert from file with length of '{}' bytes",
                certBytes.length);

        ImportCertResponse response =
                execute(new ImportCert(certBytes, initialStatus));

        LOG.debug("Cert imported successfully, keyId received: {}",
                response.getKeyId());

        return response.getKeyId();
    }

    public static void activateCert(String certId) throws Exception {
        LOG.info("Activating cert '{}'", certId);

        execute(new ActivateCert(certId, true));
    }

    public static void deactivateCert(String certId) throws Exception {
        LOG.info("Deactivating cert '{}'", certId);

        execute(new ActivateCert(certId, false));
    }

    public static byte[] generateCertRequest(String keyId, ClientId memberId,
            KeyUsageInfo keyUsage, String subjectName) throws Exception {

        // LOG.info("Generating cert request with following creation context: "
        //     + " {}, {}, {}, {}", keyId, memberId, keyUsage, subjectName);

        GenerateCertRequestResponse response =
                execute(new GenerateCertRequest(keyId, memberId, keyUsage,
                        subjectName));

        byte[] certRequestBytes = response.getCertRequest();

        LOG.debug("Cert request with length of {} bytes generated",
                certRequestBytes.length);

        return certRequestBytes;
    }

    public static void deleteCertRequest(String certRequestId) throws Exception {
        LOG.info("Deleting cert request '{}'", certRequestId);
        execute(new DeleteCertRequest(certRequestId));
    }

    public static void deleteCert(String certId) throws Exception {
        LOG.info("Deleting cert '{}'", certId);
        execute(new DeleteCert(certId));
    }

    public static void deleteKey(String keyId) throws Exception {
        LOG.info("Deleting key '{}'", keyId);
        execute(new DeleteKey(keyId));
    }

    public static void setCertStatus(String certId, String status)
            throws Exception {
        LOG.info("Setting cert ('{}') status to '{}'", certId, status);
        execute(new SetCertStatus(certId, status));
    }

    private static <T> T execute(Object message) throws Exception {
        return SignerClient.execute(message);
    }
}
