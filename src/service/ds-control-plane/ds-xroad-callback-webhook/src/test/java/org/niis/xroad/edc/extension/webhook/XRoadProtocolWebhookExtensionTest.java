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

import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.connector.core.profile.DataspaceProfileContextRegistryImpl;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.protocol.spi.ProtocolWebhookResolver;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class XRoadProtocolWebhookExtensionTest {

    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(GlobalConfProvider.class, mock(GlobalConfProvider.class));
        context.registerService(ServerConfProvider.class, mock(ServerConfProvider.class));
        context.registerService(DataspaceProfileContextRegistry.class, new DataspaceProfileContextRegistryImpl());
        context.registerService(ProtocolWebhookResolver.class, (participantContextId, protocol) -> null);
        context.registerService(Monitor.class, new Monitor() {
            @Override
            public void warning(String message, Throwable... errors) {
                warnings.add(message);
            }
        });
    }

    @Test
    void legacyCallbackAddressSettingYieldsOneWarning(ServiceExtensionContext context, ObjectFactory factory) {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(
                Map.of(XRoadProtocolWebhookExtension.LEGACY_CALLBACK_ADDRESS_SETTING, "https://localhost:8183/api/dsp/%s")));

        factory.constructInstance(XRoadProtocolWebhookExtension.class).initialize(context);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains(XRoadProtocolWebhookExtension.LEGACY_CALLBACK_ADDRESS_SETTING);
    }

    @Test
    void absentLegacySettingYieldsNoWarning(ServiceExtensionContext context, ObjectFactory factory) {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of()));

        factory.constructInstance(XRoadProtocolWebhookExtension.class).initialize(context);

        assertThat(warnings).isEmpty();
    }
}
