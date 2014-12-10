package ee.cyber.sdsb.confproxy.commandline;

import java.util.Date;

import org.apache.commons.cli.CommandLine;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.confproxy.ConfProxyProperties;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.message.GenerateKey;
import ee.cyber.sdsb.signer.protocol.message.GenerateSelfSignedCert;
import ee.cyber.sdsb.signer.protocol.message.GenerateSelfSignedCertResponse;
import static ee.cyber.sdsb.confproxy.ConfProxyProperties.*;

/**
 * Utility tool for adding new signing keys to a configuration proxy instance.
 */
public class ConfProxyUtilAddSigningKey extends ConfProxyUtil {

    ConfProxyUtilAddSigningKey() {
        super("confproxy-add-signing-key");
        getOptions()
            .addOption(PROXY_INSTANCE)
            .addOption("k", "key-id", true, "Id of the key to be added")
            .addOption("t", "token-id", true,
                    "Id of the token to generate a new key");
    }

    @Override
    void execute(CommandLine commandLine)
            throws Exception {
        ensureProxyExists(commandLine);
        final ConfProxyProperties conf = loadConf(commandLine);

        if (commandLine.hasOption("key-id")) {
            String keyId = commandLine.getOptionValue("k");
            addSigningKey(conf, keyId);
        } else if (commandLine.hasOption("token-id")) {
            String tokenId = commandLine.getOptionValue("t");
            KeyInfo keyInfo = SignerClient.execute(new GenerateKey(tokenId));
            System.out.println("Generated key with ID " + keyInfo.getId());
            addSigningKey(conf, keyInfo.getId());
        } else {
            printHelp();
            System.exit(0);
        }
    }

    private void addSigningKey(ConfProxyProperties conf, String keyId)
            throws Exception {
        ClientId clientId = null;
        GenerateSelfSignedCertResponse response = SignerClient.execute(
                        new GenerateSelfSignedCert(keyId, "N/A",
                                new Date(0), new Date(Integer.MAX_VALUE),
                                KeyUsageInfo.SIGNING, clientId));
        byte[] certBytes = response.getCertificateBytes();
        conf.saveCert(keyId, certBytes);
        System.out.println("Saved self-signed certificate to cert_"
                + keyId + ".pem");
        if (conf.getActiveSigningKey() == null) {
            System.out.println("No active key configured,"
                    + " setting new key as active in " + CONF_INI);
            conf.setActiveSigningKey(keyId);
        }
        conf.addKeyId(keyId);
        System.out.println("Added key to " + CONF_INI);
    }
}
