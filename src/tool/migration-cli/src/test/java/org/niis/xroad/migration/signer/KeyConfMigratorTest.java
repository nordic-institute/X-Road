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

package org.niis.xroad.migration.signer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.signer.common.config.SignerConfigKeys;
import org.niis.xroad.signer.core.config.SoftwarePinHasherProperties;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwarePinHasher;
import org.niis.xroad.signer.keyconf.DeviceType;
import org.niis.xroad.signer.keyconf.KeyConfType;
import org.niis.xroad.signer.keyconf.KeyType;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyConfMigratorTest {

    private static final NodeIdentity NODE = NodeIdentity.resolve(null);
    private static final String EXPORTED_PIN = "Secret1234";
    private static final String OTHER_PIN = "Other5678";
    private static final long EXISTING_TOKEN_ID = 5L;
    private static final int UNSALTED_HASH_LENGTH = 32;
    private static final SoftwarePinHasher PIN_HASHER = new SoftwarePinHasher(new SoftwarePinHasherProperties(
            XRoadConfigBuilder.create().register(SignerConfigKeys.instance()).build()));

    private final KeyConfMigrator migrator = new KeyConfMigrator();

    @Mock
    SignerRepository signerRepositoryMock;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(KeyConfMigrator.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(logAppender);
    }

    @Test
    void parseKeyConfShouldReturnDevicesAndKeysFromSignerResources() throws Exception {
        Path keyConfPath = Paths.get(getClass().getClassLoader().getResource("signer/keyconf.xml").toURI());
        KeyConfType keyConf = migrator.parseKeyConf(keyConfPath);

        assertEquals(2, keyConf.getDevice().size(), "Expected a single soft token in test keyconf");
        DeviceType softToken = keyConf.getDevice().stream()
                .filter(p -> p.getDeviceType().equalsIgnoreCase("softtoken")).findFirst().get();
        assertEquals("softToken", softToken.getDeviceType());
        assertEquals("0", softToken.getId());
        assertEquals(3, softToken.getKey().size(), "Expected authentication key + two signing keys");

        Optional<KeyType> authKey = softToken.getKey().stream()
                .filter(key -> key.getKeyId().equals("E67CCA8E9B3DA52DB740CDCDC0926F356F431063"))
                .findFirst();

        assertTrue(authKey.isPresent(), "Authentication key from signer test data must be present");
        assertEquals(1, authKey.get().getCert().size(), "Authentication key should have exactly one certificate");
        assertEquals("CKM_RSA_PKCS", authKey.get().getSignMechanismName());
    }

    @Test
    void parseKeyConfShouldRejectMissingFiles() {
        Path missingKeyConf = Paths.get("not", "existing", "path");

        assertThrows(IllegalArgumentException.class, () -> migrator.parseKeyConf(missingKeyConf));
    }

    @Test
    void testMigrate() throws Exception {
        KeyConfMigrator keyConfMigrator = new TestKeyConfMigrator(signerRepositoryMock, EXPORTED_PIN);

        when(signerRepositoryMock.saveToken(any(), any())).thenReturn(0L);
        when(signerRepositoryMock.saveKey(any(), anyLong(), anyBoolean(), any())).thenReturn(0L);

        keyConfMigrator.migrate(keyconfPath(), "not/used/in/test", NODE);

        verify(signerRepositoryMock, times(2)).saveToken(any(), any());
        verify(signerRepositoryMock, times(3)).saveKey(any(), anyLong(), anyBoolean(), any());
        verify(signerRepositoryMock, times(3)).saveCertificate(any(), anyLong());
        verify(signerRepositoryMock).saveCertificateRequest(any(), anyLong());
    }

    @ParameterizedTest
    @CsvSource({"true,1,true", "false,0,false"})
    void shouldSetPinHashOnlyOnExistingSoftTokenWithoutPin(boolean pinEmpty, int expectedPinWrites, boolean expectFillLine)
            throws Exception {
        KeyConfMigrator keyConfMigrator = new TestKeyConfMigrator(signerRepositoryMock, EXPORTED_PIN);
        stubExistingSoftToken(pinEmpty);
        if (pinEmpty) {
            when(signerRepositoryMock.setTokenPin(eq(EXISTING_TOKEN_ID), any())).thenReturn(true);
        } else {
            when(signerRepositoryMock.getTokenPin(EXISTING_TOKEN_ID))
                    .thenReturn(Optional.of(PIN_HASHER.hashPin(EXPORTED_PIN.toCharArray())));
        }
        when(signerRepositoryMock.saveKey(any(), anyLong(), anyBoolean(), any())).thenReturn(0L);

        keyConfMigrator.migrate(keyconfPath(), "not/used/in/test", NODE);

        verify(signerRepositoryMock, times(expectedPinWrites)).setTokenPin(eq(EXISTING_TOKEN_ID), any());
        verify(signerRepositoryMock, times(2)).saveKey(any(), eq(EXISTING_TOKEN_ID), eq(true), any());
        assertThat(logged().stream().anyMatch(line -> line.contains("PIN hash set on existing token")))
                .isEqualTo(expectFillLine);
        assertThat(logged()).noneMatch(line -> line.contains("does not match"));
    }

    @Test
    void shouldWarnAndKeepRowWhenStoredPinHashDoesNotMatchExportedPin() throws Exception {
        KeyConfMigrator keyConfMigrator = new TestKeyConfMigrator(signerRepositoryMock, EXPORTED_PIN);
        stubExistingSoftToken(false);
        when(signerRepositoryMock.getTokenPin(EXISTING_TOKEN_ID))
                .thenReturn(Optional.of(PIN_HASHER.hashPin(OTHER_PIN.toCharArray())));
        when(signerRepositoryMock.saveKey(any(), anyLong(), anyBoolean(), any())).thenReturn(0L);

        keyConfMigrator.migrate(keyconfPath(), "not/used/in/test", NODE);

        assertThat(logged()).anyMatch(line -> line.contains("⚠ Token 0 has a PIN hash that does not match the exported 7.8 PIN"));
        assertThat(logged()).noneMatch(line -> line.contains(EXPORTED_PIN) || line.contains(OTHER_PIN));
        verify(signerRepositoryMock, never()).setTokenPin(anyLong(), any());
    }

    @Test
    void shouldNotWarnOfMismatchWhenStoredPinHashHasUnverifiableLength() throws Exception {
        KeyConfMigrator keyConfMigrator = new TestKeyConfMigrator(signerRepositoryMock, EXPORTED_PIN);
        stubExistingSoftToken(false);
        when(signerRepositoryMock.getTokenPin(EXISTING_TOKEN_ID)).thenReturn(Optional.of(new byte[UNSALTED_HASH_LENGTH]));
        when(signerRepositoryMock.saveKey(any(), anyLong(), anyBoolean(), any())).thenReturn(0L);

        keyConfMigrator.migrate(keyconfPath(), "not/used/in/test", NODE);

        assertThat(logged()).anyMatch(line -> line.contains("⚠ Token 0 already exists"));
        assertThat(logged()).noneMatch(line -> line.contains("does not match"));
        verify(signerRepositoryMock, never()).setTokenPin(anyLong(), any());
    }

    @Test
    void shouldNotCompareWhenNoPinIsExportedAndTokenHasPinHash() throws Exception {
        KeyConfMigrator keyConfMigrator = new TestKeyConfMigrator(signerRepositoryMock, null);
        stubExistingSoftToken(false);
        when(signerRepositoryMock.saveKey(any(), anyLong(), anyBoolean(), any())).thenReturn(0L);

        keyConfMigrator.migrate(keyconfPath(), "not/used/in/test", NODE);

        verify(signerRepositoryMock, never()).getTokenPin(anyLong());
        verify(signerRepositoryMock, never()).setTokenPin(anyLong(), any());
        assertThat(logged()).noneMatch(line -> line.contains("does not match"));
    }

    @Test
    void shouldWarnAndSkipTokenWithoutPinHashWhenThereIsNoKeystore(@TempDir Path keyconfDir) throws Exception {
        Files.copy(Paths.get(keyconfPath(), "keyconf.xml"), keyconfDir.resolve("keyconf.xml"));
        KeyConfMigrator keyConfMigrator = new TestKeyConfMigrator(signerRepositoryMock, null);
        stubExistingSoftToken(true);

        keyConfMigrator.migrate(keyconfDir.toString(), "not/used/in/test", NODE);

        assertThat(logged()).anyMatch(line -> line.contains("⚠ Token 0 has no 7.x keystore and stays uninitialized"));
        verify(signerRepositoryMock, never()).setTokenPin(anyLong(), any());
        verify(signerRepositoryMock).saveToken(argThat(device -> "2".equals(device.getId())), any());
    }

    @Test
    void shouldWarnInsteadOfConfirmingWhenPinHashUpdateChangedNoRow() throws Exception {
        KeyConfMigrator keyConfMigrator = new TestKeyConfMigrator(signerRepositoryMock, EXPORTED_PIN);
        stubExistingSoftToken(true);
        when(signerRepositoryMock.setTokenPin(eq(EXISTING_TOKEN_ID), any())).thenReturn(false);
        when(signerRepositoryMock.saveKey(any(), anyLong(), anyBoolean(), any())).thenReturn(0L);

        keyConfMigrator.migrate(keyconfPath(), "not/used/in/test", NODE);

        assertThat(logged()).anyMatch(line -> line.contains("⚠ PIN hash not set on token 0"));
        assertThat(logged()).noneMatch(line -> line.contains("✅ PIN hash set"));
    }

    @Test
    void shouldFailOnWrongPinForExistingSoftTokenWithoutPin() throws Exception {
        KeyConfMigrator keyConfMigrator = new TestKeyConfMigrator(signerRepositoryMock, "WrongPin");
        when(signerRepositoryMock.getTokenId("0")).thenReturn(Optional.of(EXISTING_TOKEN_ID));
        when(signerRepositoryMock.isTokenPinEmpty(EXISTING_TOKEN_ID)).thenReturn(true);

        var exception = assertThrows(IllegalStateException.class,
                () -> keyConfMigrator.migrate(keyconfPath(), "not/used/in/test", NODE));
        assertTrue(exception.getMessage().startsWith("Invalid soft token pin provided via"));
        verify(signerRepositoryMock, never()).setTokenPin(anyLong(), any());
    }

    @Test
    void testMalformedKeyConf() throws Exception {
        Path keyConfPath = Paths.get(getClass().getClassLoader().getResource("signer-malformed").toURI());

        var exception = assertThrows(IllegalStateException.class, () -> migrator.migrate(keyConfPath.toString(),
                "not/used/in/test", NODE));
        assertTrue(exception.getMessage().startsWith("Unable to parse keyconf.xml file"));
    }

    private void stubExistingSoftToken(boolean pinEmpty) throws Exception {
        when(signerRepositoryMock.getTokenId("0")).thenReturn(Optional.of(EXISTING_TOKEN_ID));
        when(signerRepositoryMock.getTokenId("2")).thenReturn(Optional.empty());
        when(signerRepositoryMock.isTokenPinEmpty(EXISTING_TOKEN_ID)).thenReturn(pinEmpty);
    }

    private String keyconfPath() throws Exception {
        return Paths.get(getClass().getClassLoader().getResource("signer/").toURI()).toString();
    }

    private List<String> logged() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    class TestKeyConfMigrator extends KeyConfMigrator {
        private final SignerRepository signerRepository;
        private final String pin;

        TestKeyConfMigrator(SignerRepository signerRepository, String pin) {
            super();
            this.signerRepository = signerRepository;
            this.pin = pin;
        }

        @Override
        protected SignerRepository getSignerRepository(String dbPropertiesPath, NodeIdentity nodeIdentity) {
            return signerRepository;
        }

        @Override
        protected Optional<char[]> readPin() {
            return Optional.ofNullable(pin).map(String::toCharArray);
        }
    }

}
