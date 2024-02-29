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
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.SaxSoapParserImpl;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.niis.xroad.edc.sig.XrdSignatureService;
import org.niis.xroad.proxy.clientproxy.validate.RequestValidator;
import org.niis.xroad.proxy.clientproxy.validate.SoapResponseValidator;
import org.niis.xroad.proxy.edc.AssetAuthorizationManager;
import org.niis.xroad.proxy.edc.AuthorizedAssetRegistry;
import org.niis.xroad.proxy.edc.EdcHttpResponse;
import org.niis.xroad.proxy.edc.TargetSecurityServerLookup;
import org.niis.xroad.proxy.edc.XrdDataSpaceClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.UUID;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.JettyUtils.getContentType;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.io.Content.Sink.asOutputStream;
import static org.eclipse.jetty.io.Content.Source.asInputStream;

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

    ClientSoapMessageDsProcessor(Request request, Response response,
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


        SoapMessageImpl requestSoap = deserializeToSoap(getContentType(jRequest), asInputStream(jRequest));

        updateOpMonitoringDataBySoapMessage(opMonitoringData, requestSoap);
        requestValidator.validateSoap(requestSoap, clientCert);

        var targetServers = TargetSecurityServerLookup.resolveTargetSecurityServers(requestSoap.getService().getClientId());

        //TODO xroad8 in POC we're not selecting fastest server, neither handle failure with fallbacks
        var targetServerInfo = targetServers.servers().stream().findFirst().orElseThrow();

        ClientId client = requestSoap.getClient();
        var assetInfo = assetAuthorizationManager.getOrRequestAssetAccess(client, targetServerInfo, requestSoap.getService());

        processRequest(requestSoap, assetInfo);
    }

    private void updateOpMonitoringClientSecurityServerAddress() {
        try {
            opMonitoringData.setClientSecurityServerAddress(getSecurityServerAddress());
        } catch (Exception e) {
            log.error("Failed to assign operational monitoring data field {}",
                    OpMonitoringData.CLIENT_SECURITY_SERVER_ADDRESS, e);
        }
    }

    private void processRequest(SoapMessageImpl requestSoap, AuthorizedAssetRegistry.GrantedAssetInfo assetInfo) throws Exception {
        log.trace("processRequest()");

        var clientRequest = new XrdDataSpaceClient.XrdClientRequest(
                XrdDataSpaceClient.XrdClientSource.SOAP,
                RestRequest.Verb.POST.name(),
                requestSoap.getClient(),
                "",
                new HashMap<>(),
                requestSoap.getBytes(),
                requestSoap.getService(),
                "");

        opMonitoringData.setRequestOutTs(getEpochMillisecond());

        var response = xrdDataSpaceClient.processRequest(clientRequest, assetInfo);

        opMonitoringData.setResponseInTs(getEpochMillisecond());

        validateResponse(requestSoap, response);

        opMonitoringData.setResponseOutTs(getEpochMillisecond(), true);

        processResponse(response, jResponse);
    }

    private void validateResponse(SoapMessageImpl requestSoap, EdcHttpResponse response) throws Exception {
        SoapMessageImpl responseSoap = deserializeToSoap(response.contentType(), new ByteArrayInputStream(response.body()));

        responseValidator.checkConsistency(requestSoap, responseSoap);

        //TODO handle bad request/edc failure
        xrdSignatureService.verify(response.headers(), response.body(), requestSoap.getService().getClientId());

    }

    private void processResponse(EdcHttpResponse response, Response jResponse) throws Exception {
        log.trace("sendResponse()");

        jResponse.setStatus(OK_200);
        response.headers().forEach(jResponse.getHeaders()::add);
        try (InputStream body = new ByteArrayInputStream(response.body())) {
            IOUtils.copy(body, asOutputStream(jResponse));
        }

        logResponseMessage();
    }

    private void logResponseMessage() {
        log.trace("logResponseMessage()");

//        MessageLog.log(response.getSoap(), response.getSignature(), true, xRequestId);
    }

    private SoapMessageImpl deserializeToSoap(String contentType, InputStream body) {
        try {
            Soap soap = new SaxSoapParserImpl().parse(contentType, body);
            if (soap instanceof SoapFault fault) {
                throw new RuntimeException("Soap fault: " + fault.toCodedException());
            } else if (soap instanceof SoapMessageImpl soapMessage) {
                return soapMessage;
            } else {
                throw new RuntimeException("Unexpected soap type: " + soap.getClass().getName());
            }
        } catch (Exception e) {
            throw translateException(e);
        }
    }

}
