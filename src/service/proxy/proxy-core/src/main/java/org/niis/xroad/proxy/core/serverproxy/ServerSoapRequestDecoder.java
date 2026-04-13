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
package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.message.AttachmentStream;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TeeInputStream;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.protocol.Attachment;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.proxy.core.util.CachingStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;

/**
 * Per-request SOAP response decoder for server-side SOAP processing.
 * Created once per SOAP request inside {@link ServerSoapMessageProcessor#process}.
 * All fields are plain (non-volatile) because the decoder is created and used within a single thread.
 */
@Slf4j
public final class ServerSoapRequestDecoder implements SoapMessageDecoder.Callback {

    private final OpMonitoringData opMonitoringData;
    private final String tempFilesPath;
    private final ProxyMessageEncoder encoder;
    @Getter
    private final List<Attachment> attachmentCache = new ArrayList<>();

    @Getter
    private SoapMessageImpl responseSoap;
    @Getter
    private SoapFault responseFault;

    public ServerSoapRequestDecoder(OpMonitoringData opMonitoringData,
                                    String tempFilesPath,
                                    ProxyMessageEncoder encoder) {
        this.opMonitoringData = opMonitoringData;
        this.tempFilesPath = tempFilesPath;
        this.encoder = encoder;
    }

    @Override
    public void soap(SoapMessage message, Map<String, String> headers) throws UnsupportedEncodingException {
        responseSoap = (SoapMessageImpl) message;

        opMonitoringData.setResponseSize(responseSoap.getBytes().length);
        opMonitoringData.setResponseOutTs(getEpochMillisecond(), true);

        encoder.soap(responseSoap, headers);
    }

    @Override
    public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders)
            throws IOException {
        var attachmentCacheStream = new CachingStream(tempFilesPath);
        try (var tis = new TeeInputStream(content, attachmentCacheStream)) {
            encoder.attachment(contentType, tis, additionalHeaders);
            attachmentCache.add(new Attachment(contentType, attachmentCacheStream, additionalHeaders));
        }
    }

    @Override
    public void fault(SoapFault fault) {
        responseFault = fault;
    }

    @Override
    public void onCompleted() {
        // Do nothing — completion is handled by the processor after this callback returns.
    }

    @Override
    @ArchUnitSuppressed("NoVanillaExceptions")
    public void onError(Exception t) throws Exception {
        throw t;
    }

    /**
     * Updates op-monitoring attachment count and MIME size from the encoder state.
     * Must be called after the decoder has fully processed the response.
     */
    public void updateOpMonitoringDataByResponse() {
        opMonitoringData.setResponseAttachmentCount(encoder.getAttachmentCount());

        if (encoder.getAttachmentCount() > 0) {
            opMonitoringData.setResponseMimeSize(responseSoap.getBytes().length + encoder.getAttachmentsByteCount());
        }
    }

    /**
     * Returns cached attachment streams for message logging.
     */
    public List<AttachmentStream> getAttachmentStreams() {
        return attachmentCache.stream().map(Attachment::getAttachmentStream).toList();
    }
}
