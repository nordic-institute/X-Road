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

package org.niis.xroad.edc.extension.jwssigner;

import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.signer.protocol.RpcSignerClient;

import org.eclipse.edc.jwt.signer.spi.JwsSignerProvider;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import static org.niis.xroad.edc.extension.jwssigner.XRoadJWSSignerExtension.NAME;

@SuppressWarnings("checkstyle:MagicNumber")
@Extension(NAME)
public class XRoadJWSSignerExtension implements ServiceExtension {
    static final String NAME = "XRD JWS Signer extension";

    @Override
    public void initialize(ServiceExtensionContext context) {
        loadSystemProperties(context.getMonitor());
        safelyInitSignerClient(context.getMonitor());
    }

    @Provider
    public JwsSignerProvider jwsSignerProvider() {
        return privateKeyAlias -> Result.ofThrowable(() -> new XRoadJWSSigner(privateKeyAlias));
    }

    private void loadSystemProperties(Monitor monitor) {
        monitor.info("Initializing X-Road System Properties..");
        SystemPropertiesLoader.create()
                .withCommonAndLocal()
                .load();
    }

    private void safelyInitSignerClient(Monitor monitor) {
        try {
            var client = RpcSignerClient.getInstance();
            monitor.debug("RPC signer client already initialized. Hash: %s".formatted(client.hashCode()));
        } catch (Exception e) {
            initSignerClient(monitor);
        }
    }

    private void initSignerClient(Monitor monitor) {
        monitor.info("Initializing Signer client");
        try {
            RpcSignerClient.init("localhost", 5560, 10000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
