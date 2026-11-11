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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyConfigValueConverterTest {

    private static final String GROUPING_KEY = "xroad.message-log-encryption.archive.grouping-strategy";

    @Test
    void convertsLowerCaseMemberToUpperCaseConstant() {
        var result = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "member"));

        assertThat(result.properties()).containsEntry(GROUPING_KEY, "MEMBER");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void convertsMixedCaseMemberToUpperCaseConstant() {
        var result = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "Member"));

        assertThat(result.properties()).containsEntry(GROUPING_KEY, "MEMBER");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void keepsAlreadyExactCaseMemberConstant() {
        var result = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "MEMBER"));

        assertThat(result.properties()).containsEntry(GROUPING_KEY, "MEMBER");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void convertsLowerCaseSubsystemToUpperCaseConstant() {
        var result = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "subsystem"));

        assertThat(result.properties()).containsEntry(GROUPING_KEY, "SUBSYSTEM");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void convertsLowerCaseNoneToUpperCaseConstant() {
        var result = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "none"));

        assertThat(result.properties()).containsEntry(GROUPING_KEY, "NONE");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void unknownValueKeptUnchangedWithOneWarning() {
        var result = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "team"));

        assertThat(result.properties()).containsEntry(GROUPING_KEY, "team");
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().getFirst())
                .contains(GROUPING_KEY)
                .contains("team")
                .contains("NONE", "MEMBER", "SUBSYSTEM");
    }

    @Test
    void keyOutsideTablePassesThroughUnchanged() {
        var result = LegacyConfigValueConverter.convert(Map.of("some.other.key", "value"));

        assertThat(result.properties()).containsEntry("some.other.key", "value");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void lookalikeKeyPassesThroughUnchanged() {
        var lookalikeKey = GROUPING_KEY + "-x";
        var result = LegacyConfigValueConverter.convert(Map.of(lookalikeKey, "member"));

        assertThat(result.properties()).containsEntry(lookalikeKey, "member");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void reconvertingAlreadyConvertedRowsIsNoOp() {
        var firstPass = LegacyConfigValueConverter.convert(Map.of(GROUPING_KEY, "member"));

        var secondPass = LegacyConfigValueConverter.convert(firstPass.properties());

        assertThat(secondPass.properties()).isEqualTo(firstPass.properties());
        assertThat(secondPass.warnings()).isEmpty();
    }

}
