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
package org.niis.xroad.proxy.core.clientproxy;

import lombok.RequiredArgsConstructor;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Handler;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;

import java.lang.reflect.Constructor;

@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions")
final class HandlerLoader {
    private final CommonBeanProxy commonBeanProxy;


    Handler loadHandler(String className, HttpClient client)
            throws Exception {
        try {
            Class<? extends Handler> handlerClass = getHandlerClass(className);
            return instantiate(handlerClass, client);
        } catch (Exception e) {
            throw XrdRuntimeException.systemInternalError("Cannot load handler for name '" + className + "'", e);
        }
    }

    private Handler instantiate(Class<? extends Handler> handlerClass,
                                HttpClient client) throws Exception {
        try {
            Constructor<? extends Handler> constructor =
                    handlerClass.getConstructor(
                            CommonBeanProxy.class,
                            HttpClient.class);
            return constructor.newInstance(commonBeanProxy, client);
        } catch (NoSuchMethodException e) {
            throw new Exception("Handler must have constructor taking "
                    + "1 parameter (" + HttpClient.class + ")", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Handler> getHandlerClass(String className)
            throws Exception {
        Class<?> clazz = Class.forName(className);
        if (Handler.class.isAssignableFrom(clazz)) {
            return (Class<? extends Handler>) clazz;
        } else {
            throw new Exception("Handler must implement " + Handler.class);
        }
    }
}
