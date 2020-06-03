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
package ee.ria.xroad.proxyui;

import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Parser for OpenAPI descriptions
 */
@Slf4j
public class OpenApiParser {

    private final URI openApiUrl;
    private static final int BUF_SIZE = 8192;
    private static final long MAX_DESCRIPTION_SIZE = 10 * 1024 * 1024;

    /**
     * Create a OpenAPI parser
     */
    public OpenApiParser(String openApiUrl) throws ParsingException {
        try {
            this.openApiUrl = new URI(openApiUrl);
        } catch (URISyntaxException e) {
            throw new ParsingException("Invalid URL", e);
        }
    }

    /**
     * Parse
     * @return
     */
    public Result parse() throws ParsingException {

        final ParseOptions options = new ParseOptions();
        options.setResolve(false);

        final SwaggerParseResult result = new OpenAPIV3Parser().readContents(readOpenAPIDescription(), null, options);
        validate(result);

        String baseUrl = Optional.ofNullable(result.getOpenAPI().getServers())
                .flatMap(s -> s.stream().findFirst())
                .map(Server::getUrl)
                .orElse("");

        // if the base URL contains parameters, do not try to parse it
        if (!baseUrl.contains("{")) {
            baseUrl = openApiUrl.resolve(baseUrl).toString();
        }

        final List<Operation> operations;
        if (result.getOpenAPI().getPaths() == null) {
            operations = Collections.emptyList();
        } else {
            operations = new ArrayList<>();
            result.getOpenAPI().getPaths().forEach((path, item) ->
                    item.readOperationsMap().forEach((method, operation) ->
                            operations.add(new Operation(method.name(), path.replaceAll("\\{[^/]*}", "*")))));
        }

        return new Result(baseUrl, operations, result.getMessages());
    }

    private void validate(SwaggerParseResult result) throws ParsingException {
        if (result == null || result.getOpenAPI() == null) {
            throw new ParsingException("Unable to parse OpenAPI description from " + openApiUrl);
        }

        final String version = result.getOpenAPI().getOpenapi();
        if (version == null || !version.startsWith("3.")) {
            throw new ParsingException("Unsupported OpenAPI version " + version + ", expected major version 3");
        }
    }

    private String readOpenAPIDescription() throws ParsingException {
        URLConnection conn = null;
        try {
            if (!allowProtocol(openApiUrl.getScheme())) {
                throw new ParsingException("Invalid protocol: " + openApiUrl.getScheme());
            }
            conn = openApiUrl.toURL().openConnection();
            if (conn instanceof HttpURLConnection) {
                HttpUrlConnectionConfig.apply((HttpURLConnection)conn);
            }
            conn.connect();
            try (InputStreamReader in = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                long count = 0;
                int n;
                char[] buf = new char[BUF_SIZE];
                StringBuilder builder = new StringBuilder();
                while ((n = in.read(buf)) != -1) {
                    count += n;
                    if (count > MAX_DESCRIPTION_SIZE) {
                        throw new ParsingException(
                                "Error reading OpenAPI description: Size exceeds " + MAX_DESCRIPTION_SIZE + " bytes.");
                    }
                    builder.append(buf, 0, n);
                }
                return builder.toString();
            }
        } catch (UnknownHostException e) {
            throw new ParsingException("Error reading OpenAPI description: Unknown or invalid host name", e);
        } catch (IOException e) {
            throw new ParsingException("Error reading OpenAPI description: " + e.getMessage(), e);
        } finally {
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection)conn).disconnect();
            }
        }
    }

    // package private for testability
    boolean allowProtocol(String protocol) {
        return "http".equals(protocol) || "https".equals(protocol);
    }

    /**
     * OpenAPI parsing result
     */
    @Value
    public static class Result {

        private final String baseUrl;
        private final List<Operation> operations;
        private final List<String> warnings;

        /**
         * @return true if the result has non-fatal errors
         */
        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }

    }

    /**
     * Operation (method + path) in the API
     */
    @Value
    public static class Operation {
        private final String method;
        private final String path;
    }

    /**
     * OpenAPI Parsing Exception
     */
    public static class ParsingException extends Exception {
        public ParsingException() {
        }

        public ParsingException(String message) {
            super(message);
        }

        public ParsingException(String message, Throwable cause) {
            super(message, cause);
        }

        public ParsingException(Throwable cause) {
            super(cause);
        }
    }

}
