/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.confproxy.cli.action.instance;

import ee.ria.xroad.common.util.AtomicSave;

import jakarta.inject.Singleton;
import org.apache.commons.cli.CommandLine;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confproxy.common.config.ConfigurationProxyProperties;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.confproxy.common.service.AnchorGenerator;
import org.niis.xroad.confproxy.common.service.ConfProxyInstanceService;
import org.niis.xroad.signer.client.SignerRpcClient;

import java.nio.file.AccessDeniedException;

/**
 * Utility tool for creating an anchor file that is used for downloading
 * the generated global configuration.
 */
@Singleton
public class GenerateAnchorAction extends AbstractInstanceAction {

    private final AnchorGenerator anchorGenerator;

    /**
     * Constructs a confproxy-generate-anchor utility program instance.
     */
    public GenerateAnchorAction(
            SignerRpcClient signerRpcClient,
            ConfigurationProxyProperties cpProperties,
            ConfProxyInstanceService confProxyInstanceService,
            AnchorGenerator anchorGenerator) {
        super("generate-anchor", signerRpcClient, cpProperties, confProxyInstanceService);
        this.anchorGenerator = anchorGenerator;
        getOptions()
                .addOption(PROXY_INSTANCE)
                .addOption("f", "filename", true,
                        "Filename of the generated anchor");
    }

    @Override
    public final void execute(final CommandLine commandLine) {
        ensureProxyExists(commandLine);
        final ConfProxyInstance conf = loadConf(commandLine);
        if (conf == null) {
            return;
        }

        if (commandLine.hasOption("filename")) {
            var bytes = anchorGenerator.generateAnchor(conf);
            String filename = commandLine.getOptionValue("f");

            try {
                AtomicSave.execute(filename, "tmpanchor", out -> out.write(bytes));
            } catch (AccessDeniedException ex) {
                fail("Cannot write anchor to '" + filename + "', permission denied. ", ex);
            } catch (Exception ex) {
                throw XrdRuntimeException.systemInternalError("Cannot write anchor to '%s'".formatted(filename), ex);
            }
            System.out.println("Generated anchor xml to '" + filename + "'");
        } else {
            printHelp();
        }
    }

}
