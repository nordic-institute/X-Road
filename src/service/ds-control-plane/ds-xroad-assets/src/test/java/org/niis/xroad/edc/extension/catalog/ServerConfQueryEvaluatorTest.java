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
package org.niis.xroad.edc.extension.catalog;

import ee.ria.xroad.common.identifier.ServiceId;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServerConfQueryEvaluatorTest {

    private static final String PARTICIPANT_CONTEXT_ID = "xroad-provider";

    private static final ServiceId.Conf SID_1 = ServiceId.Conf.create("DEV", "GOV", "1111", "Sub1", "svcA", "v1");
    private static final ServiceId.Conf SID_2 = ServiceId.Conf.create("DEV", "GOV", "2222", "Sub2", "svcB");
    private static final ServiceId.Conf SID_3 = ServiceId.Conf.create("DEV", "COM", "3333", "Sub3", "svcC", "v2");

    private ServerConfQueryEvaluator evaluator;
    private List<Asset> testAssets;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        evaluator = new ServerConfQueryEvaluator();
        testAssets = List.of(
                AssetMapper.toAsset(SID_1, PARTICIPANT_CONTEXT_ID),
                AssetMapper.toAsset(SID_2, PARTICIPANT_CONTEXT_ID),
                AssetMapper.toAsset(SID_3, PARTICIPANT_CONTEXT_ID)
        );

        logAppender = new ListAppender<>();
        var logger = (Logger) LoggerFactory.getLogger(ServerConfQueryEvaluator.class);
        logger.addAppender(logAppender);
        logAppender.start();
    }

    @AfterEach
    void tearDown() {
        logAppender.stop();
        var logger = (Logger) LoggerFactory.getLogger(ServerConfQueryEvaluator.class);
        logger.detachAppender(logAppender);
    }

    @Test
    void idEqualsCriterionFiltersToExactlyOneAsset() {
        var spec = QuerySpec.Builder.newInstance()
                .filter(Criterion.criterion("id", "=", SID_1.asEncodedId()))
                .limit(Integer.MAX_VALUE)
                .build();

        var result = evaluator.evaluate(testAssets.stream(), spec).toList();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(SID_1.asEncodedId());
    }

    @Test
    void participantContextIdMatchesXroadProvider() {
        var spec = QuerySpec.Builder.newInstance()
                .filter(Criterion.criterion("participantContextId", "=", PARTICIPANT_CONTEXT_ID))
                .limit(Integer.MAX_VALUE)
                .build();

        var result = evaluator.evaluate(testAssets.stream(), spec).toList();

        assertThat(result).hasSize(3);
    }

    @Test
    void participantContextIdMismatchReturnsEmpty() {
        var spec = QuerySpec.Builder.newInstance()
                .filter(Criterion.criterion("participantContextId", "=", "other-provider"))
                .limit(Integer.MAX_VALUE)
                .build();

        var result = evaluator.evaluate(testAssets.stream(), spec).toList();

        assertThat(result).isEmpty();
    }

    @Test
    void unsupportedOperatorSkippedWithWarnLog() {
        var spec = QuerySpec.Builder.newInstance()
                .filter(Criterion.criterion("id", "!=", "someValue"))
                .limit(Integer.MAX_VALUE)
                .build();

        var result = evaluator.evaluate(testAssets.stream(), spec).toList();

        assertThat(result).hasSize(3);
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
                .anySatisfy(tuple -> {
                    assertThat(tuple.toList().get(0)).isEqualTo(Level.WARN);
                    assertThat((String) tuple.toList().get(1)).contains("Unsupported operator");
                });
    }

    @Test
    void unsupportedLeftOperandSkippedWithWarnLog() {
        var spec = QuerySpec.Builder.newInstance()
                .filter(Criterion.criterion("unknownField", "=", "someValue"))
                .limit(Integer.MAX_VALUE)
                .build();

        var result = evaluator.evaluate(testAssets.stream(), spec).toList();

        assertThat(result).hasSize(3);
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
                .anySatisfy(tuple -> {
                    assertThat(tuple.toList().get(0)).isEqualTo(Level.WARN);
                    assertThat((String) tuple.toList().get(1)).contains("Unsupported criterion operand");
                });
    }

    @Test
    void multiCriterionAndIdAndParticipantContextId() {
        var spec = QuerySpec.Builder.newInstance()
                .filter(List.of(
                        Criterion.criterion("id", "=", SID_2.asEncodedId()),
                        Criterion.criterion("participantContextId", "=", PARTICIPANT_CONTEXT_ID)
                ))
                .limit(Integer.MAX_VALUE)
                .build();

        var result = evaluator.evaluate(testAssets.stream(), spec).toList();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(SID_2.asEncodedId());
    }

    @Test
    void pagingLimitAndOffset() {
        var spec = QuerySpec.Builder.newInstance()
                .limit(1)
                .offset(1)
                .build();

        var result = evaluator.evaluate(testAssets.stream(), spec).toList();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(SID_2.asEncodedId());
    }

    @Test
    void sortFieldIgnoredWithWarnLog() {
        var spec = QuerySpec.Builder.newInstance()
                .sortField("name")
                .limit(Integer.MAX_VALUE)
                .build();

        var result = evaluator.evaluate(testAssets.stream(), spec).toList();

        assertThat(result).hasSize(3);
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.contains("Sort not supported"));
    }
}
