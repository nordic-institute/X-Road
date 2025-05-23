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
package ee.ria.xroad.common.util;

import ee.ria.xroad.common.HttpStatus;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Service that listens for administrative commands on a specific port.
 */
public class AdminPort {

    /**
     * Base class for AdminPort callbacks
     */
    public abstract static class AdminPortCallback {
        public abstract void handle(RequestWrapper request, ResponseWrapper response) throws Exception;
    }

    /**
     * Synchronous AdminPort callback interface.
     */
    public abstract static class SynchronousCallback extends AdminPortCallback {
    }

    private static final Logger LOG = LoggerFactory.getLogger(AdminPort.class);

    private static final String CONNECTOR_HOST = "127.0.0.1";

    private final int portNumber;

    private final Server server = new Server();

    private final Map<String, AdminPortCallback> handlers = new HashMap<>();

    /**
     * Constructs an AdminPort instance that listens for commands on the given port number.
     *
     * @param portNumber the port number AdminPort will listen on
     */
    public AdminPort(int portNumber) {
        this.portNumber = portNumber;

        createAdminConnector();
    }

    @PostConstruct
    public void afterPropertiesSet() throws Exception {
        LOG.info("Started AdminPort on port {}", portNumber);

        server.start();
    }

    @PreDestroy
    public void destroy() throws Exception {
        server.stop();
    }

    /**
     * Registers a synchronous callback for the given command string.
     *
     * @param target  the command string
     * @param handler the synchronous callback that should be executed when the command is issued
     */
    public void addHandler(String target, SynchronousCallback handler) {
        handlers.put(target, handler);
    }

    private void createAdminConnector() {
        ServerConnector connector = new ServerConnector(server);

        connector.setName("AdminPort");
        connector.setHost(CONNECTOR_HOST);
        connector.setPort(portNumber);

        server.addConnector(connector);

        var handlerCollection = new Handler.Sequence();
        handlerCollection.addHandler(new AdminHandler());

        server.setHandler(handlerCollection);
    }

    private final class AdminHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!CONNECTOR_HOST.equals(Request.getRemoteAddr(request))) {
                response.setStatus(HttpStatus.SC_FORBIDDEN);
                callback.succeeded();
            } else {
                final var target = request.getHttpURI().getPath();
                LOG.info("Admin request: {}", target);
                try {
                    AdminPortCallback handler = handlers.get(target);
                    if (handler != null) {
                        if (handler instanceof SynchronousCallback) {
                            handler.handle(RequestWrapper.of(request), ResponseWrapper.of(response));
                        } else {
                            LOG.warn("Unknown handler detected for target '{}', skipping handling delegation", target);
                        }
                    } else {
                        response.setStatus(HttpStatus.SC_NOT_FOUND);
                    }
                    callback.succeeded();
                } catch (Exception e) {
                    LOG.error("Handler got error", e);
                    response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                    Content.Sink.write(response, true, e.toString(), callback);
                }
            }

            return Boolean.TRUE;
        }
    }
}
