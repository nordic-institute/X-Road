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
import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.migration.utils.InstallationType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstallationOptionTest {

    @Test
    void shouldDefaultToSecurityServerWhenOptionIsAbsent() {
        InstallationOption option = InstallationOption.extract(new String[]{"/some/file.ini", "/some/db.properties"});

        assertThat(option.installationType()).isEqualTo(InstallationType.SECURITY_SERVER);
        assertThat(option.remainingArgs()).containsExactly("/some/file.ini", "/some/db.properties");
    }

    @Test
    void shouldTakeTheTypeFromEqualsForm() {
        InstallationOption option = InstallationOption.extract(new String[]{"/some/file.ini", "--installation=cs"});

        assertThat(option.installationType()).isEqualTo(InstallationType.CENTRAL_SERVER);
        assertThat(option.remainingArgs()).containsExactly("/some/file.ini");
    }

    @Test
    void shouldTakeTheTypeFromSeparateArgument() {
        InstallationOption option = InstallationOption.extract(new String[]{"--installation", "cs", "/some/file.ini"});

        assertThat(option.installationType()).isEqualTo(InstallationType.CENTRAL_SERVER);
        assertThat(option.remainingArgs()).containsExactly("/some/file.ini");
    }

    @Test
    void shouldLeaveTheNodeOptionForNodeOptionParsing() {
        InstallationOption option = InstallationOption.extract(
                new String[]{"--node", "node_1", "--installation=cs", "/some/script.sh"});

        NodeOption nodeOption = NodeOption.extract(option.remainingArgs().toArray(new String[0]));

        assertThat(option.installationType()).isEqualTo(InstallationType.CENTRAL_SERVER);
        assertThat(nodeOption.nodeIdentity()).isEqualTo(new NodeIdentity("node_1", true));
        assertThat(nodeOption.remainingArgs()).containsExactly("/some/script.sh");
    }

    @Test
    void shouldRejectAnUnknownTypeListingTheAcceptedValues() {
        assertThatThrownBy(() -> InstallationOption.extract(new String[]{"--installation=xx"}))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("'xx'")
                .hasMessageContaining("accepted values: ss, cs");
    }

    @Test
    void shouldRejectOptionAsLastArgumentWithoutValue() {
        assertThatThrownBy(() -> InstallationOption.extract(new String[]{"/some/file.ini", "--installation"}))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("requires a value")
                .hasMessageContaining("ss, cs");
    }

    @Test
    void shouldRejectOptionWithEmptyValue() {
        assertThatThrownBy(() -> InstallationOption.extract(new String[]{"--installation="}))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("accepted values: ss, cs");
    }

    @Test
    void shouldRejectOptionGivenTwice() {
        assertThatThrownBy(() -> InstallationOption.extract(new String[]{"--installation=ss", "--installation=cs"}))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("only once");
    }
}
