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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.signer.keyconf.DeviceType;
import org.niis.xroad.signer.keyconf.KeyConfType;
import org.niis.xroad.signer.keyconf.KeyType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyConfMigratorTest {

    private final KeyConfMigrator migrator = new KeyConfMigrator();

    @Mock
    SignerRepository signerRepositoryMock;

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

        keyConfMigrator.migrate(keyconfPath, "not/used/in/test");

        verify(signerRepositoryMock, times(2)).saveToken(any(), any());
        verify(signerRepositoryMock, times(4)).saveKey(any(), anyLong(), anyBoolean(), any());
        verify(signerRepositoryMock, times(4)).saveCertificate(any(), anyLong());
        verify(signerRepositoryMock).saveCertificateRequest(any(), anyLong());
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
        protected SignerRepository getSignerRepository(String dbPropertiesPath) {
            return signerRepository;
        }

        @Override
        protected char[] readPinFromConsole() {
            return pin.toCharArray();
        }
    }

}
