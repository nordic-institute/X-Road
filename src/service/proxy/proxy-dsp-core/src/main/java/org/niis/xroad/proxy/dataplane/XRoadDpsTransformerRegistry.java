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
package org.niis.xroad.proxy.dataplane;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.datatype.jsonp.JSONPModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.json.Json;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromDataPlaneInstanceTransformer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

/**
 * CDI producer that creates a standalone {@link TypeTransformerRegistry} pre-configured with
 * the single Data Plane Signaling (DPS) JSON-LD transformer still needed by the proxy.
 * <p>
 * The registry is instantiated directly via {@code new TypeTransformerRegistryImpl()} — no
 * EDC {@code BaseRuntime} or {@code ServiceExtensionContext} is required.
 */
@ApplicationScoped
public class XRoadDpsTransformerRegistry {

    /**
     * Produces a {@link TypeTransformerRegistry} containing the outbound registration transformer.
     * <p>
     * Provides {@code DataPlaneInstance → JsonObject} for outbound control-plane
     * registration POSTs ({@code POST /v1/dataplanes}).
     *
     * @return configured transformer registry singleton
     */
    @Produces
    @ApplicationScoped
    public TypeTransformerRegistry registry() {
        var factory = Json.createBuilderFactory(Map.of());
        var objectMapper = new ObjectMapper().registerModule(new JSONPModule());
        var typeManager = new DelegatingTypeManager(objectMapper);
        var registry = new TypeTransformerRegistryImpl();

        // Registration: DataPlaneInstance → JsonObject (outbound POST to /v1/dataplanes)
        registry.register(new JsonObjectFromDataPlaneInstanceTransformer(factory, typeManager, JSON_LD));

        return registry;
    }

    /**
     * Minimal {@link TypeManager} implementation that delegates all object mapping
     * to a single shared Jackson {@link ObjectMapper}.
     * Used only to satisfy the {@link JsonObjectFromDataPlaneInstanceTransformer} constructor —
     * the mapper is only invoked to convert simple property values (strings, maps) to JSON.
     */
    static final class DelegatingTypeManager implements TypeManager {

        private final ObjectMapper mapper;

        DelegatingTypeManager(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public ObjectMapper getMapper() {
            return mapper;
        }

        @Override
        @NotNull
        public ObjectMapper getMapper(String key) {
            return mapper;
        }

        @Override
        public void registerContext(String key, ObjectMapper objectMapper) {
            // not used in proxy
        }

        @Override
        public void registerTypes(Class<?>... type) {
            // not used in proxy
        }

        @Override
        public void registerTypes(NamedType... type) {
            // not used in proxy
        }

        @Override
        public void registerTypes(String key, Class<?>... type) {
            // not used in proxy
        }

        @Override
        public void registerTypes(String key, NamedType... type) {
            // not used in proxy
        }

        @Override
        public <T> void registerSerializer(String key, Class<T> type, JsonSerializer<T> serializer) {
            // not used in proxy
        }

        @Override
        public <T> void registerSerializer(Class<T> type, JsonSerializer<T> serializer) {
            // not used in proxy
        }

        @Override
        public <T> T readValue(String input, TypeReference<T> typeReference) {
            throw new UnsupportedOperationException("Not used in proxy transformer context");
        }

        @Override
        public <T> T readValue(String input, Class<T> type) {
            throw new UnsupportedOperationException("Not used in proxy transformer context");
        }

        @Override
        public <T> T readValue(byte[] bytes, Class<T> type) {
            throw new UnsupportedOperationException("Not used in proxy transformer context");
        }

        @Override
        public String writeValueAsString(Object value) {
            throw new UnsupportedOperationException("Not used in proxy transformer context");
        }

        @Override
        public byte[] writeValueAsBytes(Object value) {
            throw new UnsupportedOperationException("Not used in proxy transformer context");
        }

        @Override
        public String writeValueAsString(Object value, TypeReference<?> reference) {
            throw new UnsupportedOperationException("Not used in proxy transformer context");
        }
    }
}
