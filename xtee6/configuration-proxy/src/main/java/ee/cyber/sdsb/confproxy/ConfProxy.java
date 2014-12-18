package ee.cyber.sdsb.confproxy;

import java.nio.file.Files;
import java.nio.file.Paths;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.conf.globalconf.ConfigurationDirectory;
import ee.cyber.sdsb.confproxy.util.ConfProxyHelper;
import ee.cyber.sdsb.confproxy.util.OutputBuilder;

/**
 * Defines a configuration proxy instance and carries out it's main operations.
 */
@Slf4j
public class ConfProxy {
    protected ConfProxyProperties conf;

    /**
     * Initializes a new configuration proxy instance.
     * @param instance name of this proxy instance
     * @throws Exception if loading instance configuration fails
     */
    ConfProxy(final String instance) throws Exception {
        this.conf = new ConfProxyProperties(instance);
        log.debug("Starting configuration-proxy '{}'...", instance);
    }

    /**
     * Launch the configuration proxy instance. Downloads signed directory,
     * signs it's content and moves it to the public distribution directory.
     * @throws Exception in case of any errors
     */
    public final void execute() throws Exception {
        ConfProxyHelper.purgeOutdatedGenerations(conf);
        ConfigurationDirectory confDir = download();

        OutputBuilder output = new OutputBuilder(confDir, conf);
        output.buildSignedDirectory();
        output.moveAndCleanup();
    }

    /**
     * Downloads the global configuration according to
     * the instance configuration.
     * @return downloaded configuration directory
     * @throws Exception if configuration client script encounters errors
     */
    private ConfigurationDirectory download() throws Exception {
        Files.createDirectories(Paths.get(conf.getConfigurationDownloadPath()));
        return ConfProxyHelper.downloadConfiguration(
                conf.getConfigurationDownloadPath(),
                conf.getProxyAnchorPath());
    }
}
