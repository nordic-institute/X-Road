/**
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
package org.niis.xroad.securityserver.restapi.wsdl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.util.OpenAPIDeserializer;
import lombok.SneakyThrows;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_UNSUPPORTED_OPENAPI_VERSION;

/**
 * Customised parser that throws an exception, instead of returning null,
 * when the provided openapi version is not supported
 */
public class XroadOpenAPIV3Parser extends OpenAPIV3Parser {
    private static final String SUPPORTED_OPENAPI_MINOR_VERSION = "3.0";

    public XroadOpenAPIV3Parser() {
        super();
    }

    @Override
    public SwaggerParseResult parseJsonNode(String path, JsonNode node) {
        return new XroadOpenAPIDeserializer().deserialize(node, path);
    }

    private static class XroadOpenAPIDeserializer extends OpenAPIDeserializer {
        XroadOpenAPIDeserializer() {
            super();
        }

        @SneakyThrows(UnsupportedOpenApiVersionException.class)
        @Override
        public OpenAPI parseRoot(JsonNode node, ParseResult result, String path) {
            String location = "";
            if (node.getNodeType().equals(JsonNodeType.OBJECT)) {
                ObjectNode rootNode = (ObjectNode) node;
                String openapiVersion = getString("openapi", rootNode, true, location, result);

                if (openapiVersion == null) {
                    return null;
                }
                // throw an exception if wrong version
                if (!openapiVersion.startsWith(SUPPORTED_OPENAPI_MINOR_VERSION)) {
                    String errorMsg = String.format("OpenAPI version %s not supported", openapiVersion);
                    throw new UnsupportedOpenApiVersionException(errorMsg,
                            new ErrorDeviation(ERROR_UNSUPPORTED_OPENAPI_VERSION, openapiVersion));
                }
            } else {
                result.invalidType(location, "openapi", "object", node);
                result.invalid();
                return null;
            }
            return super.parseRoot(node, result, path);
        }
    }
}
