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
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedParametersV7ConverterTest {

    private static final String V7_FIXTURE = "src/test/resources/globalconf_good_v7/EE/shared-params.xml";
    private static final String MEMBER_DID_0 = "did:web:ss0.example.org%3A7183:v1:EE:BUSINESS:producer";
    private static final String MEMBER_DID_1 = "did:web:ss1.example.org%3A7183:v1:EE:BUSINESS:producer";
    private static final String SYSTEM_DID = "did:web:ss0.example.org%3A7183:v1:system";
    private static final String DSP_BASE_URL = "https://ss0.example.org:8183/api/dsp";

    private static final ClientId PRODUCER = ClientId.Conf.create("EE", "BUSINESS", "producer");
    private static final SecurityServerId SERVER_0 = SecurityServerId.Conf.create(PRODUCER, "ss0");
    private static final SecurityServerId SERVER_1 = SecurityServerId.Conf.create(PRODUCER, "ss1");

    private final Logger logger = Logger.getLogger("org.niis.xroad.globalconf.model");
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
    void shouldReadWellFormedFixtureWithoutWarning() throws IOException {
        var parameters = read(Files.readString(Path.of(V7_FIXTURE)));

        assertThat(parameters.getMembers()).flatExtracting(SharedParameters.Member::getDids).isNotEmpty();
        assertThat(parameters.getSecurityServers()).extracting(SharedParameters.SecurityServer::getSystemDid)
                .anySatisfy(did -> assertThat(did).isNotNull());
        assertThat(warnings).isEmpty();
    }

    @Test
    void shouldReadWellFormedDocumentWithoutWarning() {
        var parameters = read(document(
                "<did securityServer=\"s0\">" + MEMBER_DID_0 + "</did><did securityServer=\"s1\">" + MEMBER_DID_1 + "</did>",
                server("s0", "m0", "ss0", "<client>sub0</client>", DSP_BASE_URL, SYSTEM_DID),
                server("s1", "m0", "ss1", "", null, null)));

        assertThat(parameters.getMembers()).singleElement()
                .satisfies(member -> assertThat(member.getDids()).containsExactly(
                        new SharedParameters.MemberDid(SERVER_0, MEMBER_DID_0),
                        new SharedParameters.MemberDid(SERVER_1, MEMBER_DID_1)));
        assertThat(parameters.getSecurityServers()).hasSize(2);
        assertThat(parameters.getSecurityServers().getFirst().getSystemDid()).isEqualTo(SYSTEM_DID);
        assertThat(parameters.getSecurityServers().getFirst().getDspBaseUrl()).isEqualTo(DSP_BASE_URL);
        assertThat(warnings).isEmpty();
    }

    @Test
    void shouldRejectOwnerPointingAtServer() {
        var xml = document("",
                server("s0", "s1", "ss0", "", null, null),
                server("s1", "m0", "ss1", "", null, null));

        assertRejected(xml, ErrorCode.GLOBAL_CONF_OWNER_REFERENCES_SERVER, "owner", "s1");
    }

    @Test
    void shouldRejectClientPointingAtServer() {
        var xml = document("",
                server("s0", "m0", "ss0", "<client>s1</client>", null, null),
                server("s1", "m0", "ss1", "", null, null));

        assertRejected(xml, ErrorCode.GLOBAL_CONF_CLIENT_REFERENCES_SERVER, "client", "s1");
    }

    @Test
    void shouldRejectMemberDidPointingAtMember() {
        var xml = document("<did securityServer=\"m0\">" + MEMBER_DID_0 + "</did>",
                server("s0", "m0", "ss0", "", null, null));

        assertRejected(xml, ErrorCode.GLOBAL_CONF_MEMBER_DID_REFERENCES_NON_SERVER, "did", "m0");
    }

    @Test
    void shouldRejectMemberDidPointingAtSubsystem() {
        var xml = document("<did securityServer=\"sub0\">" + MEMBER_DID_0 + "</did>",
                server("s0", "m0", "ss0", "", null, null));

        assertRejected(xml, ErrorCode.GLOBAL_CONF_MEMBER_DID_REFERENCES_NON_SERVER, "did", "sub0");
    }

    @Test
    void shouldRejectDanglingOwnerAsMalformedGlobalConf() {
        assertMalformed(document("", server("s0", "missing", "ss0", "", null, null)));
    }

    @Test
    void shouldRejectDanglingClientAsMalformedGlobalConf() {
        assertMalformed(document("", server("s0", "m0", "ss0", "<client>missing</client>", null, null)));
    }

    @Test
    void shouldRejectDanglingMemberDidAsMalformedGlobalConf() {
        assertMalformed(document("<did securityServer=\"missing\">" + MEMBER_DID_0 + "</did>",
                server("s0", "m0", "ss0", "", null, null)));
    }

    @Test
    void shouldReadSystemDidWithoutDspBaseUrlAsNoSystemValues() {
        var parameters = read(document("", server("s0", "m0", "ss0", "", null, SYSTEM_DID)));

        assertThat(parameters.getSecurityServers()).singleElement().satisfies(server -> {
            assertThat(server.getSystemDid()).isNull();
            assertThat(server.getDspBaseUrl()).isNull();
        });
        assertThat(warnings).hasSize(1);
    }

    @Test
    void shouldReadDspBaseUrlWithoutSystemDidAsNoSystemValues() {
        var parameters = read(document("", server("s0", "m0", "ss0", "", DSP_BASE_URL, null)));

        assertThat(parameters.getSecurityServers()).singleElement().satisfies(server -> {
            assertThat(server.getSystemDid()).isNull();
            assertThat(server.getDspBaseUrl()).isNull();
        });
        assertThat(warnings).hasSize(1);
    }

    @Test
    void shouldReadBlankSystemValuesAsAbsent() {
        var parameters = read(document("", server("s0", "m0", "ss0", "", "  ", " ")));

        assertThat(parameters.getSecurityServers()).singleElement().satisfies(server -> {
            assertThat(server.getSystemDid()).isNull();
            assertThat(server.getDspBaseUrl()).isNull();
        });
    }

    @Test
    void shouldReadEqualDuplicateDidsOfOneServerAsNoDidWhileOtherServersKeepTheirs() {
        var parameters = read(document(
                "<did securityServer=\"s0\">" + MEMBER_DID_0 + "</did>"
                        + "<did securityServer=\"s0\">" + MEMBER_DID_0 + "</did>"
                        + "<did securityServer=\"s1\">" + MEMBER_DID_1 + "</did>",
                server("s0", "m0", "ss0", "", null, null),
                server("s1", "m0", "ss1", "", null, null)));

        assertThat(parameters.getMembers()).singleElement().satisfies(member ->
                assertThat(member.getDids()).containsExactly(new SharedParameters.MemberDid(SERVER_1, MEMBER_DID_1)));
        assertThat(warnings).hasSize(1);
    }

    @Test
    void shouldReadDifferingDuplicateDidsOfOneServerAsNoDidWhileOtherServersKeepTheirs() {
        var parameters = read(document(
                "<did securityServer=\"s0\">" + MEMBER_DID_0 + "</did>"
                        + "<did securityServer=\"s0\">" + MEMBER_DID_1 + "</did>"
                        + "<did securityServer=\"s1\">" + MEMBER_DID_1 + "</did>",
                server("s0", "m0", "ss0", "", null, null),
                server("s1", "m0", "ss1", "", null, null)));

        assertThat(parameters.getMembers()).singleElement().satisfies(member ->
                assertThat(member.getDids()).containsExactly(new SharedParameters.MemberDid(SERVER_1, MEMBER_DID_1)));
        assertThat(warnings).hasSize(1);
    }

    @Test
    void shouldReadBlankDidAsAbsent() {
        var parameters = read(document("<did securityServer=\"s0\">  </did>",
                server("s0", "m0", "ss0", "", null, null)));

        assertThat(parameters.getMembers()).singleElement().satisfies(member -> assertThat(member.getDids()).isEmpty());
    }

    @Test
    void shouldReadUnknownDidSchemeVersionUnchanged() {
        var did = "did:web:ss0.example.org%3A7183:v2:EE:BUSINESS:producer";

        var parameters = read(document("<did securityServer=\"s0\">" + did + "</did>",
                server("s0", "m0", "ss0", "", null, null)));

        assertThat(parameters.getMembers()).singleElement().satisfies(member ->
                assertThat(member.getDids()).containsExactly(new SharedParameters.MemberDid(SERVER_0, did)));
        assertThat(warnings).isEmpty();
    }

    private static SharedParameters read(String xml) {
        return new SharedParametersV7(xml.getBytes(UTF_8)).getSharedParameters();
    }

    private static void assertRejected(String xml, ErrorCode code, String element, String id) {
        assertThatThrownBy(() -> read(xml))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(code.code());
                    assertThat(e.getErrorCodeMetadata()).containsExactly(element, id);
                });
    }

    private static void assertMalformed(String xml) {
        assertThatThrownBy(() -> read(xml))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MALFORMED_GLOBALCONF.code()));
    }

    private static String document(String memberDids, String... servers) {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <ns3:conf xmlns:ns3="http://x-road.eu/xsd/xroad.xsd">
                    <instanceIdentifier>EE</instanceIdentifier>
                    <source>
                        <address>cs0</address>
                        <internalVerificationCert>dGVzdA==</internalVerificationCert>
                        <externalVerificationCert>dGVzdA==</externalVerificationCert>
                    </source>
                    <member id="m0">
                        <memberClass>
                            <code>BUSINESS</code>
                            <description>1</description>
                        </memberClass>
                        <memberCode>producer</memberCode>
                        <name>Experimental producer</name>
                        <subsystem id="sub0">
                            <subsystemCode>sub</subsystemCode>
                        </subsystem>
                        %s
                    </member>
                    %s
                    <globalSettings>
                        <memberClass>
                            <code>BUSINESS</code>
                            <description>Business clients</description>
                        </memberClass>
                        <ocspFreshnessSeconds>42</ocspFreshnessSeconds>
                    </globalSettings>
                </ns3:conf>
                """.formatted(memberDids, String.join("\n", servers));
    }

    private static String server(String id, String owner, String serverCode, String clients, String dspBaseUrl,
                                 String systemDid) {
        return """
                <securityServer id="%s">
                    <owner>%s</owner>
                    <serverCode>%s</serverCode>
                    <address>%s.example.org</address>
                    <authCertHash>xcuWTrx4uFeP5mNBXIhYITy+gUM72CJfL63MsWXffXM=</authCertHash>
                    %s
                    %s
                    %s
                </securityServer>
                """.formatted(id, owner, serverCode, serverCode, clients,
                dspBaseUrl == null ? "" : "<dspBaseUrl>" + dspBaseUrl + "</dspBaseUrl>",
                systemDid == null ? "" : "<systemDid>" + systemDid + "</systemDid>");
    }
}
