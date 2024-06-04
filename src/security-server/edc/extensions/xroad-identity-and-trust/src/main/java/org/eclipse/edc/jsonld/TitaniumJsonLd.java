/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.jsonld;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import com.apicatalog.jsonld.loader.FileLoader;
import com.apicatalog.jsonld.loader.HttpLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.jsonld.document.JarLoader;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
import org.eclipse.edc.spi.constants.CoreConstants;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createBuilderFactory;
import static jakarta.json.Json.createObjectBuilder;
import static java.util.Optional.ofNullable;

/**
 * Implementation of the {@link JsonLd} interface that uses the Titanium library for all JSON-LD operations.
 */
public class TitaniumJsonLd implements JsonLd {
    private static final Map<String, String> EMPTY_NAMESPACES = Collections.emptyMap();

    private static final Set<String> EMPTY_CONTEXTS = Collections.emptySet();

    private final Monitor monitor;
    private final Map<String, Map<String, String>> scopedNamespaces = new HashMap<>();
    private final Map<String, Set<String>> scopedContexts = new HashMap<>();
    private final CachedDocumentLoader documentLoader;

    public TitaniumJsonLd(Monitor monitor) {
        this(monitor, JsonLdConfiguration.Builder.newInstance().build());
    }

    public TitaniumJsonLd(Monitor monitor, JsonLdConfiguration configuration) {
        this.monitor = monitor;
        this.documentLoader = new CachedDocumentLoader(configuration, monitor);
    }

    @Override
    public Result<JsonObject> expand(JsonObject json) {
        try {
            var document = JsonDocument.of(injectVocab(json));
            var expanded = com.apicatalog.jsonld.JsonLd.expand(document)
                    .options(new JsonLdOptions(documentLoader))
                    .get();
            if (expanded.size() > 0) {
                return Result.success(expanded.getJsonObject(0));
            }
            return Result.failure("Error expanding JSON-LD structure: result was empty, it could be caused by missing '@context'");
        } catch (JsonLdError error) {
            monitor.warning("Error expanding JSON-LD structure", error);
            return Result.failure(error.getMessage());
        }
    }

    @Override
    public Result<JsonObject> compact(JsonObject json, String scope) {
        try {
            var document = JsonDocument.of(json);
            var jsonFactory = createBuilderFactory(Map.of());
            var contextDocument = JsonDocument.of(jsonFactory.createObjectBuilder()
                    .add(JsonLdKeywords.CONTEXT, createContext(scope))
                    .build());
            var compacted = com.apicatalog.jsonld.JsonLd.compact(document, contextDocument)
                    .options(new JsonLdOptions(documentLoader))
                    .get();
            return Result.success(compacted);
        } catch (JsonLdError e) {
            monitor.warning("Error compacting JSON-LD structure", e);
            return Result.failure(e.getMessage());
        }
    }

    @Override
    public void registerNamespace(String prefix, String contextIri, String scope) {
        var namespaces = scopedNamespaces.computeIfAbsent(scope, k -> new LinkedHashMap<>());
        namespaces.put(prefix, contextIri);
    }

    @Override
    public void registerContext(String contextIri, String scope) {
        var contexts = scopedContexts.computeIfAbsent(scope, k -> new LinkedHashSet<>());
        contexts.add(contextIri);
    }

    @Override
    public void registerCachedDocument(String contextUrl, URI uri) {
        documentLoader.register(contextUrl, uri);
    }

    private JsonObject injectVocab(JsonObject json) {
        var jsonObjectBuilder = createObjectBuilder(json);

        //only inject the vocab if the @context is an object, not a URL
        if (json.get(JsonLdKeywords.CONTEXT) instanceof JsonObject) {
            var contextObject = ofNullable(json.getJsonObject(JsonLdKeywords.CONTEXT)).orElseGet(() -> createObjectBuilder().build());
            var contextBuilder = createObjectBuilder(contextObject);
            if (!contextObject.containsKey(JsonLdKeywords.VOCAB)) {
                var newContextObject = contextBuilder
                        .add(JsonLdKeywords.VOCAB, CoreConstants.EDC_NAMESPACE)
                        .build();
                jsonObjectBuilder.add(JsonLdKeywords.CONTEXT, newContextObject);
            }
        }
        return jsonObjectBuilder.build();
    }

    private JsonValue createContext(String scope) {
        var builder = createObjectBuilder();
        // Adds the configured namespaces for * and the input scope
        Stream.concat(namespacesForScope(JsonLd.DEFAULT_SCOPE), namespacesForScope(scope))
                .forEach(entry -> builder.add(entry.getKey(), entry.getValue()));

        // Compute the additional context IRI defined for * and the input scope
        var contexts = Stream.concat(contextsForScope(JsonLd.DEFAULT_SCOPE), contextsForScope(scope))
                .collect(Collectors.toSet());

        var contextObject = builder.build();
        // if not empty we build a JsonArray
        if (!contexts.isEmpty()) {
            var contextArray = createArrayBuilder();
            contexts.forEach(contextArray::add);

            // don't append an empty object
            if (!contextObject.isEmpty()) {
                contextArray.add(contextObject);
            }
            return contextArray.build();
        } else {
            // return only the JsonObject with the namespaces
            return contextObject;
        }
    }

    private Stream<Map.Entry<String, String>> namespacesForScope(String scope) {
        return scopedNamespaces.getOrDefault(scope, EMPTY_NAMESPACES).entrySet().stream();
    }

    private Stream<String> contextsForScope(String scope) {
        return scopedContexts.getOrDefault(scope, EMPTY_CONTEXTS).stream();
    }

    private static class CachedDocumentLoader implements DocumentLoader {

        private final Map<String, URI> uriCache = new HashMap<>();
        private final Map<URI, Document> documentCache = new HashMap<>();
        private final DocumentLoader loader;
        private final Monitor monitor;

        CachedDocumentLoader(JsonLdConfiguration configuration, Monitor monitor) {
            loader = new SchemeRouter()
                    .set("http", configuration.isHttpEnabled() ? HttpLoader.defaultInstance() : null)
                    .set("https", configuration.isHttpsEnabled() ? HttpLoader.defaultInstance() : null)
                    .set("file", new FileLoader())
                    .set("jar", new JarLoader());
            this.monitor = monitor;
        }

        @Override
        public Document loadDocument(URI url, DocumentLoaderOptions options) throws JsonLdError {
            var uri = Optional.of(url.toString())
                    .map(uriCache::get)
                    .orElse(url);
//            return Optional.ofNullable(documentCache.get(uri))
//                    .orElse(loader.loadDocument(uri, options));
            //TODO this fixes loading on every call . Can be removed once fixed in main branch.
            return Optional.ofNullable(documentCache.get(uri))
                    .orElseGet(()-> {
                        System.out.println("Loading document from URI: " + uri);
                        try {
                            return loader.loadDocument(uri, options);
                        } catch (JsonLdError e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        public void register(String contextUrl, URI uri) {
            uriCache.put(contextUrl, uri);
            try {
                documentCache.put(uri, loader.loadDocument(uri, new DocumentLoaderOptions()));
            } catch (JsonLdError e) {
                monitor.warning("Error caching context URL '%s' for URI '%s'. Subsequent attempts to expand this context URL may fail.".formatted(contextUrl, uri));
            }
        }

    }

}
