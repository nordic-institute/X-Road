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
package org.niis.xroad.common.messagelog;

import ee.ria.xroad.common.message.AttachmentStream;
import ee.ria.xroad.common.messagelog.MessageAttachment;
import ee.ria.xroad.common.messagelog.MessageRecord;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.util.Arrays;
import org.niis.xroad.common.vault.VaultClient;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for applying message log encryption/decryption to a message record.
 * <p>
 * Implementation note:
 * The cipher used is AES-CTR, column keys are deterministically derived from the master key
 * using HKDF (RFC 5869) and the CTR initial counter value (iv) is derived from message record id
 * (database primary key); first 64 bits are id (big endian) and the rest are initially zero.
 * Since there can not be two message records with the same id in the database, the (key, counter) pair is
 * unique (as required by AES-CTR security) as long as each message is shorter than ~2^68 bytes.
 * <p>
 * For message attachments, separate master key is derived using the same HKDF method. The iv is derived from
 * the message record id and the attachment number, using 64 + 32 bits in total, leaving 32 bits for the counter.
 * This allows up to ~2^34 bytes for each attachment.
 * <p>
 * The implementation is a bit convoluted, mostly due to JPA and Blob (large object) handling.
 */
@Slf4j
public final class MessageRecordEncryption {

    private static final int AES_KEY_SIZE = 16;
    private static final int AES_BLOCK_SIZE = 16;

    private final Map<String, SecretKeySpec> messageKeys;
    private final Map<String, SecretKeySpec> attachmentKeys;

    private final String currentKeyId;
    private final boolean encryptionEnabled;

    public MessageRecordEncryption(@Nonnull MessageLogDatabaseEncryptionProperties properties, @Nonnull VaultClient vaultClient) {
        this.encryptionEnabled = properties.enabled();

        try {
            Map<String, SecretKeySpec> tmpMessageKeys = new HashMap<>();
            Map<String, SecretKeySpec> tmpAttachmentKeys = new HashMap<>();

            if (encryptionEnabled) {
                String keyId = properties.keyId();

                // Load the key from Vault
                loadKeyFromVault(vaultClient, keyId, tmpMessageKeys, tmpAttachmentKeys);

                if (tmpMessageKeys.isEmpty() || tmpAttachmentKeys.isEmpty()) {
                    throw new IllegalStateException("Message log encryption is enabled but no key found in Vault");
                }

                this.currentKeyId = keyId;
            } else {
                this.currentKeyId = null;
            }

            messageKeys = Collections.unmodifiableMap(tmpMessageKeys);
            attachmentKeys = Collections.unmodifiableMap(tmpAttachmentKeys);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize message log encryption", e);
        }
    }

    /**
     * Loads all encryption keys from Vault.
     *
     * @param vaultClient       Vault client
     * @param keyId             Key ID from configuration (key-id) to use for encryption
     * @param tmpMessageKeys    Map to populate with message encryption keys
     * @param tmpAttachmentKeys Map to populate with attachment encryption keys
     */
    private void loadKeyFromVault(VaultClient vaultClient, String keyId,
                                  Map<String, SecretKeySpec> tmpMessageKeys,
                                  Map<String, SecretKeySpec> tmpAttachmentKeys) {
        log.debug("Loading message log encryption keys from Vault (current keyId: {})", keyId);

        Map<String, String> allKeys = vaultClient.getMLogDBEncryptionSecretKeys();
        if (allKeys.isEmpty()) {
            throw new IllegalStateException("No encryption keys found in Vault");
        }

        // Load all keys to support decryption of messages encrypted with old keys
        for (Map.Entry<String, String> entry : allKeys.entrySet()) {
            String keyInVaultId = entry.getKey();
            String base64SecretKey = entry.getValue();

            if (base64SecretKey == null || base64SecretKey.isBlank()) {
                log.warn("Skipping empty key for keyId: {}", keyInVaultId);
                continue;
            }

            // Decode base64 secret key
            byte[] secret = Base64.getDecoder().decode(base64SecretKey);

            final HKDFBytesGenerator generator = new HKDFBytesGenerator(new SHA512Digest());
            final byte[] buf = new byte[AES_KEY_SIZE];

            // Use keyId as HKDF salt to derive message and attachment keys
            HKDFParameters hkdfParameters =
                    new HKDFParameters(secret, keyInVaultId.getBytes(StandardCharsets.UTF_8), null);
            generator.init(hkdfParameters);
            Arrays.clear(secret);

            // Derive message encryption key
            generator.generateBytes(buf, 0, AES_KEY_SIZE);
            tmpMessageKeys.put(keyInVaultId, new SecretKeySpec(buf, "AES"));
            Arrays.clear(buf);

            // Derive attachment encryption key
            generator.generateBytes(buf, 0, AES_KEY_SIZE);
            tmpAttachmentKeys.put(keyInVaultId, new SecretKeySpec(buf, "AES"));
            Arrays.clear(buf);

            log.debug("Loaded encryption key from Vault: {}", keyInVaultId);
        }

        // Verify that the current key ID is available
        if (!tmpMessageKeys.containsKey(keyId)) {
            throw new IllegalStateException("Current key ID '" + keyId
                    + "' not found in Vault. Available keys: " + tmpMessageKeys.keySet());
        }

        log.info("Loaded {} encryption key(s) from Vault, current key: {}", tmpMessageKeys.size(), keyId);
    }


