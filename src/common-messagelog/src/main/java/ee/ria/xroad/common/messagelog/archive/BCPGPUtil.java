/**
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
package ee.ria.xroad.common.messagelog.archive;

import org.bouncycastle.bcpg.SignatureSubpacketTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyException;
import java.util.Iterator;

final class BCPGPUtil {

    private BCPGPUtil() {
        //Utility class
    }

    public static PGPSecretKey selectPGPSigningKey(Path secretKeyRing) throws Exception {
        final PGPSecretKeyRingCollection keyRings = new PGPSecretKeyRingCollection(
                PGPUtil.getDecoderStream(Files.newInputStream(secretKeyRing)), new JcaKeyFingerprintCalculator());

        for (PGPSecretKeyRing keyRing : keyRings) {
            for (Iterator<PGPSecretKey> it = keyRing.getSecretKeys(); it.hasNext();) {
                PGPSecretKey key = it.next();
                if (key.getPublicKey().hasRevocation()) continue;
                final Iterator<PGPSignature> signatures = key.getPublicKey().getSignatures();
                while (signatures.hasNext()) {
                    PGPSignature signature = signatures.next();
                    if (!key.isMasterKey() && signature.getSignatureType() != PGPSignature.SUBKEY_BINDING) continue;
                    final KeyFlags flags = (KeyFlags) signature.getHashedSubPackets()
                            .getSubpacket(SignatureSubpacketTags.KEY_FLAGS);
                    if (flags != null) {
                        if (key.isSigningKey() && (flags.getFlags() & KeyFlags.SIGN_DATA) > 0) {
                            return key;
                        }
                    }
                }
            }
        }
        throw new KeyException("No suitable signing key found");
    }

    public static PGPPublicKey selectPGPEncryptionKey(Path publicKeyRing) throws Exception {
        final PGPPublicKeyRingCollection keyRings = new PGPPublicKeyRingCollection(
                PGPUtil.getDecoderStream(Files.newInputStream(publicKeyRing)), new JcaKeyFingerprintCalculator());

        for (PGPPublicKeyRing keyRing : keyRings) {
            for (Iterator<PGPPublicKey> it = keyRing.getPublicKeys(); it.hasNext();) {
                PGPPublicKey key = it.next();
                if (key.hasRevocation()) continue;
                final Iterator<PGPSignature> signatures = key.getSignatures();
                while (signatures.hasNext()) {
                    PGPSignature signature = signatures.next();
                    if (!key.isMasterKey() && signature.getSignatureType() != PGPSignature.SUBKEY_BINDING) continue;
                    final KeyFlags subPacket = (KeyFlags) signature.getHashedSubPackets()
                            .getSubpacket(SignatureSubpacketTags.KEY_FLAGS);
                    if (subPacket != null) {
                        if (key.isEncryptionKey() && (subPacket.getFlags() & KeyFlags.ENCRYPT_STORAGE) > 0) {
                            return key;
                        }
                    }
                }
            }
        }
        throw new KeyException("No suitable encryption key found");
    }
}
