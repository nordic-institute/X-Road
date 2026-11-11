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
package org.niis.xroad.configuration.migration;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeNameMigratorTest {

    private static final String RULE = "^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$";

    @TempDir
    Path dir;

    @Test
    void shouldCreateNodeIniWithTheName() throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=cs-1\n");
        var nodeIni = dir.resolve("node.ini");

        NodeNameMigrator.migrate(localIni, nodeIni);

        assertThat(nodeIni).hasContent("[node]\nname=cs-1\n");
    }

    @Test
    void shouldKeepOtherKeysOfAnExistingNodeIni() throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=cs-1\n");
        var nodeIni = write("node.ini", "[node]\ntype=master\n\n[other]\nname=untouched\n");

        NodeNameMigrator.migrate(localIni, nodeIni);

        assertThat(nodeIni).hasContent("[node]\nname=cs-1\ntype=master\n\n[other]\nname=untouched\n");
    }

    @Test
    void shouldAddTheNodeSectionWhenNodeIniHasNone() throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=cs-1\n");
        var nodeIni = write("node.ini", "[other]\nkey=value\n");

        NodeNameMigrator.migrate(localIni, nodeIni);

        assertThat(nodeIni).hasContent("[other]\nkey=value\n\n[node]\nname=cs-1\n");
    }

    @Test
    void shouldFillABlankNameKeyInPlace() throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=cs-1\n");
        var nodeIni = write("node.ini", "[node]\ntype=master\nname=\n");

        NodeNameMigrator.migrate(localIni, nodeIni);

        assertThat(nodeIni).hasContent("[node]\ntype=master\nname=cs-1\n");
    }

    @Test
    void shouldKeepALeadingZeroName() throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=0123\n");
        var nodeIni = dir.resolve("node.ini");

        NodeNameMigrator.migrate(localIni, nodeIni);

        assertThat(nodeIni).hasContent("[node]\nname=0123\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"cs1.example.org", "_n", "n123456789012345678901234567890123456789012345678901234567890123"})
    void shouldRejectAnInvalidNameNamingFileKeyAndRuleAndWriteNothing(String name) throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=" + name + "\n");
        var missingNodeIni = dir.resolve("node.ini");

        assertThatThrownBy(() -> NodeNameMigrator.migrate(localIni, missingNodeIni))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining(localIni.toString())
                .hasMessageContaining("[center] ha-node-name")
                .hasMessageContaining(RULE)
                .hasMessageContaining("Nothing was written")
                .hasMessageNotContaining("XROAD_NODE_NAME");
        assertThat(missingNodeIni).doesNotExist();
    }

    @Test
    void shouldNotMentionTheEnvironmentVariableAnywhereInTheCauseChainOfAnInvalidName() throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=cs1.example.org\n");

        assertThatThrownBy(() -> NodeNameMigrator.migrate(localIni, dir.resolve("node.ini")))
                .satisfies(e -> assertThat(ExceptionUtils.getStackTrace(e)).doesNotContain("XROAD_NODE_NAME"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[node] ", "[node] ; the node", "[node]# the node", "[node];c", "[node] ;c"})
    void shouldFillTheNodeSectionWhateverTheTrailingPartOfItsHeader(String header) throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=cs-1\n");
        var nodeIni = write("node.ini", header + "\ntype=master\n");

        NodeNameMigrator.migrate(localIni, nodeIni);

        assertThat(nodeIni).hasContent(header + "\nname=cs-1\ntype=master\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[node] ", "[node] ; the node", "[node]# the node", "[node];c", "[node] ;c"})
    void shouldRecogniseTheNameUnderEveryHeaderShapeCrudiniReadsAsNode(String header) throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=cs-1\n");
        var content = header + "\nname=cs-1\n";
        var nodeIni = write("node.ini", content);

        NodeNameMigrator.migrate(localIni, nodeIni);

        assertThat(nodeIni).hasContent(content);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[node] ", "[node] ; the node", "[node]# the node", "[node];c", "[node] ;c"})
    void shouldFailOnADifferentNameUnderEveryHeaderShapeCrudiniReadsAsNode(String header) throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=cs-1\n");
        var content = header + "\nname=cs-2\n";
        var nodeIni = write("node.ini", content);

        assertThatThrownBy(() -> NodeNameMigrator.migrate(localIni, nodeIni))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("cs-2")
                .hasMessageContaining("Nothing was written");
        assertThat(nodeIni).hasContent(content);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[ node ]", "[ node]", "[node ]", " [node]", "[node] x"})
    void shouldFailAndWriteNothingOnAHeaderThatLooksLikeNodeButCrudiniDoesNotReadAsNode(String header)
            throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=cs-1\n");
        var content = header + "\nname=cs-2\n";
        var nodeIni = write("node.ini", content);

        assertThatThrownBy(() -> NodeNameMigrator.migrate(localIni, nodeIni))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining(nodeIni.toString())
                .hasMessageContaining("line 1")
                .hasMessageContaining("crudini")
                .hasMessageContaining("Nothing was written");
        assertThat(nodeIni).hasContent(content);
    }

    @Test
    void shouldLeaveAnExistingNodeIniUnchangedOnAnInvalidName() throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=cs1.example.org\n");
        var nodeIni = write("node.ini", "[node]\ntype=master\n");

        assertThatThrownBy(() -> NodeNameMigrator.migrate(localIni, nodeIni))
                .isInstanceOf(MigrationException.class);
        assertThat(nodeIni).hasContent("[node]\ntype=master\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[center]\nha-node-name=\n", "[center]\nha-node-name =   \n", "[center]\nother=1\n",
            "[proxy]\nha-node-name=cs-1\n", ""})
    void shouldDoNothingWhenThereIsNoName(String localIniContent) throws IOException {
        var localIni = write("local.ini", localIniContent);
        var nodeIni = dir.resolve("node.ini");

        NodeNameMigrator.migrate(localIni, nodeIni);

        assertThat(nodeIni).doesNotExist();
    }

    @Test
    void shouldLeaveAnExistingNodeIniUnchangedWhenThereIsNoName() throws IOException {
        var localIni = write("local.ini", "[proxy]\nkey=value\n");
        var nodeIni = write("node.ini", "[node]\ntype=master\n");

        NodeNameMigrator.migrate(localIni, nodeIni);

        assertThat(nodeIni).hasContent("[node]\ntype=master\n");
    }

    @Test
    void shouldDoNothingWhenNodeIniAlreadyHoldsTheSameName() throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=cs-1\n");
        var content = "; kept as is\n[node]\ntype=master\nname = cs-1\n";
        var nodeIni = write("node.ini", content);

        NodeNameMigrator.migrate(localIni, nodeIni);

        assertThat(nodeIni).hasContent(content);
    }

    @Test
    void shouldFailAndLeaveNodeIniUnchangedWhenItHoldsADifferentName() throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=cs-1\n");
        var content = "[node]\nname=cs-2\n";
        var nodeIni = write("node.ini", content);

        assertThatThrownBy(() -> NodeNameMigrator.migrate(localIni, nodeIni))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining(nodeIni.toString())
                .hasMessageContaining("cs-1")
                .hasMessageContaining("cs-2")
                .hasMessageContaining("Nothing was written");
        assertThat(nodeIni).hasContent(content);
    }

    @Test
    void shouldFailClearlyWhenLocalIniIsMissing() {
        var localIni = dir.resolve("absent-local.ini");
        var nodeIni = dir.resolve("node.ini");

        assertThatThrownBy(() -> NodeNameMigrator.migrate(localIni, nodeIni))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining(localIni.toString());
        assertThat(nodeIni).doesNotExist();
    }

    @Test
    void shouldFailClearlyWhenLocalIniIsADirectory() {
        var nodeIni = dir.resolve("node.ini");

        assertThatThrownBy(() -> NodeNameMigrator.migrate(dir, nodeIni))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining(dir.toString());
        assertThat(nodeIni).doesNotExist();
    }

    @Test
    void iniToDbShouldWriteNoRowForTheHaNodeName() throws IOException {
        var localIni = write("local.ini", "[center]\nha-node-name=cs-1\nconf-backup-path=/var/lib/xroad/backup/\n");
        var iniUtil = new IniUtil();

        assertThat(iniUtil.loadToFlatMap(localIni.toString(), "xroad"))
                .isEqualTo(Map.of("xroad.admin-service.conf-backup-path", "/var/lib/xroad/backup/"));
        assertThat(iniUtil.loadToNestedMap(localIni.toString(), "xroad").toString())
                .doesNotContain("ha-node-name");
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content);
    }
}
