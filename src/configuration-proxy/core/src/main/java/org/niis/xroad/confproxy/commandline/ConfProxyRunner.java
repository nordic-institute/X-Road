/*
 * The MIT License
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
package org.niis.xroad.confproxy.commandline;

import ee.ria.xroad.signer.SignerRpcClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.confproxy.ConfProxy;
import org.niis.xroad.confproxy.util.ConfProxyHelper;
import org.springframework.boot.CommandLineRunner;

import java.util.Arrays;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ConfProxyRunner implements CommandLineRunner {
    private final SignerRpcClient signerRpcClient;

    /**
     * Executes all configuration proxy instances in sequence.
     *
     * @param args program arguments
     * @throws Exception if not able to get list of available instances
     */
    @Override
    public void run(String... args) throws Exception {
        List<String> instances;

        if (args.length > 0) {
            instances = Arrays.asList(args);
            log.debug("Instances from args: {}", instances);
        } else {
            instances = ConfProxyHelper.availableInstances();
            log.debug("Instances from available instances: {}", instances);
        }

        for (String instance : instances) {
            try {
                ConfProxy proxy = new ConfProxy(signerRpcClient, instance);
                log.info("ConfProxy executing for instance {}", instance);
                proxy.execute();
            } catch (Exception ex) {
                log.error("Error when executing configuration-proxy '{}'",
                        instance, ex);
            }
        }
        System.exit(0);
    }
}
