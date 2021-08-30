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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public final class MessageLogEncryption {

    //future feature: instance can be replaced if configuration changes
    private static volatile MessageLogEncryption instance;

    private static final int AES_KEY_SIZE = 16;
    private static final int AES_BLOCK_SIZE = 16;

    private final Map<String, SecretKeySpec> messageKeys;
    private final Map<String, SecretKeySpec> attachmentKeys;

    private final String currentKeyId = MessageLogProperties.getMessageLogKeyId();
    private final boolean encryptionEnabled = MessageLogProperties.isMessageLogEncryptionEnabled();

    MessageLogEncryption() {
        try {
            Map<String, SecretKeySpec> tmpMessageKeys = new HashMap<>();
            Map<String, SecretKeySpec> tmpAttachmentKeys = new HashMap<>();

            final Path store = MessageLogProperties.getMessageLogKeyStore();
            if (store != null && Files.exists(store)) {
                final char[] password = MessageLogProperties.getMessageLogKeyStorePassword();
                final KeyStore keyStore = KeyStore.getInstance("pkcs12");
                try (InputStream is = Files.newInputStream(store)) {
                    keyStore.load(is, password);
                }
                final HKDFBytesGenerator generator = new HKDFBytesGenerator(new SHA512Digest());
                final Enumeration<String> aliases = keyStore.aliases();
                final byte[] buf = new byte[AES_KEY_SIZE];

                while (aliases.hasMoreElements()) {
                    final String keyId = aliases.nextElement();
                    final Key key = keyStore.getKey(keyId, password);
                    if (!(key instanceof SecretKey && "RAW".equalsIgnoreCase(key.getFormat())
                            && key.getEncoded() != null)) {
                        log.warn("Keystore {} entry {} is not a secret key with raw encoding, ignoring", store, keyId);
                        continue;
                    }
                    byte[] secret = key.getEncoded();

                    HKDFParameters hkdfParameters =
                            new HKDFParameters(secret, keyId.getBytes(StandardCharsets.UTF_8), null);
                    generator.init(hkdfParameters);
                    Arrays.clear(secret);

                    generator.generateBytes(buf, 0, AES_KEY_SIZE);
                    tmpMessageKeys.put(keyId, new SecretKeySpec(buf, "AES"));
                    Arrays.clear(buf);

                    generator.generateBytes(buf, 0, AES_KEY_SIZE);
                    tmpAttachmentKeys.put(keyId, new SecretKeySpec(buf, "AES"));
                    Arrays.clear(buf);
                }
            }
            messageKeys = Collections.unmodifiableMap(tmpMessageKeys);
            attachmentKeys = Collections.unmodifiableMap(tmpAttachmentKeys);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize message log encryption", e);
        }

        if (encryptionEnabled && (messageKeys.isEmpty() || attachmentKeys.isEmpty())) {
            log.warn("Message log encryption is enabled but no keys are available.");
        }
    }

    public static MessageLogEncryption getInstance() {
        if (instance == null) {
            synchronized (MessageLogEncryption.class) {
                if (instance == null) {
                    instance = new MessageLogEncryption();
                }
            }
        }
        return instance;
    }

    public boolean encryptionEnabled() {
        return encryptionEnabled;
    }

    /**
     * Functions for applying message log encryption/decryption to the message record.
     *
     * The Cipher is AES-CTR, column keys are deterministically derived from the master key
     * using HKDF (RFC 5869) and the CTR initial counter value (iv) is derived from message record id
     * (database primary key); first 64 bits are id (big endian) and the rest are initially zero.
     * Since there can not be two message records with the same id in the database, the (key, counter) pair is
     * unique (as required by AES-CTR security) as long as each message is shorter than ~2^68 bytes.
     *
     * The implementation is a bit convoluted, mostly due to JPA, and Blob (large object) handling and the
     * fact that the MessageRecord class is part of a different module.
     *
     * When encrypting (assumes new message record not yet persisted to database), the message is encrypted and
     * the attachment stream is wrapped to a CipherStream so that persisting the record will eventually encrypt
     * the contents.
     *
     * When decrypting, just populates the transient cipher fields -- decryption is (lazily) done when the record
     * is converted to an asic container. We must not change the entity state it is managed by JPA. Also,
     * we try to avoid reading the blob contents to memory because it can be large (up to 2GiB).
     */

    public MessageRecord prepareDecryption(MessageRecord messageRecord) throws GeneralSecurityException {
        if (messageRecord != null && messageRecord.getKeyId() != null) {
            final Cipher messageCipher = createCipher(Cipher.DECRYPT_MODE, messageRecord.getId(),
                    messageRecord.getKeyId(),
                    messageKeys);
            final Cipher attachmentCipher = createCipher(Cipher.DECRYPT_MODE, messageRecord.getId(),
                    messageRecord.getKeyId(),
                    attachmentKeys);

            messageRecord.setMessageCipher(messageCipher);
            messageRecord.setAttachmentCipher(attachmentCipher);
        }
        return messageRecord;
    }

    public MessageRecord prepareEncryption(MessageRecord messageRecord) throws GeneralSecurityException {
        if (messageRecord == null) {
            return null;
        }

        final String keyId = currentKeyId;

        messageRecord.setKeyId(keyId);
        final int mode = Cipher.ENCRYPT_MODE;

        final Cipher messageCipher = createCipher(mode, messageRecord.getId(), keyId, messageKeys);

        messageRecord.setCipherMessage(
                messageCipher.doFinal(messageRecord.getMessage().getBytes(StandardCharsets.UTF_8)));

        if (messageRecord.getAttachmentStream() != null) {
            final Cipher attachmentCipher = createCipher(mode, messageRecord.getId(), keyId, attachmentKeys);
            messageRecord.setAttachmentStream(
                    new CipherInputStream(messageRecord.getAttachmentStream(), attachmentCipher),
                    //CTR mode does not change the message length.
                    messageRecord.getAttachmentStreamSize());
        }
        return messageRecord;
    }

    private Cipher createCipher(int mode, long recordId, String keyId, Map<String, SecretKeySpec> keys)
            throws GeneralSecurityException {

        final SecretKeySpec keySpec = keys.get(keyId);
        if (keySpec == null) {
            throw new KeyException("Messagelog cipher key '" + keyId + "' not found");
        }

        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        ByteBuffer ivBuf = ByteBuffer.allocate(AES_BLOCK_SIZE);

        ivBuf.putLong(recordId);

        @SuppressWarnings("java:S3329") //predictable IV can be used in CTR mode, uniqueness is important
        IvParameterSpec iv = new IvParameterSpec(ivBuf.array());

        cipher.init(mode, keySpec, iv);
        return cipher;
    }

    static synchronized void reload() {
        instance = new MessageLogEncryption();
    }
}
