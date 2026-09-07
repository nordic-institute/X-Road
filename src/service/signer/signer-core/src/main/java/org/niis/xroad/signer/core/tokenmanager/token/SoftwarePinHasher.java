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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.niis.xroad.signer.core.config.SoftwarePinHasherProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

@ApplicationScoped
@RequiredArgsConstructor
public class SoftwarePinHasher {
    private final SoftwarePinHasherProperties properties;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Hashes the given PIN with a freshly generated random salt and returns {@code salt || hash}.
     */
    public byte[] hashPin(char[] pin) {
        byte[] salt = new byte[properties.saltLength()];
        secureRandom.nextBytes(salt);

        byte[] hash = computeHash(pin, salt, properties.hashLength());

        byte[] result = new byte[salt.length + hash.length];
        System.arraycopy(salt, 0, result, 0, salt.length);
        System.arraycopy(hash, 0, result, salt.length, hash.length);
        return result;
    }

    /**
     * Verifies a candidate PIN against a stored hash produced by {@link #hashPin(char[])}.
     * Also accepts pre-existing hashes computed without a per-token salt, so tokens whose
     * PIN was set before salting was introduced keep working.
     */
    public boolean verifyPin(char[] pin, byte[] storedHash) {
        if (storedHash == null || storedHash.length == 0) {
            return false;
        }

        if (storedHash.length == properties.hashLength()) {
            byte[] candidate = computeHash(pin, new byte[0], properties.hashLength());
            return MessageDigest.isEqual(candidate, storedHash);
        }

        if (storedHash.length == properties.saltLength() + properties.hashLength()) {
            byte[] salt = Arrays.copyOfRange(storedHash, 0, properties.saltLength());
            byte[] expectedHash = Arrays.copyOfRange(storedHash, properties.saltLength(), storedHash.length);
            byte[] candidate = computeHash(pin, salt, expectedHash.length);
            return MessageDigest.isEqual(candidate, expectedHash);
        }

        return false;
    }

    private byte[] computeHash(char[] pin, byte[] salt, int hashLength) {
        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withIterations(properties.iterations())
                .withMemoryAsKB(properties.memoryKb())
                .withParallelism(properties.parallelism());

        if (salt.length != 0) {
            builder.withSalt(salt);
        }

        byte[] pinBytes = new String(pin).getBytes(StandardCharsets.UTF_8);
        byte[] hash = new byte[hashLength];

        var generator = new Argon2BytesGenerator();
        generator.init(builder.build());
        generator.generateBytes(pinBytes, hash);

        return hash;
    }
}
