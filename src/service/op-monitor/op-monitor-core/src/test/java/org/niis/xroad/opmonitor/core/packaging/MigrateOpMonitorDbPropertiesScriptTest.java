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
package org.niis.xroad.opmonitor.core.packaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class MigrateOpMonitorDbPropertiesScriptTest {
    private static final Path SCRIPT = Path.of("../../../../deployment/native-packages/src/xroad/common/op-monitor/"
            + "usr/share/xroad/scripts/migrate_opmonitor_db_properties.sh").toAbsolutePath().normalize();
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(60);
    private static final String MODE = "rw-r-----";
    private static final String PASSWORD_VALUE = "p&ss\\w/rd=?x";
    private static final String UNPREFIXED_URL = "op-monitor.hibernate.connection.url";
    private static final String PREFIXED_URL = "xroad.db.op-monitor.hibernate.connection.url";
    private static final String UNPREFIXED_USERNAME = "op-monitor.hibernate.connection.username";
    private static final String PREFIXED_USERNAME = "xroad.db.op-monitor.hibernate.connection.username";
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final String SENTINEL = "existing backup\n";

    @TempDir
    Path workDir;

    private Path confDir;

    @BeforeEach
    void createConfDir() throws IOException {
        confDir = Files.createDirectory(workDir.resolve("conf"));
    }

    static Stream<Arguments> renameCases() {
        return Stream.of(
                Arguments.of("op-monitor.hibernate.connection.username = opmonitor\n",
                        "xroad.db.op-monitor.hibernate.connection.username = opmonitor\n"),
                Arguments.of("op-monitor.hibernate.connection.username=opmonitor\n",
                        "xroad.db.op-monitor.hibernate.connection.username=opmonitor\n"),
                Arguments.of("   op-monitor.hibernate.connection.username=opmonitor\n",
                        "   xroad.db.op-monitor.hibernate.connection.username=opmonitor\n"),
                Arguments.of("\top-monitor.hibernate.connection.username=opmonitor\n",
                        "\txroad.db.op-monitor.hibernate.connection.username=opmonitor\n"),
                Arguments.of("op-monitor.hibernate.connection.username=opmonitor",
                        "xroad.db.op-monitor.hibernate.connection.username=opmonitor"),
                Arguments.of("a=1\nop-monitor.hibernate.connection.username=opmonitor",
                        "a=1\nxroad.db.op-monitor.hibernate.connection.username=opmonitor"),
                Arguments.of("op-monitor.hibernate.connection.password=" + PASSWORD_VALUE + "\n",
                        "xroad.db.op-monitor.hibernate.connection.password=" + PASSWORD_VALUE + "\n"),
                Arguments.of("op-monitor.hibernate.connection.url=jdbc:postgresql://db:5432/op-monitor?a=b&c=d\\e\n",
                        "xroad.db.op-monitor.hibernate.connection.url=jdbc:postgresql://db:5432/op-monitor?a=b&c=d\\e\n"));
    }

    @ParameterizedTest
    @MethodSource("renameCases")
    void shouldPrefixUnprefixedKeysAndKeepValues(String original, String expected) throws IOException {
        var file = write(original);

        var result = run(file);

        assertThat(result.exitCode()).isZero();
        assertThat(read(file)).isEqualTo(expected);
    }

    static Stream<Arguments> finalLineCases() {
        return Stream.of("\n", "").flatMap(newline -> Stream.of(
                Arguments.of("a=1\n" + UNPREFIXED_USERNAME + "=u" + newline,
                        "a=1\n" + PREFIXED_USERNAME + "=u" + newline),
                Arguments.of(PREFIXED_USERNAME + "=new\n" + UNPREFIXED_USERNAME + "=old" + newline,
                        PREFIXED_USERNAME + "=new\n"),
                Arguments.of(UNPREFIXED_USERNAME + "=u\na=1" + newline,
                        PREFIXED_USERNAME + "=u\na=1" + newline)));
    }

    @ParameterizedTest
    @MethodSource("finalLineCases")
    void shouldKeepEveryLineEndingExactlyWhenRenamingOrRemovingLines(String original, String expected)
            throws IOException {
        var file = write(original);

        run(file);

        assertThat(Files.readAllBytes(file)).isEqualTo(expected.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldKeepPrefixedLineAndDeleteUnprefixedLineWhenBothFormsExist() throws IOException {
        var file = write(UNPREFIXED_URL + "=jdbc:old\n"
                + PREFIXED_URL + "=jdbc:new\n"
                + "op-monitor.hibernate.connection.username=opmonitor\n");

        run(file);

        assertThat(read(file)).isEqualTo(PREFIXED_URL + "=jdbc:new\n"
                + "xroad.db.op-monitor.hibernate.connection.username=opmonitor\n");
    }

    @Test
    void shouldChangeNothingOnSecondRunAndOnAlreadyPrefixedFile() throws IOException {
        var prefixed = "xroad.db.op-monitor.hibernate.connection.username=opmonitor\n"
                + "xroad.db.op-monitor.hibernate.connection.password=" + PASSWORD_VALUE + "\n";
        var file = write(prefixed);
        run(file);
        assertThat(read(file)).isEqualTo(prefixed);
        assertThat(backups()).isEmpty();

        var original = "op-monitor.hibernate.connection.username=opmonitor\n";
        var migrated = write(original);
        run(migrated);
        var afterFirstRun = read(migrated);
        var backupsAfterFirstRun = backups();
        assertThat(afterFirstRun).isNotEqualTo(original);
        assertThat(backupsAfterFirstRun).hasSize(1);

        run(migrated);

        assertThat(read(migrated)).isEqualTo(afterFirstRun).doesNotContain("xroad.db.xroad.db.");
        assertThat(backups()).isEqualTo(backupsAfterFirstRun);
    }

    @Test
    void shouldWriteOneByteIdenticalBackupWithOriginalModeOnlyWhenFileChanges() throws IOException {
        var original = "op-monitor.hibernate.connection.username=opmonitor\n"
                + "op-monitor.hibernate.connection.password=" + PASSWORD_VALUE + "\n";
        var file = write(original);

        run(file);

        var backups = backups();
        assertThat(backups).hasSize(1);
        assertThat(Files.readString(backups.getFirst())).isEqualTo(original);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(backups.getFirst()))).isEqualTo(MODE);
    }

    @Test
    void shouldNeverOverwriteExistingBackupAndWriteAnotherOne() throws IOException {
        var original = UNPREFIXED_USERNAME + "=opmonitor\n";
        var file = write(original);
        var existing = new ArrayList<Path>();
        var first = Instant.now().minusSeconds(2);
        for (int second = 0; second < 40; second++) {
            var backup = confDir.resolve("db.properties." + BACKUP_STAMP.format(first.plusSeconds(second)) + ".bak");
            Files.writeString(backup, SENTINEL);
            Files.setPosixFilePermissions(backup, PosixFilePermissions.fromString("rw-------"));
            existing.add(backup);
        }

        run(file);

        for (var backup : existing) {
            assertThat(Files.readString(backup)).isEqualTo(SENTINEL);
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(backup))).isEqualTo("rw-------");
        }
        var created = backups().stream().filter(path -> !existing.contains(path)).toList();
        assertThat(created).hasSize(1);
        assertThat(Files.readString(created.getFirst())).isEqualTo(original);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(created.getFirst()))).isEqualTo(MODE);
    }

    @Test
    void shouldSkipDirectoryAtBackupNameAndWriteRegularFileBackupElsewhere() throws IOException {
        var original = UNPREFIXED_USERNAME + "=opmonitor\n";
        var file = write(original);
        var directories = occupyBackupNames(Files::createDirectory);

        var result = run(file);

        assertThat(result.exitCode()).isZero();
        for (var directory : directories) {
            try (var content = Files.list(directory)) {
                assertThat(content).isEmpty();
            }
        }
        assertSingleRegularFileBackup(directories, original, result);
    }

    @Test
    void shouldSkipSymlinkToDirectoryAtBackupNameAndWriteRegularFileBackupElsewhere() throws IOException {
        var original = UNPREFIXED_USERNAME + "=opmonitor\n";
        var file = write(original);
        var target = Files.createDirectory(workDir.resolve("link-target"));
        var links = occupyBackupNames(name -> Files.createSymbolicLink(name, target));

        var result = run(file);

        assertThat(result.exitCode()).isZero();
        try (var content = Files.list(target)) {
            assertThat(content).isEmpty();
        }
        assertSingleRegularFileBackup(links, original, result);
    }

    @Test
    void shouldKeepFileModeAndNotPrintPropertyValues() throws IOException {
        var file = write("op-monitor.hibernate.connection.password=" + PASSWORD_VALUE + "\n");

        var result = run(file);

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo(MODE);
        assertThat(result.output()).doesNotContain(PASSWORD_VALUE).doesNotContain("p&ss");
    }

    @Test
    void shouldLeaveOtherLinesUntouched() throws IOException {
        var untouched = """
                serverconf.hibernate.connection.url=jdbc:postgresql://db/serverconf
                #op-monitor.hibernate.x=1

                xroad.db.messagelog.hibernate.connection.username=messagelog
                foo.op-monitor.hibernate.x=2
                """;
        var file = write(untouched + "op-monitor.hibernate.connection.username=opmonitor\n");

        run(file);

        assertThat(read(file)).isEqualTo(untouched + "xroad.db.op-monitor.hibernate.connection.username=opmonitor\n");
    }

    @Test
    void shouldExitSuccessfullyAndCreateNothingWhenFileIsMissing() throws IOException {
        var file = confDir.resolve("db.properties");

        var result = run(file);

        assertThat(result.exitCode()).isZero();
        try (var files = Files.list(confDir)) {
            assertThat(files).isEmpty();
        }
    }

    private Path write(String content) throws IOException {
        var file = confDir.resolve("db.properties");
        Files.writeString(file, content);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(MODE));
        return file;
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file);
    }

    private List<Path> occupyBackupNames(BackupNameOccupier occupier) throws IOException {
        var occupied = new ArrayList<Path>();
        var first = Instant.now().minusSeconds(2);
        for (int second = 0; second < 40; second++) {
            var name = confDir.resolve("db.properties." + BACKUP_STAMP.format(first.plusSeconds(second)) + ".bak");
            occupier.occupy(name);
            occupied.add(name);
        }
        return occupied;
    }

    private void assertSingleRegularFileBackup(List<Path> occupied, String original, Result result)
            throws IOException {
        var created = backups().stream().filter(path -> !occupied.contains(path)).toList();
        assertThat(created).hasSize(1);
        var backup = created.getFirst();
        assertThat(Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)).isTrue();
        assertThat(Files.readString(backup)).isEqualTo(original);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(backup))).isEqualTo(MODE);
        assertThat(result.output()).contains("(backup: " + backup + ")");
    }

    private List<Path> backups() throws IOException {
        try (var files = Files.list(confDir)) {
            return files.filter(path -> path.getFileName().toString().matches("db\\.properties\\..+\\.bak")).toList();
        }
    }

    private Result run(Path file) {
        try {
            var outputFile = Files.createTempFile(workDir, "process", ".out");
            try {
                var process = new ProcessBuilder("bash", SCRIPT.toString(), file.toString())
                        .redirectErrorStream(true)
                        .redirectOutput(outputFile.toFile())
                        .start();
                if (!process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    return fail("process timed out after %s", PROCESS_TIMEOUT);
                }
                return new Result(process.exitValue(), Files.readString(outputFile));
            } finally {
                Files.deleteIfExists(outputFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @FunctionalInterface
    private interface BackupNameOccupier {
        void occupy(Path name) throws IOException;
    }

    private record Result(int exitCode, String output) {
    }
}
