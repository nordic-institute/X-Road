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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DbPropertiesOptionTest {

    @Test
    void shouldDefaultToTheProductPathWhenOptionIsAbsent() {
        DbPropertiesOption option = DbPropertiesOption.extract(new String[]{"/some/script.sh"});

        assertThat(option.path()).isEqualTo("/etc/xroad/db.properties");
        assertThat(option.remainingArgs()).containsExactly("/some/script.sh");
    }

    @Test
    void shouldTakeThePathFromEqualsForm() {
        DbPropertiesOption option = DbPropertiesOption.extract(new String[]{"--db-properties=/tmp/db.properties", "/some/script.sh"});

        assertThat(option.path()).isEqualTo("/tmp/db.properties");
        assertThat(option.remainingArgs()).containsExactly("/some/script.sh");
    }

    @Test
    void shouldTakeThePathFromSeparateArgument() {
        DbPropertiesOption option = DbPropertiesOption.extract(new String[]{"/some/script.sh", "--db-properties", "/tmp/db.properties"});

        assertThat(option.path()).isEqualTo("/tmp/db.properties");
        assertThat(option.remainingArgs()).containsExactly("/some/script.sh");
    }

    @Test
    void shouldRejectTheOptionWithoutAValue() {
        assertThatThrownBy(() -> DbPropertiesOption.extract(new String[]{"--db-properties"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a path");
    }

    @Test
    void shouldRejectABlankValue() {
        assertThatThrownBy(() -> DbPropertiesOption.extract(new String[]{"--db-properties="}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a path");
    }

    @Test
    void shouldRejectTheOptionGivenTwice() {
        assertThatThrownBy(() -> DbPropertiesOption.extract(new String[]{"--db-properties=/a", "--db-properties", "/b"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only once");
    }
}
