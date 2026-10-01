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
package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.agreementtoken.AgreementTokenGrant;
import org.niis.xroad.common.agreementtoken.AgreementTokenMinter;
import org.niis.xroad.common.agreementtoken.AgreementTokenProtocolProperties;
import org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason;
import org.niis.xroad.common.agreementtoken.AgreementTokenScope;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyProvider;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenSigningKey;
import org.niis.xroad.proxy.core.configuration.AgreementTokenKeyMaterial;
import org.niis.xroad.proxy.core.configuration.ProxyAgreementTokenProperties;
import org.niis.xroad.proxy.core.test.InMemoryAgreementTokenKeyProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgreementTokenAccessCheckTest {

    private static final ClientId CONSUMER = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ClientId OTHER_SUBSYSTEM = ClientId.Conf.create("DEV", "COM", "222", "OTHERCLIENT");
    private static final ClientId PROVIDER = ClientId.Conf.create("DEV", "COM", "333", "PROVIDER");
    private static final ServiceId SERVICE = ServiceId.Conf.create(PROVIDER, "getData", "v1");
    private static final ServiceId OTHER_VERSION = ServiceId.Conf.create(PROVIDER, "getData", "v2");

    private static final String ISSUER = "x-road-provider-data-plane";
    private static final String AUDIENCE = "x-road-server-proxy";
    private static final Duration TOKEN_TTL = Duration.ofSeconds(60);
    private static final String CONTEXT_REASON = "CONTEXT";
    private static final String VERIFIER_ERROR_REASON = "VERIFIER_ERROR";

    private InMemoryAgreementTokenKeyProvider keyProvider;
    private ProxyAgreementTokenProperties properties;
    private AgreementTokenAccessCheck accessCheck;

    private Logger logger;
    private RecordingHandler logHandler;
    private Level originalLogLevel;

    @BeforeEach
    void setUp() {
        keyProvider = InMemoryAgreementTokenKeyProvider.withGeneratedKey("1");

        properties = mock(ProxyAgreementTokenProperties.class);
        when(properties.issuer()).thenReturn(ISSUER);
        when(properties.audience()).thenReturn(AUDIENCE);
        when(properties.tokenTtl()).thenReturn(TOKEN_TTL);
        when(properties.expiryLeeway()).thenReturn(Duration.ZERO);

        var keyMaterial = mock(AgreementTokenKeyMaterial.class);
        when(keyMaterial.provider()).thenReturn(Optional.of(keyProvider));

        accessCheck = new AgreementTokenAccessCheck(keyMaterial, properties);

        logger = Logger.getLogger(AgreementTokenAccessCheck.class.getName());
        originalLogLevel = logger.getLevel();
        logHandler = new RecordingHandler();
        logger.addHandler(logHandler);
        logger.setLevel(Level.ALL);
    }

    @AfterEach
    void tearDown() {
        logger.removeHandler(logHandler);
        logger.setLevel(originalLogLevel);
    }

    @Test
    void shouldAcceptOnFullSoapMatch() {
        var token = mint(new AgreementTokenScope("*", "**"));

        assertThat(accessCheck.decide(token, CONSUMER, SERVICE)).isInstanceOf(AgreementTokenAccessCheck.Decision.Accepted.class);
    }

    @Test
    void shouldAcceptOnFullRestMatch() {
        var token = mint(new AgreementTokenScope("GET", "/foo/*"));

        assertThat(accessCheck.decide(token, CONSUMER, SERVICE, "GET", "/foo/bar"))
                .isInstanceOf(AgreementTokenAccessCheck.Decision.Accepted.class);
    }

    @Test
    void shouldReturnAbsentWhenHeaderIsAbsent() {
        assertThat(accessCheck.decide(null, CONSUMER, SERVICE)).isInstanceOf(AgreementTokenAccessCheck.Decision.Absent.class);
    }

    @Test
    void shouldReturnAbsentWhenHeaderIsBlank() {
        assertThat(accessCheck.decide("   ", CONSUMER, SERVICE)).isInstanceOf(AgreementTokenAccessCheck.Decision.Absent.class);
    }

    @Test
    void shouldRejectGarbageToken() {
        assertRejected(accessCheck.decide("not-a-jwt", CONSUMER, SERVICE), AgreementTokenRejectionReason.MALFORMED_TOKEN);
    }

    @Test
    void shouldRejectWhenTokenSignedByAnotherKey() {
        var strangerProvider = InMemoryAgreementTokenKeyProvider.withGeneratedKey("1");
        var token = new AgreementTokenMinter(strangerProvider, properties)
                .mint(new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("*", "**"))));

        assertRejected(accessCheck.decide(token, CONSUMER, SERVICE), AgreementTokenRejectionReason.INVALID_SIGNATURE);
    }

    @Test
    void shouldRejectExpiredToken() {
        var pastMinter = new AgreementTokenMinter(keyProvider, properties,
                Clock.fixed(Instant.now().minusSeconds(120), ZoneOffset.UTC));
        var token = pastMinter.mint(
                new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("*", "**"))));

        assertRejected(accessCheck.decide(token, CONSUMER, SERVICE), AgreementTokenRejectionReason.EXPIRED);
    }

    @Test
    void shouldRejectOnIssuerMismatch() {
        var wrongIssuerProperties = new TestProtocolProperties("someone-else", AUDIENCE, TOKEN_TTL);
        var token = new AgreementTokenMinter(keyProvider, wrongIssuerProperties)
                .mint(new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("*", "**"))));

        assertRejected(accessCheck.decide(token, CONSUMER, SERVICE), AgreementTokenRejectionReason.ISSUER_MISMATCH);
    }

    @Test
    void shouldRejectOnAudienceMismatch() {
        var wrongAudienceProperties = new TestProtocolProperties(ISSUER, "someone-else", TOKEN_TTL);
        var token = new AgreementTokenMinter(keyProvider, wrongAudienceProperties)
                .mint(new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("*", "**"))));

        assertRejected(accessCheck.decide(token, CONSUMER, SERVICE), AgreementTokenRejectionReason.AUDIENCE_MISMATCH);
    }

    @Test
    void shouldRejectOnClientMismatchAcrossSubsystemsOfTheSameMember() {
        var token = mint(new AgreementTokenScope("*", "**"));

        assertRejected(accessCheck.decide(token, OTHER_SUBSYSTEM, SERVICE), AgreementTokenRejectionReason.CLIENT_MISMATCH);
    }

    @Test
    void shouldRejectOnServiceVersionMismatch() {
        var token = mint(new AgreementTokenScope("*", "**"));

        assertRejected(accessCheck.decide(token, CONSUMER, OTHER_VERSION), AgreementTokenRejectionReason.SERVICE_MISMATCH);
    }

    @Test
    void shouldRejectOnRestMethodMismatch() {
        var token = mint(new AgreementTokenScope("GET", "/foo/*"));

        assertRejected(accessCheck.decide(token, CONSUMER, SERVICE, "POST", "/foo/bar"), AgreementTokenRejectionReason.SCOPE_MISMATCH);
    }

    @Test
    void shouldRejectOnRestPathOutsidePattern() {
        var token = mint(new AgreementTokenScope("GET", "/foo/*"));

        assertRejected(accessCheck.decide(token, CONSUMER, SERVICE, "GET", "/other/path"), AgreementTokenRejectionReason.SCOPE_MISMATCH);
    }

    @Test
    void shouldRejectOnPathTraversalEscapingTheGrantedScope() {
        var token = mint(new AgreementTokenScope("GET", "/foo/**"));

        assertRejected(accessCheck.decide(token, CONSUMER, SERVICE, "GET", "/foo/../admin"), AgreementTokenRejectionReason.SCOPE_MISMATCH);
    }

    @Test
    void shouldReturnAbsentOnNullClientWithoutTokenSoap() {
        assertThat(accessCheck.decide(null, null, SERVICE)).isInstanceOf(AgreementTokenAccessCheck.Decision.Absent.class);
    }

    @Test
    void shouldReturnAbsentOnNullClientWithoutTokenRest() {
        assertThat(accessCheck.decide(null, null, SERVICE, "GET", "/foo/bar"))
                .isInstanceOf(AgreementTokenAccessCheck.Decision.Absent.class);
    }

    @Test
    void shouldRejectAndNotThrowOnNullClientWithTokenSoap() {
        var token = mint(new AgreementTokenScope("*", "**"));

        assertRejected(accessCheck.decide(token, null, SERVICE), CONTEXT_REASON);
    }

    @Test
    void shouldRejectAndNotThrowOnNullClientWithTokenRest() {
        var token = mint(new AgreementTokenScope("GET", "/foo/*"));

        assertRejected(accessCheck.decide(token, null, SERVICE, "GET", "/foo/bar"), CONTEXT_REASON);
    }

    @Test
    void shouldRejectAndNotThrowOnNullRestMethodAndPathWithToken() {
        var token = mint(new AgreementTokenScope("GET", "/foo/*"));

        assertRejected(accessCheck.decide(token, CONSUMER, SERVICE, null, null), CONTEXT_REASON);
    }

    @Test
    void shouldReturnUnavailableWhenKeyMaterialIsAbsent() {
        var emptyKeyMaterial = mock(AgreementTokenKeyMaterial.class);
        when(emptyKeyMaterial.provider()).thenReturn(Optional.empty());
        var withoutKeyMaterial = new AgreementTokenAccessCheck(emptyKeyMaterial, properties);
        var token = mint(new AgreementTokenScope("*", "**"));

        assertThat(withoutKeyMaterial.decide(token, CONSUMER, SERVICE))
                .isInstanceOf(AgreementTokenAccessCheck.Decision.Unavailable.class);
    }

    @Test
    void shouldRejectAndNotThrowWhenTheProviderThrowsFromKeyById() {
        var token = mint(new AgreementTokenScope("*", "**"));
        var throwingKeyMaterial = mock(AgreementTokenKeyMaterial.class);
        when(throwingKeyMaterial.provider()).thenReturn(Optional.of(new ThrowingAgreementTokenKeyProvider()));
        var withThrowingProvider = new AgreementTokenAccessCheck(throwingKeyMaterial, properties);

        assertRejected(withThrowingProvider.decide(token, CONSUMER, SERVICE), VERIFIER_ERROR_REASON);
    }

    @Test
    void acceptedIsLoggedAtDebugWithoutTheTokenValue() {
        var validToken = mint(new AgreementTokenScope("*", "**"));

        accessCheck.decide(validToken, CONSUMER, SERVICE);

        assertThat(logHandler.records).isNotEmpty();
        assertThat(logHandler.records).allSatisfy(record -> {
            assertThat(record.getLevel().intValue()).isLessThanOrEqualTo(Level.FINE.intValue());
            assertThat(record.getMessage()).doesNotContain(validToken);
        });
    }

    @Test
    void absentIsNeverLoggedAboveDebug() {
        accessCheck.decide(null, CONSUMER, SERVICE);

        assertThat(logHandler.records).allSatisfy(record ->
                assertThat(record.getLevel().intValue()).isLessThanOrEqualTo(Level.FINE.intValue()));
    }

    @Test
    void rejectedIsLoggedAtWarnWithoutTheTokenValue() {
        var token = mint(new AgreementTokenScope("*", "**"));

        accessCheck.decide(token, OTHER_SUBSYSTEM, SERVICE);

        assertThat(logHandler.records).isNotEmpty();
        assertThat(logHandler.records).allSatisfy(record -> {
            assertThat(record.getLevel()).isEqualTo(Level.WARNING);
            assertThat(record.getMessage()).doesNotContain(token);
        });
    }

    @Test
    void unavailableIsLoggedAtWarnWithoutTheTokenValue() {
        var emptyKeyMaterial = mock(AgreementTokenKeyMaterial.class);
        when(emptyKeyMaterial.provider()).thenReturn(Optional.empty());
        var withoutKeyMaterial = new AgreementTokenAccessCheck(emptyKeyMaterial, properties);
        var token = mint(new AgreementTokenScope("*", "**"));

        withoutKeyMaterial.decide(token, CONSUMER, SERVICE);

        assertThat(logHandler.records).isNotEmpty();
        assertThat(logHandler.records).allSatisfy(record -> {
            assertThat(record.getLevel()).isEqualTo(Level.WARNING);
            assertThat(record.getMessage()).doesNotContain(token);
        });
    }

    private void assertRejected(AgreementTokenAccessCheck.Decision decision, AgreementTokenRejectionReason reason) {
        assertRejected(decision, reason.name());
    }

    private void assertRejected(AgreementTokenAccessCheck.Decision decision, String reasonClass) {
        assertThat(decision).isInstanceOf(AgreementTokenAccessCheck.Decision.Rejected.class);
        assertThat(((AgreementTokenAccessCheck.Decision.Rejected) decision).reasonClass()).isEqualTo(reasonClass);
    }

    private String mint(AgreementTokenScope scope) {
        return new AgreementTokenMinter(keyProvider, properties)
                .mint(new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(scope)));
    }

    private record TestProtocolProperties(String issuer, String audience, Duration tokenTtl) implements AgreementTokenProtocolProperties {
    }

    private static final class ThrowingAgreementTokenKeyProvider implements AgreementTokenKeyProvider {

        @Override
        public AgreementTokenSigningKey activeKey() {
            throw new IllegalStateException("key material unavailable");
        }

        @Override
        public Optional<AgreementTokenSigningKey> keyById(String keyId) {
            throw new IllegalStateException("key material unavailable");
        }

        @Override
        public AgreementTokenSigningKey rotate() {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public void refresh() {
            // nothing to reload
        }
    }

    private static final class RecordingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
            // no-op, records are kept in memory
        }

        @Override
        public void close() {
            // no-op, nothing to release
        }
    }
}
