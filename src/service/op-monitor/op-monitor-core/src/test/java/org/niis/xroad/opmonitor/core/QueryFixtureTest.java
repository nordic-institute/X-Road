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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compares the answers of the gRPC query method with the recorded answers of the HTTP query handler. The comparison
 * does not use the HTTP query handler.
 */
class QueryFixtureTest extends AbstractQueryTest {
    static final String FIXTURE_DIRECTORY = "src/test/resources/query-fixtures";

    static Path fixtureFile(QueryCase queryCase) {
        return Path.of(FIXTURE_DIRECTORY, queryCase.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".response");
    }

    static String fixture(QueryCase queryCase) throws IOException {
        return Files.readString(fixtureFile(queryCase), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @ParameterizedTest
    @EnumSource(QueryCase.class)
    void answerIsEqualToTheRecordedAnswer(QueryCase queryCase) throws Exception {
        var answer = queryOverGrpc(prepareQuery(queryCase));

        assertThat(QueryResponses.fixtureText(answer)).isEqualTo(fixture(queryCase));
    }

    @ParameterizedTest
    @EnumSource(QueryCase.class)
    void contentTypePartHeadersAndSoapElementsAreInTheRecordedOrder(QueryCase queryCase) throws Exception {
        var answer = queryOverGrpc(prepareQuery(queryCase));

        assertThat(QueryResponses.normalised(answer).contentType()).isEqualTo(QueryResponses.expectedContentType(queryCase));
        assertThat(QueryResponses.partHeaders(answer)).containsExactlyElementsOf(QueryResponses.expectedPartHeaders(queryCase));
        assertThat(QueryResponses.soapElements(answer)).containsExactlyElementsOf(QueryResponses.expectedSoapElements(queryCase));
    }
}
