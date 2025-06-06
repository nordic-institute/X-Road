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
package org.niis.xroad.securityserver.restapi.wsdl;

import org.junit.Assert;
import org.junit.Test;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for OpenAPIParser
 */
public class OpenApiParserTest {
    private final HttpUrlConnectionConfig httpUrlConnectionConfig = new HttpUrlConnectionConfig(mock(ServerConfProvider.class));

    @Test
    public void shouldParseOpenApiYaml() throws OpenApiParser.ParsingException, UnsupportedOpenApiVersionException {
        URL url = getClass().getResource("/openapiparser/valid.yaml");
        final OpenApiParser.Result result = new TestOpenApiParser().parse(url.toString());
        assertFalse(result.hasWarnings());
        assertEquals("https://example.org/api", result.baseUrl());
    }

    @Test
    public void shouldParseOpenApi31Yaml() throws OpenApiParser.ParsingException, UnsupportedOpenApiVersionException {
        URL url = getClass().getResource("/openapiparser/v310.yaml");
        final OpenApiParser.Result result = new TestOpenApiParser().parse(url.toString());
        assertFalse(result.hasWarnings());
        assertEquals("https://example.org/api", result.baseUrl());
        assertEquals(3, result.operations().size());
    }

    @Test
    public void shouldHaveWarnings() throws OpenApiParser.ParsingException, UnsupportedOpenApiVersionException {
        URL url = getClass().getResource("/openapiparser/warnings.yml");
        final OpenApiParser.Result result = new TestOpenApiParser().parse(url.toString());
        Assert.assertTrue(result.hasWarnings());
        assertEquals("https://{securityserver}/r1", result.baseUrl());
    }

    @Test
    public void shouldParseOpenApiJson() throws OpenApiParser.ParsingException, UnsupportedOpenApiVersionException {
        URL url = getClass().getResource("/openapiparser/valid.json");
        final OpenApiParser.Result result = new TestOpenApiParser().parse(url.toString());
        assertFalse(result.hasWarnings());
        assertEquals("https://example.org/api", result.baseUrl());
    }

    @Test
    public void shouldParseOpenApi31Json() throws OpenApiParser.ParsingException, UnsupportedOpenApiVersionException {
        URL url = getClass().getResource("/openapiparser/v310.json");
        final OpenApiParser.Result result = new TestOpenApiParser().parse(url.toString());
        assertFalse(result.hasWarnings());
        assertEquals("https://example.org/api", result.baseUrl());
        assertEquals(3, result.operations().size());
    }

    @Test(expected = OpenApiParser.ParsingException.class)
    public void shouldFailIfInvalidProtocol() throws OpenApiParser.ParsingException,
            UnsupportedOpenApiVersionException {
        URL url = getClass().getResource("/openapiparser/valid.json");
        final OpenApiParser.Result result = new OpenApiParser(httpUrlConnectionConfig).parse(url.toString());
    }

    @Test(expected = OpenApiParser.ParsingException.class)
    public void shouldFailIfDuplicateEndpoint() throws OpenApiParser.ParsingException,
            UnsupportedOpenApiVersionException {
        URL url = getClass().getResource("/openapiparser/duplicateendpoint.yaml");
        final OpenApiParser.Result result = new OpenApiParser(httpUrlConnectionConfig).parse(url.toString());
    }

    @Test(expected = UnsupportedOpenApiVersionException.class)
    public void shouldFailOnUnsupportedOpenApiVersionYaml() throws OpenApiParser.ParsingException,
            UnsupportedOpenApiVersionException {
        URL url = getClass().getResource("/openapiparser/invalid_version.yaml");
        new TestOpenApiParser().parse(url.toString());
    }

    @Test(expected = UnsupportedOpenApiVersionException.class)
    public void shouldFailOnUnsupportedOpenApiVersionJson() throws OpenApiParser.ParsingException,
            UnsupportedOpenApiVersionException {
        URL url = getClass().getResource("/openapiparser/invalid_version.json");
        new TestOpenApiParser().parse(url.toString());
    }

    class TestOpenApiParser extends OpenApiParser {

        TestOpenApiParser() throws ParsingException {
            super(httpUrlConnectionConfig);
        }

        @Override
        public boolean allowProtocol(String protocol) {
            return true;
        }
    }

}
