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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BasePropertiesToDbMigratorTest {

    private static final String LOWER_VALUE = "lower-case-secret-value";
    private static final String MIXED_VALUE = "mixed-case-secret-value";
    private static final String UPPER_VALUE = "upper-case-secret-value";
    private static final String PLAIN_VALUE = "plain-visible-value";

    private final ListAppender<ILoggingEvent> output = new ListAppender<>();
    private Logger rootLogger;
    private Level originalLevel;

    @BeforeEach
    void captureOutput() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        originalLevel = rootLogger.getLevel();
        rootLogger.setLevel(Level.ALL);
        output.start();
        rootLogger.addAppender(output);
    }

    @AfterEach
    void releaseOutput() {
        rootLogger.detachAppender(output);
        rootLogger.setLevel(originalLevel);
    }

    @Test
    void autoConfirmEnvVarSkipsStdinPrompt() {
        var migrator = new TestMigrator(Map.of("AUTO", "x"))
                .withEnv(BasePropertiesToDbMigrator.AUTO_CONFIRM_ENV, "true");

        try (MockedConstruction<DbRepository> mocked = Mockito.mockConstruction(DbRepository.class)) {
            migrator.migrate("input", "db.props");

            assertEquals(1, mocked.constructed().size());
            Mockito.verify(mocked.constructed().getFirst()).saveProperty("AUTO", "x");
        }
    }

    @Test
    void autoConfirmEnvVarIsCaseInsensitive() {
        var migrator = new TestMigrator(Map.of("AUTO", "x"))
                .withEnv(BasePropertiesToDbMigrator.AUTO_CONFIRM_ENV, "TrUe");

        try (MockedConstruction<DbRepository> mocked = Mockito.mockConstruction(DbRepository.class)) {
            migrator.migrate("input", "db.props");
            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    void autoConfirmEnvVarOtherValueFallsBackToStdin() {
        var migrator = new TestMigrator(Map.of("AUTO", "x"))
                .withEnv(BasePropertiesToDbMigrator.AUTO_CONFIRM_ENV, "yes-please")
                .withStdin("y\n");

        try (MockedConstruction<DbRepository> mocked = Mockito.mockConstruction(DbRepository.class)) {
            migrator.migrate("input", "db.props");
            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    void noEnvVarPromptsAndDeclineSkipsMigration() {
        var migrator = new TestMigrator(Map.of("AUTO", "x"))
                .withStdin("n\n");

        try (MockedConstruction<DbRepository> mocked = Mockito.mockConstruction(DbRepository.class)) {
            migrator.migrate("input", "db.props");
            assertThat(mocked.constructed()).isEmpty();
        }
    }

    @Test
    void noEnvVarPromptsAndYesProceeds() {
        var migrator = new TestMigrator(Map.of("AUTO", "x"))
                .withStdin("y\n");

        try (MockedConstruction<DbRepository> mocked = Mockito.mockConstruction(DbRepository.class)) {
            migrator.migrate("input", "db.props");
            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    void passwordValuesAreRedactedInEveryLogLineWhateverTheKeyLetterCase() {
        var props = new TreeMap<String, String>();
        props.put("xroad.test.db-password", LOWER_VALUE);
        props.put("xroad.test.keystorePassword", MIXED_VALUE);
        props.put("xroad.test.KEYSTORE-PASSWORD-FILE", UPPER_VALUE);
        props.put("xroad.test.plain", PLAIN_VALUE);
        var migrator = new TestMigrator(props).withEnv(BasePropertiesToDbMigrator.AUTO_CONFIRM_ENV, "true");

        try (MockedConstruction<DbRepository> mocked = Mockito.mockConstruction(DbRepository.class)) {
            migrator.migrate("input", "db.props");

            var repo = mocked.constructed().getFirst();
            Mockito.verify(repo).saveProperty("xroad.test.db-password", LOWER_VALUE);
            Mockito.verify(repo).saveProperty("xroad.test.keystorePassword", MIXED_VALUE);
            Mockito.verify(repo).saveProperty("xroad.test.KEYSTORE-PASSWORD-FILE", UPPER_VALUE);
        }

        var lines = output.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(lines)
                .noneMatch(line -> line.contains(LOWER_VALUE) || line.contains(MIXED_VALUE)
                        || line.contains(UPPER_VALUE))
                .contains("xroad.test.db-password=" + BasePropertiesToDbMigrator.REDACTED,
                        "xroad.test.keystorePassword=" + BasePropertiesToDbMigrator.REDACTED,
                        "xroad.test.KEYSTORE-PASSWORD-FILE=" + BasePropertiesToDbMigrator.REDACTED,
                        "Saving property xroad.test.db-password=" + BasePropertiesToDbMigrator.REDACTED,
                        "Saving property xroad.test.keystorePassword=" + BasePropertiesToDbMigrator.REDACTED,
                        "Saving property xroad.test.KEYSTORE-PASSWORD-FILE=" + BasePropertiesToDbMigrator.REDACTED);
    }

    @Test
    void nonPasswordValuesStillAppearInTheDebugLog() {
        var migrator = new TestMigrator(Map.of("xroad.test.plain", PLAIN_VALUE))
                .withEnv(BasePropertiesToDbMigrator.AUTO_CONFIRM_ENV, "true");

        try (MockedConstruction<DbRepository> _ = Mockito.mockConstruction(DbRepository.class)) {
            migrator.migrate("input", "db.props");
        }

        assertThat(output.list.stream().map(ILoggingEvent::getFormattedMessage))
                .contains("xroad.test.plain=" + PLAIN_VALUE, "Saving property xroad.test.plain=" + PLAIN_VALUE);
    }

    static final class TestMigrator extends BasePropertiesToDbMigrator {
        private final Map<String, String> props;
        private final Map<String, String> env = new HashMap<>();
        private InputStream stubStdin;
        private InputStream originalStdin;

        TestMigrator(Map<String, String> props) {
            this.props = props;
        }

        TestMigrator withEnv(String name, String value) {
            env.put(name, value);
            return this;
        }

        TestMigrator withStdin(String input) {
            this.stubStdin = new ByteArrayInputStream(input.getBytes());
            return this;
        }

        @Override
        Map<String, String> loadProperties(String filePath) {
            return props;
        }

        @Override
        String readEnv(String name) {
            return env.get(name);
        }

        @Override
        boolean confirmProceed(Map<String, String> properties) {
            if (stubStdin == null) {
                return super.confirmProceed(properties);
            }
            originalStdin = System.in;
            System.setIn(stubStdin);
            try {
                return super.confirmProceed(properties);
            } finally {
                System.setIn(originalStdin);
            }
        }
    }
}
