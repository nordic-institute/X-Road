/**
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
package ee.ria.xroad.common.messagelog;

import ee.ria.xroad.common.asic.AsicContainer;
import ee.ria.xroad.common.asic.TimestampData;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;

/**
 * A message log record.
 */
@Slf4j
@ToString(callSuper = true, exclude = {"attachment"})
@EqualsAndHashCode(callSuper = true, exclude = {"attachment"})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageRecord extends AbstractLogRecord {

    @Getter
    @Setter
    private String queryId;

    @Getter
    @Setter
    private String message;

    @Getter
    @Setter
    private String signature;

    @Getter
    @Setter
    private String hashChain;

    @Getter
    @Setter
    private String hashChainResult;

    @Getter
    @Setter
    private String signatureHash;

    @Getter
    @Setter
    private TimestampRecord timestampRecord;

    @Getter
    @Setter
    private String timestampHashChain;

    @Getter
    @Setter
    private boolean response;

    @Getter
    @Setter
    private String memberClass;

    @Getter
    @Setter
    private String memberCode;

    @Getter
    @Setter
    private String subsystemCode;

    @Getter
    @Setter
    private Blob attachment;

    @Getter
    private transient InputStream attachmentStream;
    @Getter
    private transient long attachmentStreamSize;

    @Getter
    @Setter
    private String xRequestId;

    @Getter
    @Setter
    private String keyId;

    private byte[] cipherMessage;

    @Setter
    private transient Cipher messageCipher;

    @Setter
    private transient Cipher attachmentCipher;

    /**
     * Constructs a message record.
     *
     * @param msg the message
     * @param sig the signature
     * @param clientId message sender client identifier
     * @param xRequestId common id between a request and it's response
     * @throws Exception in case of any errors
     */
    public MessageRecord(SoapMessageImpl msg, String sig, ClientId clientId, String xRequestId)
            throws Exception {
        this(msg.getQueryId(), msg.getXml(), sig, msg.isResponse(), clientId, xRequestId);
    }

    /**
     * Constructs a message record.
     *
     * @param qid the query ID
     * @param msg the message
     * @param sig the signature
     * @param response whether this record is for a response
     * @param clientId message sender client identifier
     * @param xRequestId common id between a request and it's response
     */
    public MessageRecord(String qid, String msg, String sig, boolean response,
            ClientId clientId, String xRequestId) {
        this.queryId = qid;
        this.message = msg;
        this.signature = sig;
        this.response = response;
        this.memberClass = clientId.getMemberClass();
        this.memberCode = clientId.getMemberCode();
        this.subsystemCode = clientId.getSubsystemCode();
        this.xRequestId = xRequestId;
    }

    @Override
    public Object[] getLinkingInfoFields() {
        return new Object[] {getId(), getTime(), queryId, message, signature,
                memberClass, memberCode, subsystemCode};
    }

    public AsicContainer toAsicContainer() throws Exception {
        final boolean encrypted = keyId != null;
        final SignatureData signatureData = new SignatureData(signature, hashChainResult, hashChain);

        if (encrypted && (messageCipher == null || attachmentCipher == null)) {
            throw new IllegalStateException("Encrypted message record has not been prepared for decryption");
        }

        TimestampData timestamp = null;

        if (timestampRecord != null) {
            timestamp = new TimestampData(
                    timestampRecord.getTimestamp(),
                    timestampRecord.getHashChainResult(),
                    timestampHashChain);
        }

        final String plaintextMessage;
        if (encrypted) {
            plaintextMessage = new String(messageCipher.doFinal(cipherMessage), StandardCharsets.UTF_8);
        } else {
            plaintextMessage = message;
        }

        final InputStream plainAttachment;
        if (encrypted && attachment != null) {
            plainAttachment = new CipherInputStream(attachment.getBinaryStream(), attachmentCipher);
        } else {
            plainAttachment = (attachment != null) ? attachment.getBinaryStream() : null;
        }

        return new AsicContainer(plaintextMessage, signatureData, timestamp, plainAttachment, getTime());
    }

    public void setAttachmentStream(InputStream stream, long size) {
        this.attachmentStream = stream;
        this.attachmentStreamSize = size;
    }

    public void setCipherMessage(byte[] msg) {
        this.cipherMessage = msg;
        this.message = null;
    }

}
