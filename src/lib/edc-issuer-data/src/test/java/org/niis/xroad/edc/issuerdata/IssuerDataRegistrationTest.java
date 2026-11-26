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

package org.niis.xroad.edc.issuerdata;

import okhttp3.OkHttpClient;
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.iam.verifiablecredentials.RevocationServiceRegistryExtension;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.RevocationServiceRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.statuslist2021.StatusList2021Status;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;

import java.time.Clock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(DependencyInjectionExtension.class)
class IssuerDataRegistrationTest {

    @Test
    void bitstringStatusListIsServedByTheXRoadServiceAndStatusList2021StaysStock(ServiceExtensionContext context,
                                                                                ObjectFactory factory) {
        context.registerService(ExecutorInstrumentation.class, ExecutorInstrumentation.noop());
        context.registerService(TypeManager.class, new JacksonTypeManager());
        context.registerService(OkHttpClient.class, new OkHttpClient());
        context.registerService(Clock.class, Clock.systemUTC());
        context.registerService(XRoadConfig.class,
                XRoadConfigBuilder.create().register(DspConfigKeys.instance()).build());
        var stockRegistry = factory.constructInstance(RevocationServiceRegistryExtension.class)
                .createRevocationListService(context);
        var registry = spy(stockRegistry);
        context.registerService(RevocationServiceRegistry.class, registry);
        var extension = factory.constructInstance(IssuerDataExtension.class);

        extension.initialize(context);
        extension.shutdown();

        verify(registry).addService(eq(BitstringStatusListStatus.TYPE),
                isA(XRoadBitstringStatusListRevocationService.class));
        verify(registry, never()).addService(eq(StatusList2021Status.TYPE), any());
    }
}
