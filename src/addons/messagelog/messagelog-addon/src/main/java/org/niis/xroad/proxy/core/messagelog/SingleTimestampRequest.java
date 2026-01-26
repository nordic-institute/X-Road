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
package org.niis.xroad.proxy.core.messagelog;

import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.signature.Signature;

import lombok.extern.slf4j.Slf4j;
import org.apache.xml.security.signature.XMLSignatureException;
import org.bouncycastle.tsp.TimeStampResponse;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;

import javax.xml.transform.TransformerException;

import java.io.IOException;

import static org.niis.xroad.common.core.exception.ErrorCode.FAILED_TO_PREPARE_SIGNATURE_DATA;
import static org.niis.xroad.common.core.exception.ErrorCode.MESSAGE_LOG_RECORD_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.UPDATING_MESSAGE_SIGNATURE_FAILED;

/**
 * Creates a timestamp request for a single message.
 * <p>
 * The data to be time-stamped is the ds:SignatureValue element.
 */
@Slf4j
class SingleTimestampRequest extends AbstractTimestampRequest {
    private MessageRecord message;
    private Signature signature;

    SingleTimestampRequest(GlobalConfProvider globalConfProvider, Long logRecord) {
        super(globalConfProvider, new Long[]{logRecord});
    }

    @Override
    byte[] getRequestData() {
        LogRecord logRecord;
        try {
            logRecord = LogRecordManager.get(logRecords[0]);
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(MESSAGE_LOG_RECORD_NOT_FOUND)
                    .details("Could not find message record #" + logRecords[0])
                    .cause(e)
                    .build();
        }

        if (!(logRecord instanceof MessageRecord mr)) {
            throw XrdRuntimeException.systemException(MESSAGE_LOG_RECORD_NOT_FOUND)
                    .details("Could not find message record #" + logRecords[0])
                    .build();
        }

        message = mr;

        signature = new Signature(message.getSignature());

        try {
            return signature.getXmlSignature().getSignatureValue();
        } catch (XMLSignatureException e) {
            throw XrdRuntimeException.systemException(FAILED_TO_PREPARE_SIGNATURE_DATA)
                    .details("Failed to create signature for timestamping, record #" + logRecords[0])
                    .cause(e)
                    .build();
        }
    }

    @Override
    Timestamper.TimestampResult result(TimeStampResponse tsResponse, String url)  {
        byte[] timestampDer = getTimestampDer(tsResponse);

        try {
            updateSignatureProperties(timestampDer);
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(UPDATING_MESSAGE_SIGNATURE_FAILED)
                    .details("Updating the message signature properties failed")
                    .cause(e)
                    .build();
        }

        return new Timestamper.TimestampSucceeded(logRecords, timestampDer, null, null, url);
    }

    private void updateSignatureProperties(byte[] timestampDer) throws TransformerException, IOException {
        log.trace("Updating unsigned signature properties");

        signature.addSignatureTimestamp(timestampDer);

        String signatureXml = signature.toXml();

        message.setSignature(signatureXml);
        String oldHash = message.getSignatureHash();
        message.setSignatureHash(LogManager.signatureHash(signatureXml));

        LogRecordManager.updateMessageRecordSignature(message, oldHash);
    }
}
