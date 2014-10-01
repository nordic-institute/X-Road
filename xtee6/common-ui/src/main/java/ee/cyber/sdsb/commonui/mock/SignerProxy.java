package ee.cyber.sdsb.commonui.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.signer.protocol.dto.CertRequestInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenStatusInfo;

/**
 * Simply a mockup to facilitate testing UI-s.
 * 
 * WARNING: Remove from use in production!
 */
public class SignerProxy {
    private static final Logger LOG = LoggerFactory
            .getLogger(SignerProxy.class);

    public static void activateToken(String tokenId, char[] password)
            throws Exception {
        LOG.info("Activating token '{}'", tokenId);

        if (Arrays.equals(password, new char[] { 'f', 'a', 'u', 'l', 't' })) {
            throw new Exception("Incorrect password, try again!");
        }
    }

    public static List<TokenInfo> getTokens() throws Exception {
        // From central, key ID can be got from database like this:
        // SELECT value FROM system_parameters WHERE key IN('confSignKeyId');
        KeyInfo signingKey = new KeyInfo(
                true, 
                KeyUsageInfo.SIGNING, 
                "Friendly name", 
                "D1A5E3B757AEDDA3BB92464B1F4A128CE945EFC2", // XXX: Id may vary
                "publicKey", 
                new ArrayList<CertificateInfo>(),
                new ArrayList<CertRequestInfo>());

        TokenInfo token = new TokenInfo(
                "type",
                "friendlyName",
                "id",
                false,
                true,
                true,
                "10",
                "label",
                10, // slotIndex
                getRandomTokenStatus(),
                Collections.singletonList(signingKey),
                new HashMap<String, String>());

        List<TokenInfo> tokens = Collections.singletonList(token);

        LOG.debug("Received tokens: {}", tokens.toString());
        return tokens;
    }

    private static TokenStatusInfo getRandomTokenStatus() {
        TokenStatusInfo[] tokenStatuses = TokenStatusInfo.values();
        int index = (int) (Math.random() * tokenStatuses.length);

        return tokenStatuses[index];
    }
}
