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
package ee.ria.xroad.common.messagelog;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.AttachmentStream;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;

import lombok.Getter;
import lombok.NonNull;

import java.util.List;

/**
 * LogMessage for SOAP
 */
public final class SoapLogMessage extends LogMessage {
    @Getter
    private final SoapMessageImpl message;

    @Getter
    @NonNull
    private final List<AttachmentStream> attachments;


    /**
     * Create a SOAP log message
     */
    public SoapLogMessage(SoapMessageImpl message,
                          SignatureData signature,
                          @NonNull List<AttachmentStream> attachments,
                          boolean clientSide,
                          String xRequestId) {
        super(signature, clientSide, xRequestId);
        this.message = message;
        this.attachments = attachments;
    }

    public String getQueryId() {
        return message.getQueryId();
    }

    public ClientId getClient() {
        return message.getClient();
    }

    public ServiceId getService() {
        return message.getService();
    }

    public boolean isResponse() {
        return message.isResponse();
    }
}
