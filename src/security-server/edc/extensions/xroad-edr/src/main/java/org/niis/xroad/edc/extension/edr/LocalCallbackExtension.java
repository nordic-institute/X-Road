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

package org.niis.xroad.edc.extension.edr;

import org.eclipse.edc.connector.spi.callback.CallbackProtocolResolverRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.edc.extension.edr.callback.LocalCallbackMessageDispatcherImpl;
import org.niis.xroad.edc.extension.edr.callback.LocalCallbackRegistryImpl;

import static org.niis.xroad.edc.extension.edr.callback.LocalCallbackMessageDispatcherImpl.CALLBACK_EVENT_LOCAL;

@Extension(value = LocalCallbackExtension.NAME)
@Provides(LocalCallbackRegistryImpl.class)
public class LocalCallbackExtension implements ServiceExtension {

    static final String NAME = "Local callback extension";

    @Inject
    private CallbackProtocolResolverRegistry callbackResolverRegistry;
    @Inject
    private RemoteMessageDispatcherRegistry remoteMessageDispatcherRegistry;

    private final LocalCallbackRegistryImpl localCallbackRegistry = new LocalCallbackRegistryImpl();

    @Override
    public void initialize(ServiceExtensionContext context) {

        callbackResolverRegistry.registerResolver(this::resolveProtocol);
        remoteMessageDispatcherRegistry.register(new LocalCallbackMessageDispatcherImpl(localCallbackRegistry));

        context.registerService(LocalCallbackRegistryImpl.class, localCallbackRegistry);

    }

    private String resolveProtocol(String scheme) {
        return scheme.equalsIgnoreCase("local") ? CALLBACK_EVENT_LOCAL : null;
    }

}
