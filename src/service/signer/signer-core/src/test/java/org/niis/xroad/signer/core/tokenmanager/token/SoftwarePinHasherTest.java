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

package org.niis.xroad.signer.core.tokenmanager.token;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.signer.common.config.SignerConfigKeys;
import org.niis.xroad.signer.core.config.SoftwarePinHasherProperties;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoftwarePinHasherTest {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;

    private SoftwarePinHasher softwarePinHasher;

    @BeforeEach
    void setUp() {
        softwarePinHasher = new SoftwarePinHasher(new SoftwarePinHasherProperties(
                XRoadConfigBuilder.create().register(SignerConfigKeys.instance()).build()));
    }

    @Test
    void hashPin() {
        byte[] hash = softwarePinHasher.hashPin("1234".toCharArray());

        assertNotNull(hash);
        assertEquals(SALT_LENGTH + HASH_LENGTH, hash.length, "Hash length should be salt length + hash length");
    }

    @Test
    void hashPinSaltApplied() {
        byte[] hash1 = softwarePinHasher.hashPin("1234".toCharArray());
        byte[] hash2 = softwarePinHasher.hashPin("1234".toCharArray());

        assertFalse(Arrays.equals(hash1, hash2), "Same PIN should produce different salted hashes");
        assertEquals(SALT_LENGTH + HASH_LENGTH, hash1.length, "Hash length should be salt length + hash length");
    }

    @Test
    void verifyPinShouldSucceedForCorrectPin() {
        byte[] hash = softwarePinHasher.hashPin("1234".toCharArray());

        assertTrue(softwarePinHasher.verifyPin("1234".toCharArray(), hash));
        assertFalse(softwarePinHasher.verifyPin("4321".toCharArray(), hash));
    }

    @Test
    void verifyPinShouldSupportLegacyUnsaltedHash() {
        byte[] legacyHash = rawArgon2Hash();

        assertEquals(HASH_LENGTH, legacyHash.length);
        assertTrue(softwarePinHasher.verifyPin("1234".toCharArray(), legacyHash));
        assertFalse(softwarePinHasher.verifyPin("4321".toCharArray(), legacyHash));
    }

    private byte[] rawArgon2Hash() {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withIterations(4)
                .withMemoryAsKB(19456)
                .withParallelism(4)
                .build();
        byte[] pinBytes = "1234".getBytes(StandardCharsets.UTF_8);
        byte[] hash = new byte[SoftwarePinHasherTest.HASH_LENGTH];

        var generator = new Argon2BytesGenerator();
        generator.init(params);
        generator.generateBytes(pinBytes, hash);
        return hash;
    }

}
