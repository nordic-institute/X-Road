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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.signature.Signature;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.tsp.TimeStampResponse;
import org.niis.xroad.globalconf.GlobalConfProvider;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

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
    byte[] getRequestData() throws Exception {
        LogRecord record = LogRecordManager.get(logRecords[0]);

        if (!(record instanceof MessageRecord mr)) {
            throw new CodedException(X_INTERNAL_ERROR, "Could not find message record #" + logRecords[0]);
        }

        message = mr;

        signature = new Signature(message.getSignature());

        return signature.getXmlSignature().getSignatureValue();
    }

    @Override
    Timestamper.TimestampResult result(TimeStampResponse tsResponse, String url) throws Exception {
        byte[] timestampDer = getTimestampDer(tsResponse);

        updateSignatureProperties(timestampDer);

        return new Timestamper.TimestampSucceeded(logRecords, timestampDer, null, null, url);
    }

    private void updateSignatureProperties(byte[] timestampDer) throws Exception {
        log.trace("Updating unsigned signature properties");

        signature.addSignatureTimestamp(timestampDer);

        String signatureXml = signature.toXml();

        message.setSignature(signatureXml);
        String oldHash = message.getSignatureHash();
        message.setSignatureHash(LogManager.signatureHash(signatureXml));

        LogRecordManager.updateMessageRecordSignature(message, oldHash);
    }
}
