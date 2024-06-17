/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.SaxSoapParserImpl;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.bouncycastle.util.io.TeeInputStream;
import org.niis.xroad.edc.sig.XrdSignatureService;
import org.niis.xroad.proxy.clientproxy.validate.RequestValidator;
import org.niis.xroad.proxy.clientproxy.validate.SoapResponseValidator;
import org.niis.xroad.proxy.edc.AssetAuthorizationManager;
import org.niis.xroad.proxy.edc.AuthorizedAssetRegistry;
import org.niis.xroad.proxy.edc.EdcDataPlaneHttpClient;
import org.niis.xroad.proxy.edc.SoapRequestData;
import org.niis.xroad.proxy.edc.TargetSecurityServerLookup;
import org.niis.xroad.proxy.edc.XrdDataSpaceClient;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.niis.xroad.edc.sig.PocConstants.HEADER_XRD_SIG;

/**
 * TODO missing opmon data
 * request hash is not used (protocol marks it as optional)
 */
@Slf4j
class ClientSoapMessageDsProcessor extends AbstractClientMessageProcessor {
    private final AssetAuthorizationManager assetAuthorizationManager;
    private final XrdDataSpaceClient xrdDataSpaceClient = new XrdDataSpaceClient();
    private final XrdSignatureService xrdSignatureService = new XrdSignatureService();

    private final RequestValidator requestValidator = new RequestValidator();
    private final SoapResponseValidator responseValidator = new SoapResponseValidator();

    ClientSoapMessageDsProcessor(RequestWrapper request, ResponseWrapper response,
                                 HttpClient httpClient, IsAuthenticationData clientCert, OpMonitoringData opMonitoringData,
                                 AssetAuthorizationManager assetAuthorizationManager)
            throws Exception {
        super(request, response, httpClient, clientCert, opMonitoringData);
        this.assetAuthorizationManager = assetAuthorizationManager;
    }

    @Override
    public void process() throws Exception {
        log.trace("process()");
        String xRequestId = UUID.randomUUID().toString();
        opMonitoringData.setXRequestId(xRequestId);
        updateOpMonitoringClientSecurityServerAddress();

        SoapRequestData requestSoap = deserializeToSoap(jRequest.getContentType(), jRequest.getInputStream());

        updateOpMonitoringDataBySoapMessage(opMonitoringData, requestSoap.soapMessage());
        requestValidator.validateSoap(requestSoap.soapMessage(), clientCert);

        var targetServers = TargetSecurityServerLookup.resolveTargetSecurityServers(requestSoap.soapMessage().getService().getClientId());

        //TODO xroad8 in POC we're not selecting fastest server, neither handle failure with fallbacks
        var targetServerInfo = targetServers.servers().stream().findFirst().orElseThrow();

        ClientId client = requestSoap.soapMessage().getClient();
        var assetInfo = assetAuthorizationManager.getOrRequestAssetAccess(client, targetServerInfo,
                requestSoap.soapMessage().getService(), false);

        processRequest(requestSoap, assetInfo, xRequestId);
    }

    private void updateOpMonitoringClientSecurityServerAddress() {
        try {
            opMonitoringData.setClientSecurityServerAddress(getSecurityServerAddress());
        } catch (Exception e) {
            log.error("Failed to assign operational monitoring data field {}",
                    OpMonitoringData.CLIENT_SECURITY_SERVER_ADDRESS, e);
        }
    }

    private void processRequest(SoapRequestData requestSoap, AuthorizedAssetRegistry.GrantedAssetInfo assetInfo,
                                String xRequestId) throws Exception {
        log.trace("processRequest()");

        opMonitoringData.setRequestOutTs(getEpochMillisecond());

        var response = xrdDataSpaceClient.processSoapRequest(requestSoap, xRequestId, assetInfo);

        opMonitoringData.setResponseInTs(getEpochMillisecond());

        validateResponse(requestSoap, response);

        opMonitoringData.setResponseOutTs(getEpochMillisecond(), true);

        processResponse(response, jResponse);
    }

    private void validateResponse(SoapRequestData requestSoap, EdcDataPlaneHttpClient.EdcSoapWrapper response) throws Exception {
        SoapMessageImpl responseSoap = (SoapMessageImpl) response.soapMessage();

        // TODO: FIXME: enable request hash validation!!!
        //checkRequestHash(requestSoap, responseSoap);
        responseValidator.checkConsistency(requestSoap.soapMessage(), responseSoap);

        //TODO handle bad request/edc failure
        var signature = response.headers().get(HEADER_XRD_SIG);
        // TODO: FIXME: enable!!! currently fails because signature includes request hash field
//        xrdSignatureService.verify(signature, ((SoapMessageImpl) response.soapMessage()).getBytes(),
//                requestSoap.getService().getClientId());
    }

    private void processResponse(EdcDataPlaneHttpClient.EdcSoapWrapper response, ResponseWrapper jResponse) throws Exception {
        log.trace("sendResponse()");

        jResponse.setStatus(OK_200);
        response.headers().forEach(jResponse.getHeaders()::add);

        try (var content = response.cachedMessage()) {
            IOUtils.copy(content.getCachedContents(), jResponse.getOutputStream());
        }
    }

    private SoapRequestData deserializeToSoap(String contentType, InputStream body) {
        try (SoapRequestMessageHandler handler = new SoapRequestMessageHandler()) {
            CachingStream requestStream = new CachingStream();
            TeeInputStream teeInputStream = new TeeInputStream(body, requestStream);
            SoapMessageDecoder decoder = new SoapMessageDecoder(contentType, handler, new SaxSoapParserImpl());
            decoder.parse(teeInputStream);

            return new SoapRequestData(contentType, (SoapMessageImpl) handler.getSoapMessage(),
                    handler.getAttachmentDigests(), requestStream);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    @Getter
    private static final class SoapRequestMessageHandler implements SoapMessageDecoder.Callback {

        private SoapMessage soapMessage;
        private final List<byte[]> attachmentDigests = new ArrayList<>();

        @Override
        public void fault(SoapFault fault) throws Exception {
            log.debug("soap fault");
        }

        @Override
        public void onCompleted() {
        }

        @Override
        public void onError(Exception t) throws Exception {
        }

        @Override
        public void soap(SoapMessage message, Map<String, String> additionalHeaders) throws Exception {
            this.soapMessage = message;
        }

        @Override
        public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders) throws Exception {
            byte[] attachmentDigest = CryptoUtils.calculateDigest(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID, content);
            attachmentDigests.add(attachmentDigest);
        }
    }

}
