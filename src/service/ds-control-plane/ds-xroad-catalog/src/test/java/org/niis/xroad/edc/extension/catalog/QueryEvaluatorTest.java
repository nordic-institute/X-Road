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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryEvaluatorTest {

    record Item(String id, String participantContextId) {
    }

    private static final Item ITEM_A = new Item("id-a", "ctx-1");
    private static final Item ITEM_B = new Item("id-b", "ctx-1");
    private static final Item ITEM_C = new Item("id-c", "ctx-2");

    private QueryEvaluator<Item> evaluator;
    private List<Item> items;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        evaluator = new QueryEvaluator<>(Item::id, Item::participantContextId);
        items = List.of(ITEM_A, ITEM_B, ITEM_C);

        logAppender = new ListAppender<>();
        var logger = (Logger) LoggerFactory.getLogger(QueryEvaluator.class);
        logger.addAppender(logAppender);
        logAppender.start();
    }

    @AfterEach
    void tearDown() {
        logAppender.stop();
        var logger = (Logger) LoggerFactory.getLogger(QueryEvaluator.class);
        logger.detachAppender(logAppender);
    }

    @Test
    void idFilterMatchesExactlyOneItem() {
        var spec = QuerySpec.Builder.newInstance()
                .filter(Criterion.criterion("id", "=", "id-a"))
                .limit(Integer.MAX_VALUE)
                .build();

        var result = evaluator.evaluate(items.stream(), spec).toList();

        assertThat(result).containsExactly(ITEM_A);
    }

    @Test
    void participantContextIdFilterMatchesSubset() {
        var spec = QuerySpec.Builder.newInstance()
                .filter(Criterion.criterion("participantContextId", "=", "ctx-1"))
                .limit(Integer.MAX_VALUE)
                .build();

        var result = evaluator.evaluate(items.stream(), spec).toList();

        assertThat(result).containsExactlyInAnyOrder(ITEM_A, ITEM_B);
    }

    @Test
    void combinedIdAndParticipantContextIdFilter() {
        var spec = QuerySpec.Builder.newInstance()
                .filter(List.of(
                        Criterion.criterion("id", "=", "id-b"),
                        Criterion.criterion("participantContextId", "=", "ctx-1")
                ))
                .limit(Integer.MAX_VALUE)
                .build();

        var result = evaluator.evaluate(items.stream(), spec).toList();

        assertThat(result).containsExactly(ITEM_B);
    }

    @Test
    void pagingOffsetAndLimit() {
        var spec = QuerySpec.Builder.newInstance()
                .limit(1)
                .offset(1)
                .build();

        var result = evaluator.evaluate(items.stream(), spec).toList();

        assertThat(result).containsExactly(ITEM_B);
    }

    @Test
    void sortFieldIgnoredWithWarnLogOnce() {
        var spec = QuerySpec.Builder.newInstance()
                .sortField("id")
                .limit(Integer.MAX_VALUE)
                .build();

        evaluator.evaluate(items.stream(), spec).toList();
        evaluator.evaluate(items.stream(), spec).toList();

        var warnMessages = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .filter(e -> e.getFormattedMessage().contains("Sort not supported"))
                .toList();
        assertThat(warnMessages).hasSize(1);
    }

    @Test
    void unsupportedOperatorSkipsWithWarnLog() {
        var spec = QuerySpec.Builder.newInstance()
                .filter(Criterion.criterion("id", "!=", "id-a"))
                .limit(Integer.MAX_VALUE)
                .build();

        var result = evaluator.evaluate(items.stream(), spec).toList();

        assertThat(result).hasSize(3);
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
                .anySatisfy(tuple -> {
                    assertThat(tuple.toList().get(0)).isEqualTo(Level.WARN);
                    assertThat((String) tuple.toList().get(1)).contains("Unsupported operator");
                });
    }

    @Test
    void unknownCriterionSkipsWithWarnLog() {
        var spec = QuerySpec.Builder.newInstance()
                .filter(Criterion.criterion("unknownField", "=", "value"))
                .limit(Integer.MAX_VALUE)
                .build();

        var result = evaluator.evaluate(items.stream(), spec).toList();

        assertThat(result).hasSize(3);
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
                .anySatisfy(tuple -> {
                    assertThat(tuple.toList().get(0)).isEqualTo(Level.WARN);
                    assertThat((String) tuple.toList().get(1)).contains("Unsupported criterion operand");
                });
    }
}
