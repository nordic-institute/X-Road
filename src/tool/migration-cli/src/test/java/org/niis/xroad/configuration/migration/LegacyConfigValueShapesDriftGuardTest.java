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

package org.niis.xroad.configuration.migration;

import org.junit.jupiter.api.Test;
import org.niis.xroad.messagelog.archive.GroupingStrategy;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards {@link LegacyConfigValueShapes} against drifting from {@link GroupingStrategy}, whose constants
 * it duplicates for the migration CLI (which has no production dependency on the message log module).
 */
class LegacyConfigValueShapesDriftGuardTest {

    private static final String GROUPING_KEY = "xroad.message-log-encryption.archive.grouping-strategy";

    @Test
    void groupingStrategyShapeMatchesMessageLogGroupingStrategyEnum() {
        var shape = (LegacyConfigValueShapes.EnumShape) LegacyConfigValueShapes.shapeOf(GROUPING_KEY);

        List<String> expectedConstants = Arrays.stream(GroupingStrategy.values())
                .map(Enum::name)
                .toList();

        assertThat(shape.allowedConstants()).containsExactlyInAnyOrderElementsOf(expectedConstants);
    }

}
