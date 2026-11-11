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
package org.niis.xroad.ds.identityhub.sts;

import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.identityhub.spi.webcontext.IdentityHubApiContext;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.apiversion.ApiVersionService;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class XRoadStsApiConfigurationExtensionTest {

    private final PortMappingRegistry portMappingRegistry = mock();
    private final ApiVersionService apiVersionService = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(PortMappingRegistry.class, portMappingRegistry);
        context.registerService(ApiVersionService.class, apiVersionService);
    }

    @Test
    void initializeRegistersUpstreamDefaultPortMapping(ServiceExtensionContext context, ObjectFactory factory) {
        when(context.getConfig()).thenReturn(ConfigFactory.empty());

        factory.constructInstance(XRoadStsApiConfigurationExtension.class).initialize(context);

        verify(portMappingRegistry).register(new PortMapping(IdentityHubApiContext.STS, 9292, "/api/sts"));
    }

    @Test
    void initializeRegistersConfiguredPortMapping(ServiceExtensionContext context, ObjectFactory factory) {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(
                Map.of("web.http.sts.port", "7184", "web.http.sts.path", "/api/custom-sts")));

        factory.constructInstance(XRoadStsApiConfigurationExtension.class).initialize(context);

        verify(portMappingRegistry).register(new PortMapping(IdentityHubApiContext.STS, 7184, "/api/custom-sts"));
    }

    @Test
    void initializeRegistersStsVersionRecordFromUpstreamVersionFile(ServiceExtensionContext context, ObjectFactory factory) {
        var versionFile = new AtomicReference<String>();
        doAnswer(invocation -> {
            versionFile.set(new String(invocation.<InputStream>getArgument(1).readAllBytes(), StandardCharsets.UTF_8));
            return null;
        }).when(apiVersionService).registerVersionInfo(eq(IdentityHubApiContext.STS), any());
        when(context.getConfig()).thenReturn(ConfigFactory.empty());

        factory.constructInstance(XRoadStsApiConfigurationExtension.class).initialize(context);

        assertThat(versionFile.get()).contains("\"version\"").contains("\"urlPath\"");
    }
}
