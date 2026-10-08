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
package org.niis.xroad.globalconf.model;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.globalconf.model.PublishedValuesNormaliser.SystemValues;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class PublishedValuesNormaliserTest {

    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "222");
    private static final SecurityServerId SERVER_0 = SecurityServerId.Conf.create(MEMBER, "ss0");
    private static final SecurityServerId SERVER_1 = SecurityServerId.Conf.create(MEMBER, "ss1");
    private static final String SYSTEM_DID = "did:web:ss0.example.org%3A7183:v1:system";
    private static final String DSP_BASE_URL = "https://ss0.example.org:8183/api/dsp";
    private static final String MEMBER_DID_0 = "did:web:ss0.example.org%3A7183:v1:DEV:COM:222";
    private static final String MEMBER_DID_1 = "did:web:ss1.example.org%3A7183:v1:DEV:COM:222";

    private final Logger logger = Logger.getLogger(PublishedValuesNormaliser.class.getName());
    private final List<LogRecord> warnings = new ArrayList<>();
    private Level originalLevel;
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord logRecord) {
            if (logRecord.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(logRecord);
            }
        }

        @Override
        public void flush() {
            // records are kept in memory
        }

        @Override
        public void close() {
            // nothing to release
        }
    };

    @BeforeEach
    void attachHandler() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
    }

    @AfterEach
    void detachHandler() {
        logger.removeHandler(handler);
        logger.setLevel(originalLevel);
    }

    @Test
    void shouldKeepCompleteSystemValuesWithoutWarning() {
        assertThat(PublishedValuesNormaliser.normaliseSystemValues(SERVER_0, SYSTEM_DID, DSP_BASE_URL))
                .isEqualTo(new SystemValues(SYSTEM_DID, DSP_BASE_URL));
        assertThat(warnings).isEmpty();
    }

    @Test
    void shouldReadNeitherSystemValueAsNotPublishedWithoutWarning() {
        assertThat(PublishedValuesNormaliser.normaliseSystemValues(SERVER_0, null, null))
                .isEqualTo(new SystemValues(null, null));
        assertThat(warnings).isEmpty();
    }

    @Test
    void shouldDropSystemDidWithoutDspBaseUrlAndWarn() {
        assertThat(PublishedValuesNormaliser.normaliseSystemValues(SERVER_0, SYSTEM_DID, null))
                .isEqualTo(new SystemValues(null, null));
        assertThat(warnings).hasSize(1);
    }

    @Test
    void shouldDropDspBaseUrlWithoutSystemDidAndWarn() {
        assertThat(PublishedValuesNormaliser.normaliseSystemValues(SERVER_0, null, DSP_BASE_URL))
                .isEqualTo(new SystemValues(null, null));
        assertThat(warnings).hasSize(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldCountBlankDspBaseUrlAsAbsent(String blank) {
        assertThat(PublishedValuesNormaliser.normaliseSystemValues(SERVER_0, SYSTEM_DID, blank))
                .isEqualTo(new SystemValues(null, null));
        assertThat(warnings).hasSize(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldCountBlankSystemDidAsAbsent(String blank) {
        assertThat(PublishedValuesNormaliser.normaliseSystemValues(SERVER_0, blank, DSP_BASE_URL))
                .isEqualTo(new SystemValues(null, null));
        assertThat(warnings).hasSize(1);
    }

    @Test
    void shouldCountBlankSystemValuesAsNotPublished() {
        assertThat(PublishedValuesNormaliser.normaliseSystemValues(SERVER_0, " ", ""))
                .isEqualTo(new SystemValues(null, null));
    }

    @Test
    void shouldKeepOneDidPerServerWithoutWarning() {
        var dids = List.of(new SharedParameters.MemberDid(SERVER_0, MEMBER_DID_0),
                new SharedParameters.MemberDid(SERVER_1, MEMBER_DID_1));

        assertThat(PublishedValuesNormaliser.normaliseMemberDids(MEMBER, dids)).containsExactlyElementsOf(dids);
        assertThat(warnings).isEmpty();
    }

    @Test
    void shouldDropEqualDuplicateDidsOfOneServerAndWarn() {
        var dids = List.of(new SharedParameters.MemberDid(SERVER_0, MEMBER_DID_0),
                new SharedParameters.MemberDid(SERVER_0, MEMBER_DID_0));

        assertThat(PublishedValuesNormaliser.normaliseMemberDids(MEMBER, dids)).isEmpty();
        assertThat(warnings).hasSize(1);
    }

    @Test
    void shouldDropDifferingDuplicateDidsOfOneServerAndKeepOtherServers() {
        var other = new SharedParameters.MemberDid(SERVER_1, MEMBER_DID_1);
        var dids = List.of(new SharedParameters.MemberDid(SERVER_0, MEMBER_DID_0), other,
                new SharedParameters.MemberDid(SERVER_0, MEMBER_DID_1));

        assertThat(PublishedValuesNormaliser.normaliseMemberDids(MEMBER, dids)).containsExactly(other);
        assertThat(warnings).hasSize(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldCountBlankDidAsAbsent(String blank) {
        var other = new SharedParameters.MemberDid(SERVER_1, MEMBER_DID_1);
        var dids = List.of(new SharedParameters.MemberDid(SERVER_0, blank), other);

        assertThat(PublishedValuesNormaliser.normaliseMemberDids(MEMBER, dids)).containsExactly(other);
    }

    @Test
    void shouldNotCountBlankDidAsDuplicate() {
        var kept = new SharedParameters.MemberDid(SERVER_0, MEMBER_DID_0);
        var dids = List.of(new SharedParameters.MemberDid(SERVER_0, " "), kept);

        assertThat(PublishedValuesNormaliser.normaliseMemberDids(MEMBER, dids)).containsExactly(kept);
        assertThat(warnings).isEmpty();
    }
}
