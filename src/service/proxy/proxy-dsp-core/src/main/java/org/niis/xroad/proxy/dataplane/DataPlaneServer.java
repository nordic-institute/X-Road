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
 * <p>
 * Lifecycle: {@link #init()} creates the server and connector; {@link #start()} must be
 * called after all JAX-RS resources are registered via {@link #registerJaxRsResource}.
 */
@Slf4j
@ApplicationScoped
@Startup
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions")
public class DataPlaneServer {

    private final DataPlaneServerProperties dataplaneProperties;

    private Server server;
    private Handler jaxRsHandler;

    /**
     * Creates the dataplane Jetty server and connector. Does not start the server.
     * Call {@link #registerJaxRsResource} followed by {@link #start()} to complete initialization.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing full-data-plane Jetty server..");
        server = createServer(dataplaneProperties);
        createConnector();
    }

    /**
     * Registers the handler and starts the Jetty server.
     * Must be called after all JAX-RS resources have been registered via {@link #registerJaxRsResource}.
     *
     * @throws Exception if the server fails to start
     */
    public void start() throws Exception {
        registerHandler();
        server.start();
        log.info("DataPlane Jetty server started on port {}", dataplaneProperties.listenPort());
    }

    /**
     * Stops the dataplane Jetty server.
     */
    @PreDestroy
    public void destroy() throws Exception {
        server.stop();
    }

    private static Server createServer(DataPlaneServerProperties properties) {
        var threadPool = new QueuedThreadPool(properties.threadPoolMax(), properties.threadPoolMin());
        threadPool.setIdleTimeout(properties.threadPoolIdleTimeout());
        threadPool.setDetailedDump(false);
        return new Server(threadPool);
    }

    private void createConnector() {
        var connector = new ServerConnector(server);
        connector.setName("DataPlaneConnector");
        connector.setPort(dataplaneProperties.listenPort());
        connector.setHost(dataplaneProperties.listenAddress());
        server.addConnector(connector);
    }

    /**
     * Registers a JAX-RS resource on the dataplane Jetty server at the given context path.
     * Must be called before the server is started (i.e. before {@link #start()}).
     *
     * @param contextPath the path prefix for the resource (e.g. "/api/v1/dataflows")
     * @param resource    the JAX-RS annotated resource instance
     */
    public void registerJaxRsResource(String contextPath, Object resource) {
        var resourceConfig = new ResourceConfig();
        // Jackson handles application/json bodies for the signaling POJO types.
        // JSON-P handles the jakarta.json.JsonObject debug-state probe.
        // The lenient ObjectMapper context resolver is needed because EDC's DspDataAddress
        // emits @type with no matching builder setter; without FAIL_ON_UNKNOWN_PROPERTIES=false
        // Jackson 400s the /start body.
        resourceConfig.register(JacksonFeature.class);
        resourceConfig.register(JsonProcessingFeature.class);
        resourceConfig.register(new LenientObjectMapperResolver());
        resourceConfig.register(resource);

        var servletContextHandler = new ServletContextHandler(contextPath);
        var jerseyServlet = new ServletContainer(resourceConfig);
        servletContextHandler.addServlet(new ServletHolder(jerseyServlet), "/*");

        this.jaxRsHandler = servletContextHandler;
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

    private void registerHandler() {
        var sequence = new Handler.Sequence();
        if (jaxRsHandler != null) {
            sequence.addHandler(jaxRsHandler);
        }
        server.setHandler(sequence);
    }
}
