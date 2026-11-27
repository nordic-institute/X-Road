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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.signer.keyconf.DeviceType;
import org.niis.xroad.signer.keyconf.KeyConfType;
import org.niis.xroad.signer.keyconf.KeyType;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyConfMigratorTest {

    private static final NodeIdentity NODE = NodeIdentity.resolve(null);

    private final KeyConfMigrator migrator = new KeyConfMigrator();

    private static final long EXISTING_TOKEN_ID = 5L;
    private static final int PIN_HASH_LENGTH = 48;

    @TempDir
    Path tempDir;

    @Mock
    SignerRepository signerRepositoryMock;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger migratorLogger;

    @BeforeEach
    void attachLogAppender() {
        migratorLogger = (Logger) LoggerFactory.getLogger(KeyConfMigrator.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        migratorLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        migratorLogger.detachAppender(logAppender);
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
        KeyConfMigrator keyConfMigrator = new TestKeyConfMigrator(signerRepositoryMock, "Secret1234");
        String keyconfPath = Paths.get(getClass().getClassLoader().getResource("signer/").toURI()).toString();

        when(signerRepositoryMock.saveToken(any(), any())).thenReturn(0L);
        when(signerRepositoryMock.saveKey(any(), anyLong(), anyBoolean(), any())).thenReturn(0L);

        keyConfMigrator.migrate(keyconfPath, "not/used/in/test", NODE);

        verify(signerRepositoryMock, times(2)).saveToken(any(), any());
        verify(signerRepositoryMock, times(3)).saveKey(any(), anyLong(), anyBoolean(), any());
        verify(signerRepositoryMock, times(3)).saveCertificate(any(), anyLong());
        verify(signerRepositoryMock).saveCertificateRequest(any(), anyLong());
    }

    @Test
    void shouldSetPinHashAndAttachKeysWhenSoftTokenRowExistsWithoutHash() throws Exception {
        when(signerRepositoryMock.getTokenId("0")).thenReturn(Optional.of(EXISTING_TOKEN_ID));
        when(signerRepositoryMock.isTokenPinEmpty(EXISTING_TOKEN_ID)).thenReturn(true);
        when(signerRepositoryMock.setTokenPinIfEmpty(eq(EXISTING_TOKEN_ID), any())).thenReturn(true);

        migrate("Secret1234");

        ArgumentCaptor<byte[]> pinHash = ArgumentCaptor.forClass(byte[].class);
        verify(signerRepositoryMock).setTokenPinIfEmpty(eq(EXISTING_TOKEN_ID), pinHash.capture());
        assertThat(pinHash.getValue()).hasSize(PIN_HASH_LENGTH);
        verify(signerRepositoryMock, times(1)).saveToken(any(), any());
        verify(signerRepositoryMock, times(2)).saveKey(any(), eq(EXISTING_TOKEN_ID), eq(true), any());
        assertThat(loggedMessages()).anyMatch(m -> m.contains("✅ PIN hash set on existing token 0"));
    }

    @Test
    void shouldLogAlreadyExistsAndAttachKeysWhenAnotherRunSetTheHashFirst() throws Exception {
        when(signerRepositoryMock.getTokenId("0")).thenReturn(Optional.of(EXISTING_TOKEN_ID));
        when(signerRepositoryMock.isTokenPinEmpty(EXISTING_TOKEN_ID)).thenReturn(true);
        when(signerRepositoryMock.setTokenPinIfEmpty(eq(EXISTING_TOKEN_ID), any())).thenReturn(false);

        migrate("Secret1234");

        verify(signerRepositoryMock, times(2)).saveKey(any(), eq(EXISTING_TOKEN_ID), eq(true), any());
        assertThat(loggedMessages()).anyMatch(m -> m.contains("⚠ Token 0 already exists"));
        assertThat(loggedMessages()).noneMatch(m -> m.contains("PIN hash set"));
    }

    @Test
    void shouldLeaveTokenUntouchedWhenRowExistsWithHash() throws Exception {
        when(signerRepositoryMock.getTokenId("0")).thenReturn(Optional.of(EXISTING_TOKEN_ID));
        when(signerRepositoryMock.isTokenPinEmpty(EXISTING_TOKEN_ID)).thenReturn(false);

        migrate("Secret1234");

        verify(signerRepositoryMock, never()).setTokenPinIfEmpty(anyLong(), any());
        verify(signerRepositoryMock, times(1)).saveToken(any(), any());
        assertThat(loggedMessages()).anyMatch(m -> m.contains("⚠ Token 0 already exists"));
    }

    @Test
    void shouldFailAndWriteNoHashNorKeysWhenPinIsWrongForHashlessRow() throws Exception {
        when(signerRepositoryMock.getTokenId("0")).thenReturn(Optional.of(EXISTING_TOKEN_ID));
        when(signerRepositoryMock.isTokenPinEmpty(EXISTING_TOKEN_ID)).thenReturn(true);

        var exception = assertThrows(IllegalStateException.class, () -> migrate("WrongPin1"));

        assertTrue(exception.getMessage().startsWith("Invalid soft token pin provided via"));
        verify(signerRepositoryMock, never()).setTokenPinIfEmpty(anyLong(), any());
        verify(signerRepositoryMock, never()).saveKey(any(), anyLong(), anyBoolean(), any());
    }

    @Test
    void shouldSkipTheRepairWithoutReadingThePinWhenTheSevenPointEightKeystoreIsAbsent() throws Exception {
        String keyconfWithoutKeystore = copyKeyconfWithoutSoftTokenKeystore();
        when(signerRepositoryMock.getTokenId("0")).thenReturn(Optional.of(EXISTING_TOKEN_ID));
        when(signerRepositoryMock.getTokenId("2")).thenReturn(Optional.empty());
        when(signerRepositoryMock.isTokenPinEmpty(EXISTING_TOKEN_ID)).thenReturn(true);
        when(signerRepositoryMock.saveToken(any(), any())).thenReturn(EXISTING_TOKEN_ID + 1);

        new TestKeyConfMigrator(signerRepositoryMock, null)
                .migrate(keyconfWithoutKeystore, "not/used/in/test", NODE);

        verify(signerRepositoryMock, never()).setTokenPinIfEmpty(anyLong(), any());
        verify(signerRepositoryMock, times(2)).saveKey(any(), eq(EXISTING_TOKEN_ID), eq(true), any());
        assertThat(loggedMessages())
                .anyMatch(m -> m.equals("  ⚠ Token 0 was not initialized in 7.8, its PIN hash was not set"));
        assertThat(loggedMessages()).noneMatch(m -> m.contains("already exists"));
    }

    @Test
    void shouldNeverSetPinHashOnExistingHardwareTokenRow() throws Exception {
        when(signerRepositoryMock.getTokenId("0")).thenReturn(Optional.empty());
        when(signerRepositoryMock.getTokenId("2")).thenReturn(Optional.of(EXISTING_TOKEN_ID + 1));
        when(signerRepositoryMock.saveToken(any(), any())).thenReturn(EXISTING_TOKEN_ID);
        lenient().when(signerRepositoryMock.isTokenPinEmpty(EXISTING_TOKEN_ID + 1)).thenReturn(true);

        migrate("Secret1234");

        verify(signerRepositoryMock, never()).setTokenPinIfEmpty(anyLong(), any());
    }

    private String copyKeyconfWithoutSoftTokenKeystore() throws Exception {
        Path source = Paths.get(getClass().getClassLoader().getResource("signer/").toURI());
        Path target = tempDir.resolve("keyconf-without-keystore");
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path copy = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(copy);
                } else {
                    Files.copy(path, copy);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Files.delete(target.resolve("softtoken").resolve(".softtoken.p12"));
        return target.toString();
    }

    private void migrate(String pin) throws Exception {
        String keyconfPath = Paths.get(getClass().getClassLoader().getResource("signer/").toURI()).toString();
        new TestKeyConfMigrator(signerRepositoryMock, pin).migrate(keyconfPath, "not/used/in/test", NODE);
    }

    private List<String> loggedMessages() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void testMalformedKeyConf() throws Exception {
        Path keyConfPath = Paths.get(getClass().getClassLoader().getResource("signer-malformed").toURI());

        var exception = assertThrows(IllegalStateException.class, () -> migrator.migrate(keyConfPath.toString(),
                "not/used/in/test", NODE));
        assertTrue(exception.getMessage().startsWith("Unable to parse keyconf.xml file"));
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
        protected char[] readPin() {
            if (pin == null) {
                throw new IllegalStateException("Soft token pin not provided");
            }
            return pin.toCharArray();
        }
    }

}
