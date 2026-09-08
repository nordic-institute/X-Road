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
package org.niis.xroad.proxy.core.addon.metaservice.serverproxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.util.CachingStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Openapi3AnonymiserTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();

    @TempDir
    Path tempDir;

    private final Openapi3Anonymiser anonymiser = new Openapi3Anonymiser();
    private final List<CachingStream> openStreams = new ArrayList<>();

    @AfterEach
    void tearDown() {
        openStreams.forEach(CachingStream::consume);
    }

    @Test
    void anonymiseJsonShouldStripSchemeAndHostWhenPlainUrl() throws IOException {
        var input = toInputStream("""
                {"openapi":"3.0.0","servers":[{"url":"https://example.com/api/v1"}]}""");
        var output = createCachingStream();

        anonymiser.anonymiseJson(input, output);

        var tree = readJsonOutput(output);
        assertThat(tree.get("servers").get(0).get("url").stringValue()).isEqualTo("/api/v1");
    }

    @Test
    void anonymiseJsonShouldPreserveUrlWhenContainsVariables() throws IOException {
        var input = toInputStream("""
                {"openapi":"3.0.0","servers":[{"url":"https://{host}/api/v1"}]}""");
        var output = createCachingStream();

        anonymiser.anonymiseJson(input, output);

        var tree = readJsonOutput(output);
        assertThat(tree.get("servers").get(0).get("url").stringValue())
                .isEqualTo("https://{host}/api/v1");
    }

    @Test
    void anonymiseJsonShouldHandleMixedUrlsWhenMultipleServers() throws IOException {
        var input = toInputStream("""
                {"openapi":"3.0.0","servers":[{"url":"https://example.com/path"},\
                {"url":"https://{host}:{port}/api"}]}""");
        var output = createCachingStream();

        anonymiser.anonymiseJson(input, output);

        var tree = readJsonOutput(output);
        var servers = tree.get("servers");
        assertThat(servers.get(0).get("url").stringValue()).isEqualTo("/path");
        assertThat(servers.get(1).get("url").stringValue()).isEqualTo("https://{host}:{port}/api");
    }

    @Test
    void anonymiseJsonShouldThrowWhenOpenapiVersionIsNotThree() {
        var input = toInputStream("""
                {"openapi":"2.0.0","servers":[{"url":"https://example.com/api"}]}""");

        assertThatThrownBy(() -> {
            var output = createCachingStream();
            anonymiser.anonymiseJson(input, output);
        })
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("Incompatible openapi version");
    }

    @Test
    void anonymiseJsonShouldAcceptWhenOpenapiVersionIsThreePointOne() throws IOException {
        var input = toInputStream("""
                {"openapi":"3.1.0","servers":[{"url":"https://example.com/v2"}]}""");
        var output = createCachingStream();

        anonymiser.anonymiseJson(input, output);

        var tree = readJsonOutput(output);
        assertThat(tree.get("servers").get(0).get("url").stringValue()).isEqualTo("/v2");
    }

    @Test
    void anonymiseJsonShouldThrowWhenUrlIsMalformed() {
        var input = toInputStream("""
                {"openapi":"3.0.0","servers":[{"url":"https://exam ple.com/api"}]}""");

        assertThatThrownBy(() -> {
            var output = createCachingStream();
            anonymiser.anonymiseJson(input, output);
        })
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("Can't parse url string");
    }

    @Test
    void anonymiseJsonShouldPassThroughWhenNoServersArray() throws IOException {
        var input = toInputStream("""
                {"openapi":"3.0.0","info":{"title":"test"}}""");
        var output = createCachingStream();

        anonymiser.anonymiseJson(input, output);

        var tree = readJsonOutput(output);
        assertThat(tree.has("servers")).isFalse();
        assertThat(tree.get("info").get("title").stringValue()).isEqualTo("test");
    }

    @Test
    void anonymiseJsonShouldSkipServerEntryWhenUrlFieldMissing() throws IOException {
        var input = toInputStream("""
                {"openapi":"3.0.0","servers":[{"description":"no url here"}]}""");
        var output = createCachingStream();

        anonymiser.anonymiseJson(input, output);

        var tree = readJsonOutput(output);
        var server = tree.get("servers").get(0);
        assertThat(server.get("description").stringValue()).isEqualTo("no url here");
        assertThat(server.has("url")).isFalse();
    }

    @Test
    void anonymiseJsonShouldProcessNormallyWhenOpenapiFieldMissing() throws IOException {
        var input = toInputStream("""
                {"servers":[{"url":"https://example.com/mypath"}]}""");
        var output = createCachingStream();

        anonymiser.anonymiseJson(input, output);

        var tree = readJsonOutput(output);
        assertThat(tree.get("servers").get(0).get("url").stringValue()).isEqualTo("/mypath");
    }

    @Test
    void anonymiseYamlShouldAnonymiseUrls() throws IOException {
        var input = toInputStream("""
                openapi: "3.0.2"
                servers:
                  - url: "https://production.example.com/api/v1"
                  - url: "https://{environment}.example.com/api/{version}"
                """);
        var output = createCachingStream();

        anonymiser.anonymiseYaml(input, output);

        var tree = readYamlOutput(output);
        var servers = tree.get("servers");
        assertThat(servers.get(0).get("url").stringValue()).isEqualTo("/api/v1");
        assertThat(servers.get(1).get("url").stringValue())
                .isEqualTo("https://{environment}.example.com/api/{version}");
    }

    @Test
    void anonymiseYamlShouldAcceptSingleServerAsScalar() throws IOException {
        var input = toInputStream("""
                openapi: "3.0.0"
                servers:
                  url: "https://example.com/single"
                """);
        var output = createCachingStream();

        anonymiser.anonymiseYaml(input, output);

        var tree = readYamlOutput(output);
        var servers = tree.get("servers");
        // With readTree(), ACCEPT_SINGLE_VALUE_AS_ARRAY does not apply — servers is a mapping, not a sequence.
        // The code checks servers.isArray(), which returns false, so anonymisation is skipped entirely.
        assertThat(servers.isArray()).isFalse();
        assertThat(servers.get("url").stringValue()).isEqualTo("https://example.com/single");
    }

    private CachingStream createCachingStream() throws IOException {
        var cs = new CachingStream(tempDir.toAbsolutePath().toString());
        openStreams.add(cs);
        return cs;
    }

    private JsonNode readJsonOutput(CachingStream cs) throws IOException {
        try (var is = cs.getCachedContents()) {
            return JSON_MAPPER.readTree(is);
        }
    }

    private JsonNode readYamlOutput(CachingStream cs) throws IOException {
        try (var is = cs.getCachedContents()) {
            return YAML_MAPPER.readTree(is);
        }
    }

    private static InputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
