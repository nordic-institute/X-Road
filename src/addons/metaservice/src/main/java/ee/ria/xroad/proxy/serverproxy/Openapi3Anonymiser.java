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
package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.CachingStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

/*
 * Class for parsing Openapi3 description and replacing strings in servers.url-node with anonymised value
 */
public class Openapi3Anonymiser {

    public static final String SERVERS = "servers";
    public static final String URL = "url";
    private static final ObjectMapper JSONMAPPER = new ObjectMapper(new JsonFactory());
    private static final ObjectMapper YAMLMAPPER =
            new ObjectMapper(new YAMLFactory()).configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    public void anonymiseJson(InputStream input, CachingStream output) throws IOException {
        JsonNode tree = JSONMAPPER.readTree(input);
        handleAnonymising(tree);
        JSONMAPPER.writeValue(output, tree);
    }

    public void anonymiseYaml(InputStream input, CachingStream output) throws IOException {
        JsonNode tree = YAMLMAPPER.readTree(input);
        handleAnonymising(tree);
        YAMLMAPPER.writeValue(output, tree);
    }

    /*
     * Find servers.url section in the openapi3 specification.
     * if the url does not contain variables then replace the url with the path part of the url
     * if the url does contain variables then it is left as is
     */
    private void handleAnonymising(JsonNode tree) {
        final JsonNode openapiVersion = tree.get("openapi");

        // Check openapi version
        if (openapiVersion != null && !openapiVersion.toString().startsWith("\"3.")) {
            throw new CodedException(X_INTERNAL_ERROR,
                    String.format("Incompatible openapi version. Openapi version 3 or greater expected. "
                            + "Given openapi document is of version %s", openapiVersion.toString()));
        }

        final JsonNode servers = tree.get(SERVERS);
        if (servers != null && servers.isArray()) {
            servers.forEach(server -> {
                final String url = server.has("url") ? server.get("url").textValue() : null;
                if (url != null && !urlContainsVariables(url)) {
                    try {
                        URI uri = new URI(url);
                        ((ObjectNode) server).put(URL, uri.getPath());
                    } catch (URISyntaxException e) {
                        throw new CodedException(X_INTERNAL_ERROR,
                                String.format("Can't parse url string: %s", url));
                    }
                }
            });
        }
    }

    // Return true if the url contains '{' and '}' meaning there's a variable in the url
    private boolean urlContainsVariables(String url) {
        return url.contains("{") && url.contains("}");
    }
}
