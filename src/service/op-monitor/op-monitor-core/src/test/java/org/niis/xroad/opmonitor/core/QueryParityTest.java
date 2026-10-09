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
package org.niis.xroad.opmonitor.core;

import ee.ria.xroad.common.util.HttpSender;

import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.jetty.http.HttpParser;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.niis.xroad.opmonitor.api.OpMonitoringDaemonEndpoints.QUERY_DATA_PATH;

/**
 * Sends each query to the HTTP query handler through an {@link HttpSender} and to the gRPC query method, and compares
 * the answers. Only the MIME boundary and the fault detail, which differ in every answer, are normalised. The content
 * type of the queries is the one the server proxy gives a plain SOAP query. The HTTP query handler is started at the
 * first query, after the test has prepared the query, because a query case may change the record manager.
 */
class QueryParityTest extends AbstractQueryTest {
    private static final String CONTENT_TYPE_FIELD_PREFIX = "Content-Type: ";
    static final String RECORD_FIXTURES_ENVIRONMENT_VARIABLE = "RECORD_QUERY_FIXTURES";

    private Server httpServer;
    private int httpPort;

    private void startHttpQueryHandler() throws Exception {
        var handler = new OpMonitorDaemonRequestHandler(OP_MONITOR_PROPERTIES, globalConfProvider, healthMetricRegistry,
                operationalDataRecordManager, operationalDataStore);
        httpServer = new Server();
        var connector = new ServerConnector(httpServer);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        httpServer.addConnector(connector);
        httpServer.setHandler(handler);
        httpServer.start();
        httpPort = connector.getLocalPort();
    }

    @AfterEach
    void stopHttpQueryHandler() throws Exception {
        if (httpServer != null) {
            httpServer.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(QueryCase.class)
    void answerIsEqualToTheHttpQueryHandlerAnswer(QueryCase queryCase) throws Exception {
        var query = prepareQuery(queryCase);

        var viaHttp = queryOverHttp(query);
        var viaGrpc = queryOverGrpc(query);

        var normalisedViaHttp = QueryResponses.normalised(viaHttp);
        var normalisedViaGrpc = QueryResponses.normalised(viaGrpc);
        assertThat(normalisedViaGrpc.contentType()).isEqualTo(normalisedViaHttp.contentType());
        assertThat(normalisedViaGrpc.body()).isEqualTo(normalisedViaHttp.body());
        assertThat(QueryResponses.hasFaultDetail(viaHttp)).isEqualTo(queryCase.name().startsWith("FAULT_"));
        assertThat(QueryResponses.hasFaultDetail(viaGrpc)).isEqualTo(queryCase.name().startsWith("FAULT_"));
    }

    @ParameterizedTest
    @EnumSource(QueryCase.class)
    void contentTypeAndPartHeadersAreSpelledAsTheHttpQueryHandlerSpellsThem(QueryCase queryCase) throws Exception {
        var query = prepareQuery(queryCase);

        var viaHttp = queryOverHttp(query);
        var viaGrpc = queryOverGrpc(query);

        var expectedContentType = QueryResponses.expectedContentType(queryCase);
        assertThat(QueryResponses.normalised(viaHttp).contentType()).isEqualTo(expectedContentType);
        assertThat(QueryResponses.normalised(viaGrpc).contentType()).isEqualTo(expectedContentType);
        var expectedPartHeaders = QueryResponses.expectedPartHeaders(queryCase);
        assertThat(QueryResponses.partHeaders(viaHttp)).isEqualTo(expectedPartHeaders);
        assertThat(QueryResponses.partHeaders(viaGrpc)).isEqualTo(expectedPartHeaders);
    }

    @ParameterizedTest
    @EnumSource(QueryCase.class)
    void soapElementsAndMimePartsAreInTheSameOrderAsInTheHttpQueryHandlerAnswer(QueryCase queryCase) throws Exception {
        var query = prepareQuery(queryCase);

        var viaHttp = queryOverHttp(query);
        var viaGrpc = queryOverGrpc(query);

        var expectedElements = QueryResponses.expectedSoapElements(queryCase);
        assertThat(QueryResponses.soapElements(viaHttp)).containsExactlyElementsOf(expectedElements);
        assertThat(QueryResponses.soapElements(viaGrpc)).containsExactlyElementsOf(expectedElements);
        assertThat(QueryResponses.partHeaders(viaGrpc)).containsExactlyElementsOf(QueryResponses.partHeaders(viaHttp));
    }

    @Test
    void everyContentTypeTheHttpServerSpellsInLowerCaseIsSpelledSoByTheGrpcQueryMethod() {
        var cachedContentTypes = HttpParser.CACHE.keySet().stream()
                .filter(key -> key.regionMatches(true, 0, CONTENT_TYPE_FIELD_PREFIX, 0, CONTENT_TYPE_FIELD_PREFIX.length()))
                .map(key -> key.substring(CONTENT_TYPE_FIELD_PREFIX.length()))
                .filter(value -> !value.isEmpty())
                .toList();

        assertThat(cachedContentTypes).isNotEmpty().allSatisfy(contentType ->
                assertThat(QueryContentType.spell(contentType.toUpperCase(Locale.ROOT))).isEqualTo(contentType));
    }

    @Test
    void queryHandlerFailureIsAnsweredWithSoapFaultByGrpcAndWithoutContentTypeByHttp() throws Exception {
        var query = operationalDataQueryWithInvertedTimeRange();

        var viaGrpc = queryOverGrpc(query);

        assertThat(QueryResponses.normalised(viaGrpc).contentType())
                .isEqualTo(QueryResponses.expectedContentType(QueryCase.FAULT_INVALID_SOAP));
        assertThat(new String(viaGrpc.body(), StandardCharsets.UTF_8))
                .contains("<faultcode>server.serverproxy.opmonitor.client.invalid_request</faultcode>")
                .contains("Records to timestamp is earlier than records from timestamp");
        assertThatThrownBy(() -> queryOverHttp(query))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> assertThat(e.getCode()).endsWith("invalid_content_type"));
    }

    @ParameterizedTest
    @EnumSource(QueryCase.class)
    void recordFixtures(QueryCase queryCase) throws Exception {
        assumeTrue(System.getenv(RECORD_FIXTURES_ENVIRONMENT_VARIABLE) != null,
                RECORD_FIXTURES_ENVIRONMENT_VARIABLE + " is not set");

        var viaHttp = queryOverHttp(prepareQuery(queryCase));

        Files.createDirectories(Path.of(QueryFixtureTest.FIXTURE_DIRECTORY));
        Files.writeString(QueryFixtureTest.fixtureFile(queryCase), QueryResponses.fixtureText(viaHttp), StandardCharsets.UTF_8);
    }

    Answer queryOverHttp(Query query) throws Exception {
        if (httpServer == null) {
            startHttpQueryHandler();
        }
        try (var httpClient = HttpClients.createDefault(); var sender = new HttpSender(httpClient, false)) {
            sender.addHeader("accept-encoding", "");
            var entity = new ByteArrayEntity(query.body());
            entity.setContentType(query.contentType());
            sender.doPost(URI.create("http://127.0.0.1:" + httpPort + QUERY_DATA_PATH), entity);
            return new Answer(sender.getResponseContentType(), sender.getResponseContent().readAllBytes());
        }
    }
}
