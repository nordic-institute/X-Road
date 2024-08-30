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
package ee.ria.xroad.proxy.testutil;

import ee.ria.xroad.common.util.StartStop;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;

import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.JettyUtils.getContentType;
import static ee.ria.xroad.common.util.JettyUtils.setContentType;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.io.Content.Sink.asOutputStream;
import static org.eclipse.jetty.io.Content.Source.asInputStream;

/**
 * Test service
 */
@Slf4j
public class TestService implements StartStop {

    private final Server server = new Server();

    private Handler handler;
    private Throwable exception;

    /**
     * Handler
     */
    @FunctionalInterface
    public interface Handler {
        void handle(Request request, Response response) throws Exception;
    }

    /**
     * Create a dummy service returning the specified message
     */
    public TestService(int port) {
        try {
            setupConnectors(port);
            handler = ECHO;
            exception = null;
            server.setHandler(new ServiceHandler());
            server.setStopTimeout(1000);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    private void setupConnectors(int port) throws Exception {
        ServerConnector connector = new ServerConnector(server);
        connector.setName("dummy-rest-service");
        connector.setHost("127.0.0.1");
        connector.setPort(port);
        connector.getConnectionFactories().stream()
                .filter(HttpConnectionFactory.class::isInstance)
                .map(HttpConnectionFactory.class::cast)
                .forEach(httpCf -> {
                    Optional.ofNullable(httpCf.getHttpConfiguration().getCustomizer(SecureRequestCustomizer.class))
                            .ifPresent(customizer -> {
                                customizer.setSniHostCheck(false);
                            });
                });
        server.addConnector(connector);
    }

    @Override
    public synchronized void start() throws Exception {
        server.start();
    }

    @Override
    public synchronized void stop() throws Exception {
        server.stop();
    }

    @Override
    public synchronized void join() throws InterruptedException {
        server.join();
    }

    /**
     * setup service
     */
    public synchronized void before() {
        handler = ECHO;
        exception = null;
    }

    public synchronized void setHandler(Handler handler) {
        this.handler = handler;
    }

    /**
     * checks if the handler was successful
     */
    public synchronized void assertOk() throws Throwable {
        if (exception != null) {
            throw exception;
        }
    }

    private final class ServiceHandler extends org.eclipse.jetty.server.Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            log.debug("Service simulator received request {}, contentType={}", request.getHttpURI().getPath(), getContentType(request));
            synchronized (TestService.this) {
                try {
                    handler.handle(request, response);
                    callback.succeeded();
                } catch (Throwable t) {
                    exception = t;
                    log.error("Error when handling request", t);
                    response.setStatus(INTERNAL_SERVER_ERROR_500);
                    Content.Sink.write(response, true, UTF_8.encode(t.getMessage()));
                    callback.failed(t);
                }
                return true;
            }
        }
    }

    public static final Handler ECHO = (request, response) -> {
        setContentType(response, getContentType(request));
        IOUtils.copy(asInputStream(request), asOutputStream(response));
    };
}
