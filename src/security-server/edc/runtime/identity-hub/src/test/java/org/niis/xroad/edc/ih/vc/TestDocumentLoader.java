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
package org.niis.xroad.edc.ih.vc;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;

import java.io.IOException;
import java.net.URI;

/**
 * JSON-LD document loader that allows to "redirect" the loading of remote documents (contexts,...).
 * For example, referencing a remote context, or a remote verificationMethod would fail, if that document doesn't exist, but we need it
 * for testing, so we can "redirect" the pointer to the local test resources folder.
 */
public class TestDocumentLoader implements DocumentLoader {
    private final String base;
    private final DocumentLoader baseLoader;
    private final String resourcePath;

    public TestDocumentLoader(String base, String resourcePath, DocumentLoader baseLoader) {
        this.base = base;
        this.resourcePath = resourcePath;
        this.baseLoader = baseLoader;
    }

    @Override
    public Document loadDocument(URI uri, DocumentLoaderOptions options) throws JsonLdError {
        Document document;
        var url = uri.toString();
        if (url.startsWith(base)) {
            try (var is = Thread.currentThread().getContextClassLoader().getResourceAsStream(rewrite(uri))) {
                document = JsonDocument.of(is);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } else {
            document = baseLoader.loadDocument(uri, options);
        }
        return document;
    }

    private String rewrite(URI url) {
        var path = resourcePath + url.toString().replace(base, "");
        if (!path.endsWith(".json")) {
            path += ".json";
        }
        return path;
    }
}