    public boolean encryptionEnabled() {
        return encryptionEnabled;
    }

    /**
     * Prepares a message record for decryption.
     * <p>
     * Just populates the transient cipher fields — decryption is (lazily) done when the record
     * is converted to an asic container. Does not change the entity state as it is managed by JPA. Also,
     * avoids reading the blob contents to memory prematurely (can be large, up to 2 GiB).
     *
     * @param messageRecord record to decrypt
     * @return the message record prepared for decryption
     * @throws GeneralSecurityException if setting up the encryption fails.
     */
    public MessageRecord prepareDecryption(MessageRecord messageRecord) throws GeneralSecurityException {
        if (messageRecord != null && messageRecord.getKeyId() != null) {
            final Cipher messageCipher = createCipher(Cipher.DECRYPT_MODE,
                    messageRecord.getKeyId(),
                    messageKeys, messageIv(messageRecord.getId()));
            messageRecord.setMessageCipher(messageCipher);

            for (MessageAttachment attachment : messageRecord.getAttachments()) {
                final Cipher attachmentCipher = createCipher(Cipher.DECRYPT_MODE,
                        messageRecord.getKeyId(),
                        attachmentKeys, attachmentIv(messageRecord.getId(), attachment.getAttachmentNo()));
                attachment.setAttachmentCipher(attachmentCipher);
            }
        }
        return messageRecord;
    }

    /**
     * Prepares a message record for encryption.
     * <p>
     * Assumes a new message record not yet persisted to database. The message is encrypted and the attachment stream
     * (if any) is wrapped to a CipherStream so that persisting the record will eventually encrypt the contents. In
     * order to be able to use record id sequence as IV, the setup needs to be deferred until transaction is active.
     *
     * @param messageRecord message record to encrypt
     * @return the message record prepared for encryption
     * @throws GeneralSecurityException if setting up the encryption fails.
     */
    public MessageRecord prepareEncryption(MessageRecord messageRecord) throws GeneralSecurityException {
        if (messageRecord == null) {
            return null;
        }

        final String keyId = currentKeyId;

        messageRecord.setKeyId(keyId);
        final int mode = Cipher.ENCRYPT_MODE;

        final Cipher messageCipher = createCipher(mode, keyId, messageKeys, messageIv(messageRecord.getId()));

        messageRecord.setCipherMessage(
                messageCipher.doFinal(messageRecord.getMessage().getBytes(StandardCharsets.UTF_8)));

        if (!messageRecord.getAttachmentStreams().isEmpty()) {
            List<AttachmentStream> cipherAttachmentStreams = new ArrayList<>();
            for (int i = 0; i < messageRecord.getAttachmentStreams().size(); i++) {
                Cipher attachmentCipher = createCipher(mode, keyId, attachmentKeys, attachmentIv(messageRecord.getId(), i + 1));
                cipherAttachmentStreams.add(new CipherAttachmentStream(messageRecord.getAttachmentStreams().get(i), attachmentCipher));
            }
            messageRecord.setAttachmentStreams(cipherAttachmentStreams);
        }
        return messageRecord;
    }

    private Cipher createCipher(int mode, String keyId, Map<String, SecretKeySpec> keys, IvParameterSpec iv)
            throws GeneralSecurityException {

        final SecretKeySpec keySpec = keys.get(keyId);
        if (keySpec == null) {
            throw new KeyException("Messagelog cipher key '" + keyId + "' not found");
        }

        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");

        cipher.init(mode, keySpec, iv);
        return cipher;
    }

    private IvParameterSpec messageIv(long messageRecordId) {
        ByteBuffer ivBuf = ByteBuffer.allocate(AES_BLOCK_SIZE);

        ivBuf.putLong(messageRecordId);

        return toIvParameterSpec(ivBuf);
    }

    private IvParameterSpec attachmentIv(long messageRecordId, int attachmentNo) {
        ByteBuffer ivBuf = ByteBuffer.allocate(AES_BLOCK_SIZE);

        ivBuf.putLong(messageRecordId);
        // Attachment numbering starts from 1. To keep backward compatibility, one is subtracted.
        // Previously, only one attachment was supported and it was not included in the IV.
        ivBuf.putInt(attachmentNo - 1);

        return toIvParameterSpec(ivBuf);
    }

    @SuppressWarnings("java:S3329") //predictable IV can be used in CTR mode, uniqueness is important
    private IvParameterSpec toIvParameterSpec(ByteBuffer ivBuf) {
        return new IvParameterSpec(ivBuf.array());
    }

    private record CipherAttachmentStream(AttachmentStream attachmentStream, Cipher cipher) implements AttachmentStream {
        @Override
        public InputStream getStream() {
            return new CipherInputStream(attachmentStream.getStream(), cipher);
        }

        @Override
        public long getSize() {
            //CTR mode does not change the message length.
            return attachmentStream.getSize();
        }
    }
}

