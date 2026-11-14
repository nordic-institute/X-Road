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
package org.niis.xroad.edc.extension.webhook;

import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.protocol.spi.ProtocolWebhookResolver;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;

import static org.niis.xroad.edc.extension.webhook.XRoadProtocolWebhookExtension.NAME;

/**
 * Replaces EDC's protocol webhook resolver, so the callback address every negotiation, transfer and catalog response
 * carries is the DSP base URL published in globalconf for this Security Server.
 *
 * <p>The {@code @Inject ProtocolWebhookResolver} field makes EDC's dependency resolver load this extension after the
 * extension providing EDC's own resolver, so that this resolver is the registered one.
 */
@Extension(NAME)
public class XRoadProtocolWebhookExtension implements ServiceExtension {

    static final String NAME = "X-Road Protocol Webhook";

    static final String LEGACY_CALLBACK_ADDRESS_SETTING = "edc.dsp.callback.address";

    @Inject
    @SuppressWarnings("unused")
    private ProtocolWebhookResolver defaultResolver;

    @Inject
    private GlobalConfProvider globalConfProvider;

    @Inject
    private ServerConfProvider serverConfProvider;

    @Inject
    private DataspaceProfileContextRegistry profileRegistry;

    @Inject
    private Monitor monitor;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        if (context.getConfig().hasKey(LEGACY_CALLBACK_ADDRESS_SETTING)) {
            monitor.warning("Setting '%s' is ignored: the DSP callback address is the DSP base URL published in global configuration"
                    .formatted(LEGACY_CALLBACK_ADDRESS_SETTING));
        }
    }

    @Provider
    public ProtocolWebhookResolver protocolWebhookResolver() {
        return new XRoadProtocolWebhookResolver(globalConfProvider, serverConfProvider, profileRegistry, monitor);
    }
}
