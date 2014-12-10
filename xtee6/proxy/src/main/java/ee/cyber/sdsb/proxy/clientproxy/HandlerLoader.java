package ee.cyber.sdsb.proxy.clientproxy;

import java.lang.reflect.Constructor;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Handler;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class HandlerLoader {

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
                    + "1 parameter (" + HttpClient.class + ")");
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
