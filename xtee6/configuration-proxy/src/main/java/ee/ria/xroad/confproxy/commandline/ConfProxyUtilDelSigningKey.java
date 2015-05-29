package ee.ria.xroad.confproxy.commandline;

import org.apache.commons.cli.CommandLine;

import ee.ria.xroad.confproxy.ConfProxyProperties;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.message.DeleteKey;

import static ee.ria.xroad.confproxy.ConfProxyProperties.CONF_INI;

/**
 * Utility tool for deleting signing keys from a configuration proxy instance.
 */
public class ConfProxyUtilDelSigningKey extends ConfProxyUtil {

    /**
     * Constructs a confproxy-del-signing-key utility program instance.
     */
    ConfProxyUtilDelSigningKey() {
        super("confproxy-del-signing-key");
        getOptions()
            .addOption(PROXY_INSTANCE)
            .addOption("k", "key-id", true, "Id of the signing key to delete");
    }

    @Override
    final void execute(final CommandLine commandLine)
            throws Exception {
        ensureProxyExists(commandLine);
        final ConfProxyProperties conf = loadConf(commandLine);

        if (commandLine.hasOption("key-id")) {
            String keyId = commandLine.getOptionValue("k");
            if (keyId.equals(conf.getActiveSigningKey())) {
                fail("Not allowed to delete an active signing key!");
            }
            if (!conf.removeKeyId(keyId)) {
                fail("The key ID '" + keyId
                        + "' could not be found in '" + CONF_INI + "'.");
            }
            System.out.println("Deleted key from '" + CONF_INI + "'.");
            conf.deleteCert(keyId);
            System.out.println("Deleted self-signed certificate 'cert_"
                    + keyId + ".pem'");
            SignerClient.execute(new DeleteKey(keyId, true));
            System.out.println("Deleted key from signer");
        } else {
            printHelp();
            System.exit(0);
        }
    }
}
