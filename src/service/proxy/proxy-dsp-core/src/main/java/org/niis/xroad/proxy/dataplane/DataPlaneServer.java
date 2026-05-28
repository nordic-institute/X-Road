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
package org.niis.xroad.proxy.dataplane;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.jsonp.JsonProcessingFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;

/**
 * CDI bean that owns a separate Jetty Server instance for the data-plane listener.
 * Starts on the configured data-plane port (default 5590) with plain HTTP on localhost.
 */
@Slf4j
@Startup
@ApplicationScoped
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions")
public class DataPlaneServer {

    private static final String SIGNALING_CONTEXT_PATH = "/full/api/";

    private final DataPlaneServerProperties dataplaneProperties;
    private final XRoadDataPlaneSignalingApiController signalingController;

    private Server server;

    @PostConstruct
    public void start() throws Exception {
        log.info("Initializing DataPlane signaling server..");
        server = buildServer();
        addConnector();
        server.setHandler(jaxRsHandler(SIGNALING_CONTEXT_PATH, signalingController));
        server.start();
        log.info("DataPlane signaling server started on port {}", dataplaneProperties.listenPort());
    }

    @PreDestroy
    public void stop() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    Server getServer() {
        return server;
    }

    private Server buildServer() {
        var threadPool = new QueuedThreadPool(dataplaneProperties.threadPoolMax(), dataplaneProperties.threadPoolMin());
        threadPool.setIdleTimeout(dataplaneProperties.threadPoolIdleTimeout());
        threadPool.setDetailedDump(false);
        return new Server(threadPool);
    }

    private void addConnector() {
        var connector = new ServerConnector(server);
        connector.setName("DataPlaneConnector");
        connector.setPort(dataplaneProperties.listenPort());
        connector.setHost(dataplaneProperties.listenAddress());
        server.addConnector(connector);
    }

    private Handler jaxRsHandler(String contextPath, Object resource) {
        var rc = new ResourceConfig();
        // Lenient ObjectMapper required: EDC's DspDataAddress emits @type with no matching
        // builder setter; without FAIL_ON_UNKNOWN_PROPERTIES=false Jackson 400s the /start body.
        rc.register(JacksonFeature.class);
        rc.register(JsonProcessingFeature.class);
        rc.register(new LenientObjectMapperResolver());
        rc.register(resource);
        var ctx = new ServletContextHandler(contextPath);
        ctx.addServlet(new ServletHolder(new ServletContainer(rc)), "/*");
        return ctx;
    }

    @Provider
    static final class LenientObjectMapperResolver implements ContextResolver<ObjectMapper> {
        private final ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        @Override
        public ObjectMapper getContext(Class<?> type) {
            return mapper;
        }
    }
}
