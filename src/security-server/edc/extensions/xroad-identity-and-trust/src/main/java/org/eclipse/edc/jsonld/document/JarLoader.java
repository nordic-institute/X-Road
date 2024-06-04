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

package org.eclipse.edc.jsonld.document;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdErrorCode;
import com.apicatalog.jsonld.StringUtils;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.document.RdfDocument;
import com.apicatalog.jsonld.http.media.MediaType;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import org.eclipse.edc.spi.result.Result;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.util.Optional;
import java.util.function.Function;

/**
 * Enables loading documents from jar files
 */
public class JarLoader implements DocumentLoader {

    @Override
    public Document loadDocument(URI uri, DocumentLoaderOptions options) throws JsonLdError {
        if (!"jar".equalsIgnoreCase(uri.getScheme())) {
            throw new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED, "Unsupported URL scheme [" + uri.getScheme() + "]. JarLoader accepts only jar scheme.");
        }

        try (var is = uri.toURL().openStream()) {
            var document = createDocument(uri)
                    .apply(is)
                    .orElseThrow(f -> new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED, f.getFailureDetail()));
            document.setDocumentUrl(uri);
            return document;

        } catch (NoSuchFileException | FileNotFoundException e) {
            throw new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED, "File not found [" + uri + "]: " + e.getMessage());
        } catch (IOException e) {
            throw new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED, e);
        }
    }

    @NotNull
    private Function<InputStream, Result<Document>> createDocument(URI uri) {
        var type = detectedContentType(uri.getSchemeSpecificPart().toLowerCase())
                .orElse(MediaType.JSON);

        if (JsonDocument.accepts(type)) {
            return jsonDocumentResolver(type);
        }

        if (RdfDocument.accepts(type)) {
            return rdfDocumentResolver(type);
        }

        return s -> Result.failure("cannot read document");
    }

    @NotNull
    private Function<InputStream, Result<Document>> jsonDocumentResolver(MediaType type) {
        return stream -> {
            try {
                return Result.success(JsonDocument.of(type, stream));
            } catch (JsonLdError e) {
                return Result.failure(e.getMessage());
            }
        };
    }

    @NotNull
    private Function<InputStream, Result<Document>> rdfDocumentResolver(MediaType type) {
        return stream -> {
            try {
                return Result.success(RdfDocument.of(type, stream));
            } catch (JsonLdError e) {
                return Result.failure(e.getMessage());
            }
        };
    }

    private Optional<MediaType> detectedContentType(String name) {
        if (name == null || StringUtils.isBlank(name)) {
            return Optional.empty();
        }
        if (name.endsWith(".nq")) {
            return Optional.of(MediaType.N_QUADS);
        }
        if (name.endsWith(".json")) {
            return Optional.of(MediaType.JSON);
        }
        if (name.endsWith(".jsonld")) {
            return Optional.of(MediaType.JSON_LD);
        }
        if (name.endsWith(".html")) {
            return Optional.of(MediaType.HTML);
        }

        return Optional.empty();
    }
}
