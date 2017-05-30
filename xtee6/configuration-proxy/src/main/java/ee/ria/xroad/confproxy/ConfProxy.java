/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.confproxy;

import java.nio.file.Files;
import java.nio.file.Paths;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.conf.globalconf.ConfigurationDirectory;
import ee.ria.xroad.confproxy.util.ConfProxyHelper;
import ee.ria.xroad.confproxy.util.OutputBuilder;

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
