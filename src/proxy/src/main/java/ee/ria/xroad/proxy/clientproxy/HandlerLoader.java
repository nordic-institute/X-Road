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
package ee.ria.xroad.proxy.clientproxy;

import java.lang.reflect.Constructor;

import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Handler;

final class HandlerLoader {

    private HandlerLoader() {
    }

    static Handler loadHandler(String className, HttpClient client)
            throws Exception {
        try {
            Class<? extends Handler> handlerClass = getHandlerClass(className);
            return instantiate(handlerClass, client);
        } catch (Exception e) {
            throw new RuntimeException("Cannot load handler for name '"
                    + className + "'", e);
        }
    }

    private static Handler instantiate(Class<? extends Handler> handlerClass,
            HttpClient client) throws Exception {
        try {
            Constructor<? extends Handler> constructor =
                    handlerClass.getConstructor(HttpClient.class);
            return constructor.newInstance(client);
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
