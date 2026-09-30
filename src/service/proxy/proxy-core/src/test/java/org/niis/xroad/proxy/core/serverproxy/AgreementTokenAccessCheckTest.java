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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.agreementtoken.AgreementTokenGrant;
import org.niis.xroad.common.agreementtoken.AgreementTokenMinter;
import org.niis.xroad.common.agreementtoken.AgreementTokenProtocolProperties;
import org.niis.xroad.common.agreementtoken.AgreementTokenScope;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyProvider;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenSigningKey;
import org.niis.xroad.proxy.core.configuration.AgreementTokenKeyMaterial;
import org.niis.xroad.proxy.core.configuration.ProxyAgreementTokenProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private InMemoryAgreementTokenKeyProvider keyProvider;
    private ProxyAgreementTokenProperties properties;
    private AgreementTokenAccessCheck accessCheck;

    private Logger logger;
    private RecordingHandler logHandler;

    @BeforeEach
    void setUp() {
        keyProvider = new InMemoryAgreementTokenKeyProvider();
        keyProvider.addKey("1", generateKeyPair());

        properties = mock(ProxyAgreementTokenProperties.class);
        when(properties.issuer()).thenReturn(ISSUER);
        when(properties.audience()).thenReturn(AUDIENCE);
        when(properties.tokenTtl()).thenReturn(TOKEN_TTL);

        var keyMaterial = mock(AgreementTokenKeyMaterial.class);
        when(keyMaterial.provider()).thenReturn(Optional.of(keyProvider));

        accessCheck = new AgreementTokenAccessCheck(keyMaterial, properties);

        logger = Logger.getLogger(AgreementTokenAccessCheck.class.getName());
        logHandler = new RecordingHandler();
        logger.addHandler(logHandler);
        logger.setLevel(Level.ALL);
    }

    @AfterEach
    void tearDown() {
        logger.removeHandler(logHandler);
    }

    @Test
    void shouldSkipAclOnFullSoapMatch() {
        var token = mint(new AgreementTokenScope("*", "**"));

        assertThat(accessCheck.allowsAclSkip(token, CONSUMER, SERVICE)).isTrue();
    }

    @Test
    void shouldSkipAclOnFullRestMatch() {
        var token = mint(new AgreementTokenScope("GET", "/foo/*"));

        assertThat(accessCheck.allowsAclSkip(token, CONSUMER, SERVICE, "GET", "/foo/bar")).isTrue();
    }

    @Test
    void shouldNotSkipAclWhenHeaderIsAbsent() {
        assertThat(accessCheck.allowsAclSkip(null, CONSUMER, SERVICE)).isFalse();
    }

    @Test
    void shouldNotSkipAclWhenHeaderIsBlank() {
        assertThat(accessCheck.allowsAclSkip("   ", CONSUMER, SERVICE)).isFalse();
    }

    @Test
    void shouldNotSkipAclOnGarbageToken() {
        assertThat(accessCheck.allowsAclSkip("not-a-jwt", CONSUMER, SERVICE)).isFalse();
    }

    @Test
    void shouldNotSkipAclWhenTokenSignedByAnotherKey() {
        var strangerProvider = new InMemoryAgreementTokenKeyProvider();
        strangerProvider.addKey("1", generateKeyPair());
        var token = new AgreementTokenMinter(strangerProvider, properties)
                .mint(new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("*", "**"))));

        assertThat(accessCheck.allowsAclSkip(token, CONSUMER, SERVICE)).isFalse();
    }

    @Test
    void shouldNotSkipAclOnExpiredToken() {
        var pastMinter = new AgreementTokenMinter(keyProvider, properties,
                Clock.fixed(Instant.now().minusSeconds(120), ZoneOffset.UTC));
        var token = pastMinter.mint(
                new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("*", "**"))));

        assertThat(accessCheck.allowsAclSkip(token, CONSUMER, SERVICE)).isFalse();
    }

    @Test
    void shouldNotSkipAclOnIssuerMismatch() {
        var wrongIssuerProperties = new TestProtocolProperties("someone-else", AUDIENCE, TOKEN_TTL);
        var token = new AgreementTokenMinter(keyProvider, wrongIssuerProperties)
                .mint(new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("*", "**"))));

        assertThat(accessCheck.allowsAclSkip(token, CONSUMER, SERVICE)).isFalse();
    }

    @Test
    void shouldNotSkipAclOnAudienceMismatch() {
        var wrongAudienceProperties = new TestProtocolProperties(ISSUER, "someone-else", TOKEN_TTL);
        var token = new AgreementTokenMinter(keyProvider, wrongAudienceProperties)
                .mint(new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("*", "**"))));

        assertThat(accessCheck.allowsAclSkip(token, CONSUMER, SERVICE)).isFalse();
    }

    @Test
    void shouldNotSkipAclOnClientMismatchAcrossSubsystemsOfTheSameMember() {
        var token = mint(new AgreementTokenScope("*", "**"));

        assertThat(accessCheck.allowsAclSkip(token, OTHER_SUBSYSTEM, SERVICE)).isFalse();
    }

    @Test
    void shouldNotSkipAclOnServiceVersionMismatch() {
        var token = mint(new AgreementTokenScope("*", "**"));

        assertThat(accessCheck.allowsAclSkip(token, CONSUMER, OTHER_VERSION)).isFalse();
    }

    @Test
    void shouldNotSkipAclOnRestMethodMismatch() {
        var token = mint(new AgreementTokenScope("GET", "/foo/*"));

        assertThat(accessCheck.allowsAclSkip(token, CONSUMER, SERVICE, "POST", "/foo/bar")).isFalse();
    }

    @Test
    void shouldNotSkipAclOnRestPathOutsidePattern() {
        var token = mint(new AgreementTokenScope("GET", "/foo/*"));

        assertThat(accessCheck.allowsAclSkip(token, CONSUMER, SERVICE, "GET", "/other/path")).isFalse();
    }

    @Test
    void shouldNotSkipAclOnPathTraversalEscapingTheGrantedScope() {
        var token = mint(new AgreementTokenScope("GET", "/foo/**"));

        assertThat(accessCheck.allowsAclSkip(token, CONSUMER, SERVICE, "GET", "/foo/../admin")).isFalse();
    }

    @Test
    void shouldNotSkipAclWhenKeyMaterialIsAbsent() {
        var emptyKeyMaterial = mock(AgreementTokenKeyMaterial.class);
        when(emptyKeyMaterial.provider()).thenReturn(Optional.empty());
        var withoutKeyMaterial = new AgreementTokenAccessCheck(emptyKeyMaterial, properties);
        var token = mint(new AgreementTokenScope("*", "**"));

        assertThat(withoutKeyMaterial.allowsAclSkip(token, CONSUMER, SERVICE)).isFalse();
    }

    @Test
    void shouldNotSkipAclAndShouldNotThrowWhenTheProviderThrowsFromKeyById() {
        var token = mint(new AgreementTokenScope("*", "**"));
        var throwingKeyMaterial = mock(AgreementTokenKeyMaterial.class);
        when(throwingKeyMaterial.provider()).thenReturn(Optional.of(new ThrowingAgreementTokenKeyProvider()));
        var withThrowingProvider = new AgreementTokenAccessCheck(throwingKeyMaterial, properties);

        assertThat(withThrowingProvider.allowsAclSkip(token, CONSUMER, SERVICE)).isFalse();
    }

    @Test
    void shouldNeverLogAboveDebugOrLogTheTokenValue() {
        var validToken = mint(new AgreementTokenScope("*", "**"));
        accessCheck.allowsAclSkip(validToken, CONSUMER, SERVICE);
        accessCheck.allowsAclSkip(validToken, OTHER_SUBSYSTEM, SERVICE);
        accessCheck.allowsAclSkip("not-a-jwt", CONSUMER, SERVICE);

        assertThat(logHandler.records).isNotEmpty();
        assertThat(logHandler.records).allSatisfy(record -> {
            assertThat(record.getLevel().intValue()).isLessThanOrEqualTo(Level.FINE.intValue());
            assertThat(record.getMessage()).doesNotContain(validToken);
        });
    }

    private String mint(AgreementTokenScope scope) {
        return new AgreementTokenMinter(keyProvider, properties)
                .mint(new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(scope)));
    }

    private static ECKey generateKeyPair() {
        try {
            return new ECKeyGenerator(Curve.P_256).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private record TestProtocolProperties(String issuer, String audience, Duration tokenTtl) implements AgreementTokenProtocolProperties {
    }

    private static final class InMemoryAgreementTokenKeyProvider implements AgreementTokenKeyProvider {

        private final Map<String, AgreementTokenSigningKey> keysById = new LinkedHashMap<>();
        private String activeKeyId;

        void addKey(String keyId, ECKey keyPair) {
            keysById.put(keyId, new AgreementTokenSigningKey(keyId, keyPair));
            activeKeyId = keyId;
        }

        @Override
        public AgreementTokenSigningKey activeKey() {
            return keysById.get(activeKeyId);
        }

        @Override
        public Optional<AgreementTokenSigningKey> keyById(String keyId) {
            return Optional.ofNullable(keysById.get(keyId));
        }

        @Override
        public AgreementTokenSigningKey rotate() {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public void refresh() {
            // nothing to reload, everything lives in memory
        }
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
